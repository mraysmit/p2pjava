package dev.mars.p2pjava.circuit;

import dev.mars.p2pjava.common.exception.ServiceException;
import dev.mars.p2pjava.util.ServiceMonitor;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Noddy implementation of the Circuit Breaker pattern to prevent cascading failures
 * when a service is unavailable or experiencing issues.
 * 
 * The circuit breaker has three states:
 * - CLOSED: Normal operation, requests are allowed through
 * - OPEN: Service is considered unavailable, requests fail fast without attempting to call the service
 * - HALF_OPEN: After a reset timeout, allows a limited number of test requests to determine if the service is healthy again
 */
public class CircuitBreaker {
    private static final Logger logger = Logger.getLogger(CircuitBreaker.class.getName());

    public enum State {
        CLOSED,     // Normal operation
        OPEN,       // Circuit is open, calls fail fast
        HALF_OPEN   // Testing if service is back online
    }

    private final String name;
    private final int failureThreshold;
    private final long resetTimeoutMs;
    private final int successThreshold; // Number of successful calls needed to close circuit in HALF_OPEN
    private final Predicate<Exception> failureFilter; // Determines which exceptions should trigger circuit breaker
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0); // Track successes in HALF_OPEN state
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final ServiceMonitor.ServiceMetrics metrics;

    /**
     * Creates a new CircuitBreaker with the specified parameters.
     *
     * @param name Name of this circuit breaker (for logging)
     * @param failureThreshold Number of failures before opening the circuit
     * @param resetTimeoutMs Time in milliseconds before attempting to reset the circuit
     * @param successThreshold Number of successful calls needed to close circuit in HALF_OPEN state
     * @param failureFilter Predicate to determine which exceptions should trigger the circuit breaker
     */
    public CircuitBreaker(String name, int failureThreshold, long resetTimeoutMs,
                         int successThreshold, Predicate<Exception> failureFilter) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.resetTimeoutMs = resetTimeoutMs;
        this.successThreshold = successThreshold;
        this.failureFilter = failureFilter != null ? failureFilter : (e -> true);
        this.metrics = ServiceMonitor.registerService("CircuitBreaker-" + name);
    }

    /**
     * Creates a new CircuitBreaker with default parameters.
     *
     * @param name Name of this circuit breaker
     */
    public CircuitBreaker(String name) {
        this(name, 5, 30000, 3, null); // Default: 5 failures, 30 second reset, 3 successes to close
    }

    /**
     * Creates a new CircuitBreaker with custom failure threshold and reset timeout.
     *
     * @param name Name of this circuit breaker
     * @param failureThreshold Number of failures before opening the circuit
     * @param resetTimeoutMs Time in milliseconds before attempting to reset the circuit
     */
    public CircuitBreaker(String name, int failureThreshold, long resetTimeoutMs) {
        this(name, failureThreshold, resetTimeoutMs, 3, null);
    }

    /**
     * Executes the given operation with circuit breaker protection.
     *
     * @param operation The operation to execute
     * @param <T> The return type of the operation
     * @return The result of the operation
     * @throws Exception If the operation fails or the circuit is open
     */
    public <T> T execute(Callable<T> operation) throws Exception {
        long startTime = System.currentTimeMillis();

        if (state.get() == State.OPEN) {
            // Check if it's time to try resetting the circuit
            if (System.currentTimeMillis() - lastFailureTime.get() > resetTimeoutMs) {
                logger.log(Level.INFO, "{0}: Moving to HALF_OPEN state", name);
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    successCount.set(0); // Reset success counter
                }
            } else {
                // Circuit is open and reset timeout hasn't elapsed
                logger.log(Level.INFO, "{0}: Circuit OPEN, failing fast", name);
                metrics.recordRequest(System.currentTimeMillis() - startTime, true);
                throw new ServiceException.CircuitBreakerOpenException(name);
            }
        }

        try {
            // Execute the operation
            T result = operation.call();

            // Record successful execution
            recordSuccess();
            metrics.recordRequest(System.currentTimeMillis() - startTime, false);

            return result;
        } catch (Exception e) {
            // Record the failure if it matches our failure filter
            if (failureFilter.test(e)) {
                recordFailure(e);
            }
            metrics.recordRequest(System.currentTimeMillis() - startTime, true);
            throw e;
        }
    }

    /**
     * Executes the given operation with circuit breaker protection and returns a default value if the circuit is open.
     *
     * @param operation The operation to execute
     * @param defaultValue Supplier for the default value to return if the circuit is open
     * @param <T> The return type of the operation
     * @return The result of the operation or the default value
     * @throws Exception If the operation fails and the circuit is not open
     */
    public <T> T executeWithFallback(Callable<T> operation, Supplier<T> defaultValue) throws Exception {
        try {
            return execute(operation);
        } catch (ServiceException.CircuitBreakerOpenException e) {
            logger.log(Level.INFO, "{0}: Using fallback value due to open circuit", name);
            metrics.incrementCounter("fallbackUsed");
            return defaultValue.get();
        }
    }

    /**
     * Records a successful operation execution.
     */
    private void recordSuccess() {
        State currentState = state.get();

        if (currentState == State.HALF_OPEN) {
            int successes = successCount.incrementAndGet();
            logger.log(Level.INFO, "{0}: Success #{1} in HALF_OPEN state", new Object[]{name, successes});

            // If we've reached the success threshold, close the circuit
            if (successes >= successThreshold) {
                logger.log(Level.INFO, "{0}: CLOSING circuit after {1} successes", new Object[]{name, successes});
                reset();
            }
        } else if (currentState == State.CLOSED) {
            // Reset failure count on successful operation in CLOSED state
            failureCount.set(0);
        }
    }

    /**
     * Records a failure and potentially opens the circuit if the threshold is reached.
     *
     * @param e The exception that caused the failure
     */
    private void recordFailure(Exception e) {
        lastFailureTime.set(System.currentTimeMillis());
        State currentState = state.get();

        // If we're already in OPEN state, no need to increment
        if (currentState == State.OPEN) {
            return;
        }

        // In HALF_OPEN state, any failure immediately opens the circuit
        if (currentState == State.HALF_OPEN) {
            logger.log(Level.WARNING, "{0}: Failure in HALF_OPEN state, reopening circuit - {1}",
                      new Object[]{name, e.getMessage()});
            state.compareAndSet(State.HALF_OPEN, State.OPEN);
            successCount.set(0);
            metrics.incrementCounter("circuitReopened");
            return;
        }

        // Increment failure count in CLOSED state
        int failures = failureCount.incrementAndGet();
        logger.log(Level.WARNING, "{0}: Failure #{1} - {2}", new Object[]{name, failures, e.getMessage()});

        // If we've reached the threshold, open the circuit
        if (failures >= failureThreshold) {
            logger.log(Level.WARNING, "{0}: OPENING circuit after {1} failures", new Object[]{name, failures});
            if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                metrics.incrementCounter("circuitOpened");
            }
        }
    }

    /**
     * Resets the circuit breaker to the CLOSED state.
     */
    public void reset() {
        logger.log(Level.INFO, "{0}: Resetting circuit breaker", name);
        failureCount.set(0);
        successCount.set(0);
        state.set(State.CLOSED);
        metrics.incrementCounter("circuitReset");
    }

    /**
     * Checks if the circuit is currently open.
     *
     * @return true if the circuit is open, false otherwise
     */
    public boolean isOpen() {
        return state.get() == State.OPEN;
    }

    /**
     * Checks if the circuit is currently in half-open state.
     *
     * @return true if the circuit is half-open, false otherwise
     */
    public boolean isHalfOpen() {
        return state.get() == State.HALF_OPEN;
    }

    /**
     * Gets the current state of the circuit breaker.
     *
     * @return The current state
     */
    public State getState() {
        return state.get();
    }

    /**
     * Gets the current failure count.
     *
     * @return The current failure count
     */
    public int getFailureCount() {
        return failureCount.get();
    }

    /**
     * Gets the current success count (relevant in HALF_OPEN state).
     *
     * @return The current success count
     */
    public int getSuccessCount() {
        return successCount.get();
    }

    /**
     * Gets the metrics for this circuit breaker.
     *
     * @return The ServiceMetrics object
     */
    public ServiceMonitor.ServiceMetrics getMetrics() {
        return metrics;
    }

    /**
     * Exception thrown when a call is attempted while the circuit is open.
     * @deprecated Use ServiceException.CircuitBreakerOpenException instead
     */
    @Deprecated
    public static class CircuitBreakerOpenException extends Exception {
        public CircuitBreakerOpenException(String message) {
            super(message);
        }
    }
}
