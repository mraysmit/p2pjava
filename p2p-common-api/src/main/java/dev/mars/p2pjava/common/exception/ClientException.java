package dev.mars.p2pjava.common.exception;

import dev.mars.p2pjava.common.protocol.ErrorMessage;

/**
 * Exception for client-related errors that are typically not retryable.
 */
public class ClientException extends P2PException {
    
    public ClientException(String message, String errorCode) {
        super(message, errorCode, ErrorCategory.CLIENT_ERROR, RecoveryStrategy.FAIL_FAST, false);
    }
    
    public ClientException(String message, Throwable cause, String errorCode) {
        super(message, cause, errorCode, ErrorCategory.CLIENT_ERROR, RecoveryStrategy.FAIL_FAST, false);
    }
    
    /**
     * Invalid message format exception
     */
    public static class InvalidMessageException extends ClientException {
        public InvalidMessageException(String message) {
            super("Invalid message format: " + message, ErrorMessage.ErrorCodes.INVALID_MESSAGE);
        }
    }
    
    /**
     * Unknown command exception
     */
    public static class UnknownCommandException extends ClientException {
        public UnknownCommandException(String command) {
            super("Unknown command: " + command, ErrorMessage.ErrorCodes.UNKNOWN_COMMAND);
        }
    }
    
    /**
     * Invalid parameters exception
     */
    public static class InvalidParametersException extends ClientException {
        public InvalidParametersException(String message) {
            super("Invalid parameters: " + message, ErrorMessage.ErrorCodes.INVALID_PARAMETERS);
        }
    }
    
    /**
     * Authentication failed exception
     */
    public static class AuthenticationFailedException extends ClientException {
        public AuthenticationFailedException(String message) {
            super("Authentication failed: " + message, ErrorMessage.ErrorCodes.AUTHENTICATION_FAILED);
        }
    }
    
    /**
     * Authorization failed exception
     */
    public static class AuthorizationFailedException extends ClientException {
        public AuthorizationFailedException(String message) {
            super("Authorization failed: " + message, ErrorMessage.ErrorCodes.AUTHORIZATION_FAILED);
        }
    }
    
    /**
     * Resource not found exception
     */
    public static class ResourceNotFoundException extends ClientException {
        public ResourceNotFoundException(String resource) {
            super("Resource not found: " + resource, ErrorMessage.ErrorCodes.RESOURCE_NOT_FOUND);
        }
    }
    
    /**
     * File not found exception
     */
    public static class FileNotFoundException extends ClientException {
        public FileNotFoundException(String fileName) {
            super("File not found: " + fileName, ErrorMessage.ErrorCodes.FILE_NOT_FOUND);
        }
    }
}
