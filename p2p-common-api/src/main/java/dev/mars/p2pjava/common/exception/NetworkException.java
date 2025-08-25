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
 * Exception for network-related errors that are typically retryable.
 */
public class NetworkException extends P2PException {
    
    public NetworkException(String message) {
        super(message, ErrorMessage.ErrorCodes.NETWORK_ERROR, 
              ErrorCategory.NETWORK_ERROR, RecoveryStrategy.RETRY_EXPONENTIAL_JITTER, true);
    }
    
    public NetworkException(String message, Throwable cause) {
        super(message, cause, ErrorMessage.ErrorCodes.NETWORK_ERROR, 
              ErrorCategory.NETWORK_ERROR, RecoveryStrategy.RETRY_EXPONENTIAL_JITTER, true);
    }
    
    /**
     * Connection timeout exception - retryable with exponential backoff
     */
    public static class TimeoutException extends NetworkException {
        public TimeoutException(String message) {
            super("Connection timeout: " + message);
        }
        
        public TimeoutException(String message, Throwable cause) {
            super("Connection timeout: " + message, cause);
        }
    }
    
    /**
     * Connection failed exception - retryable with exponential backoff
     */
    public static class ConnectionFailedException extends NetworkException {
        public ConnectionFailedException(String message) {
            super("Connection failed: " + message);
        }
        
        public ConnectionFailedException(String message, Throwable cause) {
            super("Connection failed: " + message, cause);
        }
    }
    
    /**
     * Peer unavailable exception - retryable with circuit breaker
     */
    public static class PeerUnavailableException extends P2PException {
        public PeerUnavailableException(String peerId) {
            super("Peer unavailable: " + peerId, ErrorMessage.ErrorCodes.PEER_UNAVAILABLE,
                  ErrorCategory.NETWORK_ERROR, RecoveryStrategy.CIRCUIT_BREAKER, true);
        }
        
        public PeerUnavailableException(String peerId, Throwable cause) {
            super("Peer unavailable: " + peerId, cause, ErrorMessage.ErrorCodes.PEER_UNAVAILABLE,
                  ErrorCategory.NETWORK_ERROR, RecoveryStrategy.CIRCUIT_BREAKER, true);
        }
    }
}
