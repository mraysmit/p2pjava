package dev.mars.p2pjava.util;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * Circuit breaker implementation for preventing cascading failures.
 * Provides automatic failure detection and recovery with configurable thresholds.
 */
public class CircuitBreaker {
    private static final Logger logger = Logger.getLogger(CircuitBreaker.class.getName());
    
    public enum State {
        CLOSED,    // Normal operation
        OPEN,      // Circuit is open, calls fail fast
        HALF_OPEN  // Testing if service has recovered
    }
    
    private final String name;
    private final int failureThreshold;
    private final Duration timeout;
    private final Duration retryTimeout;
    private final Predicate<Exception> failurePredicate;
    
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final AtomicLong lastSuccessTime = new AtomicLong(0);
    
    /**
     * Creates a circuit breaker with default configuration.
     */
    public CircuitBreaker(String name) {
        this(name, 5, Duration.ofSeconds(60), Duration.ofSeconds(30), 
             e -> !(e instanceof IllegalArgumentException));
    }
    
    /**
     * Creates a circuit breaker with custom configuration.
     */
    public CircuitBreaker(String name, int failureThreshold, Duration timeout, 
                         Duration retryTimeout, Predicate<Exception> failurePredicate) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.timeout = timeout;
        this.retryTimeout = retryTimeout;
        this.failurePredicate = failurePredicate;
        
        logger.info("Circuit breaker '" + name + "' created with threshold=" + failureThreshold + 
                   ", timeout=" + timeout + ", retryTimeout=" + retryTimeout);
    }
    
    /**
     * Executes a callable with circuit breaker protection.
     */
    public <T> T execute(Callable<T> operation) throws Exception {
        if (!canExecute()) {
            throw new CircuitBreakerOpenException("Circuit breaker '" + name + "' is OPEN");
        }
        
        try {
            T result = operation.call();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure(e);
            throw e;
        }
    }
    
    /**
     * Executes a runnable with circuit breaker protection.
     */
    public void execute(Runnable operation) throws Exception {
        execute(() -> {
            operation.run();
            return null;
        });
    }
    
    /**
     * Executes a callable with circuit breaker protection and fallback.
     */
    public <T> T executeWithFallback(Callable<T> operation, Callable<T> fallback) {
        try {
            return execute(operation);
        } catch (Exception e) {
            logger.warning("Circuit breaker '" + name + "' operation failed, using fallback: " + e.getMessage());
            try {
                return fallback.call();
            } catch (Exception fallbackException) {
                logger.severe("Circuit breaker '" + name + "' fallback also failed: " + fallbackException.getMessage());
                throw new RuntimeException("Both operation and fallback failed", fallbackException);
            }
        }
    }
    
    /**
     * Checks if the circuit breaker allows execution.
     */
    private boolean canExecute() {
        State currentState = state.get();
        
        switch (currentState) {
            case CLOSED:
                return true;
                
            case OPEN:
                if (shouldAttemptReset()) {
                    state.compareAndSet(State.OPEN, State.HALF_OPEN);
                    logger.info("Circuit breaker '" + name + "' transitioning to HALF_OPEN");
                    return true;
                }
                return false;
                
            case HALF_OPEN:
                return true;
                
            default:
                return false;
        }
    }
    
    /**
     * Handles successful operation execution.
     */
    private void onSuccess() {
        lastSuccessTime.set(System.currentTimeMillis());
        successCount.incrementAndGet();
        
        State currentState = state.get();
        if (currentState == State.HALF_OPEN) {
            reset();
            logger.info("Circuit breaker '" + name + "' reset to CLOSED after successful test");
        }
    }
    
    /**
     * Handles failed operation execution.
     */
    private void onFailure(Exception exception) {
        if (!failurePredicate.test(exception)) {
            // Don't count this as a failure for circuit breaker purposes
            return;
        }
        
        lastFailureTime.set(System.currentTimeMillis());
        int failures = failureCount.incrementAndGet();
        
        State currentState = state.get();
        if (currentState == State.HALF_OPEN) {
            // Failed during test, go back to OPEN
            state.set(State.OPEN);
            logger.warning("Circuit breaker '" + name + "' test failed, returning to OPEN");
        } else if (currentState == State.CLOSED && failures >= failureThreshold) {
            // Too many failures, open the circuit
            state.set(State.OPEN);
            logger.warning("Circuit breaker '" + name + "' opened after " + failures + " failures");
        }
    }
    
    /**
     * Checks if we should attempt to reset the circuit breaker.
     */
    private boolean shouldAttemptReset() {
        long timeSinceLastFailure = System.currentTimeMillis() - lastFailureTime.get();
        return timeSinceLastFailure >= retryTimeout.toMillis();
    }
    
    /**
     * Resets the circuit breaker to CLOSED state.
     */
    public void reset() {
        state.set(State.CLOSED);
        failureCount.set(0);
        logger.info("Circuit breaker '" + name + "' manually reset to CLOSED");
    }
    
    /**
     * Forces the circuit breaker to OPEN state.
     */
    public void forceOpen() {
        state.set(State.OPEN);
        lastFailureTime.set(System.currentTimeMillis());
        logger.warning("Circuit breaker '" + name + "' manually forced to OPEN");
    }
    
    /**
     * Gets the current state of the circuit breaker.
     */
    public State getState() {
        return state.get();
    }
    
    /**
     * Gets the current failure count.
     */
    public int getFailureCount() {
        return failureCount.get();
    }
    
    /**
     * Gets the current success count.
     */
    public int getSuccessCount() {
        return successCount.get();
    }
    
    /**
     * Gets the name of the circuit breaker.
     */
    public String getName() {
        return name;
    }
    
    /**
     * Gets circuit breaker statistics.
     */
    public CircuitBreakerStats getStats() {
        return new CircuitBreakerStats(
            name,
            state.get(),
            failureCount.get(),
            successCount.get(),
            lastFailureTime.get(),
            lastSuccessTime.get(),
            failureThreshold
        );
    }
    
    /**
     * Circuit breaker statistics.
     */
    public static class CircuitBreakerStats {
        private final String name;
        private final State state;
        private final int failureCount;
        private final int successCount;
        private final long lastFailureTime;
        private final long lastSuccessTime;
        private final int failureThreshold;
        
        CircuitBreakerStats(String name, State state, int failureCount, int successCount,
                           long lastFailureTime, long lastSuccessTime, int failureThreshold) {
            this.name = name;
            this.state = state;
            this.failureCount = failureCount;
            this.successCount = successCount;
            this.lastFailureTime = lastFailureTime;
            this.lastSuccessTime = lastSuccessTime;
            this.failureThreshold = failureThreshold;
        }
        
        public String getName() { return name; }
        public State getState() { return state; }
        public int getFailureCount() { return failureCount; }
        public int getSuccessCount() { return successCount; }
        public long getLastFailureTime() { return lastFailureTime; }
        public long getLastSuccessTime() { return lastSuccessTime; }
        public int getFailureThreshold() { return failureThreshold; }
        
        public double getFailureRate() {
            int total = failureCount + successCount;
            return total > 0 ? (double) failureCount / total : 0.0;
        }
        
        @Override
        public String toString() {
            return "CircuitBreakerStats{" +
                    "name='" + name + '\'' +
                    ", state=" + state +
                    ", failureCount=" + failureCount +
                    ", successCount=" + successCount +
                    ", failureRate=" + String.format("%.2f%%", getFailureRate() * 100) +
                    ", threshold=" + failureThreshold +
                    '}';
        }
    }
    
    /**
     * Exception thrown when circuit breaker is open.
     */
    public static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String message) {
            super(message);
        }
    }
    
    /**
     * Builder for CircuitBreaker.
     */
    public static class Builder {
        private String name;
        private int failureThreshold = 5;
        private Duration timeout = Duration.ofSeconds(60);
        private Duration retryTimeout = Duration.ofSeconds(30);
        private Predicate<Exception> failurePredicate = e -> !(e instanceof IllegalArgumentException);
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder failureThreshold(int threshold) {
            this.failureThreshold = threshold;
            return this;
        }
        
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }
        
        public Builder retryTimeout(Duration retryTimeout) {
            this.retryTimeout = retryTimeout;
            return this;
        }
        
        public Builder failurePredicate(Predicate<Exception> predicate) {
            this.failurePredicate = predicate;
            return this;
        }
        
        public CircuitBreaker build() {
            if (name == null) {
                throw new IllegalArgumentException("Circuit breaker name is required");
            }
            return new CircuitBreaker(name, failureThreshold, timeout, retryTimeout, failurePredicate);
        }
    }
    
    /**
     * Creates a builder for CircuitBreaker.
     */
    public static Builder builder() {
        return new Builder();
    }
}
