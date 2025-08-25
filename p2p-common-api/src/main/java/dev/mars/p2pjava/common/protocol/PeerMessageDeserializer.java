package dev.mars.p2pjava.common.protocol;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom deserializer for PeerMessage that handles polymorphic deserialization
 * based on the action field.
 */
public class PeerMessageDeserializer extends JsonDeserializer<PeerMessage> {
    
    @Override
    public PeerMessage deserialize(JsonParser p, DeserializationContext ctxt) 
            throws IOException, JsonProcessingException {
        
        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        JsonNode node = mapper.readTree(p);
        
        // Extract common fields
        String senderId = getTextValue(node, "senderId");
        String receiverId = getTextValue(node, "receiverId");
        String messageType = getTextValue(node, "messageType");
        String action = getTextValue(node, "action");
        String messageId = getTextValue(node, "messageId");
        String version = getTextValue(node, "version");
        String correlationId = getTextValue(node, "correlationId");
        
        // Parse timestamp
        Instant timestamp = null;
        if (node.has("timestamp")) {
            String timestampStr = node.get("timestamp").asText();
            try {
                timestamp = Instant.parse(timestampStr);
            } catch (Exception e) {
                timestamp = Instant.now();
            }
        }
        
        // Parse headers
        Map<String, String> headers = new HashMap<>();
        if (node.has("headers") && node.get("headers").isObject()) {
            JsonNode headersNode = node.get("headers");
            headersNode.fields().forEachRemaining(entry -> {
                headers.put(entry.getKey(), entry.getValue().asText());
            });
        }
        
        // Create the appropriate PeerMessage subclass based on action
        PeerMessage message = createMessageByAction(action, senderId, receiverId, node, mapper);
        
        // Set common fields
        if (messageId != null) message.setMessageId(messageId);
        if (timestamp != null) message.setTimestamp(timestamp);
        if (version != null) message.setVersion(version);
        if (correlationId != null) message.setCorrelationId(correlationId);
        message.getHeaders().putAll(headers);
        
        return message;
    }
    
    private PeerMessage createMessageByAction(String action, String senderId, String receiverId, 
                                            JsonNode node, ObjectMapper mapper) throws IOException {
        
        if (action == null) {
            return new PeerMessage(senderId, receiverId, "unknown");
        }
        
        PeerMessage message;
        switch (action) {
            case "fileRequest":
                message = createFileRequest(senderId, receiverId, node);
                break;
            case "fileResponse":
                message = createFileResponse(senderId, receiverId, node);
                break;
            case "fileTransferStart":
                message = createFileTransferStart(senderId, receiverId, node);
                break;
            case "fileTransferComplete":
                message = createFileTransferComplete(senderId, receiverId, node);
                break;
            case "fileTransferError":
                message = createFileTransferError(senderId, receiverId, node);
                break;
            case "ping":
                message = createPingRequest(senderId, receiverId, node);
                break;
            case "pong":
                message = createPongResponse(senderId, receiverId, node);
                break;
            default:
                message = new PeerMessage(senderId, receiverId, action);
                break;
        }
        
        return message;
    }
    
    private PeerMessage.FileRequest createFileRequest(String senderId, String receiverId, JsonNode node) {
        PeerMessage.FileRequest request = new PeerMessage.FileRequest();
        request.setSenderId(senderId);
        request.setReceiverId(receiverId);
        
        if (node.has("fileName")) request.setFileName(node.get("fileName").asText());
        if (node.has("expectedChecksum")) request.setExpectedChecksum(node.get("expectedChecksum").asText());
        if (node.has("rangeStart")) request.setRangeStart(node.get("rangeStart").asLong());
        if (node.has("rangeEnd")) request.setRangeEnd(node.get("rangeEnd").asLong());
        
        return request;
    }
    
    private PeerMessage.FileResponse createFileResponse(String senderId, String receiverId, JsonNode node) {
        PeerMessage.FileResponse response = new PeerMessage.FileResponse();
        response.setSenderId(senderId);
        response.setReceiverId(receiverId);
        
        if (node.has("fileName")) response.setFileName(node.get("fileName").asText());
        if (node.has("available")) response.setAvailable(node.get("available").asBoolean());
        if (node.has("fileSize")) response.setFileSize(node.get("fileSize").asLong());
        if (node.has("checksum")) response.setChecksum(node.get("checksum").asText());
        if (node.has("message")) response.setMessage(node.get("message").asText());
        
        return response;
    }
    
    private PeerMessage.FileTransferStart createFileTransferStart(String senderId, String receiverId, JsonNode node) {
        PeerMessage.FileTransferStart start = new PeerMessage.FileTransferStart();
        start.setSenderId(senderId);
        start.setReceiverId(receiverId);
        
        if (node.has("fileName")) start.setFileName(node.get("fileName").asText());
        if (node.has("transferId")) start.setTransferId(node.get("transferId").asText());
        if (node.has("totalSize")) start.setTotalSize(node.get("totalSize").asLong());
        if (node.has("chunkSize")) start.setChunkSize(node.get("chunkSize").asInt());
        
        return start;
    }
    
    private PeerMessage.FileTransferComplete createFileTransferComplete(String senderId, String receiverId, JsonNode node) {
        PeerMessage.FileTransferComplete complete = new PeerMessage.FileTransferComplete();
        complete.setSenderId(senderId);
        complete.setReceiverId(receiverId);
        
        if (node.has("transferId")) complete.setTransferId(node.get("transferId").asText());
        if (node.has("success")) complete.setSuccess(node.get("success").asBoolean());
        if (node.has("bytesTransferred")) complete.setBytesTransferred(node.get("bytesTransferred").asLong());
        if (node.has("checksum")) complete.setChecksum(node.get("checksum").asText());
        if (node.has("message")) complete.setMessage(node.get("message").asText());
        
        return complete;
    }
    
    private PeerMessage.FileTransferError createFileTransferError(String senderId, String receiverId, JsonNode node) {
        PeerMessage.FileTransferError error = new PeerMessage.FileTransferError();
        error.setSenderId(senderId);
        error.setReceiverId(receiverId);
        
        if (node.has("transferId")) error.setTransferId(node.get("transferId").asText());
        if (node.has("errorCode")) error.setErrorCode(node.get("errorCode").asText());
        if (node.has("errorMessage")) error.setErrorMessage(node.get("errorMessage").asText());
        if (node.has("retryable")) error.setRetryable(node.get("retryable").asBoolean());
        
        return error;
    }
    
    private PeerMessage.PingRequest createPingRequest(String senderId, String receiverId, JsonNode node) {
        PeerMessage.PingRequest ping = new PeerMessage.PingRequest();
        ping.setSenderId(senderId);
        ping.setReceiverId(receiverId);
        
        if (node.has("pingTimestamp")) ping.setPingTimestamp(node.get("pingTimestamp").asLong());
        
        return ping;
    }
    
    private PeerMessage.PongResponse createPongResponse(String senderId, String receiverId, JsonNode node) {
        PeerMessage.PongResponse pong = new PeerMessage.PongResponse();
        pong.setSenderId(senderId);
        pong.setReceiverId(receiverId);
        
        if (node.has("pingTimestamp")) pong.setPingTimestamp(node.get("pingTimestamp").asLong());
        if (node.has("pongTimestamp")) pong.setPongTimestamp(node.get("pongTimestamp").asLong());
        
        return pong;
    }
    
    private String getTextValue(JsonNode node, String fieldName) {
        return node.has(fieldName) ? node.get(fieldName).asText() : null;
    }
}
