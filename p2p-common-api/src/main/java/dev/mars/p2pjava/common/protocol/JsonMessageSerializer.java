package dev.mars.p2pjava.common.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Utility class for serializing and deserializing JSON messages.
 * Provides thread-safe JSON operations for the P2P messaging protocol.
 */
public class JsonMessageSerializer {
    private static final Logger logger = Logger.getLogger(JsonMessageSerializer.class.getName());
    
    private static volatile JsonMessageSerializer instance;
    private static final Object lock = new Object();
    
    private final ObjectMapper objectMapper;
    
    /**
     * Private constructor for singleton pattern.
     */
    private JsonMessageSerializer() {
        this.objectMapper = createObjectMapper();
    }
    
    /**
     * Gets the singleton instance.
     */
    public static JsonMessageSerializer getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new JsonMessageSerializer();
                }
            }
        }
        return instance;
    }
    
    /**
     * Creates and configures the ObjectMapper.
     */
    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Register Java 8 time module for Instant serialization
        mapper.registerModule(new JavaTimeModule());

        // Register custom deserializers
        SimpleModule customModule = new SimpleModule();
        customModule.addDeserializer(TrackerMessage.class, new TrackerMessageDeserializer());
        customModule.addDeserializer(IndexServerMessage.class, new IndexServerMessageDeserializer());
        customModule.addDeserializer(PeerMessage.class, new PeerMessageDeserializer());
        mapper.registerModule(customModule);

        // Configure serialization features
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        // Configure deserialization features
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);

        return mapper;
    }
    
    /**
     * Serializes a JsonMessage to JSON string.
     *
     * @param message The message to serialize
     * @return JSON string representation
     * @throws JsonSerializationException If serialization fails
     */
    public String serialize(JsonMessage message) throws JsonSerializationException {
        if (message == null) {
            throw new JsonSerializationException("Message cannot be null");
        }
        
        if (!message.isValid()) {
            throw new JsonSerializationException("Message validation failed: " + message);
        }
        
        try {
            String json = objectMapper.writeValueAsString(message);
            logger.fine("Serialized message: " + message.getClass().getSimpleName() + " -> " + json.length() + " bytes");
            return json;
        } catch (JsonProcessingException e) {
            logger.log(Level.SEVERE, "Failed to serialize message: " + message, e);
            throw new JsonSerializationException("Failed to serialize message", e);
        }
    }
    
    /**
     * Deserializes a JSON string to JsonMessage.
     *
     * @param json The JSON string to deserialize
     * @return The deserialized message
     * @throws JsonSerializationException If deserialization fails
     */
    public JsonMessage deserialize(String json) throws JsonSerializationException {
        if (json == null || json.trim().isEmpty()) {
            throw new JsonSerializationException("JSON string cannot be null or empty");
        }
        
        try {
            JsonMessage message = objectMapper.readValue(json, JsonMessage.class);
            
            if (!message.isValid()) {
                throw new JsonSerializationException("Deserialized message validation failed: " + message);
            }
            
            logger.fine("Deserialized message: " + json.length() + " bytes -> " + message.getClass().getSimpleName());
            return message;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to deserialize JSON: " + json, e);
            throw new JsonSerializationException("Failed to deserialize JSON", e);
        }
    }
    
    /**
     * Deserializes a JSON string to a specific message type.
     *
     * @param json The JSON string to deserialize
     * @param messageClass The expected message class
     * @param <T> The message type
     * @return The deserialized message
     * @throws JsonSerializationException If deserialization fails or type doesn't match
     */
    public <T extends JsonMessage> T deserialize(String json, Class<T> messageClass) throws JsonSerializationException {
        JsonMessage message = deserialize(json);
        
        if (!messageClass.isInstance(message)) {
            throw new JsonSerializationException(
                String.format("Expected message type %s but got %s", 
                            messageClass.getSimpleName(), 
                            message.getClass().getSimpleName()));
        }
        
        return messageClass.cast(message);
    }
    
    /**
     * Serializes a message to compact JSON (no indentation).
     *
     * @param message The message to serialize
     * @return Compact JSON string
     * @throws JsonSerializationException If serialization fails
     */
    public String serializeCompact(JsonMessage message) throws JsonSerializationException {
        if (message == null) {
            throw new JsonSerializationException("Message cannot be null");
        }
        
        if (!message.isValid()) {
            throw new JsonSerializationException("Message validation failed: " + message);
        }
        
        try {
            // Create a temporary mapper without indentation
            ObjectMapper compactMapper = objectMapper.copy();
            compactMapper.disable(SerializationFeature.INDENT_OUTPUT);
            
            String json = compactMapper.writeValueAsString(message);
            logger.fine("Serialized compact message: " + message.getClass().getSimpleName() + " -> " + json.length() + " bytes");
            return json;
        } catch (JsonProcessingException e) {
            logger.log(Level.SEVERE, "Failed to serialize compact message: " + message, e);
            throw new JsonSerializationException("Failed to serialize compact message", e);
        }
    }
    
    /**
     * Validates a JSON string without full deserialization.
     *
     * @param json The JSON string to validate
     * @return true if the JSON is valid, false otherwise
     */
    public boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        
        try {
            objectMapper.readTree(json);
            return true;
        } catch (IOException e) {
            logger.fine("Invalid JSON: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Gets the underlying ObjectMapper for advanced operations.
     * Use with caution as this breaks encapsulation.
     *
     * @return The ObjectMapper instance
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
    
    /**
     * Creates an error message for serialization failures.
     *
     * @param senderId The sender ID
     * @param receiverId The receiver ID
     * @param originalJson The original JSON that failed
     * @param error The error that occurred
     * @return An ErrorMessage describing the serialization failure
     */
    public ErrorMessage createSerializationErrorMessage(String senderId, String receiverId, String originalJson, Exception error) {
        ErrorMessage errorMessage = new ErrorMessage(
            senderId, 
            receiverId, 
            ErrorMessage.ErrorCodes.INVALID_MESSAGE, 
            "JSON serialization/deserialization failed"
        );
        
        errorMessage.setErrorDetails(error.getMessage());
        errorMessage.addHeader("originalJson", originalJson != null ? originalJson.substring(0, Math.min(100, originalJson.length())) : "null");
        errorMessage.addHeader("errorType", error.getClass().getSimpleName());
        
        return errorMessage;
    }
    
    /**
     * Exception thrown when JSON serialization/deserialization fails.
     */
    public static class JsonSerializationException extends Exception {
        public JsonSerializationException(String message) {
            super(message);
        }
        
        public JsonSerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
