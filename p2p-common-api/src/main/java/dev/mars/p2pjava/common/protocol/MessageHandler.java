package dev.mars.p2pjava.common.protocol;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for handling JSON messages in the P2P system.
 * Implementations should handle specific message types and provide responses.
 *
 * @param <T> The type of message this handler processes
 * @param <R> The type of response this handler produces
 */
public interface MessageHandler<T extends JsonMessage, R extends JsonMessage> {
    
    /**
     * Handles a message and returns a response asynchronously.
     *
     * @param message The message to handle
     * @param context The message context containing connection and session information
     * @return A CompletableFuture containing the response message, or null if no response is needed
     */
    CompletableFuture<R> handle(T message, MessageContext context);
    
    /**
     * Gets the message type this handler can process.
     *
     * @return The message class this handler supports
     */
    Class<T> getMessageType();
    
    /**
     * Validates if this handler can process the given message.
     *
     * @param message The message to validate
     * @return true if this handler can process the message, false otherwise
     */
    default boolean canHandle(JsonMessage message) {
        return getMessageType().isInstance(message);
    }
    
    /**
     * Gets the priority of this handler. Higher priority handlers are invoked first.
     * Default priority is 0.
     *
     * @return The handler priority
     */
    default int getPriority() {
        return 0;
    }
    
    /**
     * Called when the handler is registered with a message dispatcher.
     */
    default void onRegister() {
        // Default implementation does nothing
    }
    
    /**
     * Called when the handler is unregistered from a message dispatcher.
     */
    default void onUnregister() {
        // Default implementation does nothing
    }
}
