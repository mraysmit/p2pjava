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


import dev.mars.p2pjava.common.exception.P2PException;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for implementing retry logic with various backoff strategies.
 * This helps improve resilience for network operations and other potentially
 * transient failures.
 */
public class RetryHelper {
    private static final Logger logger = Logger.getLogger(RetryHelper.class.getName());
    private static final Random random = new Random();

    /**
     * Backoff strategy for retry operations.
     */
    public enum BackoffStrategy {
        /** Fixed delay between retries */
        FIXED,
        /** Linear increase in delay */
        LINEAR,
        /** Exponential increase in delay */
        EXPONENTIAL,
        /** Exponential increase with jitter to prevent thundering herd */
        EXPONENTIAL_JITTER
    }
    
    /**
     * Executes the given operation with retry logic using exponential backoff.
     *
     * @param operation The operation to execute
     * @param maxRetries Maximum number of retry attempts
     * @param initialBackoffMs Initial backoff time in milliseconds
     * @param maxBackoffMs Maximum backoff time in milliseconds
     * @param retryableExceptions Predicate to determine if an exception is retryable
     * @param <T> The return type of the operation
     * @return The result of the operation
     * @throws Exception If the operation fails after all retry attempts
     */
    public static <T> T executeWithRetry(
            Callable<T> operation,
            int maxRetries,
            long initialBackoffMs,
            long maxBackoffMs,
            Predicate<Exception> retryableExceptions) throws Exception {
        
        int attempts = 0;
        long backoffMs = initialBackoffMs;
        Exception lastException = null;
        
        while (attempts <= maxRetries) {
            try {
                if (attempts > 0) {
                    logger.log(Level.INFO, "Retry attempt {0} after {1}ms", new Object[]{attempts, backoffMs});
                }
                return operation.call();
            } catch (Exception e) {
                lastException = e;
                attempts++;
                
                if (attempts > maxRetries || !retryableExceptions.test(e)) {
                    logger.log(Level.WARNING, "Operation failed after {0} attempts: {1}", 
                            new Object[]{attempts, e.getMessage()});
                    throw e;
                }
                
                logger.log(Level.WARNING, "Operation failed (attempt {0}), retrying in {1}ms: {2}", 
                        new Object[]{attempts, backoffMs, e.getMessage()});
                
                try {
                    TimeUnit.MILLISECONDS.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }
                
                // Exponential backoff with jitter
                backoffMs = calculateNextBackoff(backoffMs, initialBackoffMs, maxBackoffMs, attempts, BackoffStrategy.EXPONENTIAL_JITTER);
            }
        }
        
        // This should never happen due to the throw in the catch block
        throw new RuntimeException("Unexpected error in retry logic", lastException);
    }
    
    /**
     * Executes the given operation with retry logic using default parameters.
     *
     * @param operation The operation to execute
     * @param <T> The return type of the operation
     * @return The result of the operation
     * @throws Exception If the operation fails after all retry attempts
     */
    public static <T> T executeWithRetry(Callable<T> operation) throws Exception {
        return executeWithRetry(
                operation,
                3,                  // Default max retries
                1000,               // Default initial backoff: 1 second
                10000,              // Default max backoff: 10 seconds
                e -> e instanceof java.io.IOException || e instanceof java.net.SocketException
        );
    }
    
    /**
     * Executes the given operation with retry logic using custom retry count but default backoff parameters.
     *
     * @param operation The operation to execute
     * @param maxRetries Maximum number of retry attempts
     * @param <T> The return type of the operation
     * @return The result of the operation
     * @throws Exception If the operation fails after all retry attempts
     */
    public static <T> T executeWithRetry(Callable<T> operation, int maxRetries) throws Exception {
        return executeWithRetry(
                operation,
                maxRetries,
                1000,               // Default initial backoff: 1 second
                10000,              // Default max backoff: 10 seconds
                e -> e instanceof java.io.IOException || e instanceof java.net.SocketException
        );
    }
    
    /**
     * Executes an operation that doesn't return a value with retry logic.
     *
     * @param operation The operation to execute
     * @param maxRetries Maximum number of retry attempts
     * @param initialBackoffMs Initial backoff time in milliseconds
     * @param maxBackoffMs Maximum backoff time in milliseconds
     * @param retryableExceptions Predicate to determine if an exception is retryable
     * @throws Exception If the operation fails after all retry attempts
     */
    public static void executeWithRetry(
            Runnable operation,
            int maxRetries,
            long initialBackoffMs,
            long maxBackoffMs,
            Predicate<Exception> retryableExceptions) throws Exception {
        
        executeWithRetry(() -> {
            operation.run();
            return null;
        }, maxRetries, initialBackoffMs, maxBackoffMs, retryableExceptions);
    }
    
    /**
     * Executes an operation that doesn't return a value with default retry parameters.
     *
     * @param operation The operation to execute
     * @throws Exception If the operation fails after all retry attempts
     */
    public static void executeWithRetry(Runnable operation) throws Exception {
        executeWithRetry(operation, 3, 1000, 10000,
                e -> e instanceof java.io.IOException || e instanceof java.net.SocketException);
    }

    /**
     * Executes the given operation with retry logic and configurable backoff strategy.
     *
     * @param operation The operation to execute
     * @param maxRetries Maximum number of retry attempts
     * @param initialBackoffMs Initial backoff delay in milliseconds
     * @param maxBackoffMs Maximum backoff delay in milliseconds
     * @param retryableExceptions Predicate to determine if an exception should trigger a retry
     * @param strategy The backoff strategy to use
     * @param <T> The return type of the operation
     * @return The result of the operation
     * @throws Exception If the operation fails after all retry attempts
     */
    public static <T> T executeWithRetry(Callable<T> operation, int maxRetries,
                                       long initialBackoffMs, long maxBackoffMs,
                                       Predicate<Exception> retryableExceptions,
                                       BackoffStrategy strategy) throws Exception {
        Exception lastException = null;
        int attempts = 0;
        long backoffMs = initialBackoffMs;

        while (attempts <= maxRetries) {
            try {
                if (attempts > 0) {
                    logger.log(Level.INFO, "Retry attempt {0} after {1}ms using {2} strategy",
                              new Object[]{attempts, backoffMs, strategy});
                }
                return operation.call();
            } catch (Exception e) {
                lastException = e;
                attempts++;

                // Check if this is a P2PException and use its retry guidance
                if (e instanceof P2PException) {
                    P2PException p2pEx = (P2PException) e;
                    if (!p2pEx.isRetryable()) {
                        logger.log(Level.WARNING, "Non-retryable exception: {0}", e.getMessage());
                        throw e;
                    }
                    // Use suggested retry delay if available
                    if (p2pEx.getRetryAfterMs() > 0) {
                        backoffMs = p2pEx.getRetryAfterMs();
                    }
                }

                if (attempts > maxRetries || !retryableExceptions.test(e)) {
                    logger.log(Level.WARNING, "Operation failed after {0} attempts: {1}",
                            new Object[]{attempts, e.getMessage()});
                    throw e;
                }

                logger.log(Level.WARNING, "Operation failed (attempt {0}), retrying in {1}ms: {2}",
                        new Object[]{attempts, backoffMs, e.getMessage()});

                try {
                    TimeUnit.MILLISECONDS.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }

                // Calculate next backoff delay based on strategy
                backoffMs = calculateNextBackoff(backoffMs, initialBackoffMs, maxBackoffMs, attempts, strategy);
            }
        }

        // This should never be reached, but just in case
        throw lastException != null ? lastException : new RuntimeException("Retry failed");
    }

    /**
     * Calculates the next backoff delay based on the specified strategy.
     *
     * @param currentBackoffMs Current backoff delay
     * @param initialBackoffMs Initial backoff delay
     * @param maxBackoffMs Maximum backoff delay
     * @param attempt Current attempt number
     * @param strategy Backoff strategy to use
     * @return Next backoff delay in milliseconds
     */
    private static long calculateNextBackoff(long currentBackoffMs, long initialBackoffMs,
                                           long maxBackoffMs, int attempt, BackoffStrategy strategy) {
        long nextBackoff;

        switch (strategy) {
            case FIXED:
                nextBackoff = initialBackoffMs;
                break;

            case LINEAR:
                nextBackoff = initialBackoffMs * attempt;
                break;

            case EXPONENTIAL:
                nextBackoff = currentBackoffMs * 2;
                break;

            case EXPONENTIAL_JITTER:
            default:
                // Exponential backoff with jitter to prevent thundering herd
                nextBackoff = currentBackoffMs * 2;
                // Add jitter: ±25% of the calculated backoff
                long jitter = (long) (nextBackoff * 0.25 * (random.nextDouble() * 2 - 1));
                nextBackoff += jitter;
                break;
        }

        // Ensure we don't exceed the maximum backoff
        return Math.min(Math.max(nextBackoff, initialBackoffMs), maxBackoffMs);
    }

    /**
     * Creates a predicate for retryable exceptions based on P2PException categorization.
     *
     * @return Predicate that returns true for retryable exceptions
     */
    public static Predicate<Exception> createSmartRetryPredicate() {
        return e -> {
            if (e instanceof P2PException) {
                return ((P2PException) e).isRetryable();
            }
            // Default behavior for non-P2P exceptions
            return e instanceof java.io.IOException ||
                   e instanceof java.net.SocketException ||
                   e instanceof java.net.SocketTimeoutException;
        };
    }
}