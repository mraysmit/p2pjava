package dev.mars.p2pjava.discovery;

/**
 * Metrics for the gossip protocol performance and network health.
 */
public class GossipMetrics {
    private final int activePeerCount;
    private final long messagesSent;
    private final long messagesReceived;
    private final long bytesCompressed;
    private final int pendingMessages;
    
    public GossipMetrics(int activePeerCount, long messagesSent, long messagesReceived, 
                        long bytesCompressed, int pendingMessages) {
        this.activePeerCount = activePeerCount;
        this.messagesSent = messagesSent;
        this.messagesReceived = messagesReceived;
        this.bytesCompressed = bytesCompressed;
        this.pendingMessages = pendingMessages;
    }
    
    public int getActivePeerCount() {
        return activePeerCount;
    }
    
    public long getMessagesSent() {
        return messagesSent;
    }
    
    public long getMessagesReceived() {
        return messagesReceived;
    }
    
    public long getBytesCompressed() {
        return bytesCompressed;
    }
    
    public int getPendingMessages() {
        return pendingMessages;
    }
    
    public double getMessageRatio() {
        if (messagesReceived == 0) return 0.0;
        return (double) messagesSent / messagesReceived;
    }
    
    @Override
    public String toString() {
        return String.format("GossipMetrics{peers=%d, sent=%d, received=%d, compressed=%d bytes, pending=%d}",
                activePeerCount, messagesSent, messagesReceived, bytesCompressed, pendingMessages);
    }
}
