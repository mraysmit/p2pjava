package dev.mars.p2pjava.common.exception;

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
