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

import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test class for MessageDispatcher.
 */
class MessageDispatcherTest {
    
    private MessageDispatcher dispatcher;
    private MessageContext testContext;
    
    @BeforeEach
    void setUp() throws Exception {
        dispatcher = new MessageDispatcher();
        testContext = new MessageContext(
            "test-connection", 
            InetAddress.getLocalHost(), 
            8080, 
            "TCP"
        );
        testContext.setRemotePeerId("test-peer");
    }
    
    @AfterEach
    void tearDown() {
        dispatcher.clear();
    }
    
    @Test
    void testRegisterAndDispatchHandler() throws Exception {
        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        
        // Create a test handler
        MessageHandler<TrackerMessage.RegisterRequest, TrackerMessage.RegisterResponse> handler = 
            new MessageHandler<TrackerMessage.RegisterRequest, TrackerMessage.RegisterResponse>() {
                @Override
                public CompletableFuture<TrackerMessage.RegisterResponse> handle(
                        TrackerMessage.RegisterRequest message, MessageContext context) {
                    handlerCalled.set(true);
                    TrackerMessage.RegisterResponse response = new TrackerMessage.RegisterResponse(
                        "tracker", message.getSenderId(), true, message.getPeerId(), "Success"
                    );
                    return CompletableFuture.completedFuture(response);
                }
                
                @Override
                public Class<TrackerMessage.RegisterRequest> getMessageType() {
                    return TrackerMessage.RegisterRequest.class;
                }
            };
        
        // Register handler
        dispatcher.registerHandler(handler);
        
        // Create and dispatch message
        TrackerMessage.RegisterRequest request = new TrackerMessage.RegisterRequest(
            "peer1", "peer1", "localhost", 8080
        );
        
        CompletableFuture<JsonMessage> responseFuture = dispatcher.dispatch(request, testContext);
        JsonMessage response = responseFuture.get();
        
        assertTrue(handlerCalled.get());
        assertNotNull(response);
        assertInstanceOf(TrackerMessage.RegisterResponse.class, response);
        
        TrackerMessage.RegisterResponse registerResponse = (TrackerMessage.RegisterResponse) response;
        assertTrue(registerResponse.isSuccess());
        assertEquals("peer1", registerResponse.getPeerId());
    }
    
    @Test
    void testDispatchJsonString() throws Exception {
        // Register handler
        MessageHandler<TrackerMessage.DiscoverRequest, TrackerMessage.DiscoverResponse> handler = 
            new MessageHandler<TrackerMessage.DiscoverRequest, TrackerMessage.DiscoverResponse>() {
                @Override
                public CompletableFuture<TrackerMessage.DiscoverResponse> handle(
                        TrackerMessage.DiscoverRequest message, MessageContext context) {
                    TrackerMessage.DiscoverResponse response = new TrackerMessage.DiscoverResponse(
                        "tracker", message.getSenderId(), java.util.Collections.emptyList()
                    );
                    return CompletableFuture.completedFuture(response);
                }
                
                @Override
                public Class<TrackerMessage.DiscoverRequest> getMessageType() {
                    return TrackerMessage.DiscoverRequest.class;
                }
            };
        
        dispatcher.registerHandler(handler);
        
        // Create JSON message
        String jsonMessage = """
            {
              "messageType" : "tracker",
              "action" : "discover",
              "messageId" : "test-id",
              "timestamp" : "2025-07-08T16:00:00Z",
              "version" : "1.0",
              "senderId" : "peer1",
              "receiverId" : "tracker"
            }
            """;
        
        CompletableFuture<JsonMessage> responseFuture = dispatcher.dispatch(jsonMessage, testContext);
        JsonMessage response = responseFuture.get();
        
        assertNotNull(response);
        assertInstanceOf(TrackerMessage.DiscoverResponse.class, response);
    }
    
    @Test
    void testNoHandlerFound() throws Exception {
        TrackerMessage.RegisterRequest request = new TrackerMessage.RegisterRequest(
            "peer1", "peer1", "localhost", 8080
        );
        
        CompletableFuture<JsonMessage> responseFuture = dispatcher.dispatch(request, testContext);
        JsonMessage response = responseFuture.get();
        
        assertNotNull(response);
        assertInstanceOf(ErrorMessage.class, response);
        
        ErrorMessage errorMessage = (ErrorMessage) response;
        assertEquals(ErrorMessage.ErrorCodes.UNKNOWN_COMMAND, errorMessage.getErrorCode());
    }
    
    @Test
    void testInvalidJsonMessage() throws Exception {
        String invalidJson = "{ invalid json }";
        
        CompletableFuture<JsonMessage> responseFuture = dispatcher.dispatch(invalidJson, testContext);
        JsonMessage response = responseFuture.get();
        
        assertNotNull(response);
        assertInstanceOf(ErrorMessage.class, response);
        
        ErrorMessage errorMessage = (ErrorMessage) response;
        assertEquals(ErrorMessage.ErrorCodes.INVALID_MESSAGE, errorMessage.getErrorCode());
    }
    
    @Test
    void testHandlerPriority() throws Exception {
        AtomicInteger callOrder = new AtomicInteger(0);
        AtomicInteger firstHandlerCall = new AtomicInteger(-1);
        AtomicInteger secondHandlerCall = new AtomicInteger(-1);
        
        // Create handlers with different priorities
        MessageHandler<TrackerMessage.RegisterRequest, TrackerMessage.RegisterResponse> lowPriorityHandler = 
            new MessageHandler<TrackerMessage.RegisterRequest, TrackerMessage.RegisterResponse>() {
                @Override
                public CompletableFuture<TrackerMessage.RegisterResponse> handle(
                        TrackerMessage.RegisterRequest message, MessageContext context) {
                    firstHandlerCall.set(callOrder.incrementAndGet());
                    return CompletableFuture.completedFuture(new TrackerMessage.RegisterResponse(
                        "tracker", message.getSenderId(), true, message.getPeerId(), "Low priority"
                    ));
                }
                
                @Override
                public Class<TrackerMessage.RegisterRequest> getMessageType() {
                    return TrackerMessage.RegisterRequest.class;
                }
                
                @Override
                public int getPriority() {
                    return 1; // Lower priority
                }
            };
        
        MessageHandler<TrackerMessage.RegisterRequest, TrackerMessage.RegisterResponse> highPriorityHandler = 
            new MessageHandler<TrackerMessage.RegisterRequest, TrackerMessage.RegisterResponse>() {
                @Override
                public CompletableFuture<TrackerMessage.RegisterResponse> handle(
                        TrackerMessage.RegisterRequest message, MessageContext context) {
                    secondHandlerCall.set(callOrder.incrementAndGet());
                    return CompletableFuture.completedFuture(new TrackerMessage.RegisterResponse(
                        "tracker", message.getSenderId(), true, message.getPeerId(), "High priority"
                    ));
                }
                
                @Override
                public Class<TrackerMessage.RegisterRequest> getMessageType() {
                    return TrackerMessage.RegisterRequest.class;
                }
                
                @Override
                public int getPriority() {
                    return 10; // Higher priority
                }
            };
        
        // Register handlers (low priority first)
        dispatcher.registerHandler(lowPriorityHandler);
        dispatcher.registerHandler(highPriorityHandler);
        
        // Dispatch message
        TrackerMessage.RegisterRequest request = new TrackerMessage.RegisterRequest(
            "peer1", "peer1", "localhost", 8080
        );
        
        CompletableFuture<JsonMessage> responseFuture = dispatcher.dispatch(request, testContext);
        JsonMessage response = responseFuture.get();
        
        // High priority handler should be called first (and only, since only first handler is used)
        assertEquals(1, secondHandlerCall.get());
        assertEquals(-1, firstHandlerCall.get()); // Should not be called
        
        TrackerMessage.RegisterResponse registerResponse = (TrackerMessage.RegisterResponse) response;
        assertEquals("High priority", registerResponse.getMessage());
    }
    
    @Test
    void testMessageInterceptor() throws Exception {
        AtomicBoolean preProcessCalled = new AtomicBoolean(false);
        AtomicBoolean postProcessCalled = new AtomicBoolean(false);
        
        // Create interceptor
        MessageDispatcher.MessageInterceptor interceptor = new MessageDispatcher.MessageInterceptor() {
            @Override
            public boolean preProcess(JsonMessage message, MessageContext context) {
                preProcessCalled.set(true);
                return true; // Continue processing
            }
            
            @Override
            public void postProcess(JsonMessage request, JsonMessage response, MessageContext context) {
                postProcessCalled.set(true);
            }
        };
        
        // Register interceptor and handler
        dispatcher.registerInterceptor(interceptor);
        
        MessageHandler<TrackerMessage.RegisterRequest, TrackerMessage.RegisterResponse> handler = 
            new MessageHandler<TrackerMessage.RegisterRequest, TrackerMessage.RegisterResponse>() {
                @Override
                public CompletableFuture<TrackerMessage.RegisterResponse> handle(
                        TrackerMessage.RegisterRequest message, MessageContext context) {
                    return CompletableFuture.completedFuture(new TrackerMessage.RegisterResponse(
                        "tracker", message.getSenderId(), true, message.getPeerId(), "Success"
                    ));
                }
                
                @Override
                public Class<TrackerMessage.RegisterRequest> getMessageType() {
                    return TrackerMessage.RegisterRequest.class;
                }
            };
        
        dispatcher.registerHandler(handler);
        
        // Dispatch message
        TrackerMessage.RegisterRequest request = new TrackerMessage.RegisterRequest(
            "peer1", "peer1", "localhost", 8080
        );
        
        CompletableFuture<JsonMessage> responseFuture = dispatcher.dispatch(request, testContext);
        responseFuture.get();
        
        assertTrue(preProcessCalled.get());
        assertTrue(postProcessCalled.get());
    }
    
    @Test
    void testInterceptorStopsProcessing() throws Exception {
        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        
        // Create interceptor that stops processing
        MessageDispatcher.MessageInterceptor interceptor = new MessageDispatcher.MessageInterceptor() {
            @Override
            public boolean preProcess(JsonMessage message, MessageContext context) {
                return false; // Stop processing
            }
            
            @Override
            public void postProcess(JsonMessage request, JsonMessage response, MessageContext context) {
                // Should not be called
            }
        };
        
        // Register interceptor and handler
        dispatcher.registerInterceptor(interceptor);
        
        MessageHandler<TrackerMessage.RegisterRequest, TrackerMessage.RegisterResponse> handler = 
            new MessageHandler<TrackerMessage.RegisterRequest, TrackerMessage.RegisterResponse>() {
                @Override
                public CompletableFuture<TrackerMessage.RegisterResponse> handle(
                        TrackerMessage.RegisterRequest message, MessageContext context) {
                    handlerCalled.set(true);
                    return CompletableFuture.completedFuture(new TrackerMessage.RegisterResponse(
                        "tracker", message.getSenderId(), true, message.getPeerId(), "Success"
                    ));
                }
                
                @Override
                public Class<TrackerMessage.RegisterRequest> getMessageType() {
                    return TrackerMessage.RegisterRequest.class;
                }
            };
        
        dispatcher.registerHandler(handler);
        
        // Dispatch message
        TrackerMessage.RegisterRequest request = new TrackerMessage.RegisterRequest(
            "peer1", "peer1", "localhost", 8080
        );
        
        CompletableFuture<JsonMessage> responseFuture = dispatcher.dispatch(request, testContext);
        JsonMessage response = responseFuture.get();
        
        assertFalse(handlerCalled.get());
        assertNull(response); // No response when processing is stopped
    }
    
    @Test
    void testUnregisterHandler() throws Exception {
        MessageHandler<TrackerMessage.RegisterRequest, TrackerMessage.RegisterResponse> handler = 
            new MessageHandler<TrackerMessage.RegisterRequest, TrackerMessage.RegisterResponse>() {
                @Override
                public CompletableFuture<TrackerMessage.RegisterResponse> handle(
                        TrackerMessage.RegisterRequest message, MessageContext context) {
                    return CompletableFuture.completedFuture(new TrackerMessage.RegisterResponse(
                        "tracker", message.getSenderId(), true, message.getPeerId(), "Success"
                    ));
                }
                
                @Override
                public Class<TrackerMessage.RegisterRequest> getMessageType() {
                    return TrackerMessage.RegisterRequest.class;
                }
            };
        
        // Register and then unregister handler
        dispatcher.registerHandler(handler);
        assertEquals(1, dispatcher.getHandlerCount());
        
        dispatcher.unregisterHandler(handler);
        assertEquals(0, dispatcher.getHandlerCount());
        
        // Dispatch message should result in error
        TrackerMessage.RegisterRequest request = new TrackerMessage.RegisterRequest(
            "peer1", "peer1", "localhost", 8080
        );
        
        CompletableFuture<JsonMessage> responseFuture = dispatcher.dispatch(request, testContext);
        JsonMessage response = responseFuture.get();
        
        assertInstanceOf(ErrorMessage.class, response);
    }
    
    @Test
    void testGetRegisteredMessageTypes() {
        MessageHandler<TrackerMessage.RegisterRequest, TrackerMessage.RegisterResponse> trackerHandler = 
            new MessageHandler<TrackerMessage.RegisterRequest, TrackerMessage.RegisterResponse>() {
                @Override
                public CompletableFuture<TrackerMessage.RegisterResponse> handle(
                        TrackerMessage.RegisterRequest message, MessageContext context) {
                    return CompletableFuture.completedFuture(null);
                }
                
                @Override
                public Class<TrackerMessage.RegisterRequest> getMessageType() {
                    return TrackerMessage.RegisterRequest.class;
                }
            };
        
        MessageHandler<PeerMessage.FileRequest, PeerMessage.FileResponse> peerHandler = 
            new MessageHandler<PeerMessage.FileRequest, PeerMessage.FileResponse>() {
                @Override
                public CompletableFuture<PeerMessage.FileResponse> handle(
                        PeerMessage.FileRequest message, MessageContext context) {
                    return CompletableFuture.completedFuture(null);
                }
                
                @Override
                public Class<PeerMessage.FileRequest> getMessageType() {
                    return PeerMessage.FileRequest.class;
                }
            };
        
        dispatcher.registerHandler(trackerHandler);
        dispatcher.registerHandler(peerHandler);
        
        assertEquals(2, dispatcher.getRegisteredMessageTypes().size());
        assertTrue(dispatcher.getRegisteredMessageTypes().contains(TrackerMessage.RegisterRequest.class));
        assertTrue(dispatcher.getRegisteredMessageTypes().contains(PeerMessage.FileRequest.class));
    }
}
