package dev.mars.p2pjava;

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


import dev.mars.p2pjava.config.TrackerConfiguration;
import dev.mars.p2pjava.util.CircuitBreaker;
import dev.mars.p2pjava.util.ErrorHandlingManager;
import dev.mars.p2pjava.util.RetryManager;
import dev.mars.p2pjava.util.ThreadPoolManager;
import dev.mars.p2pjava.discovery.ServiceRegistryManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for error handling system including circuit breakers,
 * retry mechanisms, and fallback strategies.
 */
class ErrorHandlingIntegrationTest {

    private TrackerService trackerService;
    private ErrorHandlingManager errorHandlingManager;

    @BeforeEach
    void setUp() {
        TrackerConfiguration config = TrackerConfiguration.builder()
                .trackerPort(6001) // Use different port to avoid conflicts
                .distributedDiscoveryEnabled(false)
                .build();

        ThreadPoolManager threadPoolManager = new ThreadPoolManager();
        ServiceRegistryManager serviceRegistryManager = new ServiceRegistryManager();
        errorHandlingManager = new ErrorHandlingManager();

        trackerService = new TrackerService(config, threadPoolManager, 
                                          serviceRegistryManager, errorHandlingManager);
    }

    @AfterEach
    void tearDown() {
        if (trackerService != null && trackerService.isRunning()) {
            trackerService.stop();
        }
    }

    @Test
    void testCircuitBreakerBasicFunctionality() {
        CircuitBreaker circuitBreaker = CircuitBreaker.builder()
                .name("test-circuit-breaker")
                .failureThreshold(3)
                .build();

        AtomicInteger callCount = new AtomicInteger(0);

        // Test successful operations
        assertDoesNotThrow(() -> {
            String result = circuitBreaker.execute(() -> {
                callCount.incrementAndGet();
                return "success";
            });
            assertEquals("success", result);
        });

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertEquals(1, callCount.get());

        // Test failure threshold
        for (int i = 0; i < 3; i++) {
            assertThrows(RuntimeException.class, () -> {
                circuitBreaker.execute(() -> {
                    callCount.incrementAndGet();
                    throw new RuntimeException("Test failure");
                });
            });
        }

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        assertEquals(4, callCount.get()); // 1 success + 3 failures

        // Test circuit breaker open - should fail fast
        assertThrows(CircuitBreaker.CircuitBreakerOpenException.class, () -> {
            circuitBreaker.execute(() -> {
                callCount.incrementAndGet();
                return "should not execute";
            });
        });

        assertEquals(4, callCount.get()); // No additional calls
    }

    @Test
    void testRetryManagerFunctionality() {
        RetryManager retryManager = RetryManager.builder()
                .maxRetries(3)
                .initialBackoff(10) // Short backoff for testing
                .build();

        AtomicInteger attemptCount = new AtomicInteger(0);

        // Test successful retry after failures
        assertDoesNotThrow(() -> {
            String result = retryManager.executeWithRetry(() -> {
                int attempt = attemptCount.incrementAndGet();
                if (attempt < 3) {
                    throw new RuntimeException("Temporary failure");
                }
                return "success on attempt " + attempt;
            });
            assertEquals("success on attempt 3", result);
        });

        assertEquals(3, attemptCount.get());
    }

    @Test
    void testErrorHandlingManagerIntegration() {
        // Register circuit breaker and fallback
        errorHandlingManager.registerCircuitBreaker("test-operation");
        errorHandlingManager.registerFallback("test-operation", "fallback-result");

        AtomicInteger callCount = new AtomicInteger(0);

        // Test successful operation
        String result = errorHandlingManager.operation("test-operation")
                .withAll()
                .execute(() -> {
                    callCount.incrementAndGet();
                    return "success";
                });

        assertEquals("success", result);
        assertEquals(1, callCount.get());

        // Test fallback when operation fails
        String fallbackResult = errorHandlingManager.operation("test-operation")
                .withFallback()
                .execute(() -> {
                    callCount.incrementAndGet();
                    throw new RuntimeException("Operation failed");
                });

        assertEquals("fallback-result", fallbackResult);
        assertEquals(2, callCount.get());
    }

    @Test
    void testTrackerServiceErrorHandling() throws Exception {
        // Start the tracker service
        trackerService.start();
        assertTrue(trackerService.isRunning());

        // Test that error handling is properly initialized
        assertTrue(errorHandlingManager.hasCircuitBreaker("service-registry-registration"));
        assertTrue(errorHandlingManager.hasCircuitBreaker("peer-registration"));
        assertTrue(errorHandlingManager.hasCircuitBreaker("service-discovery"));

        assertTrue(errorHandlingManager.hasFallback("service-registry-registration"));
        assertTrue(errorHandlingManager.hasFallback("peer-registration"));
        assertTrue(errorHandlingManager.hasFallback("service-discovery"));

        // Test service discovery with error handling
        // This should use fallback and return empty list if service registry fails
        var otherTrackers = trackerService.discoverOtherTrackers();
        assertNotNull(otherTrackers);
        // Should be empty list due to fallback when no other trackers are available
        assertEquals(0, otherTrackers.size());
    }

    @Test
    void testCircuitBreakerStats() {
        CircuitBreaker circuitBreaker = new CircuitBreaker("stats-test");

        // Execute some operations
        assertDoesNotThrow(() -> circuitBreaker.execute(() -> "success"));
        
        try {
            circuitBreaker.execute(() -> {
                throw new RuntimeException("failure");
            });
        } catch (Exception e) {
            // Expected
        }

        CircuitBreaker.CircuitBreakerStats stats = circuitBreaker.getStats();
        assertEquals("stats-test", stats.getName());
        assertEquals(CircuitBreaker.State.CLOSED, stats.getState());
        assertEquals(1, stats.getSuccessCount());
        assertEquals(1, stats.getFailureCount());
        assertEquals(0.5, stats.getFailureRate(), 0.01);
    }

    @Test
    void testCircuitBreakerWithFallback() {
        CircuitBreaker circuitBreaker = new CircuitBreaker("fallback-test");

        // Test successful execution with fallback
        String result = circuitBreaker.executeWithFallback(
                () -> "primary-result",
                () -> "fallback-result"
        );
        assertEquals("primary-result", result);

        // Test fallback when primary fails
        String fallbackResult = circuitBreaker.executeWithFallback(
                () -> {
                    throw new RuntimeException("Primary failed");
                },
                () -> "fallback-result"
        );
        assertEquals("fallback-result", fallbackResult);
    }

    @Test
    void testErrorHandlingManagerStats() {
        errorHandlingManager.registerCircuitBreaker("stats-operation");

        // Execute some operations to generate stats
        errorHandlingManager.operation("stats-operation")
                .withCircuitBreaker()
                .execute(() -> "success");

        var stats = errorHandlingManager.getCircuitBreakerStats();
        assertNotNull(stats);
        assertTrue(stats.containsKey("stats-operation"));

        var operationStats = errorHandlingManager.getCircuitBreakerStats("stats-operation");
        assertNotNull(operationStats);
        assertEquals("stats-operation", operationStats.getName());
        assertEquals(1, operationStats.getSuccessCount());
    }

    @Test
    void testManualCircuitBreakerControl() {
        errorHandlingManager.registerCircuitBreaker("manual-control");

        // Test manual reset
        errorHandlingManager.resetCircuitBreaker("manual-control");
        
        var stats = errorHandlingManager.getCircuitBreakerStats("manual-control");
        assertEquals(CircuitBreaker.State.CLOSED, stats.getState());

        // Test reset all
        errorHandlingManager.resetAllCircuitBreakers();
        
        // Should not throw any exceptions
        assertDoesNotThrow(() -> errorHandlingManager.logStatus());
    }
}
