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


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for TrackerConfiguration to verify the configuration management system.
 */
class TrackerConfigurationTest {

    private Properties originalSystemProperties;

    @BeforeEach
    void setUp() {
        // Save original system properties
        originalSystemProperties = new Properties();
        originalSystemProperties.putAll(System.getProperties());
    }

    @AfterEach
    void tearDown() {
        // Restore original system properties
        System.setProperties(originalSystemProperties);
    }

    @Test
    void testDefaultConfiguration() {
        TrackerConfiguration config = new TrackerConfiguration();
        
        assertEquals(6000, config.getTrackerPort());
        assertEquals(10, config.getThreadPoolSize());
        assertEquals(90000, config.getPeerTimeoutMs());
        assertFalse(config.isDistributedDiscoveryEnabled());
        assertEquals(6003, config.getGossipPort());
        assertEquals(5000, config.getGossipIntervalMs());
        assertEquals(3, config.getGossipFanout());
        assertEquals(30000, config.getMessageTtlMs());
        assertEquals("", config.getBootstrapPeers());
        assertTrue(config.isHealthCheckEnabled());
        assertEquals(30000, config.getHealthCheckIntervalMs());
        assertEquals("INFO", config.getLogLevel());
        assertTrue(config.isMetricsEnabled());
    }

    @Test
    void testConfigurationFromProperties() {
        Properties props = new Properties();
        props.setProperty("tracker.port", "7000");
        props.setProperty("tracker.threadpool.size", "20");
        props.setProperty("tracker.peer.timeout.ms", "120000");
        props.setProperty("discovery.distributed.enabled", "true");
        props.setProperty("discovery.gossip.port", "7003");
        props.setProperty("discovery.gossip.bootstrap.peers", "peer1:6003,peer2:6003");
        props.setProperty("tracker.log.level", "DEBUG");
        props.setProperty("tracker.metrics.enabled", "false");

        TrackerConfiguration config = new TrackerConfiguration(props);

        assertEquals(7000, config.getTrackerPort());
        assertEquals(20, config.getThreadPoolSize());
        assertEquals(120000, config.getPeerTimeoutMs());
        assertTrue(config.isDistributedDiscoveryEnabled());
        assertEquals(7003, config.getGossipPort());
        assertEquals("peer1:6003,peer2:6003", config.getBootstrapPeers());
        assertEquals("DEBUG", config.getLogLevel());
        assertFalse(config.isMetricsEnabled());
    }

    @Test
    void testBuilderPattern() {
        TrackerConfiguration config = TrackerConfiguration.builder()
                .trackerPort(8000)
                .threadPoolSize(15)
                .distributedDiscoveryEnabled(true)
                .gossipPort(8003)
                .bootstrapPeers("peer1:8003,peer2:8003")
                .logLevel("WARN")
                .metricsEnabled(false)
                .build();

        assertEquals(8000, config.getTrackerPort());
        assertEquals(15, config.getThreadPoolSize());
        assertTrue(config.isDistributedDiscoveryEnabled());
        assertEquals(8003, config.getGossipPort());
        assertEquals("peer1:8003,peer2:8003", config.getBootstrapPeers());
        assertEquals("WARN", config.getLogLevel());
        assertFalse(config.isMetricsEnabled());
    }

    @Test
    void testInvalidPropertyValues() {
        Properties props = new Properties();
        props.setProperty("tracker.port", "invalid");
        props.setProperty("tracker.threadpool.size", "not-a-number");
        props.setProperty("discovery.distributed.enabled", "maybe");

        TrackerConfiguration config = new TrackerConfiguration(props);

        // Should use default values for invalid properties
        assertEquals(6000, config.getTrackerPort());
        assertEquals(10, config.getThreadPoolSize());
        assertFalse(config.isDistributedDiscoveryEnabled()); // "maybe" -> false
    }

    @Test
    void testConfigurationToString() {
        TrackerConfiguration config = TrackerConfiguration.builder()
                .trackerPort(9000)
                .distributedDiscoveryEnabled(true)
                .build();

        String configString = config.toString();
        
        assertNotNull(configString);
        assertTrue(configString.contains("trackerPort=9000"));
        assertTrue(configString.contains("distributedDiscoveryEnabled=true"));
    }

    @Test
    void testBuilderFromProperties() {
        Properties props = new Properties();
        props.setProperty("tracker.port", "5000");
        props.setProperty("discovery.distributed.enabled", "true");

        TrackerConfiguration config = TrackerConfiguration.builder()
                .fromProperties(props)
                .threadPoolSize(25) // Override one property
                .build();

        assertEquals(5000, config.getTrackerPort());
        assertEquals(25, config.getThreadPoolSize());
        assertTrue(config.isDistributedDiscoveryEnabled());
    }

    @Test
    void testConfigurationLoader() {
        // Test that ConfigurationLoader can create a configuration
        TrackerConfiguration config = ConfigurationLoader.loadConfiguration();
        
        assertNotNull(config);
        // Should have default values since no config file or system properties are set
        assertEquals(6000, config.getTrackerPort());
    }

    @Test
    void testSystemPropertyOverride() {
        // Set system properties
        System.setProperty("tracker.port", "9999");
        System.setProperty("discovery.distributed.enabled", "true");

        TrackerConfiguration config = ConfigurationLoader.loadConfiguration();

        assertEquals(9999, config.getTrackerPort());
        assertTrue(config.isDistributedDiscoveryEnabled());
    }
}
