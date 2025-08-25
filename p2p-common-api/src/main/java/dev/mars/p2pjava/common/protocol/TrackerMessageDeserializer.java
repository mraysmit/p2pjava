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
 * Custom deserializer for TrackerMessage that handles polymorphic deserialization
 * based on the action field.
 */
public class TrackerMessageDeserializer extends JsonDeserializer<TrackerMessage> {
    
    @Override
    public TrackerMessage deserialize(JsonParser p, DeserializationContext ctxt) 
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
        
        // Create the appropriate TrackerMessage subclass based on action
        TrackerMessage message = createMessageByAction(action, senderId, receiverId, node, mapper);
        
        // Set common fields
        if (messageId != null) message.setMessageId(messageId);
        if (timestamp != null) message.setTimestamp(timestamp);
        if (version != null) message.setVersion(version);
        if (correlationId != null) message.setCorrelationId(correlationId);
        message.getHeaders().putAll(headers);
        
        return message;
    }
    
    private TrackerMessage createMessageByAction(String action, String senderId, String receiverId, 
                                               JsonNode node, ObjectMapper mapper) throws IOException {
        
        if (action == null) {
            return new TrackerMessage(senderId, receiverId, "unknown");
        }
        
        switch (action) {
            case "register":
                return createRegisterRequest(senderId, receiverId, node);
            case "registerResponse":
                return createRegisterResponse(senderId, receiverId, node);
            case "deregister":
                return createDeregisterRequest(senderId, receiverId, node);
            case "deregisterResponse":
                return createDeregisterResponse(senderId, receiverId, node);
            case "discover":
                return createDiscoverRequest(senderId, receiverId, node);
            case "discoverResponse":
                return createDiscoverResponse(senderId, receiverId, node, mapper);
            case "isAlive":
                return createIsAliveRequest(senderId, receiverId, node);
            case "isAliveResponse":
                return createIsAliveResponse(senderId, receiverId, node);
            default:
                return new TrackerMessage(senderId, receiverId, action);
        }
    }
    
    private TrackerMessage.RegisterRequest createRegisterRequest(String senderId, String receiverId, JsonNode node) {
        String peerId = getTextValue(node, "peerId");
        String host = getTextValue(node, "host");
        int port = node.has("port") ? node.get("port").asInt() : 0;
        
        TrackerMessage.RegisterRequest request = new TrackerMessage.RegisterRequest();
        request.setSenderId(senderId);
        request.setReceiverId(receiverId);
        request.setPeerId(peerId);
        request.setHost(host);
        request.setPort(port);
        return request;
    }
    
    private TrackerMessage.RegisterResponse createRegisterResponse(String senderId, String receiverId, JsonNode node) {
        boolean success = node.has("success") ? node.get("success").asBoolean() : false;
        String message = getTextValue(node, "message");
        
        TrackerMessage.RegisterResponse response = new TrackerMessage.RegisterResponse();
        response.setSenderId(senderId);
        response.setReceiverId(receiverId);
        response.setSuccess(success);
        response.setMessage(message);
        return response;
    }
    
    private TrackerMessage.DeregisterRequest createDeregisterRequest(String senderId, String receiverId, JsonNode node) {
        String peerId = getTextValue(node, "peerId");
        
        TrackerMessage.DeregisterRequest request = new TrackerMessage.DeregisterRequest();
        request.setSenderId(senderId);
        request.setReceiverId(receiverId);
        request.setPeerId(peerId);
        return request;
    }
    
    private TrackerMessage.DeregisterResponse createDeregisterResponse(String senderId, String receiverId, JsonNode node) {
        boolean success = node.has("success") ? node.get("success").asBoolean() : false;
        String message = getTextValue(node, "message");
        
        TrackerMessage.DeregisterResponse response = new TrackerMessage.DeregisterResponse();
        response.setSenderId(senderId);
        response.setReceiverId(receiverId);
        response.setSuccess(success);
        response.setMessage(message);
        return response;
    }
    
    private TrackerMessage.DiscoverRequest createDiscoverRequest(String senderId, String receiverId, JsonNode node) {
        TrackerMessage.DiscoverRequest request = new TrackerMessage.DiscoverRequest();
        request.setSenderId(senderId);
        request.setReceiverId(receiverId);
        return request;
    }
    
    private TrackerMessage.DiscoverResponse createDiscoverResponse(String senderId, String receiverId, 
                                                                 JsonNode node, ObjectMapper mapper) throws IOException {
        TrackerMessage.DiscoverResponse response = new TrackerMessage.DiscoverResponse();
        response.setSenderId(senderId);
        response.setReceiverId(receiverId);
        
        // Parse peers list if present
        if (node.has("peers") && node.get("peers").isArray()) {
            // This would need to be implemented based on the PeerInfo structure
            // For now, we'll leave it empty
        }
        
        return response;
    }
    
    private TrackerMessage.IsAliveRequest createIsAliveRequest(String senderId, String receiverId, JsonNode node) {
        TrackerMessage.IsAliveRequest request = new TrackerMessage.IsAliveRequest();
        request.setSenderId(senderId);
        request.setReceiverId(receiverId);
        return request;
    }
    
    private TrackerMessage.IsAliveResponse createIsAliveResponse(String senderId, String receiverId, JsonNode node) {
        boolean alive = node.has("alive") ? node.get("alive").asBoolean() : false;
        
        TrackerMessage.IsAliveResponse response = new TrackerMessage.IsAliveResponse();
        response.setSenderId(senderId);
        response.setReceiverId(receiverId);
        response.setAlive(alive);
        return response;
    }
    
    private String getTextValue(JsonNode node, String fieldName) {
        return node.has(fieldName) ? node.get(fieldName).asText() : null;
    }
}
