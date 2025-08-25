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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Base class for all JSON messages in the P2P system.
 * Provides common message structure and metadata.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "messageType"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = TrackerMessage.class, name = "tracker"),
    @JsonSubTypes.Type(value = IndexServerMessage.class, name = "indexserver"),
    @JsonSubTypes.Type(value = PeerMessage.class, name = "peer"),
    @JsonSubTypes.Type(value = ErrorMessage.class, name = "error"),
    @JsonSubTypes.Type(value = HeartbeatMessage.class, name = "heartbeat")
})
public abstract class JsonMessage {
    
    @JsonProperty("messageId")
    private String messageId;
    
    @JsonProperty("timestamp")
    private Instant timestamp;
    
    @JsonProperty("version")
    private String version;
    
    @JsonProperty("senderId")
    private String senderId;
    
    @JsonProperty("receiverId")
    private String receiverId;
    
    @JsonProperty("correlationId")
    private String correlationId;
    
    @JsonProperty("headers")
    private Map<String, String> headers;
    
    /**
     * Default constructor for JSON deserialization.
     */
    protected JsonMessage() {
        this.messageId = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
        this.version = "1.0";
        this.headers = new HashMap<>();
    }
    
    /**
     * Constructor with sender and receiver.
     */
    protected JsonMessage(String senderId, String receiverId) {
        this();
        this.senderId = senderId;
        this.receiverId = receiverId;
    }
    
    /**
     * Gets the message type identifier.
     */
    public abstract String getMessageType();
    
    /**
     * Validates the message content.
     */
    public abstract boolean isValid();
    
    // Getters and setters
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    
    public String getReceiverId() { return receiverId; }
    public void setReceiverId(String receiverId) { this.receiverId = receiverId; }
    
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    
    public Map<String, String> getHeaders() { return headers; }
    public void setHeaders(Map<String, String> headers) { this.headers = headers; }
    
    /**
     * Adds a header to the message.
     */
    public void addHeader(String key, String value) {
        if (headers == null) {
            headers = new HashMap<>();
        }
        headers.put(key, value);
    }
    
    /**
     * Gets a header value.
     */
    public String getHeader(String key) {
        return headers != null ? headers.get(key) : null;
    }
    
    /**
     * Creates a response message with correlation ID.
     */
    public void setAsResponseTo(JsonMessage originalMessage) {
        this.correlationId = originalMessage.getMessageId();
        this.receiverId = originalMessage.getSenderId();
    }
    
    @Override
    public String toString() {
        return String.format("%s{messageId='%s', senderId='%s', receiverId='%s', timestamp=%s}", 
                           getClass().getSimpleName(), messageId, senderId, receiverId, timestamp);
    }
}
