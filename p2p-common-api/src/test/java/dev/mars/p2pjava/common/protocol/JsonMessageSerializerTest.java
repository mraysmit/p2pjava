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


import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

/**
 * Test class for JsonMessageSerializer.
 */
class JsonMessageSerializerTest {
    
    private JsonMessageSerializer serializer;
    
    @BeforeEach
    void setUp() {
        serializer = JsonMessageSerializer.getInstance();
    }
    
    @Test
    void testSerializeTrackerRegisterRequest() throws Exception {
        TrackerMessage.RegisterRequest request = new TrackerMessage.RegisterRequest(
            "peer1", "peer1", "localhost", 8080
        );
        
        String json = serializer.serialize(request);
        
        assertNotNull(json);
        assertTrue(json.contains("\"messageType\" : \"tracker\""));
        assertTrue(json.contains("\"action\" : \"register\""));
        assertTrue(json.contains("\"peerId\" : \"peer1\""));
        assertTrue(json.contains("\"host\" : \"localhost\""));
        assertTrue(json.contains("\"port\" : 8080"));
    }
    
    @Test
    void testDeserializeTrackerRegisterRequest() throws Exception {
        String json = """
            {
              "messageType" : "tracker",
              "action" : "register",
              "messageId" : "test-id",
              "timestamp" : "2025-07-08T16:00:00Z",
              "version" : "1.0",
              "senderId" : "peer1",
              "receiverId" : "tracker",
              "peerId" : "peer1",
              "host" : "localhost",
              "port" : 8080
            }
            """;
        
        JsonMessage message = serializer.deserialize(json);
        
        assertNotNull(message);
        assertInstanceOf(TrackerMessage.RegisterRequest.class, message);
        
        TrackerMessage.RegisterRequest request = (TrackerMessage.RegisterRequest) message;
        assertEquals("peer1", request.getPeerId());
        assertEquals("localhost", request.getHost());
        assertEquals(8080, request.getPort());
        assertEquals("register", request.getAction());
    }
    
    @Test
    void testSerializeIndexServerMessage() throws Exception {
        IndexServerMessage.RegisterFileRequest request = new IndexServerMessage.RegisterFileRequest(
            "peer1", "test.txt", "peer1", "localhost", 8080, 1024, "abc123"
        );
        
        String json = serializer.serialize(request);
        
        assertNotNull(json);
        assertTrue(json.contains("\"messageType\" : \"indexserver\""));
        assertTrue(json.contains("\"action\" : \"registerFile\""));
        assertTrue(json.contains("\"fileName\" : \"test.txt\""));
        assertTrue(json.contains("\"fileSize\" : 1024"));
        assertTrue(json.contains("\"checksum\" : \"abc123\""));
    }
    
    @Test
    void testSerializePeerMessage() throws Exception {
        PeerMessage.FileRequest request = new PeerMessage.FileRequest(
            "peer1", "peer2", "test.txt", "abc123"
        );
        
        String json = serializer.serialize(request);
        
        assertNotNull(json);
        assertTrue(json.contains("\"messageType\" : \"peer\""));
        assertTrue(json.contains("\"action\" : \"fileRequest\""));
        assertTrue(json.contains("\"fileName\" : \"test.txt\""));
        assertTrue(json.contains("\"expectedChecksum\" : \"abc123\""));
    }
    
    @Test
    void testSerializeErrorMessage() throws Exception {
        ErrorMessage error = new ErrorMessage(
            "system", "peer1", "FILE_NOT_FOUND", "File not found: test.txt"
        );
        
        String json = serializer.serialize(error);
        
        assertNotNull(json);
        assertTrue(json.contains("\"messageType\" : \"error\""));
        assertTrue(json.contains("\"errorCode\" : \"FILE_NOT_FOUND\""));
        assertTrue(json.contains("\"errorMessage\" : \"File not found: test.txt\""));
    }
    
    @Test
    void testSerializeHeartbeatMessage() throws Exception {
        HeartbeatMessage heartbeat = new HeartbeatMessage(
            "peer1", "tracker", "peer1", "ONLINE"
        );
        heartbeat.setLoad(0.5);
        heartbeat.setAvailableFiles(10);
        heartbeat.setActiveConnections(3);
        
        String json = serializer.serialize(heartbeat);
        
        assertNotNull(json);
        assertTrue(json.contains("\"messageType\" : \"heartbeat\""));
        assertTrue(json.contains("\"peerId\" : \"peer1\""));
        assertTrue(json.contains("\"status\" : \"ONLINE\""));
        assertTrue(json.contains("\"load\" : 0.5"));
        assertTrue(json.contains("\"availableFiles\" : 10"));
    }
    
    @Test
    void testSerializeCompact() throws Exception {
        TrackerMessage.RegisterRequest request = new TrackerMessage.RegisterRequest(
            "peer1", "peer1", "localhost", 8080
        );
        
        String compactJson = serializer.serializeCompact(request);
        String regularJson = serializer.serialize(request);
        
        assertNotNull(compactJson);
        assertNotNull(regularJson);
        assertTrue(compactJson.length() < regularJson.length());
        assertFalse(compactJson.contains("\n"));
        assertFalse(compactJson.contains("  "));
    }
    
    @Test
    void testDeserializeWithSpecificType() throws Exception {
        TrackerMessage.RegisterRequest originalRequest = new TrackerMessage.RegisterRequest(
            "peer1", "peer1", "localhost", 8080
        );
        
        String json = serializer.serialize(originalRequest);
        TrackerMessage.RegisterRequest deserializedRequest = 
            serializer.deserialize(json, TrackerMessage.RegisterRequest.class);
        
        assertNotNull(deserializedRequest);
        assertEquals(originalRequest.getPeerId(), deserializedRequest.getPeerId());
        assertEquals(originalRequest.getHost(), deserializedRequest.getHost());
        assertEquals(originalRequest.getPort(), deserializedRequest.getPort());
    }
    
    @Test
    void testRoundTripSerialization() throws Exception {
        // Test various message types
        List<JsonMessage> messages = Arrays.asList(
            new TrackerMessage.RegisterRequest("peer1", "peer1", "localhost", 8080),
            new TrackerMessage.DiscoverRequest("peer1"),
            new IndexServerMessage.SearchFilesRequest("peer1", "*.txt", 50),
            new PeerMessage.PingRequest("peer1", "peer2"),
            new ErrorMessage("system", "peer1", "TEST_ERROR", "Test error message"),
            new HeartbeatMessage("peer1", "tracker", "peer1", "ONLINE")
        );
        
        for (JsonMessage originalMessage : messages) {
            String json = serializer.serialize(originalMessage);
            JsonMessage deserializedMessage = serializer.deserialize(json);
            
            assertNotNull(deserializedMessage);
            assertEquals(originalMessage.getClass(), deserializedMessage.getClass());
            assertEquals(originalMessage.getMessageType(), deserializedMessage.getMessageType());
            assertEquals(originalMessage.getSenderId(), deserializedMessage.getSenderId());
            assertEquals(originalMessage.getReceiverId(), deserializedMessage.getReceiverId());
        }
    }
    
    @Test
    void testInvalidMessageSerialization() {
        // Test with invalid message (null required field)
        TrackerMessage.RegisterRequest invalidRequest = new TrackerMessage.RegisterRequest();
        invalidRequest.setPeerId(null); // Invalid - required field
        
        assertThrows(JsonMessageSerializer.JsonSerializationException.class, () -> {
            serializer.serialize(invalidRequest);
        });
    }
    
    @Test
    void testInvalidJsonDeserialization() {
        String invalidJson = "{ invalid json }";
        
        assertThrows(JsonMessageSerializer.JsonSerializationException.class, () -> {
            serializer.deserialize(invalidJson);
        });
    }
    
    @Test
    void testNullMessageSerialization() {
        assertThrows(JsonMessageSerializer.JsonSerializationException.class, () -> {
            serializer.serialize(null);
        });
    }
    
    @Test
    void testEmptyJsonDeserialization() {
        assertThrows(JsonMessageSerializer.JsonSerializationException.class, () -> {
            serializer.deserialize("");
        });
        
        assertThrows(JsonMessageSerializer.JsonSerializationException.class, () -> {
            serializer.deserialize(null);
        });
    }
    
    @Test
    void testIsValidJson() {
        assertTrue(serializer.isValidJson("{\"test\": \"value\"}"));
        assertTrue(serializer.isValidJson("[]"));
        assertTrue(serializer.isValidJson("\"string\""));
        assertTrue(serializer.isValidJson("123"));
        assertTrue(serializer.isValidJson("true"));
        
        assertFalse(serializer.isValidJson("{ invalid }"));
        assertFalse(serializer.isValidJson(""));
        assertFalse(serializer.isValidJson(null));
        assertFalse(serializer.isValidJson("not json at all"));
    }
    
    @Test
    void testWrongTypeDeserialization() throws Exception {
        TrackerMessage.RegisterRequest request = new TrackerMessage.RegisterRequest(
            "peer1", "peer1", "localhost", 8080
        );
        
        String json = serializer.serialize(request);
        
        // Try to deserialize as wrong type
        assertThrows(JsonMessageSerializer.JsonSerializationException.class, () -> {
            serializer.deserialize(json, PeerMessage.FileRequest.class);
        });
    }
    
    @Test
    void testMessageHeaders() throws Exception {
        TrackerMessage.RegisterRequest request = new TrackerMessage.RegisterRequest(
            "peer1", "peer1", "localhost", 8080
        );
        
        request.addHeader("custom-header", "custom-value");
        request.addHeader("session-id", "session123");
        
        String json = serializer.serialize(request);
        JsonMessage deserializedMessage = serializer.deserialize(json);
        
        assertEquals("custom-value", deserializedMessage.getHeader("custom-header"));
        assertEquals("session123", deserializedMessage.getHeader("session-id"));
    }
    
    @Test
    void testMessageCorrelation() throws Exception {
        TrackerMessage.RegisterRequest request = new TrackerMessage.RegisterRequest(
            "peer1", "peer1", "localhost", 8080
        );
        
        TrackerMessage.RegisterResponse response = new TrackerMessage.RegisterResponse(
            "tracker", "peer1", true, "peer1", "Registration successful"
        );
        response.setAsResponseTo(request);
        
        String json = serializer.serialize(response);
        TrackerMessage.RegisterResponse deserializedResponse = 
            serializer.deserialize(json, TrackerMessage.RegisterResponse.class);
        
        assertEquals(request.getMessageId(), deserializedResponse.getCorrelationId());
        assertEquals(request.getSenderId(), deserializedResponse.getReceiverId());
    }
}
