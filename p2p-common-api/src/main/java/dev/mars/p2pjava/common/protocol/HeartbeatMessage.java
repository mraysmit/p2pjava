package dev.mars.p2pjava.common.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Heartbeat message for maintaining connections and monitoring peer health.
 */
public class HeartbeatMessage extends JsonMessage {
    
    @JsonProperty("peerId")
    private String peerId;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("load")
    private double load = 0.0;
    
    @JsonProperty("availableFiles")
    private int availableFiles = 0;
    
    @JsonProperty("activeConnections")
    private int activeConnections = 0;
    
    @JsonProperty("uptime")
    private long uptime = 0;
    
    public HeartbeatMessage() {
        super();
    }
    
    public HeartbeatMessage(String senderId, String receiverId, String peerId, String status) {
        super(senderId, receiverId);
        this.peerId = peerId;
        this.status = status;
    }
    
    @Override
    public String getMessageType() {
        return "heartbeat";
    }
    
    @Override
    public boolean isValid() {
        return peerId != null && !peerId.trim().isEmpty() &&
               status != null && !status.trim().isEmpty();
    }
    
    // Getters and setters
    public String getPeerId() { return peerId; }
    public void setPeerId(String peerId) { this.peerId = peerId; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public double getLoad() { return load; }
    public void setLoad(double load) { this.load = load; }
    
    public int getAvailableFiles() { return availableFiles; }
    public void setAvailableFiles(int availableFiles) { this.availableFiles = availableFiles; }
    
    public int getActiveConnections() { return activeConnections; }
    public void setActiveConnections(int activeConnections) { this.activeConnections = activeConnections; }
    
    public long getUptime() { return uptime; }
    public void setUptime(long uptime) { this.uptime = uptime; }
    
    /**
     * Common status values.
     */
    public static class Status {
        public static final String ONLINE = "ONLINE";
        public static final String BUSY = "BUSY";
        public static final String IDLE = "IDLE";
        public static final String SHUTTING_DOWN = "SHUTTING_DOWN";
        public static final String MAINTENANCE = "MAINTENANCE";
    }
}
