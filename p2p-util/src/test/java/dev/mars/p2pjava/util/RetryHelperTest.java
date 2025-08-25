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


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.SocketException;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the RetryHelper class.
 */
public class RetryHelperTest {

    /**
     * Test successful execution without retries.
     */
    @Test
    void testSuccessfulExecution() throws Exception {
        // Execute an operation that succeeds on the first try
        String result = RetryHelper.executeWithRetry(() -> "Success");

        // Verify the result
        assertEquals("Success", result, "Operation should succeed without retries");
    }

    /**
     * Test execution with retries that eventually succeeds.
     */
    @Test
    void testExecutionWithRetries() throws Exception {
        // Counter to track the number of attempts
        AtomicInteger attempts = new AtomicInteger(0);

        // Execute an operation that fails on the first two attempts but succeeds on the third
        String result = RetryHelper.executeWithRetry(() -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 3) {
                throw new IOException("Simulated failure on attempt " + attempt);
            }
            return "Success after retries";
        });

        // Verify the result and number of attempts
        assertEquals("Success after retries", result, "Operation should succeed after retries");
        assertEquals(3, attempts.get(), "Operation should be attempted 3 times");
    }

    /**
     * Test execution that fails after all retry attempts.
     */
    @Test
    void testExecutionFailsAfterRetries() {
        // Counter to track the number of attempts
        AtomicInteger attempts = new AtomicInteger(0);

        // Execute an operation that always fails
        Exception exception = assertThrows(IOException.class, () -> {
            RetryHelper.executeWithRetry(() -> {
                attempts.incrementAndGet();
                throw new IOException("Simulated failure");
            }, 3);
        });

        // Verify the exception and number of attempts
        assertEquals("Simulated failure", exception.getMessage(), "Exception message should match");
        assertEquals(4, attempts.get(), "Operation should be attempted 4 times (initial + 3 retries)");
    }

    /**
     * Test execution with custom retry parameters.
     * Interesting to test with custom predicate to determine retryable exceptions.
     */
    @Test
    void testExecutionWithCustomParameters() throws Exception {
        // Counter to track the number of attempts
        AtomicInteger attempts = new AtomicInteger(0);

        // Custom predicate that only retries on IOException
        Predicate<Exception> retryableExceptions = e -> e instanceof IOException;

        // Execute an operation with custom parameters
        String result = RetryHelper.executeWithRetry(
                () -> {
                    int attempt = attempts.incrementAndGet();
                    if (attempt < 2) {
                        throw new IOException("Simulated failure on attempt " + attempt);
                    }
                    return "Success with custom parameters";
                },
                2,          // maxRetries
                100,        // initialBackoffMs
                500,        // maxBackoffMs
                retryableExceptions
        );

        // Verify the result and number of attempts
        assertEquals("Success with custom parameters", result, "Operation should succeed with custom parameters");
        assertEquals(2, attempts.get(), "Operation should be attempted 2 times");
    }

    /**
     * Test that non-retryable exceptions are not retried.
     */
    @Test
    void testNonRetryableException() {
        // Counter to track the number of attempts
        AtomicInteger attempts = new AtomicInteger(0);

        // Custom predicate that only retries on IOException
        Predicate<Exception> retryableExceptions = e -> e instanceof IOException;

        // Execute an operation that throws a non-retryable exception
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            RetryHelper.executeWithRetry(
                    () -> {
                        attempts.incrementAndGet();
                        throw new IllegalArgumentException("Non-retryable exception");
                    },
                    3,          // maxRetries
                    100,        // initialBackoffMs
                    500,        // maxBackoffMs
                    retryableExceptions
            );
        });

        // Verify the exception and number of attempts
        assertEquals("Non-retryable exception", exception.getMessage(), "Exception message should match");
        assertEquals(1, attempts.get(), "Operation should be attempted only once");
    }

    /**
     * Test execution with default retryable exceptions.
     */
    @Test
    void testDefaultRetryableExceptions() throws Exception {
        // Counter to track the number of attempts
        AtomicInteger attempts = new AtomicInteger(0);

        // Execute an operation that throws a SocketException (which is retryable by default)
        String result = RetryHelper.executeWithRetry(() -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 2) {
                throw new SocketException("Simulated socket exception");
            }
            return "Success after socket exception";
        });

        // Verify the result and number of attempts
        assertEquals("Success after socket exception", result, "Operation should succeed after retrying a socket exception");
        assertEquals(2, attempts.get(), "Operation should be attempted 2 times");
    }

    /**
     * Test execution with a Runnable that doesn't return a value.
     */
    @Test
    void testExecutionWithRunnable() throws Exception {
        // Counter to track the number of attempts and success
        AtomicInteger attempts = new AtomicInteger(0);
        AtomicInteger successFlag = new AtomicInteger(0);

        // Custom predicate that also checks the cause of the exception
        Predicate<Exception> retryableExceptions = e -> {
            if (e instanceof IOException) {
                return true;
            }
            if (e instanceof RuntimeException && e.getCause() instanceof IOException) {
                return true;
            }
            return false;
        };

        // Execute a Runnable that fails on the first attempt but succeeds on the second
        RetryHelper.executeWithRetry(
            () -> {
                int attempt = attempts.incrementAndGet();
                if (attempt < 2) {
                    // Wrap the checked exception in a RuntimeException since Runnable can't throw checked exceptions
                    throw new RuntimeException(new IOException("Simulated failure on attempt " + attempt));
                }
                successFlag.set(1);
            },
            3,          // maxRetries
            100,        // initialBackoffMs
            500,        // maxBackoffMs
            retryableExceptions
        );

        // Verify the number of attempts and success flag
        assertEquals(2, attempts.get(), "Runnable should be attempted 2 times");
        assertEquals(1, successFlag.get(), "Runnable should have executed successfully");
    }

    /**
     * Test that exponential backoff increases the wait time between retries.
     */
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testExponentialBackoff() throws Exception {
        // Counter to track the number of attempts
        AtomicInteger attempts = new AtomicInteger(0);

        // Record the time of each attempt
        long[] attemptTimes = new long[4];

        // Execute an operation that fails on the first three attempts but succeeds on the fourth
        RetryHelper.executeWithRetry(
                () -> {
                    int attempt = attempts.incrementAndGet();
                    attemptTimes[attempt - 1] = System.currentTimeMillis();
                    if (attempt < 4) {
                        throw new IOException("Simulated failure on attempt " + attempt);
                    }
                    return "Success after backoff";
                },
                3,          // maxRetries
                100,        // initialBackoffMs
                1000,       // maxBackoffMs
                e -> true   // Retry all exceptions
        );

        // Verify the number of attempts
        assertEquals(4, attempts.get(), "Operation should be attempted 4 times");

        // Verify that the time between attempts increases (exponential backoff)
        long firstInterval = attemptTimes[1] - attemptTimes[0];
        long secondInterval = attemptTimes[2] - attemptTimes[1];
        long thirdInterval = attemptTimes[3] - attemptTimes[2];

        // The second interval should be greater than the first (due to exponential backoff)
        assertTrue(secondInterval > firstInterval, 
                "Second interval (" + secondInterval + "ms) should be greater than first interval (" + firstInterval + "ms)");

        // The third interval should be greater than or equal to the second (due to exponential backoff, but capped by maxBackoffMs)
        assertTrue(thirdInterval >= secondInterval, 
                "Third interval (" + thirdInterval + "ms) should be greater than or equal to second interval (" + secondInterval + "ms)");
    }

    /**
     * Test that interrupted sleep during backoff throws the expected exception.
     */
    @Test
    void testInterruptedDuringBackoff() {
        // Create a thread that will execute the retry operation
        Thread testThread = new Thread(() -> {
            try {
                RetryHelper.executeWithRetry(() -> {
                    throw new IOException("Simulated failure");
                });
                fail("Should not reach here due to interruption");
            } catch (Exception e) {
                // Expected exception
                assertTrue(e instanceof RuntimeException, "Exception should be RuntimeException");
                assertTrue(e.getMessage().contains("Retry interrupted"), "Exception message should mention interruption");
                assertTrue(e.getCause() instanceof InterruptedException, "Cause should be InterruptedException");
            }
        });

        // Start the thread and immediately interrupt it
        testThread.start();
        testThread.interrupt();

        // Wait for the thread to complete
        try {
            testThread.join(1000);
        } catch (InterruptedException e) {
            fail("Test thread join was interrupted");
        }

        // Verify the thread completed
        assertFalse(testThread.isAlive(), "Test thread should have completed");
    }
}
