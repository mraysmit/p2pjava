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
