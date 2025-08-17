package dev.mars.p2pjava.discovery;

/**
 * Configuration class for gossip protocol settings.
 */
public class GossipConfig {
    private int port = 6003;
    private long intervalMs = 5000;
    private int fanout = 3;
    private long messageTtlMs = 30000;
    private boolean adaptiveFanout = false;
    private boolean priorityPropagation = false;
    private boolean compressionEnabled = false;

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    public void setIntervalMs(long intervalMs) {
        this.intervalMs = intervalMs;
    }

    public int getFanout() {
        return fanout;
    }

    public void setFanout(int fanout) {
        this.fanout = fanout;
    }

    public long getMessageTtlMs() {
        return messageTtlMs;
    }

    public void setMessageTtlMs(long messageTtlMs) {
        this.messageTtlMs = messageTtlMs;
    }

    public boolean isAdaptiveFanout() {
        return adaptiveFanout;
    }

    public void setAdaptiveFanout(boolean adaptiveFanout) {
        this.adaptiveFanout = adaptiveFanout;
    }

    public boolean isPriorityPropagation() {
        return priorityPropagation;
    }

    public void setPriorityPropagation(boolean priorityPropagation) {
        this.priorityPropagation = priorityPropagation;
    }

    public boolean isCompressionEnabled() {
        return compressionEnabled;
    }

    public void setCompressionEnabled(boolean compressionEnabled) {
        this.compressionEnabled = compressionEnabled;
    }
}
