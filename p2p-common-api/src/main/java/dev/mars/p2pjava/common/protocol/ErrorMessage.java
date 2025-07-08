package dev.mars.p2pjava.common.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Error message for communicating errors across components.
 */
public class ErrorMessage extends JsonMessage {
    
    @JsonProperty("errorCode")
    private String errorCode;
    
    @JsonProperty("errorMessage")
    private String errorMessage;
    
    @JsonProperty("errorDetails")
    private String errorDetails;
    
    @JsonProperty("originalMessageId")
    private String originalMessageId;
    
    @JsonProperty("component")
    private String component;
    
    public ErrorMessage() {
        super();
    }
    
    public ErrorMessage(String senderId, String receiverId, String errorCode, String errorMessage) {
        super(senderId, receiverId);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
    
    public ErrorMessage(String senderId, String receiverId, String errorCode, String errorMessage, String originalMessageId) {
        this(senderId, receiverId, errorCode, errorMessage);
        this.originalMessageId = originalMessageId;
    }
    
    @Override
    public String getMessageType() {
        return "error";
    }
    
    @Override
    public boolean isValid() {
        return errorCode != null && !errorCode.trim().isEmpty() &&
               errorMessage != null && !errorMessage.trim().isEmpty();
    }
    
    // Getters and setters
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public String getErrorDetails() { return errorDetails; }
    public void setErrorDetails(String errorDetails) { this.errorDetails = errorDetails; }
    
    public String getOriginalMessageId() { return originalMessageId; }
    public void setOriginalMessageId(String originalMessageId) { this.originalMessageId = originalMessageId; }
    
    public String getComponent() { return component; }
    public void setComponent(String component) { this.component = component; }
    
    /**
     * Common error codes with categorization for retry logic.
     */
    public static class ErrorCodes {
        // Client errors (4xx) - typically not retryable
        public static final String INVALID_MESSAGE = "INVALID_MESSAGE";
        public static final String UNKNOWN_COMMAND = "UNKNOWN_COMMAND";
        public static final String AUTHENTICATION_FAILED = "AUTHENTICATION_FAILED";
        public static final String AUTHORIZATION_FAILED = "AUTHORIZATION_FAILED";
        public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
        public static final String FILE_NOT_FOUND = "FILE_NOT_FOUND";
        public static final String INVALID_PARAMETERS = "INVALID_PARAMETERS";
        public static final String CHECKSUM_MISMATCH = "CHECKSUM_MISMATCH";

        // Server errors (5xx) - potentially retryable
        public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
        public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
        public static final String FILE_ACCESS_ERROR = "FILE_ACCESS_ERROR";
        public static final String TRANSFER_FAILED = "TRANSFER_FAILED";

        // Network errors - retryable
        public static final String NETWORK_ERROR = "NETWORK_ERROR";
        public static final String TIMEOUT = "TIMEOUT";
        public static final String CONNECTION_FAILED = "CONNECTION_FAILED";
        public static final String PEER_UNAVAILABLE = "PEER_UNAVAILABLE";

        // Rate limiting - retryable with backoff
        public static final String RATE_LIMITED = "RATE_LIMITED";
        public static final String CIRCUIT_BREAKER_OPEN = "CIRCUIT_BREAKER_OPEN";

        // System errors - may require manual intervention
        public static final String SYSTEM_OVERLOAD = "SYSTEM_OVERLOAD";
        public static final String RESOURCE_EXHAUSTED = "RESOURCE_EXHAUSTED";
    }
    
    /**
     * Creates a standard error message for invalid message format.
     */
    public static ErrorMessage invalidMessage(String senderId, String receiverId, String details) {
        ErrorMessage error = new ErrorMessage(senderId, receiverId, ErrorCodes.INVALID_MESSAGE, "Invalid message format");
        error.setErrorDetails(details);
        return error;
    }
    
    /**
     * Creates a standard error message for unknown command.
     */
    public static ErrorMessage unknownCommand(String senderId, String receiverId, String command) {
        ErrorMessage error = new ErrorMessage(senderId, receiverId, ErrorCodes.UNKNOWN_COMMAND, "Unknown command: " + command);
        return error;
    }
    
    /**
     * Creates a standard error message for resource not found.
     */
    public static ErrorMessage resourceNotFound(String senderId, String receiverId, String resource) {
        ErrorMessage error = new ErrorMessage(senderId, receiverId, ErrorCodes.RESOURCE_NOT_FOUND, "Resource not found: " + resource);
        return error;
    }
    
    /**
     * Creates a standard error message for internal errors.
     */
    public static ErrorMessage internalError(String senderId, String receiverId, String details) {
        ErrorMessage error = new ErrorMessage(senderId, receiverId, ErrorCodes.INTERNAL_ERROR, "Internal server error");
        error.setErrorDetails(details);
        return error;
    }
    
    /**
     * Creates a standard error message for file not found.
     */
    public static ErrorMessage fileNotFound(String senderId, String receiverId, String fileName) {
        ErrorMessage error = new ErrorMessage(senderId, receiverId, ErrorCodes.FILE_NOT_FOUND, "File not found: " + fileName);
        return error;
    }
    
    /**
     * Creates a standard error message for peer unavailable.
     */
    public static ErrorMessage peerUnavailable(String senderId, String receiverId, String peerId) {
        ErrorMessage error = new ErrorMessage(senderId, receiverId, ErrorCodes.PEER_UNAVAILABLE, "Peer unavailable: " + peerId);
        return error;
    }
}
