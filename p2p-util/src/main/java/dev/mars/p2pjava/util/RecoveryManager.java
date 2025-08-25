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


import dev.mars.p2pjava.circuit.CircuitBreaker;
import dev.mars.p2pjava.circuit.CircuitBreakerFactory;
import dev.mars.p2pjava.common.exception.P2PException;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages recovery strategies for different types of operations and failures.
 * Provides a unified interface for retry logic, circuit breakers, and fallback mechanisms.
 */
public class RecoveryManager {
    private static final Logger logger = Logger.getLogger(RecoveryManager.class.getName());
    
    private static final ConcurrentHashMap<String, RecoveryStrategy> strategies = new ConcurrentHashMap<>();
    
    /**
     * Recovery strategy configuration for different operation types.
     */
    public static class RecoveryStrategy {
        public final boolean useRetry;
        public final int maxRetries;
        public final long initialBackoffMs;
        public final long maxBackoffMs;
        public final RetryHelper.BackoffStrategy backoffStrategy;
        
        public final boolean useCircuitBreaker;
        public final CircuitBreakerFactory.Config circuitBreakerConfig;
        
        public final boolean useFallback;
        
        public RecoveryStrategy(boolean useRetry, int maxRetries, long initialBackoffMs, 
                              long maxBackoffMs, RetryHelper.BackoffStrategy backoffStrategy,
                              boolean useCircuitBreaker, CircuitBreakerFactory.Config circuitBreakerConfig,
                              boolean useFallback) {
            this.useRetry = useRetry;
            this.maxRetries = maxRetries;
            this.initialBackoffMs = initialBackoffMs;
            this.maxBackoffMs = maxBackoffMs;
            this.backoffStrategy = backoffStrategy;
            this.useCircuitBreaker = useCircuitBreaker;
            this.circuitBreakerConfig = circuitBreakerConfig;
            this.useFallback = useFallback;
        }
    }
    
    // Predefined recovery strategies
    public static final RecoveryStrategy NETWORK_OPERATION_STRATEGY = new RecoveryStrategy(
        true, 3, 1000, 10000, RetryHelper.BackoffStrategy.EXPONENTIAL_JITTER,
        true, CircuitBreakerFactory.NETWORK_SERVICE_CONFIG,
        true
    );
    
    public static final RecoveryStrategy CRITICAL_OPERATION_STRATEGY = new RecoveryStrategy(
        true, 5, 500, 30000, RetryHelper.BackoffStrategy.EXPONENTIAL_JITTER,
        true, CircuitBreakerFactory.CRITICAL_SERVICE_CONFIG,
        true
    );
    
    public static final RecoveryStrategy FAST_OPERATION_STRATEGY = new RecoveryStrategy(
        true, 2, 100, 1000, RetryHelper.BackoffStrategy.LINEAR,
        false, null,
        false
    );
    
    public static final RecoveryStrategy TRACKER_OPERATION_STRATEGY = new RecoveryStrategy(
        true, 3, 1000, 15000, RetryHelper.BackoffStrategy.EXPONENTIAL_JITTER,
        true, CircuitBreakerFactory.TRACKER_SERVICE_CONFIG,
        true
    );
    
    public static final RecoveryStrategy INDEX_SERVER_STRATEGY = new RecoveryStrategy(
        true, 2, 2000, 20000, RetryHelper.BackoffStrategy.EXPONENTIAL_JITTER,
        true, CircuitBreakerFactory.INDEX_SERVER_CONFIG,
        true
    );
    
    /**
     * Registers a recovery strategy for a specific operation type.
     *
     * @param operationType The type of operation
     * @param strategy The recovery strategy to use
     */
    public static void registerStrategy(String operationType, RecoveryStrategy strategy) {
        strategies.put(operationType, strategy);
        logger.info("Registered recovery strategy for operation type: " + operationType);
    }
    
    /**
     * Executes an operation with the appropriate recovery strategy.
     *
     * @param operationType The type of operation
     * @param operation The operation to execute
     * @param fallback Optional fallback supplier
     * @param <T> The return type
     * @return The result of the operation or fallback
     * @throws Exception If the operation fails and no fallback is available
     */
    public static <T> T executeWithRecovery(String operationType, Callable<T> operation, 
                                          Supplier<T> fallback) throws Exception {
        RecoveryStrategy strategy = strategies.get(operationType);
        if (strategy == null) {
            logger.warning("No recovery strategy found for operation type: " + operationType + 
                          ", using default network strategy");
            strategy = NETWORK_OPERATION_STRATEGY;
        }
        
        return executeWithStrategy(operationType, operation, fallback, strategy);
    }
    
    /**
     * Executes an operation asynchronously with recovery strategy.
     *
     * @param operationType The type of operation
     * @param operation The operation to execute
     * @param fallback Optional fallback supplier
     * @param <T> The return type
     * @return CompletableFuture with the result
     */
    public static <T> CompletableFuture<T> executeAsyncWithRecovery(String operationType, 
                                                                   Callable<T> operation, 
                                                                   Supplier<T> fallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeWithRecovery(operationType, operation, fallback);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
    
    private static <T> T executeWithStrategy(String operationType, Callable<T> operation, 
                                           Supplier<T> fallback, RecoveryStrategy strategy) throws Exception {
        Callable<T> wrappedOperation = operation;
        
        // Wrap with retry logic if enabled
        if (strategy.useRetry) {
            wrappedOperation = () -> RetryHelper.executeWithRetry(
                operation, 
                strategy.maxRetries, 
                strategy.initialBackoffMs, 
                strategy.maxBackoffMs,
                RetryHelper.createSmartRetryPredicate(),
                strategy.backoffStrategy
            );
        }
        
        // Wrap with circuit breaker if enabled
        if (strategy.useCircuitBreaker) {
            CircuitBreaker circuitBreaker = CircuitBreakerFactory.getOrCreate(
                operationType, strategy.circuitBreakerConfig);
            
            if (strategy.useFallback && fallback != null) {
                return circuitBreaker.executeWithFallback(wrappedOperation, fallback);
            } else {
                return circuitBreaker.execute(wrappedOperation);
            }
        }
        
        // Execute without circuit breaker
        try {
            return wrappedOperation.call();
        } catch (Exception e) {
            if (strategy.useFallback && fallback != null) {
                logger.log(Level.WARNING, "Operation failed, using fallback: {0}", e.getMessage());
                return fallback.get();
            }
            throw e;
        }
    }
    
    /**
     * Gets the recovery strategy for an operation type.
     *
     * @param operationType The operation type
     * @return The recovery strategy, or null if not found
     */
    public static RecoveryStrategy getStrategy(String operationType) {
        return strategies.get(operationType);
    }
    
    /**
     * Removes a recovery strategy.
     *
     * @param operationType The operation type
     * @return The removed strategy, or null if not found
     */
    public static RecoveryStrategy removeStrategy(String operationType) {
        return strategies.remove(operationType);
    }
    
    /**
     * Clears all registered strategies.
     */
    public static void clearStrategies() {
        strategies.clear();
        logger.info("Cleared all recovery strategies");
    }
    
    // Initialize default strategies
    static {
        registerStrategy("network", NETWORK_OPERATION_STRATEGY);
        registerStrategy("critical", CRITICAL_OPERATION_STRATEGY);
        registerStrategy("fast", FAST_OPERATION_STRATEGY);
        registerStrategy("tracker", TRACKER_OPERATION_STRATEGY);
        registerStrategy("indexServer", INDEX_SERVER_STRATEGY);
    }
}
