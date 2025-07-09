package dev.mars.p2pjava.common.exception;

import dev.mars.p2pjava.common.protocol.ErrorMessage;

/**
 * Exception for service-related errors.
 */
public class ServiceException extends P2PException {
    
    public ServiceException(String message, String errorCode, boolean retryable) {
        super(message, errorCode, ErrorCategory.SERVER_ERROR, 
              retryable ? RecoveryStrategy.RETRY_EXPONENTIAL : RecoveryStrategy.FAIL_FAST, retryable);
    }
    
    public ServiceException(String message, Throwable cause, String errorCode, boolean retryable) {
        super(message, cause, errorCode, ErrorCategory.SERVER_ERROR, 
              retryable ? RecoveryStrategy.RETRY_EXPONENTIAL : RecoveryStrategy.FAIL_FAST, retryable);
    }
    
    /**
     * Service unavailable exception - retryable with circuit breaker
     */
    public static class ServiceUnavailableException extends P2PException {
        public ServiceUnavailableException(String serviceName) {
            super("Service unavailable: " + serviceName, ErrorMessage.ErrorCodes.SERVICE_UNAVAILABLE,
                  ErrorCategory.SERVER_ERROR, RecoveryStrategy.CIRCUIT_BREAKER, true);
        }
        
        public ServiceUnavailableException(String serviceName, long retryAfterMs) {
            super("Service unavailable: " + serviceName, null, ErrorMessage.ErrorCodes.SERVICE_UNAVAILABLE,
                  ErrorCategory.SERVER_ERROR, RecoveryStrategy.CIRCUIT_BREAKER, true, retryAfterMs);
        }
    }
    
    /**
     * Rate limiting exception - retryable with exponential backoff
     */
    public static class RateLimitedException extends P2PException {
        public RateLimitedException(String message, long retryAfterMs) {
            super(message, null, ErrorMessage.ErrorCodes.RATE_LIMITED,
                  ErrorCategory.RATE_LIMIT_ERROR, RecoveryStrategy.RETRY_EXPONENTIAL_JITTER, true, retryAfterMs);
        }
    }
    
    /**
     * Circuit breaker open exception - use fallback
     */
    public static class CircuitBreakerOpenException extends P2PException {
        public CircuitBreakerOpenException(String serviceName) {
            super("Circuit breaker open for service: " + serviceName, 
                  ErrorMessage.ErrorCodes.CIRCUIT_BREAKER_OPEN,
                  ErrorCategory.SERVER_ERROR, RecoveryStrategy.FALLBACK, false);
        }
    }
    
    /**
     * Internal server error - retryable
     */
    public static class InternalServerException extends ServiceException {
        public InternalServerException(String message) {
            super("Internal server error: " + message, ErrorMessage.ErrorCodes.INTERNAL_ERROR, true);
        }
        
        public InternalServerException(String message, Throwable cause) {
            super("Internal server error: " + message, cause, ErrorMessage.ErrorCodes.INTERNAL_ERROR, true);
        }
    }
}
