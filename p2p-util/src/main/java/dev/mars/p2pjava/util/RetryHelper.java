package dev.mars.p2pjava.util;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for implementing retry logic with exponential backoff.
 * This helps improve resilience for network operations and other potentially
 * transient failures.
 */
public class RetryHelper {
    private static final Logger logger = Logger.getLogger(RetryHelper.class.getName());
    
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
                backoffMs = Math.min(maxBackoffMs, backoffMs * 2) + (long)(Math.random() * 100);
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
}