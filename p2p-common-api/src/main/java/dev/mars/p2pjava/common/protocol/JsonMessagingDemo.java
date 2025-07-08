package dev.mars.p2pjava.common.protocol;

import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Demonstration of the standardized JSON messaging protocol.
 * Shows message creation, serialization, dispatching, and handling.
 */
public class JsonMessagingDemo {
    private static final Logger logger = Logger.getLogger(JsonMessagingDemo.class.getName());
    
    public static void main(String[] args) {
        try {
            JsonMessagingDemo demo = new JsonMessagingDemo();
            demo.runDemo();
        } catch (Exception e) {
            logger.severe("Demo failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void runDemo() throws Exception {
        logger.info("=== JSON Messaging Protocol Demo ===");
        
        // 1. Demonstrate message creation and serialization
        demonstrateMessageSerialization();
        
        // 2. Demonstrate message dispatching and handling
        demonstrateMessageDispatching();
        
        // 3. Demonstrate different message types
        demonstrateMessageTypes();
        
        // 4. Demonstrate error handling
        demonstrateErrorHandling();
        
        logger.info("=== Demo completed successfully ===");
    }
    
    /**
     * Demonstrates creating and serializing different message types.
     */
    private void demonstrateMessageSerialization() throws Exception {
        logger.info("\n--- Message Serialization Demo ---");
        
        JsonMessageSerializer serializer = JsonMessageSerializer.getInstance();
        
        // Create various message types
        TrackerMessage.RegisterRequest registerRequest = new TrackerMessage.RegisterRequest(
            "peer1", "peer1", "localhost", 8080
        );
        
        IndexServerMessage.SearchFilesRequest searchRequest = new IndexServerMessage.SearchFilesRequest(
            "peer1", "*.txt", 50
        );
        
        PeerMessage.FileRequest fileRequest = new PeerMessage.FileRequest(
            "peer1", "peer2", "document.txt", "abc123"
        );
        
        HeartbeatMessage heartbeat = new HeartbeatMessage(
            "peer1", "tracker", "peer1", HeartbeatMessage.Status.ONLINE
        );
        heartbeat.setLoad(0.3);
        heartbeat.setAvailableFiles(15);
        
        ErrorMessage error = ErrorMessage.fileNotFound("system", "peer1", "missing.txt");
        
        // Serialize messages
        logger.info("Serializing messages to JSON:");
        
        String registerJson = serializer.serializeCompact(registerRequest);
        logger.info("TrackerMessage.RegisterRequest: " + registerJson.substring(0, Math.min(100, registerJson.length())) + "...");
        
        String searchJson = serializer.serializeCompact(searchRequest);
        logger.info("IndexServerMessage.SearchFilesRequest: " + searchJson.substring(0, Math.min(100, searchJson.length())) + "...");
        
        String fileJson = serializer.serializeCompact(fileRequest);
        logger.info("PeerMessage.FileRequest: " + fileJson.substring(0, Math.min(100, fileJson.length())) + "...");
        
        String heartbeatJson = serializer.serializeCompact(heartbeat);
        logger.info("HeartbeatMessage: " + heartbeatJson.substring(0, Math.min(100, heartbeatJson.length())) + "...");
        
        String errorJson = serializer.serializeCompact(error);
        logger.info("ErrorMessage: " + errorJson.substring(0, Math.min(100, errorJson.length())) + "...");
    }
    
    /**
     * Demonstrates message dispatching and handling.
     */
    private void demonstrateMessageDispatching() throws Exception {
        logger.info("\n--- Message Dispatching Demo ---");
        
        MessageDispatcher dispatcher = new MessageDispatcher();
        MessageContext context = new MessageContext(
            "demo-connection", 
            InetAddress.getLocalHost(), 
            8080, 
            "TCP"
        );
        context.setRemotePeerId("demo-peer");
        
        // Register handlers
        registerDemoHandlers(dispatcher);
        
        // Create and dispatch messages
        TrackerMessage.RegisterRequest registerRequest = new TrackerMessage.RegisterRequest(
            "peer1", "peer1", "localhost", 8080
        );
        
        logger.info("Dispatching TrackerMessage.RegisterRequest...");
        CompletableFuture<JsonMessage> responseFuture = dispatcher.dispatch(registerRequest, context);
        JsonMessage response = responseFuture.get();
        
        if (response instanceof TrackerMessage.RegisterResponse) {
            TrackerMessage.RegisterResponse registerResponse = (TrackerMessage.RegisterResponse) response;
            logger.info("Received response: success=" + registerResponse.isSuccess() + 
                       ", message=" + registerResponse.getMessage());
        }
        
        // Test heartbeat
        HeartbeatMessage heartbeat = new HeartbeatMessage(
            "peer1", "tracker", "peer1", HeartbeatMessage.Status.ONLINE
        );
        
        logger.info("Dispatching HeartbeatMessage...");
        responseFuture = dispatcher.dispatch(heartbeat, context);
        response = responseFuture.get();
        
        if (response == null) {
            logger.info("Heartbeat processed successfully (no response expected)");
        }
        
        dispatcher.clear();
    }
    
    /**
     * Demonstrates different message types and their features.
     */
    private void demonstrateMessageTypes() throws Exception {
        logger.info("\n--- Message Types Demo ---");
        
        // Tracker Messages
        logger.info("Tracker Messages:");
        TrackerMessage.DiscoverRequest discoverRequest = new TrackerMessage.DiscoverRequest("peer1");
        logger.info("  DiscoverRequest: " + discoverRequest);
        
        TrackerMessage.IsAliveRequest aliveRequest = new TrackerMessage.IsAliveRequest("tracker", "peer1");
        logger.info("  IsAliveRequest: " + aliveRequest);
        
        // Index Server Messages
        logger.info("Index Server Messages:");
        IndexServerMessage.RegisterFileRequest fileRegister = new IndexServerMessage.RegisterFileRequest(
            "peer1", "document.pdf", "peer1", "localhost", 8080, 2048, "def456"
        );
        logger.info("  RegisterFileRequest: " + fileRegister);
        
        IndexServerMessage.GetPeersWithFileRequest getPeers = new IndexServerMessage.GetPeersWithFileRequest(
            "peer1", "document.pdf"
        );
        logger.info("  GetPeersWithFileRequest: " + getPeers);
        
        // Peer Messages
        logger.info("Peer Messages:");
        PeerMessage.PingRequest ping = new PeerMessage.PingRequest("peer1", "peer2");
        logger.info("  PingRequest: " + ping);
        
        PeerMessage.FileTransferStart transferStart = new PeerMessage.FileTransferStart(
            "peer1", "peer2", "transfer123", "document.pdf", 2048, "def456"
        );
        logger.info("  FileTransferStart: " + transferStart);
        
        // Message correlation
        logger.info("Message Correlation:");
        TrackerMessage.RegisterResponse response = new TrackerMessage.RegisterResponse(
            "tracker", "peer1", true, "peer1", "Registration successful"
        );
        response.setAsResponseTo(discoverRequest);
        logger.info("  Response correlated to: " + response.getCorrelationId());
        
        // Message headers
        logger.info("Message Headers:");
        ping.addHeader("session-id", "session123");
        ping.addHeader("priority", "high");
        logger.info("  Ping with headers: session-id=" + ping.getHeader("session-id") + 
                   ", priority=" + ping.getHeader("priority"));
    }
    
    /**
     * Demonstrates error handling in the messaging system.
     */
    private void demonstrateErrorHandling() throws Exception {
        logger.info("\n--- Error Handling Demo ---");
        
        MessageDispatcher dispatcher = new MessageDispatcher();
        MessageContext context = new MessageContext(
            "demo-connection", 
            InetAddress.getLocalHost(), 
            8080, 
            "TCP"
        );
        
        // Test with no handlers registered
        TrackerMessage.RegisterRequest request = new TrackerMessage.RegisterRequest(
            "peer1", "peer1", "localhost", 8080
        );
        
        logger.info("Dispatching message with no handlers registered...");
        CompletableFuture<JsonMessage> responseFuture = dispatcher.dispatch(request, context);
        JsonMessage response = responseFuture.get();
        
        if (response instanceof ErrorMessage) {
            ErrorMessage error = (ErrorMessage) response;
            logger.info("Received error: " + error.getErrorCode() + " - " + error.getErrorMessage());
        }
        
        // Test with invalid JSON
        logger.info("Dispatching invalid JSON...");
        responseFuture = dispatcher.dispatch("{ invalid json }", context);
        response = responseFuture.get();
        
        if (response instanceof ErrorMessage) {
            ErrorMessage error = (ErrorMessage) response;
            logger.info("Received error: " + error.getErrorCode() + " - " + error.getErrorMessage());
        }
        
        // Test standard error messages
        logger.info("Standard Error Messages:");
        ErrorMessage fileNotFound = ErrorMessage.fileNotFound("system", "peer1", "missing.txt");
        logger.info("  File Not Found: " + fileNotFound.getErrorCode() + " - " + fileNotFound.getErrorMessage());
        
        ErrorMessage peerUnavailable = ErrorMessage.peerUnavailable("system", "peer1", "peer2");
        logger.info("  Peer Unavailable: " + peerUnavailable.getErrorCode() + " - " + peerUnavailable.getErrorMessage());
        
        ErrorMessage internalError = ErrorMessage.internalError("system", "peer1", "Database connection failed");
        logger.info("  Internal Error: " + internalError.getErrorCode() + " - " + internalError.getErrorMessage());
        
        dispatcher.clear();
    }
    
    /**
     * Registers demo message handlers.
     */
    private void registerDemoHandlers(MessageDispatcher dispatcher) {
        // Tracker register handler
        dispatcher.registerHandler(new MessageHandler<TrackerMessage.RegisterRequest, TrackerMessage.RegisterResponse>() {
            @Override
            public CompletableFuture<TrackerMessage.RegisterResponse> handle(
                    TrackerMessage.RegisterRequest message, MessageContext context) {
                logger.info("Handling TrackerMessage.RegisterRequest from " + message.getSenderId());
                
                TrackerMessage.RegisterResponse response = new TrackerMessage.RegisterResponse(
                    "tracker", message.getSenderId(), true, message.getPeerId(), 
                    "Peer registered successfully"
                );
                response.setAsResponseTo(message);
                
                return CompletableFuture.completedFuture(response);
            }
            
            @Override
            public Class<TrackerMessage.RegisterRequest> getMessageType() {
                return TrackerMessage.RegisterRequest.class;
            }
        });
        
        // Heartbeat handler
        dispatcher.registerHandler(new MessageHandler<HeartbeatMessage, JsonMessage>() {
            @Override
            public CompletableFuture<JsonMessage> handle(HeartbeatMessage message, MessageContext context) {
                logger.info("Handling HeartbeatMessage from " + message.getSenderId() + 
                           " (status: " + message.getStatus() + ", load: " + message.getLoad() + ")");
                
                // Heartbeat messages typically don't require responses
                return CompletableFuture.completedFuture(null);
            }
            
            @Override
            public Class<HeartbeatMessage> getMessageType() {
                return HeartbeatMessage.class;
            }
        });
        
        // Add logging interceptor
        dispatcher.registerInterceptor(new MessageDispatcher.MessageInterceptor() {
            @Override
            public boolean preProcess(JsonMessage message, MessageContext context) {
                logger.fine("Pre-processing message: " + message.getClass().getSimpleName() + 
                           " from " + context.getRemoteAddress());
                return true;
            }
            
            @Override
            public void postProcess(JsonMessage request, JsonMessage response, MessageContext context) {
                logger.fine("Post-processing complete. Response: " + 
                           (response != null ? response.getClass().getSimpleName() : "none"));
            }
        });
    }
}
