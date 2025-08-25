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


import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AsyncOperationManager provides high-level utilities for managing asynchronous operations
 * using CompletableFuture with proper error handling, timeouts, and monitoring.
 */
public class AsyncOperationManager {
    private static final Logger logger = Logger.getLogger(AsyncOperationManager.class.getName());

    /**
     * Helper method to get ExecutorService from ThreadManager.
     */
    private static ExecutorService getExecutorService(String poolName) {
        ExecutorService executor = ThreadManager.getThreadPool(poolName);
        if (executor == null) {
            throw new IllegalArgumentException("Thread pool not found: " + poolName);
        }
        return executor;
    }
    
    /**
     * Executes an operation with timeout and proper error handling.
     *
     * @param <T> The return type of the operation
     * @param poolName The thread pool to use
     * @param operation The operation to execute
     * @param timeoutMs Timeout in milliseconds
     * @param operationName Name for logging purposes
     * @return CompletableFuture with the result
     */
    public static <T> CompletableFuture<T> executeWithTimeout(
            String poolName, 
            Supplier<T> operation, 
            long timeoutMs, 
            String operationName) {
        
        logger.info("Starting async operation: " + operationName);
        long startTime = System.currentTimeMillis();
        
        return ThreadManager.executeAsync(poolName, operation)
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .whenComplete((result, throwable) -> {
                    long duration = System.currentTimeMillis() - startTime;
                    if (throwable != null) {
                        if (throwable instanceof TimeoutException) {
                            logger.warning(String.format(
                                "Operation '%s' timed out after %d ms", operationName, duration));
                        } else {
                            logger.log(Level.WARNING, 
                                String.format("Operation '%s' failed after %d ms", operationName, duration), 
                                throwable);
                        }
                    } else {
                        logger.info(String.format(
                            "Operation '%s' completed successfully in %d ms", operationName, duration));
                    }
                });
    }
    
    /**
     * Creates a sequential chain of async operations with error handling.
     *
     * @param <T> Type of first operation result
     * @param <U> Type of second operation result
     * @param <V> Type of final result
     * @param poolName Thread pool to use
     * @param firstOperation First operation to execute
     * @param secondOperation Second operation that depends on first
     * @param finalOperation Final operation that depends on second
     * @param operationName Name for logging
     * @return CompletableFuture with final result
     */
    public static <T, U, V> CompletableFuture<V> executeSequentialChain(
            String poolName,
            Supplier<T> firstOperation,
            Function<T, U> secondOperation,
            Function<U, V> finalOperation,
            String operationName) {
        
        logger.info("Starting sequential operation chain: " + operationName);
        long startTime = System.currentTimeMillis();
        
        ExecutorService executor = getExecutorService(poolName);
        return ThreadManager.executeAsync(poolName, firstOperation)
                .thenApplyAsync(secondOperation, executor)
                .thenApplyAsync(finalOperation, executor)
                .whenComplete((result, throwable) -> {
                    long duration = System.currentTimeMillis() - startTime;
                    if (throwable != null) {
                        logger.log(Level.WARNING, 
                            String.format("Operation chain '%s' failed after %d ms", operationName, duration), 
                            throwable);
                    } else {
                        logger.info(String.format(
                            "Operation chain '%s' completed successfully in %d ms", operationName, duration));
                    }
                });
    }
    
    /**
     * Executes multiple operations in parallel and combines results.
     *
     * @param poolName Thread pool to use
     * @param operations Array of operations to execute in parallel
     * @param operationName Name for logging
     * @return CompletableFuture with list of results
     */
    @SafeVarargs
    public static <T> CompletableFuture<java.util.List<T>> executeParallel(
            String poolName,
            String operationName,
            Supplier<T>... operations) {

        logger.info("Starting parallel operations: " + operationName + " (count: " + operations.length + ")");
        long startTime = System.currentTimeMillis();

        @SuppressWarnings("unchecked")
        CompletableFuture<T>[] futures = new CompletableFuture[operations.length];

        for (int i = 0; i < operations.length; i++) {
            futures[i] = ThreadManager.executeAsync(poolName, operations[i]);
        }

        return CompletableFuture.allOf(futures)
                .thenApply(v -> {
                    java.util.List<T> results = new java.util.ArrayList<>();
                    for (int i = 0; i < futures.length; i++) {
                        results.add(futures[i].join());
                    }
                    return results;
                })
                .whenComplete((results, throwable) -> {
                    long duration = System.currentTimeMillis() - startTime;
                    if (throwable != null) {
                        logger.log(Level.WARNING, 
                            String.format("Parallel operations '%s' failed after %d ms", operationName, duration), 
                            throwable);
                    } else {
                        logger.info(String.format(
                            "Parallel operations '%s' completed successfully in %d ms", operationName, duration));
                    }
                });
    }
    
    /**
     * Executes an operation with retry logic and exponential backoff.
     *
     * @param <T> Return type of the operation
     * @param poolName Thread pool to use
     * @param operation Operation to execute
     * @param maxRetries Maximum number of retries
     * @param initialDelayMs Initial delay between retries
     * @param operationName Name for logging
     * @return CompletableFuture with result
     */
    public static <T> CompletableFuture<T> executeWithRetry(
            String poolName,
            Supplier<T> operation,
            int maxRetries,
            long initialDelayMs,
            String operationName) {
        
        return executeWithRetryInternal(poolName, operation, maxRetries, initialDelayMs, 0, operationName);
    }
    
    private static <T> CompletableFuture<T> executeWithRetryInternal(
            String poolName,
            Supplier<T> operation,
            int maxRetries,
            long delayMs,
            int currentAttempt,
            String operationName) {
        
        return ThreadManager.executeAsync(poolName, operation)
                .handle((result, throwable) -> {
                    if (throwable == null) {
                        if (currentAttempt > 0) {
                            logger.info(String.format(
                                "Operation '%s' succeeded on attempt %d", operationName, currentAttempt + 1));
                        }
                        return CompletableFuture.completedFuture(result);
                    }
                    
                    if (currentAttempt >= maxRetries) {
                        logger.warning(String.format(
                            "Operation '%s' failed after %d attempts", operationName, currentAttempt + 1));
                        CompletableFuture<T> failedFuture = new CompletableFuture<>();
                        failedFuture.completeExceptionally(throwable);
                        return failedFuture;
                    }
                    
                    logger.info(String.format(
                        "Operation '%s' failed on attempt %d, retrying in %d ms", 
                        operationName, currentAttempt + 1, delayMs));
                    
                    CompletableFuture<T> delayedRetry = new CompletableFuture<>();
                    CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS)
                            .execute(() -> {
                                executeWithRetryInternal(poolName, operation, maxRetries, delayMs * 2, currentAttempt + 1, operationName)
                                        .whenComplete((retryResult, retryThrowable) -> {
                                            if (retryThrowable != null) {
                                                delayedRetry.completeExceptionally(retryThrowable);
                                            } else {
                                                delayedRetry.complete(retryResult);
                                            }
                                        });
                            });
                    return delayedRetry;
                })
                .thenCompose(Function.identity());
    }
}
