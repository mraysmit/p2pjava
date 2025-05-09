package dev.mars.p2pjava.circuit;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);

    /**
     * Creates a new CircuitBreaker with the specified parameters.
     *
     * @param name Name of this circuit breaker (for logging)
     * @param failureThreshold Number of failures before opening the circuit
     * @param resetTimeoutMs Time in milliseconds before attempting to reset the circuit
     */
    public CircuitBreaker(String name, int failureThreshold, long resetTimeoutMs) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.resetTimeoutMs = resetTimeoutMs;
    }

    /**
     * Creates a new CircuitBreaker with default parameters.
     *
     * @param name Name of this circuit breaker
     */
    public CircuitBreaker(String name) {
        this(name, 5, 30000); // Default: 5 failures, 30 second reset
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
        if (isOpen()) {
            // Check if it's time to try resetting the circuit
            if (System.currentTimeMillis() - lastFailureTime.get() > resetTimeoutMs) {
                logger.log(Level.INFO, "{0}: Moving to HALF_OPEN state", name);
                state.compareAndSet(State.OPEN, State.HALF_OPEN);
            } else {
                // Circuit is open and reset timeout hasn't elapsed
                logger.log(Level.INFO, "{0}: Circuit OPEN, failing fast", name);
                throw new CircuitBreakerOpenException(name + " circuit is OPEN");
            }
        }

        try {
            // Execute the operation
            T result = operation.call();

            // If successful and in HALF_OPEN, reset the circuit
            if (state.get() == State.HALF_OPEN) {
                reset();
            }

            return result;
        } catch (Exception e) {
            // Record the failure
            recordFailure(e);
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
        } catch (CircuitBreakerOpenException e) {
            logger.log(Level.INFO, "{0}: Using fallback value due to open circuit", name);
            return defaultValue.get();
        }
    }

    /**
     * Records a failure and potentially opens the circuit if the threshold is reached.
     *
     * @param e The exception that caused the failure
     */
    private void recordFailure(Exception e) {
        lastFailureTime.set(System.currentTimeMillis());

        // If we're already in OPEN state, no need to increment
        if (state.get() == State.OPEN) {
            return;
        }

        // Increment failure count
        int failures = failureCount.incrementAndGet();
        logger.log(Level.WARNING, "{0}: Failure #{1} - {2}", new Object[]{name, failures, e.getMessage()});

        // If we've reached the threshold, open the circuit
        if (failures >= failureThreshold) {
            logger.log(Level.WARNING, "{0}: OPENING circuit after {1} failures", new Object[]{name, failures});
            // Use compareAndSet to avoid race conditions when multiple threads are recording failures
            state.compareAndSet(State.CLOSED, State.OPEN);
            state.compareAndSet(State.HALF_OPEN, State.OPEN);
        }
    }

    /**
     * Resets the circuit breaker to the CLOSED state.
     */
    public void reset() {
        logger.log(Level.INFO, "{0}: Resetting circuit breaker", name);
        failureCount.set(0);
        state.set(State.CLOSED);
    }

    /**
     * Checks if the circuit is currently open (including HALF_OPEN state).
     *
     * @return true if the circuit is open, false otherwise
     */
    public boolean isOpen() {
        return state.get() == State.OPEN || state.get() == State.HALF_OPEN;
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
     * Exception thrown when a call is attempted while the circuit is open.
     */
    public static class CircuitBreakerOpenException extends Exception {
        public CircuitBreakerOpenException(String message) {
            super(message);
        }
    }
}
