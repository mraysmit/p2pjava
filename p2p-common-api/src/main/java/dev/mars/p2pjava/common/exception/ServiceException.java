package dev.mars.p2pjava.common.exception;

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
