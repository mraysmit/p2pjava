package dev.mars.p2pjava.util;

/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Comprehensive error handling manager that combines circuit breakers,
 * retry mechanisms, and fallback strategies for robust error recovery.
 */
public class ErrorHandlingManager {
    private static final Logger logger = Logger.getLogger(ErrorHandlingManager.class.getName());
    
    private final RetryManager retryManager;
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final Map<String, Function<Exception, ?>> fallbackStrategies = new ConcurrentHashMap<>();
    
    /**
     * Creates an ErrorHandlingManager with default retry configuration.
     */
    public ErrorHandlingManager() {
        this.retryManager = new RetryManager();
    }
    
    /**
     * Creates an ErrorHandlingManager with custom retry manager.
     */
    public ErrorHandlingManager(RetryManager retryManager) {
        this.retryManager = retryManager;
    }
    
    /**
     * Registers a circuit breaker for a specific operation.
     */
    public void registerCircuitBreaker(String operationName, CircuitBreaker circuitBreaker) {
        circuitBreakers.put(operationName, circuitBreaker);
        logger.info("Registered circuit breaker for operation: " + operationName);
    }
    
    /**
     * Registers a circuit breaker with default configuration.
     */
    public void registerCircuitBreaker(String operationName) {
        CircuitBreaker circuitBreaker = new CircuitBreaker(operationName);
        registerCircuitBreaker(operationName, circuitBreaker);
    }
    
    /**
     * Registers a fallback strategy for a specific operation.
     */
    public <T> void registerFallback(String operationName, Function<Exception, T> fallbackFunction) {
        fallbackStrategies.put(operationName, fallbackFunction);
        logger.info("Registered fallback strategy for operation: " + operationName);
    }
    
    /**
     * Registers a simple fallback value for a specific operation.
     */
    public <T> void registerFallback(String operationName, T fallbackValue) {
        registerFallback(operationName, exception -> fallbackValue);
    }
    
    /**
     * Registers a fallback supplier for a specific operation.
     */
    public <T> void registerFallback(String operationName, Supplier<T> fallbackSupplier) {
        registerFallback(operationName, exception -> fallbackSupplier.get());
    }
    
    /**
     * Executes an operation with full error handling (circuit breaker + retry + fallback).
     */
    public <T> T executeWithFullProtection(String operationName, Callable<T> operation) {
        CircuitBreaker circuitBreaker = circuitBreakers.get(operationName);
        Function<Exception, T> fallback = (Function<Exception, T>) fallbackStrategies.get(operationName);
        
        try {
            if (circuitBreaker != null) {
                // Execute with circuit breaker protection
                return circuitBreaker.execute(() -> {
                    // Execute with retry protection
                    return retryManager.executeWithRetry(operation);
                });
            } else {
                // Execute with retry protection only
                return retryManager.executeWithRetry(operation);
            }
        } catch (Exception e) {
            if (fallback != null) {
                logger.info("Operation '" + operationName + "' failed, using fallback: " + e.getMessage());
                return fallback.apply(e);
            } else {
                logger.warning("Operation '" + operationName + "' failed with no fallback available: " + e.getMessage());
                throw new RuntimeException("Operation failed: " + operationName, e);
            }
        }
    }
    
    /**
     * Executes an operation with circuit breaker protection only.
     */
    public <T> T executeWithCircuitBreaker(String operationName, Callable<T> operation) throws Exception {
        CircuitBreaker circuitBreaker = circuitBreakers.get(operationName);
        if (circuitBreaker != null) {
            return circuitBreaker.execute(operation);
        } else {
            logger.warning("No circuit breaker registered for operation: " + operationName);
            return operation.call();
        }
    }
    
    /**
     * Executes an operation with retry protection only.
     */
    public <T> T executeWithRetry(String operationName, Callable<T> operation) throws Exception {
        try {
            return retryManager.executeWithRetry(operation);
        } catch (Exception e) {
            logger.warning("Operation '" + operationName + "' failed after retries: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Executes an operation with fallback protection only.
     */
    public <T> T executeWithFallback(String operationName, Callable<T> operation) {
        try {
            return operation.call();
        } catch (Exception e) {
            Function<Exception, T> fallback = (Function<Exception, T>) fallbackStrategies.get(operationName);
            if (fallback != null) {
                logger.info("Operation '" + operationName + "' failed, using fallback: " + e.getMessage());
                return fallback.apply(e);
            } else {
                logger.warning("Operation '" + operationName + "' failed with no fallback available: " + e.getMessage());
                throw new RuntimeException("Operation failed: " + operationName, e);
            }
        }
    }
    
    /**
     * Creates a protected operation builder for fluent configuration.
     */
    public ProtectedOperationBuilder operation(String operationName) {
        return new ProtectedOperationBuilder(operationName, this);
    }
    
    /**
     * Gets circuit breaker statistics for all registered circuit breakers.
     */
    public Map<String, CircuitBreaker.CircuitBreakerStats> getCircuitBreakerStats() {
        Map<String, CircuitBreaker.CircuitBreakerStats> stats = new ConcurrentHashMap<>();
        circuitBreakers.forEach((name, cb) -> stats.put(name, cb.getStats()));
        return stats;
    }
    
    /**
     * Gets circuit breaker statistics for a specific operation.
     */
    public CircuitBreaker.CircuitBreakerStats getCircuitBreakerStats(String operationName) {
        CircuitBreaker circuitBreaker = circuitBreakers.get(operationName);
        return circuitBreaker != null ? circuitBreaker.getStats() : null;
    }
    
    /**
     * Resets a specific circuit breaker.
     */
    public void resetCircuitBreaker(String operationName) {
        CircuitBreaker circuitBreaker = circuitBreakers.get(operationName);
        if (circuitBreaker != null) {
            circuitBreaker.reset();
            logger.info("Reset circuit breaker for operation: " + operationName);
        }
    }
    
    /**
     * Resets all circuit breakers.
     */
    public void resetAllCircuitBreakers() {
        circuitBreakers.forEach((name, cb) -> {
            cb.reset();
            logger.info("Reset circuit breaker for operation: " + name);
        });
    }
    
    /**
     * Gets the retry manager.
     */
    public RetryManager getRetryManager() {
        return retryManager;
    }
    
    /**
     * Checks if a circuit breaker is registered for an operation.
     */
    public boolean hasCircuitBreaker(String operationName) {
        return circuitBreakers.containsKey(operationName);
    }
    
    /**
     * Checks if a fallback is registered for an operation.
     */
    public boolean hasFallback(String operationName) {
        return fallbackStrategies.containsKey(operationName);
    }
    
    /**
     * Logs the status of all error handling components.
     */
    public void logStatus() {
        logger.info("Error Handling Manager Status:");
        logger.info("  Circuit Breakers: " + circuitBreakers.size());
        logger.info("  Fallback Strategies: " + fallbackStrategies.size());
        
        circuitBreakers.forEach((name, cb) -> {
            logger.info("  " + cb.getStats());
        });
    }
    
    /**
     * Builder for protected operations with fluent configuration.
     */
    public static class ProtectedOperationBuilder {
        private final String operationName;
        private final ErrorHandlingManager manager;
        private boolean useCircuitBreaker = false;
        private boolean useRetry = false;
        private boolean useFallback = false;
        
        ProtectedOperationBuilder(String operationName, ErrorHandlingManager manager) {
            this.operationName = operationName;
            this.manager = manager;
        }
        
        public ProtectedOperationBuilder withCircuitBreaker() {
            this.useCircuitBreaker = true;
            return this;
        }
        
        public ProtectedOperationBuilder withRetry() {
            this.useRetry = true;
            return this;
        }
        
        public ProtectedOperationBuilder withFallback() {
            this.useFallback = true;
            return this;
        }
        
        public ProtectedOperationBuilder withAll() {
            return withCircuitBreaker().withRetry().withFallback();
        }
        
        public <T> T execute(Callable<T> operation) {
            if (useCircuitBreaker && useRetry && useFallback) {
                return manager.executeWithFullProtection(operationName, operation);
            } else if (useCircuitBreaker && useRetry) {
                try {
                    return manager.executeWithCircuitBreaker(operationName, () -> 
                        manager.retryManager.executeWithRetry(operation));
                } catch (Exception e) {
                    throw new RuntimeException("Operation failed: " + operationName, e);
                }
            } else if (useCircuitBreaker && useFallback) {
                return manager.executeWithFallback(operationName, () -> 
                    manager.executeWithCircuitBreaker(operationName, operation));
            } else if (useRetry && useFallback) {
                return manager.executeWithFallback(operationName, () -> 
                    manager.retryManager.executeWithRetry(operation));
            } else if (useCircuitBreaker) {
                try {
                    return manager.executeWithCircuitBreaker(operationName, operation);
                } catch (Exception e) {
                    throw new RuntimeException("Operation failed: " + operationName, e);
                }
            } else if (useRetry) {
                try {
                    return manager.executeWithRetry(operationName, operation);
                } catch (Exception e) {
                    throw new RuntimeException("Operation failed: " + operationName, e);
                }
            } else if (useFallback) {
                return manager.executeWithFallback(operationName, operation);
            } else {
                // No protection, execute directly
                try {
                    return operation.call();
                } catch (Exception e) {
                    throw new RuntimeException("Operation failed: " + operationName, e);
                }
            }
        }
    }
}
