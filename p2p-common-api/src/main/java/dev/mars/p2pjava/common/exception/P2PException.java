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


/**
 * Base exception class for all P2P system exceptions.
 * Provides categorization for retry logic and error handling strategies.
 */
public abstract class P2PException extends Exception {
    
    public enum ErrorCategory {
        /** Client errors - typically not retryable */
        CLIENT_ERROR,
        /** Server errors - potentially retryable */
        SERVER_ERROR,
        /** Network errors - retryable with backoff */
        NETWORK_ERROR,
        /** Rate limiting - retryable with exponential backoff */
        RATE_LIMIT_ERROR,
        /** System errors - may require manual intervention */
        SYSTEM_ERROR
    }
    
    public enum RecoveryStrategy {
        /** Do not retry, fail immediately */
        FAIL_FAST,
        /** Retry with linear backoff */
        RETRY_LINEAR,
        /** Retry with exponential backoff */
        RETRY_EXPONENTIAL,
        /** Retry with exponential backoff and jitter */
        RETRY_EXPONENTIAL_JITTER,
        /** Use circuit breaker pattern */
        CIRCUIT_BREAKER,
        /** Use fallback mechanism */
        FALLBACK,
        /** Require manual intervention */
        MANUAL_INTERVENTION
    }
    
    private final String errorCode;
    private final ErrorCategory category;
    private final RecoveryStrategy recoveryStrategy;
    private final boolean retryable;
    private final long retryAfterMs;
    
    protected P2PException(String message, String errorCode, ErrorCategory category, 
                          RecoveryStrategy recoveryStrategy, boolean retryable) {
        this(message, null, errorCode, category, recoveryStrategy, retryable, 0);
    }
    
    protected P2PException(String message, Throwable cause, String errorCode, 
                          ErrorCategory category, RecoveryStrategy recoveryStrategy, 
                          boolean retryable) {
        this(message, cause, errorCode, category, recoveryStrategy, retryable, 0);
    }
    
    protected P2PException(String message, Throwable cause, String errorCode, 
                          ErrorCategory category, RecoveryStrategy recoveryStrategy, 
                          boolean retryable, long retryAfterMs) {
        super(message, cause);
        this.errorCode = errorCode;
        this.category = category;
        this.recoveryStrategy = recoveryStrategy;
        this.retryable = retryable;
        this.retryAfterMs = retryAfterMs;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public ErrorCategory getCategory() {
        return category;
    }
    
    public RecoveryStrategy getRecoveryStrategy() {
        return recoveryStrategy;
    }
    
    public boolean isRetryable() {
        return retryable;
    }
    
    public long getRetryAfterMs() {
        return retryAfterMs;
    }
    
    /**
     * Returns a structured error response that can be sent to clients.
     */
    public String getStructuredErrorResponse() {
        return String.format("ERROR %s %s %s retryable=%s retryAfter=%d", 
                           errorCode, category, recoveryStrategy, retryable, retryAfterMs);
    }
    
    @Override
    public String toString() {
        return String.format("%s[code=%s, category=%s, strategy=%s, retryable=%s]: %s",
                           getClass().getSimpleName(), errorCode, category, recoveryStrategy, 
                           retryable, getMessage());
    }
}
