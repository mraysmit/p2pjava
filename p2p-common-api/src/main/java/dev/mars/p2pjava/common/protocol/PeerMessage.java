package dev.mars.p2pjava.common.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base class for peer-to-peer messages.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "action"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = PeerMessage.FileRequest.class, name = "fileRequest"),
    @JsonSubTypes.Type(value = PeerMessage.FileResponse.class, name = "fileResponse"),
    @JsonSubTypes.Type(value = PeerMessage.FileTransferStart.class, name = "fileTransferStart"),
    @JsonSubTypes.Type(value = PeerMessage.FileTransferComplete.class, name = "fileTransferComplete"),
    @JsonSubTypes.Type(value = PeerMessage.FileTransferError.class, name = "fileTransferError"),
    @JsonSubTypes.Type(value = PeerMessage.PingRequest.class, name = "ping"),
    @JsonSubTypes.Type(value = PeerMessage.PongResponse.class, name = "pong")
})
public abstract class PeerMessage extends JsonMessage {
    
    @JsonProperty("action")
    private String action;
    
    protected PeerMessage() {
        super();
    }
    
    protected PeerMessage(String senderId, String receiverId, String action) {
        super(senderId, receiverId);
        this.action = action;
    }
    
    @Override
    public String getMessageType() {
        return "peer";
    }
    
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    
    /**
     * File request message.
     */
    public static class FileRequest extends PeerMessage {
        @JsonProperty("fileName")
        private String fileName;
        
        @JsonProperty("expectedChecksum")
        private String expectedChecksum;
        
        @JsonProperty("rangeStart")
        private long rangeStart = 0;
        
        @JsonProperty("rangeEnd")
        private long rangeEnd = -1; // -1 means entire file
        
        public FileRequest() {
            super();
            setAction("fileRequest");
        }
        
        public FileRequest(String senderId, String receiverId, String fileName, String expectedChecksum) {
            super(senderId, receiverId, "fileRequest");
            this.fileName = fileName;
            this.expectedChecksum = expectedChecksum;
        }
        
        public FileRequest(String senderId, String receiverId, String fileName, String expectedChecksum, long rangeStart, long rangeEnd) {
            this(senderId, receiverId, fileName, expectedChecksum);
            this.rangeStart = rangeStart;
            this.rangeEnd = rangeEnd;
        }
        
        @Override
        public boolean isValid() {
            return fileName != null && !fileName.trim().isEmpty() &&
                   rangeStart >= 0 &&
                   (rangeEnd == -1 || rangeEnd >= rangeStart);
        }
        
        // Getters and setters
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        
        public String getExpectedChecksum() { return expectedChecksum; }
        public void setExpectedChecksum(String expectedChecksum) { this.expectedChecksum = expectedChecksum; }
        
        public long getRangeStart() { return rangeStart; }
        public void setRangeStart(long rangeStart) { this.rangeStart = rangeStart; }
        
        public long getRangeEnd() { return rangeEnd; }
        public void setRangeEnd(long rangeEnd) { this.rangeEnd = rangeEnd; }
    }
    
    /**
     * File response message.
     */
    public static class FileResponse extends PeerMessage {
        @JsonProperty("fileName")
        private String fileName;
        
        @JsonProperty("success")
        private boolean success;
        
        @JsonProperty("message")
        private String message;
        
        @JsonProperty("fileSize")
        private long fileSize;
        
        @JsonProperty("checksum")
        private String checksum;
        
        @JsonProperty("transferId")
        private String transferId;
        
        public FileResponse() {
            super();
            setAction("fileResponse");
        }
        
        public FileResponse(String senderId, String receiverId, String fileName, boolean success, String message) {
            super(senderId, receiverId, "fileResponse");
            this.fileName = fileName;
            this.success = success;
            this.message = message;
        }
        
        @Override
        public boolean isValid() {
            return fileName != null && !fileName.trim().isEmpty() &&
                   message != null;
        }
        
        // Getters and setters
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public long getFileSize() { return fileSize; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }
        
        public String getChecksum() { return checksum; }
        public void setChecksum(String checksum) { this.checksum = checksum; }
        
        public String getTransferId() { return transferId; }
        public void setTransferId(String transferId) { this.transferId = transferId; }
    }
    
    /**
     * File transfer start message.
     */
    public static class FileTransferStart extends PeerMessage {
        @JsonProperty("transferId")
        private String transferId;
        
        @JsonProperty("fileName")
        private String fileName;
        
        @JsonProperty("fileSize")
        private long fileSize;
        
        @JsonProperty("checksum")
        private String checksum;
        
        @JsonProperty("chunkSize")
        private int chunkSize = 8192; // Default 8KB chunks
        
        public FileTransferStart() {
            super();
            setAction("fileTransferStart");
        }
        
        public FileTransferStart(String senderId, String receiverId, String transferId, String fileName, long fileSize, String checksum) {
            super(senderId, receiverId, "fileTransferStart");
            this.transferId = transferId;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.checksum = checksum;
        }
        
        @Override
        public boolean isValid() {
            return transferId != null && !transferId.trim().isEmpty() &&
                   fileName != null && !fileName.trim().isEmpty() &&
                   fileSize >= 0 &&
                   chunkSize > 0;
        }
        
        // Getters and setters
        public String getTransferId() { return transferId; }
        public void setTransferId(String transferId) { this.transferId = transferId; }
        
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        
        public long getFileSize() { return fileSize; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }
        
        public String getChecksum() { return checksum; }
        public void setChecksum(String checksum) { this.checksum = checksum; }
        
        public int getChunkSize() { return chunkSize; }
        public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
    }
    
    /**
     * File transfer complete message.
     */
    public static class FileTransferComplete extends PeerMessage {
        @JsonProperty("transferId")
        private String transferId;
        
        @JsonProperty("fileName")
        private String fileName;
        
        @JsonProperty("success")
        private boolean success;
        
        @JsonProperty("bytesTransferred")
        private long bytesTransferred;
        
        @JsonProperty("actualChecksum")
        private String actualChecksum;
        
        @JsonProperty("transferTimeMs")
        private long transferTimeMs;
        
        public FileTransferComplete() {
            super();
            setAction("fileTransferComplete");
        }
        
        public FileTransferComplete(String senderId, String receiverId, String transferId, String fileName, boolean success, long bytesTransferred) {
            super(senderId, receiverId, "fileTransferComplete");
            this.transferId = transferId;
            this.fileName = fileName;
            this.success = success;
            this.bytesTransferred = bytesTransferred;
        }
        
        @Override
        public boolean isValid() {
            return transferId != null && !transferId.trim().isEmpty() &&
                   fileName != null && !fileName.trim().isEmpty() &&
                   bytesTransferred >= 0;
        }
        
        // Getters and setters
        public String getTransferId() { return transferId; }
        public void setTransferId(String transferId) { this.transferId = transferId; }
        
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public long getBytesTransferred() { return bytesTransferred; }
        public void setBytesTransferred(long bytesTransferred) { this.bytesTransferred = bytesTransferred; }
        
        public String getActualChecksum() { return actualChecksum; }
        public void setActualChecksum(String actualChecksum) { this.actualChecksum = actualChecksum; }
        
        public long getTransferTimeMs() { return transferTimeMs; }
        public void setTransferTimeMs(long transferTimeMs) { this.transferTimeMs = transferTimeMs; }
    }
    
    /**
     * File transfer error message.
     */
    public static class FileTransferError extends PeerMessage {
        @JsonProperty("transferId")
        private String transferId;
        
        @JsonProperty("fileName")
        private String fileName;
        
        @JsonProperty("errorCode")
        private String errorCode;
        
        @JsonProperty("errorMessage")
        private String errorMessage;
        
        public FileTransferError() {
            super();
            setAction("fileTransferError");
        }
        
        public FileTransferError(String senderId, String receiverId, String transferId, String fileName, String errorCode, String errorMessage) {
            super(senderId, receiverId, "fileTransferError");
            this.transferId = transferId;
            this.fileName = fileName;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }
        
        @Override
        public boolean isValid() {
            return transferId != null && !transferId.trim().isEmpty() &&
                   errorCode != null && !errorCode.trim().isEmpty() &&
                   errorMessage != null && !errorMessage.trim().isEmpty();
        }
        
        // Getters and setters
        public String getTransferId() { return transferId; }
        public void setTransferId(String transferId) { this.transferId = transferId; }
        
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        
        public String getErrorCode() { return errorCode; }
        public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
        
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
    
    /**
     * Ping request message for connectivity testing.
     */
    public static class PingRequest extends PeerMessage {
        @JsonProperty("pingTimestamp")
        private long pingTimestamp;

        public PingRequest() {
            super();
            setAction("ping");
            this.pingTimestamp = System.currentTimeMillis();
        }

        public PingRequest(String senderId, String receiverId) {
            super(senderId, receiverId, "ping");
            this.pingTimestamp = System.currentTimeMillis();
        }

        @Override
        public boolean isValid() {
            return pingTimestamp > 0;
        }

        public long getPingTimestamp() { return pingTimestamp; }
        public void setPingTimestamp(long pingTimestamp) { this.pingTimestamp = pingTimestamp; }
    }
    
    /**
     * Pong response message for connectivity testing.
     */
    public static class PongResponse extends PeerMessage {
        @JsonProperty("originalTimestamp")
        private long originalTimestamp;
        
        @JsonProperty("responseTimestamp")
        private long responseTimestamp;
        
        public PongResponse() {
            super();
            setAction("pong");
            this.responseTimestamp = System.currentTimeMillis();
        }
        
        public PongResponse(String senderId, String receiverId, long originalTimestamp) {
            super(senderId, receiverId, "pong");
            this.originalTimestamp = originalTimestamp;
            this.responseTimestamp = System.currentTimeMillis();
        }
        
        @Override
        public boolean isValid() {
            return originalTimestamp > 0 && responseTimestamp > 0;
        }
        
        public long getOriginalTimestamp() { return originalTimestamp; }
        public void setOriginalTimestamp(long originalTimestamp) { this.originalTimestamp = originalTimestamp; }
        
        public long getResponseTimestamp() { return responseTimestamp; }
        public void setResponseTimestamp(long responseTimestamp) { this.responseTimestamp = responseTimestamp; }
        
        /**
         * Calculates the round-trip time in milliseconds.
         */
        public long getRoundTripTime() {
            return responseTimestamp - originalTimestamp;
        }
    }
}
