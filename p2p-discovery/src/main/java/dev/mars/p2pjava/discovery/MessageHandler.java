package dev.mars.p2pjava.discovery;

/**
 * Interface for handling gossip protocol messages.
 */
public interface MessageHandler {
    
    /**
     * Handles a received gossip message.
     *
     * @param message The received message
     * @param senderAddress The address of the sender
     */
    void handleMessage(RegistryMessage message, String senderAddress);
}
