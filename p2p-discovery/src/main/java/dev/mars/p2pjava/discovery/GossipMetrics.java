package dev.mars.p2pjava.discovery;

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
