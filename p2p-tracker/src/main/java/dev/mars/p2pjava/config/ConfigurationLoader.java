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


import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Configuration loader that can load configuration from multiple sources
 * with a defined precedence order:
 * 1. System properties (highest priority)
 * 2. Environment variables
 * 3. Configuration file
 * 4. Default values (lowest priority)
 */
public class ConfigurationLoader {
    private static final Logger logger = Logger.getLogger(ConfigurationLoader.class.getName());
    
    private static final String DEFAULT_CONFIG_FILE = "tracker.properties";
    private static final String CONFIG_FILE_PROPERTY = "tracker.config.file";
    private static final String CONFIG_FILE_ENV = "TRACKER_CONFIG_FILE";
    
    /**
     * Loads TrackerConfiguration from all available sources.
     */
    public static TrackerConfiguration loadConfiguration() {
        Properties properties = new Properties();
        
        // 1. Load from configuration file (lowest priority)
        loadFromConfigFile(properties);
        
        // 2. Load from environment variables
        loadFromEnvironment(properties);
        
        // 3. Load from system properties (highest priority)
        loadFromSystemProperties(properties);
        
        return new TrackerConfiguration(properties);
    }
    
    /**
     * Loads TrackerConfiguration from a specific file.
     */
    public static TrackerConfiguration loadFromFile(String configFile) {
        Properties properties = new Properties();
        loadFromConfigFile(properties, configFile);
        
        // Still allow system properties and environment to override
        loadFromEnvironment(properties);
        loadFromSystemProperties(properties);
        
        return new TrackerConfiguration(properties);
    }
    
    /**
     * Loads configuration from a configuration file.
     */
    private static void loadFromConfigFile(Properties properties) {
        // Check for config file location in system property or environment
        String configFile = System.getProperty(CONFIG_FILE_PROPERTY);
        if (configFile == null) {
            configFile = System.getenv(CONFIG_FILE_ENV);
        }
        if (configFile == null) {
            configFile = DEFAULT_CONFIG_FILE;
        }
        
        loadFromConfigFile(properties, configFile);
    }
    
    /**
     * Loads configuration from a specific file.
     */
    private static void loadFromConfigFile(Properties properties, String configFile) {
        Path configPath = Paths.get(configFile);
        
        // Try to load from file system first
        if (Files.exists(configPath)) {
            try (InputStream input = Files.newInputStream(configPath)) {
                properties.load(input);
                logger.info("Loaded configuration from file: " + configPath.toAbsolutePath());
                return;
            } catch (IOException e) {
                logger.warning("Failed to load configuration from file: " + configPath + " - " + e.getMessage());
            }
        }
        
        // Try to load from classpath
        try (InputStream input = ConfigurationLoader.class.getClassLoader().getResourceAsStream(configFile)) {
            if (input != null) {
                properties.load(input);
                logger.info("Loaded configuration from classpath: " + configFile);
                return;
            }
        } catch (IOException e) {
            logger.warning("Failed to load configuration from classpath: " + configFile + " - " + e.getMessage());
        }
        
        logger.info("No configuration file found, using defaults and system properties");
    }
    
    /**
     * Loads configuration from environment variables.
     * Environment variables are mapped from property names by:
     * - Converting to uppercase
     * - Replacing dots with underscores
     * - Adding TRACKER_ prefix
     */
    private static void loadFromEnvironment(Properties properties) {
        // Define mappings from property names to environment variable names
        String[][] envMappings = {
            {"tracker.port", "TRACKER_PORT"},
            {"tracker.threadpool.size", "TRACKER_THREADPOOL_SIZE"},
            {"tracker.peer.timeout.ms", "TRACKER_PEER_TIMEOUT_MS"},
            {"discovery.distributed.enabled", "DISCOVERY_DISTRIBUTED_ENABLED"},
            {"discovery.gossip.port", "DISCOVERY_GOSSIP_PORT"},
            {"discovery.gossip.interval.ms", "DISCOVERY_GOSSIP_INTERVAL_MS"},
            {"discovery.gossip.fanout", "DISCOVERY_GOSSIP_FANOUT"},
            {"discovery.gossip.message.ttl.ms", "DISCOVERY_GOSSIP_MESSAGE_TTL_MS"},
            {"discovery.gossip.bootstrap.peers", "DISCOVERY_GOSSIP_BOOTSTRAP_PEERS"},
            {"tracker.health.enabled", "TRACKER_HEALTH_ENABLED"},
            {"tracker.health.interval.ms", "TRACKER_HEALTH_INTERVAL_MS"},
            {"tracker.log.level", "TRACKER_LOG_LEVEL"},
            {"tracker.metrics.enabled", "TRACKER_METRICS_ENABLED"}
        };
        
        int envCount = 0;
        for (String[] mapping : envMappings) {
            String propertyName = mapping[0];
            String envName = mapping[1];
            String envValue = System.getenv(envName);
            
            if (envValue != null && !envValue.trim().isEmpty()) {
                properties.setProperty(propertyName, envValue.trim());
                envCount++;
            }
        }
        
        if (envCount > 0) {
            logger.info("Loaded " + envCount + " configuration values from environment variables");
        }
    }
    
    /**
     * Loads configuration from system properties.
     */
    private static void loadFromSystemProperties(Properties properties) {
        // Define the property names we're interested in
        String[] propertyNames = {
            "tracker.port",
            "tracker.threadpool.size",
            "tracker.peer.timeout.ms",
            "discovery.distributed.enabled",
            "discovery.gossip.port",
            "discovery.gossip.interval.ms",
            "discovery.gossip.fanout",
            "discovery.gossip.message.ttl.ms",
            "discovery.gossip.bootstrap.peers",
            "tracker.health.enabled",
            "tracker.health.interval.ms",
            "tracker.log.level",
            "tracker.metrics.enabled"
        };
        
        int propCount = 0;
        for (String propertyName : propertyNames) {
            String value = System.getProperty(propertyName);
            if (value != null && !value.trim().isEmpty()) {
                properties.setProperty(propertyName, value.trim());
                propCount++;
            }
        }
        
        if (propCount > 0) {
            logger.info("Loaded " + propCount + " configuration values from system properties");
        }
    }
    
    /**
     * Saves configuration to a file.
     */
    public static void saveConfiguration(TrackerConfiguration config, String filename) throws IOException {
        Properties properties = new Properties();
        
        // Convert configuration back to properties
        properties.setProperty("tracker.port", String.valueOf(config.getTrackerPort()));
        properties.setProperty("tracker.threadpool.size", String.valueOf(config.getThreadPoolSize()));
        properties.setProperty("tracker.peer.timeout.ms", String.valueOf(config.getPeerTimeoutMs()));
        properties.setProperty("discovery.distributed.enabled", String.valueOf(config.isDistributedDiscoveryEnabled()));
        properties.setProperty("discovery.gossip.port", String.valueOf(config.getGossipPort()));
        properties.setProperty("discovery.gossip.interval.ms", String.valueOf(config.getGossipIntervalMs()));
        properties.setProperty("discovery.gossip.fanout", String.valueOf(config.getGossipFanout()));
        properties.setProperty("discovery.gossip.message.ttl.ms", String.valueOf(config.getMessageTtlMs()));
        properties.setProperty("discovery.gossip.bootstrap.peers", config.getBootstrapPeers());
        properties.setProperty("tracker.health.enabled", String.valueOf(config.isHealthCheckEnabled()));
        properties.setProperty("tracker.health.interval.ms", String.valueOf(config.getHealthCheckIntervalMs()));
        properties.setProperty("tracker.log.level", config.getLogLevel());
        properties.setProperty("tracker.metrics.enabled", String.valueOf(config.isMetricsEnabled()));
        
        try (FileOutputStream output = new FileOutputStream(filename)) {
            properties.store(output, "Tracker Configuration - Generated on " + new java.util.Date());
            logger.info("Configuration saved to: " + filename);
        }
    }
    
    /**
     * Creates a sample configuration file with default values and comments.
     */
    public static void createSampleConfigFile(String filename) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("# Tracker Configuration File");
            writer.println("# This file contains configuration settings for the P2P Tracker service");
            writer.println("# Values can be overridden by environment variables or system properties");
            writer.println();
            
            writer.println("# Core Tracker Settings");
            writer.println("tracker.port=6000");
            writer.println("tracker.threadpool.size=10");
            writer.println("tracker.peer.timeout.ms=90000");
            writer.println();
            
            writer.println("# Service Discovery Settings");
            writer.println("discovery.distributed.enabled=false");
            writer.println("discovery.gossip.port=6003");
            writer.println("discovery.gossip.interval.ms=5000");
            writer.println("discovery.gossip.fanout=3");
            writer.println("discovery.gossip.message.ttl.ms=30000");
            writer.println("discovery.gossip.bootstrap.peers=");
            writer.println();
            
            writer.println("# Health Check Settings");
            writer.println("tracker.health.enabled=true");
            writer.println("tracker.health.interval.ms=30000");
            writer.println();
            
            writer.println("# Logging and Monitoring Settings");
            writer.println("tracker.log.level=INFO");
            writer.println("tracker.metrics.enabled=true");
            
            logger.info("Sample configuration file created: " + filename);
        }
    }
}
