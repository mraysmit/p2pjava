package dev.mars.p2pjava.common.protocol;

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


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;
import java.util.Map;

/**
 * Base class for index server-related messages.
 * This class is now concrete to avoid Jackson deserialization issues.
 */
public class IndexServerMessage extends JsonMessage {
    
    @JsonProperty("action")
    private String action;
    
    protected IndexServerMessage() {
        super();
    }
    
    protected IndexServerMessage(String senderId, String receiverId, String action) {
        super(senderId, receiverId);
        this.action = action;
    }
    
    @Override
    public String getMessageType() {
        return "indexserver";
    }
    
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    @Override
    public boolean isValid() {
        // Base validation - subclasses can override for specific validation
        return action != null && !action.trim().isEmpty();
    }
    
    /**
     * Register file request message.
     */
    public static class RegisterFileRequest extends IndexServerMessage {
        @JsonProperty("fileName")
        private String fileName;
        
        @JsonProperty("peerId")
        private String peerId;
        
        @JsonProperty("host")
        private String host;
        
        @JsonProperty("port")
        private int port;
        
        @JsonProperty("fileSize")
        private long fileSize;
        
        @JsonProperty("checksum")
        private String checksum;
        
        public RegisterFileRequest() {
            super();
            setAction("registerFile");
        }
        
        public RegisterFileRequest(String senderId, String fileName, String peerId, String host, int port, long fileSize, String checksum) {
            super(senderId, "indexserver", "registerFile");
            this.fileName = fileName;
            this.peerId = peerId;
            this.host = host;
            this.port = port;
            this.fileSize = fileSize;
            this.checksum = checksum;
        }
        
        @Override
        public boolean isValid() {
            return fileName != null && !fileName.trim().isEmpty() &&
                   peerId != null && !peerId.trim().isEmpty() &&
                   host != null && !host.trim().isEmpty() &&
                   port > 0 && port <= 65535 &&
                   fileSize >= 0;
        }
        
        // Getters and setters
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        
        public String getPeerId() { return peerId; }
        public void setPeerId(String peerId) { this.peerId = peerId; }
        
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        
        public long getFileSize() { return fileSize; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }
        
        public String getChecksum() { return checksum; }
        public void setChecksum(String checksum) { this.checksum = checksum; }
    }
    
    /**
     * Register file response message.
     */
    public static class RegisterFileResponse extends IndexServerMessage {
        @JsonProperty("success")
        private boolean success;
        
        @JsonProperty("message")
        private String message;
        
        @JsonProperty("fileName")
        private String fileName;
        
        public RegisterFileResponse() {
            super();
            setAction("registerFileResponse");
        }
        
        public RegisterFileResponse(String senderId, String receiverId, boolean success, String fileName, String message) {
            super(senderId, receiverId, "registerFileResponse");
            this.success = success;
            this.fileName = fileName;
            this.message = message;
        }
        
        @Override
        public boolean isValid() {
            return message != null;
        }
        
        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
    }
    
    /**
     * Unregister file request message.
     */
    public static class UnregisterFileRequest extends IndexServerMessage {
        @JsonProperty("fileName")
        private String fileName;
        
        @JsonProperty("peerId")
        private String peerId;
        
        public UnregisterFileRequest() {
            super();
            setAction("unregisterFile");
        }
        
        public UnregisterFileRequest(String senderId, String fileName, String peerId) {
            super(senderId, "indexserver", "unregisterFile");
            this.fileName = fileName;
            this.peerId = peerId;
        }
        
        @Override
        public boolean isValid() {
            return fileName != null && !fileName.trim().isEmpty() &&
                   peerId != null && !peerId.trim().isEmpty();
        }
        
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        
        public String getPeerId() { return peerId; }
        public void setPeerId(String peerId) { this.peerId = peerId; }
    }
    
    /**
     * Unregister file response message.
     */
    public static class UnregisterFileResponse extends IndexServerMessage {
        @JsonProperty("success")
        private boolean success;
        
        @JsonProperty("message")
        private String message;
        
        @JsonProperty("fileName")
        private String fileName;
        
        public UnregisterFileResponse() {
            super();
            setAction("unregisterFileResponse");
        }
        
        public UnregisterFileResponse(String senderId, String receiverId, boolean success, String fileName, String message) {
            super(senderId, receiverId, "unregisterFileResponse");
            this.success = success;
            this.fileName = fileName;
            this.message = message;
        }
        
        @Override
        public boolean isValid() {
            return message != null;
        }
        
        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
    }
    
    /**
     * Get peers with file request message.
     */
    public static class GetPeersWithFileRequest extends IndexServerMessage {
        @JsonProperty("fileName")
        private String fileName;
        
        public GetPeersWithFileRequest() {
            super();
            setAction("getPeersWithFile");
        }
        
        public GetPeersWithFileRequest(String senderId, String fileName) {
            super(senderId, "indexserver", "getPeersWithFile");
            this.fileName = fileName;
        }
        
        @Override
        public boolean isValid() {
            return fileName != null && !fileName.trim().isEmpty();
        }
        
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
    }
    
    /**
     * Get peers with file response message.
     */
    public static class GetPeersWithFileResponse extends IndexServerMessage {
        @JsonProperty("fileName")
        private String fileName;
        
        @JsonProperty("peers")
        private List<FileOwnerInfo> peers;
        
        public GetPeersWithFileResponse() {
            super();
            setAction("getPeersWithFileResponse");
        }
        
        public GetPeersWithFileResponse(String senderId, String receiverId, String fileName, List<FileOwnerInfo> peers) {
            super(senderId, receiverId, "getPeersWithFileResponse");
            this.fileName = fileName;
            this.peers = peers;
        }
        
        @Override
        public boolean isValid() {
            return fileName != null && peers != null;
        }
        
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        
        public List<FileOwnerInfo> getPeers() { return peers; }
        public void setPeers(List<FileOwnerInfo> peers) { this.peers = peers; }
    }
    
    /**
     * Search files request message.
     */
    public static class SearchFilesRequest extends IndexServerMessage {
        @JsonProperty("pattern")
        private String pattern;
        
        @JsonProperty("maxResults")
        private int maxResults = 100;
        
        public SearchFilesRequest() {
            super();
            setAction("searchFiles");
        }
        
        public SearchFilesRequest(String senderId, String pattern, int maxResults) {
            super(senderId, "indexserver", "searchFiles");
            this.pattern = pattern;
            this.maxResults = maxResults;
        }
        
        @Override
        public boolean isValid() {
            return pattern != null && !pattern.trim().isEmpty() && maxResults > 0;
        }
        
        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }
        
        public int getMaxResults() { return maxResults; }
        public void setMaxResults(int maxResults) { this.maxResults = maxResults; }
    }
    
    /**
     * Search files response message.
     */
    public static class SearchFilesResponse extends IndexServerMessage {
        @JsonProperty("pattern")
        private String pattern;
        
        @JsonProperty("results")
        private Map<String, List<FileOwnerInfo>> results;
        
        public SearchFilesResponse() {
            super();
            setAction("searchFilesResponse");
        }
        
        public SearchFilesResponse(String senderId, String receiverId, String pattern, Map<String, List<FileOwnerInfo>> results) {
            super(senderId, receiverId, "searchFilesResponse");
            this.pattern = pattern;
            this.results = results;
        }
        
        @Override
        public boolean isValid() {
            return pattern != null && results != null;
        }
        
        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }
        
        public Map<String, List<FileOwnerInfo>> getResults() { return results; }
        public void setResults(Map<String, List<FileOwnerInfo>> results) { this.results = results; }
    }
    
    /**
     * File owner information.
     */
    public static class FileOwnerInfo {
        @JsonProperty("peerId")
        private String peerId;
        
        @JsonProperty("host")
        private String host;
        
        @JsonProperty("port")
        private int port;
        
        @JsonProperty("fileSize")
        private long fileSize;
        
        @JsonProperty("checksum")
        private String checksum;
        
        @JsonProperty("lastSeen")
        private long lastSeen;
        
        public FileOwnerInfo() {}
        
        public FileOwnerInfo(String peerId, String host, int port, long fileSize, String checksum, long lastSeen) {
            this.peerId = peerId;
            this.host = host;
            this.port = port;
            this.fileSize = fileSize;
            this.checksum = checksum;
            this.lastSeen = lastSeen;
        }
        
        // Getters and setters
        public String getPeerId() { return peerId; }
        public void setPeerId(String peerId) { this.peerId = peerId; }
        
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        
        public long getFileSize() { return fileSize; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }
        
        public String getChecksum() { return checksum; }
        public void setChecksum(String checksum) { this.checksum = checksum; }
        
        public long getLastSeen() { return lastSeen; }
        public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }
    }
}
