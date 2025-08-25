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
 * Custom deserializer for IndexServerMessage that handles polymorphic deserialization
 * based on the action field.
 */
public class IndexServerMessageDeserializer extends JsonDeserializer<IndexServerMessage> {
    
    @Override
    public IndexServerMessage deserialize(JsonParser p, DeserializationContext ctxt) 
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
        
        // Create the appropriate IndexServerMessage subclass based on action
        IndexServerMessage message = createMessageByAction(action, senderId, receiverId, node, mapper);
        
        // Set common fields
        if (messageId != null) message.setMessageId(messageId);
        if (timestamp != null) message.setTimestamp(timestamp);
        if (version != null) message.setVersion(version);
        if (correlationId != null) message.setCorrelationId(correlationId);
        message.getHeaders().putAll(headers);
        
        return message;
    }
    
    private IndexServerMessage createMessageByAction(String action, String senderId, String receiverId, 
                                                   JsonNode node, ObjectMapper mapper) throws IOException {
        
        if (action == null) {
            return new IndexServerMessage(senderId, receiverId, "unknown");
        }
        
        IndexServerMessage message;
        switch (action) {
            case "registerFile":
                message = createRegisterFileRequest(senderId, receiverId, node);
                break;
            case "registerFileResponse":
                message = createRegisterFileResponse(senderId, receiverId, node);
                break;
            case "unregisterFile":
                message = createUnregisterFileRequest(senderId, receiverId, node);
                break;
            case "unregisterFileResponse":
                message = createUnregisterFileResponse(senderId, receiverId, node);
                break;
            case "getPeersWithFile":
                message = createGetPeersWithFileRequest(senderId, receiverId, node);
                break;
            case "getPeersWithFileResponse":
                message = createGetPeersWithFileResponse(senderId, receiverId, node);
                break;
            case "searchFiles":
                message = createSearchFilesRequest(senderId, receiverId, node);
                break;
            case "searchFilesResponse":
                message = createSearchFilesResponse(senderId, receiverId, node);
                break;
            default:
                message = new IndexServerMessage(senderId, receiverId, action);
                break;
        }
        
        return message;
    }
    
    private IndexServerMessage.RegisterFileRequest createRegisterFileRequest(String senderId, String receiverId, JsonNode node) {
        IndexServerMessage.RegisterFileRequest request = new IndexServerMessage.RegisterFileRequest();
        request.setSenderId(senderId);
        request.setReceiverId(receiverId);
        
        if (node.has("fileName")) request.setFileName(node.get("fileName").asText());
        if (node.has("peerId")) request.setPeerId(node.get("peerId").asText());
        if (node.has("host")) request.setHost(node.get("host").asText());
        if (node.has("port")) request.setPort(node.get("port").asInt());
        if (node.has("fileSize")) request.setFileSize(node.get("fileSize").asLong());
        if (node.has("checksum")) request.setChecksum(node.get("checksum").asText());
        
        return request;
    }
    
    private IndexServerMessage.RegisterFileResponse createRegisterFileResponse(String senderId, String receiverId, JsonNode node) {
        IndexServerMessage.RegisterFileResponse response = new IndexServerMessage.RegisterFileResponse();
        response.setSenderId(senderId);
        response.setReceiverId(receiverId);
        
        if (node.has("success")) response.setSuccess(node.get("success").asBoolean());
        if (node.has("message")) response.setMessage(node.get("message").asText());
        
        return response;
    }
    
    private IndexServerMessage.UnregisterFileRequest createUnregisterFileRequest(String senderId, String receiverId, JsonNode node) {
        IndexServerMessage.UnregisterFileRequest request = new IndexServerMessage.UnregisterFileRequest();
        request.setSenderId(senderId);
        request.setReceiverId(receiverId);
        
        if (node.has("fileName")) request.setFileName(node.get("fileName").asText());
        if (node.has("peerId")) request.setPeerId(node.get("peerId").asText());
        
        return request;
    }
    
    private IndexServerMessage.UnregisterFileResponse createUnregisterFileResponse(String senderId, String receiverId, JsonNode node) {
        IndexServerMessage.UnregisterFileResponse response = new IndexServerMessage.UnregisterFileResponse();
        response.setSenderId(senderId);
        response.setReceiverId(receiverId);
        
        if (node.has("success")) response.setSuccess(node.get("success").asBoolean());
        if (node.has("message")) response.setMessage(node.get("message").asText());
        
        return response;
    }
    
    private IndexServerMessage.GetPeersWithFileRequest createGetPeersWithFileRequest(String senderId, String receiverId, JsonNode node) {
        IndexServerMessage.GetPeersWithFileRequest request = new IndexServerMessage.GetPeersWithFileRequest();
        request.setSenderId(senderId);
        request.setReceiverId(receiverId);
        
        if (node.has("fileName")) request.setFileName(node.get("fileName").asText());
        
        return request;
    }
    
    private IndexServerMessage.GetPeersWithFileResponse createGetPeersWithFileResponse(String senderId, String receiverId, JsonNode node) {
        IndexServerMessage.GetPeersWithFileResponse response = new IndexServerMessage.GetPeersWithFileResponse();
        response.setSenderId(senderId);
        response.setReceiverId(receiverId);
        
        // Parse peers list if present - this would need to be implemented based on the PeerInfo structure
        // For now, we'll leave it empty
        
        return response;
    }
    
    private IndexServerMessage.SearchFilesRequest createSearchFilesRequest(String senderId, String receiverId, JsonNode node) {
        IndexServerMessage.SearchFilesRequest request = new IndexServerMessage.SearchFilesRequest();
        request.setSenderId(senderId);
        request.setReceiverId(receiverId);
        
        if (node.has("pattern")) request.setPattern(node.get("pattern").asText());
        if (node.has("maxResults")) request.setMaxResults(node.get("maxResults").asInt());
        
        return request;
    }
    
    private IndexServerMessage.SearchFilesResponse createSearchFilesResponse(String senderId, String receiverId, JsonNode node) {
        IndexServerMessage.SearchFilesResponse response = new IndexServerMessage.SearchFilesResponse();
        response.setSenderId(senderId);
        response.setReceiverId(receiverId);
        
        // Parse file results if present - this would need to be implemented based on the FileInfo structure
        // For now, we'll leave it empty
        
        return response;
    }
    
    private String getTextValue(JsonNode node, String fieldName) {
        return node.has(fieldName) ? node.get(fieldName).asText() : null;
    }
}
