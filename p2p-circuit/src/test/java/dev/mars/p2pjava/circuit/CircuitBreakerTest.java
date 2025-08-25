package dev.mars.p2pjava.circuit;

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


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the CircuitBreaker class.
 * These tests verify the circuit breaker pattern implementation including state transitions,
 * failure counting, timeout behavior, and fallback mechanisms.
 */
public class CircuitBreakerTest {

    private CircuitBreaker circuitBreaker;
    private static final String TEST_CIRCUIT_NAME = "TestCircuit";
    private static final int FAILURE_THRESHOLD = 3;
    private static final long RESET_TIMEOUT_MS = 500;

    @BeforeEach
    void setUp() {
        // Create a circuit breaker with a small threshold and timeout for testing
        circuitBreaker = new CircuitBreaker(TEST_CIRCUIT_NAME, FAILURE_THRESHOLD, RESET_TIMEOUT_MS);
    }

    @Test
    void testInitialState() {
        // Verify the circuit breaker starts in CLOSED state
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertFalse(circuitBreaker.isOpen());
    }

    @Test
    void testSuccessfulExecution() throws Exception {
        // Test that successful operations work as expected
        String result = circuitBreaker.execute(() -> "Success");
        assertEquals("Success", result);
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    @Test
    void testFailureThreshold() {
        // Test that the circuit opens after reaching the failure threshold
        for (int i = 0; i < FAILURE_THRESHOLD; i++) {
            Exception exception = assertThrows(RuntimeException.class, () -> 
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("Simulated failure");
                })
            );
            assertEquals("Simulated failure", exception.getMessage());
        }
        
        // After FAILURE_THRESHOLD failures, the circuit should be OPEN
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        assertTrue(circuitBreaker.isOpen());
    }

    @Test
    void testCircuitOpenException() {
        // First, cause the circuit to open
        for (int i = 0; i < FAILURE_THRESHOLD; i++) {
            assertThrows(RuntimeException.class, () -> 
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("Simulated failure");
                })
            );
        }
        
        // Now the circuit should be open
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        
        // Attempting to execute while the circuit is open should throw CircuitBreakerOpenException
        Exception exception = assertThrows(CircuitBreaker.CircuitBreakerOpenException.class, () -> 
            circuitBreaker.execute(() -> "This should not execute")
        );
        assertTrue(exception.getMessage().contains(TEST_CIRCUIT_NAME));
    }

    @Test
    void testFallbackMechanism() throws Exception {
        // First, cause the circuit to open
        for (int i = 0; i < FAILURE_THRESHOLD; i++) {
            assertThrows(RuntimeException.class, () -> 
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("Simulated failure");
                })
            );
        }
        
        // Now the circuit should be open
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        
        // Test that the fallback is used when the circuit is open
        String result = circuitBreaker.executeWithFallback(
            () -> "This should not execute",
            () -> "Fallback value"
        );
        
        assertEquals("Fallback value", result);
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void testHalfOpenStateTransition() throws Exception {
        // First, cause the circuit to open
        for (int i = 0; i < FAILURE_THRESHOLD; i++) {
            assertThrows(RuntimeException.class, () -> 
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("Simulated failure");
                })
            );
        }
        
        // Now the circuit should be open
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        
        // Wait for the reset timeout to elapse
        Thread.sleep(RESET_TIMEOUT_MS + 100);
        
        // The next call should transition to HALF_OPEN
        String result = circuitBreaker.execute(() -> "Success after timeout");
        assertEquals("Success after timeout", result);
        
        // After a successful call in HALF_OPEN, the circuit should be CLOSED
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void testHalfOpenStateFailure() throws Exception {
        // First, cause the circuit to open
        for (int i = 0; i < FAILURE_THRESHOLD; i++) {
            assertThrows(RuntimeException.class, () -> 
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("Simulated failure");
                })
            );
        }
        
        // Now the circuit should be open
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        
        // Wait for the reset timeout to elapse
        Thread.sleep(RESET_TIMEOUT_MS + 100);
        
        // The next call should transition to HALF_OPEN, but we'll make it fail
        Exception exception = assertThrows(RuntimeException.class, () -> 
            circuitBreaker.execute(() -> {
                throw new RuntimeException("Failure in half-open state");
            })
        );
        assertEquals("Failure in half-open state", exception.getMessage());
        
        // After a failure in HALF_OPEN, the circuit should be OPEN again
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    @Test
    void testManualReset() throws Exception {
        // First, cause the circuit to open
        for (int i = 0; i < FAILURE_THRESHOLD; i++) {
            assertThrows(RuntimeException.class, () -> 
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("Simulated failure");
                })
            );
        }
        
        // Now the circuit should be open
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        
        // Manually reset the circuit
        circuitBreaker.reset();
        
        // The circuit should now be closed
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        
        // And operations should execute normally
        String result = circuitBreaker.execute(() -> "Success after reset");
        assertEquals("Success after reset", result);
    }

    @Test
    void testConcurrentAccess() throws Exception {
        // Test that the circuit breaker handles concurrent access correctly
        final int THREAD_COUNT = 10;
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger failureCount = new AtomicInteger(0);
        final AtomicInteger openCircuitCount = new AtomicInteger(0);
        
        // Create a circuit breaker with a higher threshold for this test
        CircuitBreaker concurrentBreaker = new CircuitBreaker("ConcurrentTest", 50, 1000);
        
        // Create a callable that will sometimes succeed and sometimes fail
        Callable<String> testOperation = () -> {
            if (Math.random() < 0.7) {  // 70% chance of success
                return "Success";
            } else {
                throw new RuntimeException("Random failure");
            }
        };
        
        // Create and start multiple threads
        Thread[] threads = new Thread[THREAD_COUNT];
        for (int i = 0; i < THREAD_COUNT; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 20; j++) {
                    try {
                        String result = concurrentBreaker.executeWithFallback(
                            testOperation,
                            () -> {
                                openCircuitCount.incrementAndGet();
                                return "Fallback";
                            }
                        );
                        if ("Success".equals(result)) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    }
                    
                    // Small delay to simulate real-world conditions
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
            threads[i].start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Verify that we had some successes, some failures, and some open circuit responses
        assertTrue(successCount.get() > 0, "Should have some successful operations");
        assertTrue(failureCount.get() > 0, "Should have some failed operations");
        
        // Note: We might not have any open circuit responses if the failure threshold wasn't reached,
        // but that's acceptable for this test
        System.out.println("Concurrent test results - Successes: " + successCount.get() + 
                           ", Failures: " + failureCount.get() + 
                           ", Open circuit responses: " + openCircuitCount.get());
    }
}