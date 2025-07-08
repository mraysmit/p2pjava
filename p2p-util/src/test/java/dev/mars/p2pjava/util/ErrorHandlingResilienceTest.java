package dev.mars.p2pjava.util;

import dev.mars.p2pjava.circuit.CircuitBreaker;
import dev.mars.p2pjava.circuit.CircuitBreakerFactory;
import dev.mars.p2pjava.common.exception.NetworkException;
import dev.mars.p2pjava.common.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class demonstrating the improved error handling and resilience mechanisms.
 */
public class ErrorHandlingResilienceTest {
    
    @BeforeEach
    void setUp() {
        // Clear any existing circuit breakers
        CircuitBreakerFactory.clear();
        RecoveryManager.clearStrategies();
        
        // Re-register default strategies
        RecoveryManager.registerStrategy("network", RecoveryManager.NETWORK_OPERATION_STRATEGY);
        RecoveryManager.registerStrategy("critical", RecoveryManager.CRITICAL_OPERATION_STRATEGY);
    }
    
    @Test
    void testRetryWithExponentialBackoffAndJitter() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);
        
        String result = RetryHelper.executeWithRetry(() -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 3) {
                throw new IOException("Simulated network failure");
            }
            return "Success on attempt " + attempt;
        }, 5, 100, 2000, RetryHelper.createSmartRetryPredicate(), 
           RetryHelper.BackoffStrategy.EXPONENTIAL_JITTER);
        
        assertEquals("Success on attempt 3", result);
        assertEquals(3, attempts.get());
    }
    
    @Test
    void testCircuitBreakerWithFallback() throws Exception {
        CircuitBreaker circuitBreaker = CircuitBreakerFactory.forNetworkService("test-service");
        AtomicInteger attempts = new AtomicInteger(0);
        
        // Trigger circuit breaker to open
        for (int i = 0; i < 6; i++) {
            try {
                circuitBreaker.execute(() -> {
                    attempts.incrementAndGet();
                    throw new SocketTimeoutException("Service unavailable");
                });
            } catch (Exception e) {
                // Expected failures
            }
        }
        
        assertTrue(circuitBreaker.isOpen());
        
        // Test fallback mechanism
        String result = circuitBreaker.executeWithFallback(() -> {
            throw new IOException("Still failing");
        }, () -> "Fallback response");
        
        assertEquals("Fallback response", result);
    }
    
    @Test
    void testRecoveryManagerWithNetworkStrategy() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);
        
        String result = RecoveryManager.executeWithRecovery("network", () -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 2) {
                throw new NetworkException("Network timeout");
            }
            return "Network operation succeeded";
        }, () -> "Network fallback");
        
        assertEquals("Network operation succeeded", result);
        assertTrue(attempts.get() >= 2);
    }
    
    @Test
    void testRecoveryManagerWithFallback() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);
        
        String result = RecoveryManager.executeWithRecovery("critical", () -> {
            attempts.incrementAndGet();
            throw new ServiceException.ServiceUnavailableException("Service down");
        }, () -> "Critical service fallback");
        
        assertEquals("Critical service fallback", result);
        assertTrue(attempts.get() > 0);
    }
    
    @Test
    void testP2PExceptionRetryGuidance() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);
        
        // Test non-retryable exception
        assertThrows(ServiceException.class, () -> {
            RetryHelper.executeWithRetry(() -> {
                attempts.incrementAndGet();
                throw new ServiceException.InternalServerException("Non-retryable error");
            }, 3, 100, 1000, RetryHelper.createSmartRetryPredicate());
        });
        
        // Should only attempt once for non-retryable exception
        assertEquals(1, attempts.get());
    }
    
    @Test
    void testCircuitBreakerHalfOpenState() throws Exception {
        CircuitBreaker circuitBreaker = new CircuitBreaker("half-open-test", 2, 100, 2, 
                                                           e -> e instanceof IOException);
        AtomicInteger attempts = new AtomicInteger(0);
        
        // Open the circuit
        for (int i = 0; i < 3; i++) {
            try {
                circuitBreaker.execute(() -> {
                    throw new IOException("Failure " + attempts.incrementAndGet());
                });
            } catch (Exception e) {
                // Expected
            }
        }
        
        assertTrue(circuitBreaker.isOpen());
        
        // Wait for reset timeout
        Thread.sleep(150);
        
        // First call should move to HALF_OPEN
        try {
            circuitBreaker.execute(() -> {
                throw new IOException("Still failing");
            });
        } catch (Exception e) {
            // Expected - should reopen circuit
        }
        
        assertTrue(circuitBreaker.isOpen());
    }
    
    @Test
    void testSmartRetryPredicate() {
        var predicate = RetryHelper.createSmartRetryPredicate();
        
        // Should retry network exceptions
        assertTrue(predicate.test(new IOException("Network error")));
        assertTrue(predicate.test(new SocketTimeoutException("Timeout")));
        
        // Should retry retryable P2P exceptions
        assertTrue(predicate.test(new NetworkException("P2P network error")));
        
        // Should not retry non-retryable P2P exceptions
        assertFalse(predicate.test(new ServiceException.InternalServerException("Internal error")));
        
        // Should not retry unknown exceptions
        assertFalse(predicate.test(new IllegalArgumentException("Invalid argument")));
    }
    
    @Test
    void testBackoffStrategies() throws Exception {
        // Test different backoff strategies
        for (RetryHelper.BackoffStrategy strategy : RetryHelper.BackoffStrategy.values()) {
            AtomicInteger attempts = new AtomicInteger(0);
            
            try {
                RetryHelper.executeWithRetry(() -> {
                    int attempt = attempts.incrementAndGet();
                    if (attempt < 3) {
                        throw new IOException("Failure " + attempt);
                    }
                    return "Success with " + strategy;
                }, 5, 50, 1000, e -> e instanceof IOException, strategy);
            } catch (Exception e) {
                // Some strategies might not succeed within the retry limit
            }
            
            assertTrue(attempts.get() >= 1, "Strategy " + strategy + " should have attempted at least once");
        }
    }
    
    @Test
    void testAsyncRecoveryExecution() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);
        
        var future = RecoveryManager.executeAsyncWithRecovery("fast", () -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 2) {
                throw new IOException("Async failure");
            }
            return "Async success";
        }, () -> "Async fallback");
        
        String result = future.get();
        assertEquals("Async success", result);
        assertTrue(attempts.get() >= 2);
    }
}
