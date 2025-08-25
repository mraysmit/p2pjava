package dev.mars.p2pjava.config;

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


import java.util.Properties;
import java.util.logging.Logger;

/**
 * Configuration class for TrackerService that replaces system properties
 * and provides proper dependency injection support.
 */
public class TrackerConfiguration {
    private static final Logger logger = Logger.getLogger(TrackerConfiguration.class.getName());
    
    // Default values
    private static final int DEFAULT_TRACKER_PORT = 6000;
    private static final int DEFAULT_THREAD_POOL_SIZE = 10;
    private static final long DEFAULT_PEER_TIMEOUT_MS = 90000; // 90 seconds
    private static final boolean DEFAULT_DISTRIBUTED_DISCOVERY = false;
    private static final int DEFAULT_GOSSIP_PORT = 6003;
    private static final long DEFAULT_GOSSIP_INTERVAL_MS = 5000;
    private static final int DEFAULT_GOSSIP_FANOUT = 3;
    private static final long DEFAULT_MESSAGE_TTL_MS = 30000;
    
    // Core tracker settings
    private final int trackerPort;
    private final int threadPoolSize;
    private final long peerTimeoutMs;
    
    // Service discovery settings
    private final boolean distributedDiscoveryEnabled;
    private final int gossipPort;
    private final long gossipIntervalMs;
    private final int gossipFanout;
    private final long messageTtlMs;
    private final String bootstrapPeers;
    
    // Health check settings
    private final boolean healthCheckEnabled;
    private final long healthCheckIntervalMs;
    
    // Logging settings
    private final String logLevel;
    private final boolean metricsEnabled;
    
    /**
     * Creates a TrackerConfiguration with default values.
     */
    public TrackerConfiguration() {
        this(new Properties());
    }
    
    /**
     * Creates a TrackerConfiguration from properties.
     *
     * @param properties Configuration properties
     */
    public TrackerConfiguration(Properties properties) {
        // Core tracker settings
        this.trackerPort = getIntProperty(properties, "tracker.port", DEFAULT_TRACKER_PORT);
        this.threadPoolSize = getIntProperty(properties, "tracker.threadpool.size", DEFAULT_THREAD_POOL_SIZE);
        this.peerTimeoutMs = getLongProperty(properties, "tracker.peer.timeout.ms", DEFAULT_PEER_TIMEOUT_MS);
        
        // Service discovery settings
        this.distributedDiscoveryEnabled = getBooleanProperty(properties, "discovery.distributed.enabled", DEFAULT_DISTRIBUTED_DISCOVERY);
        this.gossipPort = getIntProperty(properties, "discovery.gossip.port", DEFAULT_GOSSIP_PORT);
        this.gossipIntervalMs = getLongProperty(properties, "discovery.gossip.interval.ms", DEFAULT_GOSSIP_INTERVAL_MS);
        this.gossipFanout = getIntProperty(properties, "discovery.gossip.fanout", DEFAULT_GOSSIP_FANOUT);
        this.messageTtlMs = getLongProperty(properties, "discovery.gossip.message.ttl.ms", DEFAULT_MESSAGE_TTL_MS);
        this.bootstrapPeers = properties.getProperty("discovery.gossip.bootstrap.peers", "");
        
        // Health check settings
        this.healthCheckEnabled = getBooleanProperty(properties, "tracker.health.enabled", true);
        this.healthCheckIntervalMs = getLongProperty(properties, "tracker.health.interval.ms", 30000);
        
        // Logging settings
        this.logLevel = properties.getProperty("tracker.log.level", "INFO");
        this.metricsEnabled = getBooleanProperty(properties, "tracker.metrics.enabled", true);
        
        logger.info("TrackerConfiguration initialized with port: " + trackerPort + 
                   ", distributed discovery: " + distributedDiscoveryEnabled);
    }
    
    /**
     * Creates a builder for TrackerConfiguration.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public int getTrackerPort() { return trackerPort; }
    public int getThreadPoolSize() { return threadPoolSize; }
    public long getPeerTimeoutMs() { return peerTimeoutMs; }
    public boolean isDistributedDiscoveryEnabled() { return distributedDiscoveryEnabled; }
    public int getGossipPort() { return gossipPort; }
    public long getGossipIntervalMs() { return gossipIntervalMs; }
    public int getGossipFanout() { return gossipFanout; }
    public long getMessageTtlMs() { return messageTtlMs; }
    public String getBootstrapPeers() { return bootstrapPeers; }
    public boolean isHealthCheckEnabled() { return healthCheckEnabled; }
    public long getHealthCheckIntervalMs() { return healthCheckIntervalMs; }
    public String getLogLevel() { return logLevel; }
    public boolean isMetricsEnabled() { return metricsEnabled; }
    
    // Helper methods for property parsing
    private int getIntProperty(Properties props, String key, int defaultValue) {
        String value = props.getProperty(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                logger.warning("Invalid integer value for " + key + ": " + value + ", using default: " + defaultValue);
            }
        }
        return defaultValue;
    }
    
    private long getLongProperty(Properties props, String key, long defaultValue) {
        String value = props.getProperty(key);
        if (value != null) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                logger.warning("Invalid long value for " + key + ": " + value + ", using default: " + defaultValue);
            }
        }
        return defaultValue;
    }
    
    private boolean getBooleanProperty(Properties props, String key, boolean defaultValue) {
        String value = props.getProperty(key);
        if (value != null) {
            return Boolean.parseBoolean(value);
        }
        return defaultValue;
    }
    
    /**
     * Builder pattern for TrackerConfiguration.
     */
    public static class Builder {
        private final Properties properties = new Properties();
        
        public Builder trackerPort(int port) {
            properties.setProperty("tracker.port", String.valueOf(port));
            return this;
        }
        
        public Builder threadPoolSize(int size) {
            properties.setProperty("tracker.threadpool.size", String.valueOf(size));
            return this;
        }
        
        public Builder peerTimeoutMs(long timeoutMs) {
            properties.setProperty("tracker.peer.timeout.ms", String.valueOf(timeoutMs));
            return this;
        }
        
        public Builder distributedDiscoveryEnabled(boolean enabled) {
            properties.setProperty("discovery.distributed.enabled", String.valueOf(enabled));
            return this;
        }
        
        public Builder gossipPort(int port) {
            properties.setProperty("discovery.gossip.port", String.valueOf(port));
            return this;
        }
        
        public Builder gossipIntervalMs(long intervalMs) {
            properties.setProperty("discovery.gossip.interval.ms", String.valueOf(intervalMs));
            return this;
        }
        
        public Builder gossipFanout(int fanout) {
            properties.setProperty("discovery.gossip.fanout", String.valueOf(fanout));
            return this;
        }
        
        public Builder messageTtlMs(long ttlMs) {
            properties.setProperty("discovery.gossip.message.ttl.ms", String.valueOf(ttlMs));
            return this;
        }
        
        public Builder bootstrapPeers(String peers) {
            properties.setProperty("discovery.gossip.bootstrap.peers", peers);
            return this;
        }
        
        public Builder healthCheckEnabled(boolean enabled) {
            properties.setProperty("tracker.health.enabled", String.valueOf(enabled));
            return this;
        }
        
        public Builder healthCheckIntervalMs(long intervalMs) {
            properties.setProperty("tracker.health.interval.ms", String.valueOf(intervalMs));
            return this;
        }
        
        public Builder logLevel(String level) {
            properties.setProperty("tracker.log.level", level);
            return this;
        }
        
        public Builder metricsEnabled(boolean enabled) {
            properties.setProperty("tracker.metrics.enabled", String.valueOf(enabled));
            return this;
        }
        
        public Builder fromProperties(Properties props) {
            properties.putAll(props);
            return this;
        }
        
        public TrackerConfiguration build() {
            return new TrackerConfiguration(properties);
        }
    }
    
    @Override
    public String toString() {
        return "TrackerConfiguration{" +
                "trackerPort=" + trackerPort +
                ", threadPoolSize=" + threadPoolSize +
                ", peerTimeoutMs=" + peerTimeoutMs +
                ", distributedDiscoveryEnabled=" + distributedDiscoveryEnabled +
                ", gossipPort=" + gossipPort +
                ", gossipIntervalMs=" + gossipIntervalMs +
                ", gossipFanout=" + gossipFanout +
                ", messageTtlMs=" + messageTtlMs +
                ", bootstrapPeers='" + bootstrapPeers + '\'' +
                ", healthCheckEnabled=" + healthCheckEnabled +
                ", healthCheckIntervalMs=" + healthCheckIntervalMs +
                ", logLevel='" + logLevel + '\'' +
                ", metricsEnabled=" + metricsEnabled +
                '}';
    }
}
