package dev.mars.p2pjava.common.protocol;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Central dispatcher for routing JSON messages to appropriate handlers.
 * Supports multiple handlers per message type with priority-based ordering.
 */
public class MessageDispatcher {
    private static final Logger logger = Logger.getLogger(MessageDispatcher.class.getName());
    
    private final Map<Class<? extends JsonMessage>, List<MessageHandler<?, ?>>> handlers;
    private final List<MessageInterceptor> interceptors;
    private final JsonMessageSerializer serializer;
    
    public MessageDispatcher() {
        this.handlers = new ConcurrentHashMap<>();
        this.interceptors = new CopyOnWriteArrayList<>();
        this.serializer = JsonMessageSerializer.getInstance();
    }
    
    /**
     * Registers a message handler.
     *
     * @param handler The handler to register
     * @param <T> The message type
     * @param <R> The response type
     */
    public <T extends JsonMessage, R extends JsonMessage> void registerHandler(MessageHandler<T, R> handler) {
        Class<T> messageType = handler.getMessageType();
        
        handlers.computeIfAbsent(messageType, k -> new CopyOnWriteArrayList<>()).add(handler);
        
        // Sort handlers by priority (highest first)
        handlers.get(messageType).sort((h1, h2) -> Integer.compare(h2.getPriority(), h1.getPriority()));
        
        handler.onRegister();
        logger.info("Registered handler for message type: " + messageType.getSimpleName());
    }
    
    /**
     * Unregisters a message handler.
     *
     * @param handler The handler to unregister
     * @param <T> The message type
     * @param <R> The response type
     */
    public <T extends JsonMessage, R extends JsonMessage> void unregisterHandler(MessageHandler<T, R> handler) {
        Class<T> messageType = handler.getMessageType();
        List<MessageHandler<?, ?>> handlerList = handlers.get(messageType);
        
        if (handlerList != null) {
            handlerList.remove(handler);
            if (handlerList.isEmpty()) {
                handlers.remove(messageType);
            }
            handler.onUnregister();
            logger.info("Unregistered handler for message type: " + messageType.getSimpleName());
        }
    }
    
    /**
     * Registers a message interceptor.
     *
     * @param interceptor The interceptor to register
     */
    public void registerInterceptor(MessageInterceptor interceptor) {
        interceptors.add(interceptor);
        logger.info("Registered message interceptor: " + interceptor.getClass().getSimpleName());
    }
    
    /**
     * Unregisters a message interceptor.
     *
     * @param interceptor The interceptor to unregister
     */
    public void unregisterInterceptor(MessageInterceptor interceptor) {
        interceptors.remove(interceptor);
        logger.info("Unregistered message interceptor: " + interceptor.getClass().getSimpleName());
    }
    
    /**
     * Dispatches a JSON string message to appropriate handlers.
     *
     * @param jsonMessage The JSON message string
     * @param context The message context
     * @return A CompletableFuture containing the response message, or null if no response
     */
    public CompletableFuture<JsonMessage> dispatch(String jsonMessage, MessageContext context) {
        try {
            // Deserialize the message
            JsonMessage message = serializer.deserialize(jsonMessage);
            return dispatch(message, context);
            
        } catch (JsonMessageSerializer.JsonSerializationException e) {
            logger.log(Level.WARNING, "Failed to deserialize message: " + jsonMessage, e);
            
            // Create error response
            ErrorMessage errorMessage = serializer.createSerializationErrorMessage(
                "system", 
                context.getRemotePeerId() != null ? context.getRemotePeerId() : "unknown",
                jsonMessage, 
                e
            );
            
            return CompletableFuture.completedFuture(errorMessage);
        }
    }
    
    /**
     * Dispatches a message object to appropriate handlers.
     *
     * @param message The message to dispatch
     * @param context The message context
     * @return A CompletableFuture containing the response message, or null if no response
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<JsonMessage> dispatch(JsonMessage message, MessageContext context) {
        logger.fine("Dispatching message: " + message.getClass().getSimpleName() + " from " + context.getRemoteAddress());
        
        // Apply pre-processing interceptors
        for (MessageInterceptor interceptor : interceptors) {
            try {
                if (!interceptor.preProcess(message, context)) {
                    logger.fine("Message processing stopped by interceptor: " + interceptor.getClass().getSimpleName());
                    return CompletableFuture.completedFuture(null);
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error in message interceptor pre-processing", e);
            }
        }
        
        // Find handlers for this message type
        List<MessageHandler<?, ?>> messageHandlers = handlers.get(message.getClass());
        
        if (messageHandlers == null || messageHandlers.isEmpty()) {
            logger.warning("No handlers found for message type: " + message.getClass().getSimpleName());
            
            ErrorMessage errorMessage = ErrorMessage.unknownCommand(
                "system",
                message.getSenderId(),
                message.getClass().getSimpleName()
            );
            
            return CompletableFuture.completedFuture(errorMessage);
        }
        
        // Process with the first available handler
        MessageHandler<JsonMessage, JsonMessage> handler = 
            (MessageHandler<JsonMessage, JsonMessage>) messageHandlers.get(0);
        
        try {
            CompletableFuture<JsonMessage> responseFuture = handler.handle(message, context);
            
            // Apply post-processing interceptors to the response
            return responseFuture.thenApply(response -> {
                if (response != null) {
                    for (MessageInterceptor interceptor : interceptors) {
                        try {
                            interceptor.postProcess(message, response, context);
                        } catch (Exception e) {
                            logger.log(Level.WARNING, "Error in message interceptor post-processing", e);
                        }
                    }
                }
                return response;
            });
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling message: " + message, e);
            
            ErrorMessage errorMessage = ErrorMessage.internalError(
                "system",
                message.getSenderId(),
                e.getMessage()
            );
            
            return CompletableFuture.completedFuture(errorMessage);
        }
    }
    
    /**
     * Gets all registered handlers for a specific message type.
     *
     * @param messageType The message type
     * @return List of handlers, or empty list if none found
     */
    public List<MessageHandler<?, ?>> getHandlers(Class<? extends JsonMessage> messageType) {
        List<MessageHandler<?, ?>> handlerList = handlers.get(messageType);
        return handlerList != null ? new ArrayList<>(handlerList) : Collections.emptyList();
    }
    
    /**
     * Gets all registered message types.
     *
     * @return Set of message types that have registered handlers
     */
    public Set<Class<? extends JsonMessage>> getRegisteredMessageTypes() {
        return new HashSet<>(handlers.keySet());
    }
    
    /**
     * Gets the total number of registered handlers.
     *
     * @return The total number of handlers
     */
    public int getHandlerCount() {
        return handlers.values().stream().mapToInt(List::size).sum();
    }
    
    /**
     * Clears all registered handlers and interceptors.
     */
    public void clear() {
        // Notify handlers they're being unregistered
        handlers.values().stream()
                .flatMap(List::stream)
                .forEach(MessageHandler::onUnregister);
        
        handlers.clear();
        interceptors.clear();
        logger.info("Cleared all message handlers and interceptors");
    }
    
    /**
     * Interface for message interceptors that can modify or monitor message processing.
     */
    public interface MessageInterceptor {
        
        /**
         * Called before message processing.
         *
         * @param message The message being processed
         * @param context The message context
         * @return true to continue processing, false to stop
         */
        boolean preProcess(JsonMessage message, MessageContext context);
        
        /**
         * Called after message processing.
         *
         * @param request The original request message
         * @param response The response message (may be null)
         * @param context The message context
         */
        void postProcess(JsonMessage request, JsonMessage response, MessageContext context);
    }
}
