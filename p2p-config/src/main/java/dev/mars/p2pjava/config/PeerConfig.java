package dev.mars.p2pjava.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Peer-specific configuration settings.
 */
public class PeerConfig {
    
    @JsonProperty("socketTimeoutMs")
    private long socketTimeoutMs = 30000;
    
    @JsonProperty("heartbeat")
    private HeartbeatConfig heartbeat = new HeartbeatConfig();
    
    @JsonProperty("connection")
    private P2PConfiguration.ConnectionConfig connection = new P2PConfiguration.ConnectionConfig();
    
    @JsonProperty("threadPool")
    private P2PConfiguration.ThreadPoolConfig threadPool = new P2PConfiguration.ThreadPoolConfig(5);
    
    @JsonProperty("fileSharing")
    private FileSharingConfig fileSharing = new FileSharingConfig();
    
    @JsonProperty("discovery")
    private DiscoveryConfig discovery = new DiscoveryConfig();
    
    // Getters and setters
    public long getSocketTimeoutMs() { return socketTimeoutMs; }
    public void setSocketTimeoutMs(long socketTimeoutMs) { this.socketTimeoutMs = socketTimeoutMs; }
    
    public HeartbeatConfig getHeartbeat() { return heartbeat; }
    public void setHeartbeat(HeartbeatConfig heartbeat) { this.heartbeat = heartbeat; }
    
    public P2PConfiguration.ConnectionConfig getConnection() { return connection; }
    public void setConnection(P2PConfiguration.ConnectionConfig connection) { this.connection = connection; }
    
    public P2PConfiguration.ThreadPoolConfig getThreadPool() { return threadPool; }
    public void setThreadPool(P2PConfiguration.ThreadPoolConfig threadPool) { this.threadPool = threadPool; }
    
    public FileSharingConfig getFileSharing() { return fileSharing; }
    public void setFileSharing(FileSharingConfig fileSharing) { this.fileSharing = fileSharing; }
    
    public DiscoveryConfig getDiscovery() { return discovery; }
    public void setDiscovery(DiscoveryConfig discovery) { this.discovery = discovery; }
    
    /**
     * Heartbeat configuration
     */
    public static class HeartbeatConfig {
        @JsonProperty("intervalSeconds")
        private int intervalSeconds = 30;
        
        @JsonProperty("timeoutMs")
        private long timeoutMs = 10000;
        
        @JsonProperty("maxMissed")
        private int maxMissed = 3;
        
        @JsonProperty("enabled")
        private boolean enabled = true;
        
        // Getters and setters
        public int getIntervalSeconds() { return intervalSeconds; }
        public void setIntervalSeconds(int intervalSeconds) { this.intervalSeconds = intervalSeconds; }
        
        public long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
        
        public int getMaxMissed() { return maxMissed; }
        public void setMaxMissed(int maxMissed) { this.maxMissed = maxMissed; }
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
    
    /**
     * File sharing configuration
     */
    public static class FileSharingConfig {
        @JsonProperty("enabled")
        private boolean enabled = true;
        
        @JsonProperty("shareDirectory")
        private String shareDirectory = "shared";
        
        @JsonProperty("maxFileSize")
        private long maxFileSize = 104857600; // 100MB
        
        @JsonProperty("allowedExtensions")
        private String[] allowedExtensions = {".txt", ".pdf", ".jpg", ".png", ".mp3", ".mp4"};
        
        @JsonProperty("uploadRateLimit")
        private long uploadRateLimit = 1048576; // 1MB/s
        
        @JsonProperty("downloadRateLimit")
        private long downloadRateLimit = 2097152; // 2MB/s
        
        @JsonProperty("maxConcurrentTransfers")
        private int maxConcurrentTransfers = 5;
        
        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public String getShareDirectory() { return shareDirectory; }
        public void setShareDirectory(String shareDirectory) { this.shareDirectory = shareDirectory; }
        
        public long getMaxFileSize() { return maxFileSize; }
        public void setMaxFileSize(long maxFileSize) { this.maxFileSize = maxFileSize; }
        
        public String[] getAllowedExtensions() { return allowedExtensions; }
        public void setAllowedExtensions(String[] allowedExtensions) { this.allowedExtensions = allowedExtensions; }
        
        public long getUploadRateLimit() { return uploadRateLimit; }
        public void setUploadRateLimit(long uploadRateLimit) { this.uploadRateLimit = uploadRateLimit; }
        
        public long getDownloadRateLimit() { return downloadRateLimit; }
        public void setDownloadRateLimit(long downloadRateLimit) { this.downloadRateLimit = downloadRateLimit; }
        
        public int getMaxConcurrentTransfers() { return maxConcurrentTransfers; }
        public void setMaxConcurrentTransfers(int maxConcurrentTransfers) { this.maxConcurrentTransfers = maxConcurrentTransfers; }
    }
    
    /**
     * Peer discovery configuration
     */
    public static class DiscoveryConfig {
        @JsonProperty("enabled")
        private boolean enabled = true;

        @JsonProperty("broadcastPort")
        private int broadcastPort = 6002;

        @JsonProperty("discoveryIntervalMs")
        private long discoveryIntervalMs = 60000;

        @JsonProperty("maxPeers")
        private int maxPeers = 50;

        @JsonProperty("bootstrapPeers")
        private String[] bootstrapPeers = {};

        @JsonProperty("gossip")
        private GossipConfig gossip = new GossipConfig();

        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getBroadcastPort() { return broadcastPort; }
        public void setBroadcastPort(int broadcastPort) { this.broadcastPort = broadcastPort; }

        public long getDiscoveryIntervalMs() { return discoveryIntervalMs; }
        public void setDiscoveryIntervalMs(long discoveryIntervalMs) { this.discoveryIntervalMs = discoveryIntervalMs; }

        public int getMaxPeers() { return maxPeers; }
        public void setMaxPeers(int maxPeers) { this.maxPeers = maxPeers; }

        public String[] getBootstrapPeers() { return bootstrapPeers; }
        public void setBootstrapPeers(String[] bootstrapPeers) { this.bootstrapPeers = bootstrapPeers; }

        public GossipConfig getGossip() { return gossip; }
        public void setGossip(GossipConfig gossip) { this.gossip = gossip; }
    }

    /**
     * Enhanced gossip protocol configuration for efficient service propagation
     */
    public static class GossipConfig {
        @JsonProperty("enabled")
        private boolean enabled = true;

        @JsonProperty("port")
        private int port = 6003;

        @JsonProperty("intervalMs")
        private long intervalMs = 5000;

        @JsonProperty("fanout")
        private int fanout = 3;

        @JsonProperty("messageTtlMs")
        private long messageTtlMs = 30000;

        @JsonProperty("maxHops")
        private int maxHops = 10;

        @JsonProperty("adaptiveFanout")
        private boolean adaptiveFanout = true;

        @JsonProperty("priorityPropagation")
        private boolean priorityPropagation = true;

        @JsonProperty("compressionEnabled")
        private boolean compressionEnabled = true;

        @JsonProperty("batchSize")
        private int batchSize = 10;

        @JsonProperty("antiEntropyIntervalMs")
        private long antiEntropyIntervalMs = 60000;

        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }

        public long getIntervalMs() { return intervalMs; }
        public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }

        public int getFanout() { return fanout; }
        public void setFanout(int fanout) { this.fanout = fanout; }

        public long getMessageTtlMs() { return messageTtlMs; }
        public void setMessageTtlMs(long messageTtlMs) { this.messageTtlMs = messageTtlMs; }

        public int getMaxHops() { return maxHops; }
        public void setMaxHops(int maxHops) { this.maxHops = maxHops; }

        public boolean isAdaptiveFanout() { return adaptiveFanout; }
        public void setAdaptiveFanout(boolean adaptiveFanout) { this.adaptiveFanout = adaptiveFanout; }

        public boolean isPriorityPropagation() { return priorityPropagation; }
        public void setPriorityPropagation(boolean priorityPropagation) { this.priorityPropagation = priorityPropagation; }

        public boolean isCompressionEnabled() { return compressionEnabled; }
        public void setCompressionEnabled(boolean compressionEnabled) { this.compressionEnabled = compressionEnabled; }

        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

        public long getAntiEntropyIntervalMs() { return antiEntropyIntervalMs; }
        public void setAntiEntropyIntervalMs(long antiEntropyIntervalMs) { this.antiEntropyIntervalMs = antiEntropyIntervalMs; }
    }
}
