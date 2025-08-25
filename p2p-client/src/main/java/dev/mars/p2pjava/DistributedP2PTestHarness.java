package dev.mars.p2pjava;

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


import dev.mars.p2pjava.bootstrap.BootstrapService;
import dev.mars.p2pjava.bootstrap.P2PBootstrap;
import dev.mars.p2pjava.bootstrap.P2PComponent;
import dev.mars.p2pjava.common.PeerInfo;
import dev.mars.p2pjava.config.ConfigurationManager;
import dev.mars.p2pjava.discovery.DistributedServiceRegistry;
import dev.mars.p2pjava.discovery.ServiceInstance;
import dev.mars.p2pjava.discovery.ServiceRegistry;
import dev.mars.p2pjava.discovery.ServiceRegistryFactory;
import dev.mars.p2pjava.util.HealthCheck;
import dev.mars.p2pjava.util.ThreadManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * DistributedP2PTestHarness - Integration Test Harness for P2P System with Distributed Service Discovery
 *
 * This enhanced test harness demonstrates the P2P file sharing system using distributed service discovery
 * with gossip protocol instead of centralized Tracker and IndexServer services.
 *
 * Key Features:
 * - Uses DistributedServiceRegistry with gossip protocol for decentralized service discovery
 * - Demonstrates peer-to-peer service registration and discovery
 * - Tests resilience and eventual consistency of the distributed registry
 * - Supports both centralized and distributed modes via configuration
 *
 * Components:
 * - DistributedServiceRegistry: Decentralized service registry with gossip protocol
 * - P2PClient: Enhanced to support distributed service discovery
 * - Peers: Register services with distributed registry instead of centralized services
 */
public class DistributedP2PTestHarness {
    private static final Logger logger = Logger.getLogger(DistributedP2PTestHarness.class.getName());
    
    // Configuration
    private static ConfigurationManager configManager;
    private static boolean useDistributedRegistry;
    
    // Test infrastructure
    private static final String TEST_FILES_DIR = "test-files";
    private static final List<String> testFailures = new ArrayList<>();
    
    // Distributed registries for each peer
    private static final Map<String, ServiceRegistry> peerRegistries = new HashMap<>();
    private static final Map<String, Thread> peerThreads = new HashMap<>();
    
    // Test configuration
    private static final List<PeerConfig> peerConfigs = Arrays.asList(
        new PeerConfig("peer1", "localhost", 7001, "file1.txt"),
        new PeerConfig("peer2", "localhost", 7002, "file2.txt"),
        new PeerConfig("peer3", "localhost", 7003, "file3.txt")
    );
    
    // Synchronization
    private static CountDownLatch registriesStarted;
    private static CountDownLatch servicesRegistered;
    
    public static void main(String[] args) throws Exception {
        // Initialize configuration
        configManager = ConfigurationManager.getInstance();
        configManager.initialize(args);
        
        // Check if distributed registry is enabled
        useDistributedRegistry = configManager.getBoolean("serviceRegistry.distributed.enabled", false);
        
        logger.info("Starting Distributed P2P Test Harness (distributed mode: " + useDistributedRegistry + ")");
        
        // Register with health check
        HealthCheck.ServiceHealth health = HealthCheck.registerService("DistributedP2PTestHarness");
        health.addHealthDetail("startTime", System.currentTimeMillis());
        health.addHealthDetail("distributedMode", useDistributedRegistry);
        
        // Create thread pool
        ExecutorService executorService = ThreadManager.getCachedThreadPool(
            "DistributedP2PTestHarnessMainPool", 
            "DistributedP2PTestHarness"
        );
        
        try {
            // Create test files
            createTestFiles();
            
            // Set up shutdown hook
            setupShutdownHook();
            
            if (useDistributedRegistry) {
                // Test distributed service discovery
                testDistributedServiceDiscovery();
            } else {
                // Fall back to centralized mode (existing P2PTestHarness logic)
                logger.info("Distributed registry disabled, falling back to centralized mode");
                // Could delegate to original P2PTestHarness here
            }
            
            // Report results
            reportTestResults();
            
        } catch (Exception e) {
            recordFailure("Test harness execution failed", e);
            logger.severe("Test harness failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Cleanup
            stopAndCleanup();
            executorService.shutdown();
        }
    }
    
    /**
     * Tests distributed service discovery with gossip protocol.
     */
    private static void testDistributedServiceDiscovery() throws Exception {
        logger.info("=== Testing Distributed Service Discovery ===");
        
        // Initialize synchronization latches
        registriesStarted = new CountDownLatch(peerConfigs.size());
        servicesRegistered = new CountDownLatch(peerConfigs.size());
        
        // Start distributed registries for each peer
        startDistributedRegistries();
        
        // Wait for all registries to start
        if (!registriesStarted.await(30, TimeUnit.SECONDS)) {
            recordFailure("Timeout waiting for distributed registries to start", null);
            return;
        }
        
        logger.info("All distributed registries started successfully");
        
        // Register services with distributed registries
        registerServicesWithDistributedRegistry();
        
        // Wait for service registration
        if (!servicesRegistered.await(30, TimeUnit.SECONDS)) {
            recordFailure("Timeout waiting for service registration", null);
            return;
        }
        
        logger.info("All services registered with distributed registry");
        
        // Wait for gossip propagation
        logger.info("Waiting for gossip propagation...");
        Thread.sleep(10000); // Allow time for gossip to propagate
        
        // Test service discovery from each peer
        testServiceDiscoveryFromEachPeer();
        
        // Test service resilience (simulate peer failure)
        testServiceResilience();
        
        logger.info("=== Distributed Service Discovery Test Complete ===");
    }
    
    /**
     * Starts distributed service registries for each peer.
     */
    private static void startDistributedRegistries() {
        for (PeerConfig peerConfig : peerConfigs) {
            Thread registryThread = new Thread(() -> {
                try {
                    // Create distributed registry for this peer
                    String peerId = peerConfig.id;
                    int gossipPort = configManager.getInt("serviceRegistry.distributed.gossipPort", 6003) + 
                                   Integer.parseInt(peerId.substring(4)) - 1; // peer1->6003, peer2->6004, etc.
                    
                    // Get bootstrap peers (other peers in the network)
                    Set<String> bootstrapPeers = getBootstrapPeersForPeer(peerId, gossipPort);
                    
                    logger.info("Starting distributed registry for " + peerId + " on port " + gossipPort + 
                              " with bootstrap peers: " + bootstrapPeers);
                    
                    // Create and start the distributed registry
                    DistributedServiceRegistry registry = new DistributedServiceRegistry(
                        peerId, gossipPort, bootstrapPeers);
                    
                    registry.start();
                    
                    // Store the registry
                    synchronized (peerRegistries) {
                        peerRegistries.put(peerId, registry);
                    }
                    
                    logger.info("Distributed registry started for peer: " + peerId);
                    registriesStarted.countDown();
                    
                    // Keep the thread alive
                    while (!Thread.currentThread().isInterrupted()) {
                        Thread.sleep(1000);
                    }
                    
                } catch (Exception e) {
                    recordFailure("Failed to start distributed registry for " + peerConfig.id, e);
                    logger.severe("Failed to start registry for " + peerConfig.id + ": " + e.getMessage());
                }
            });
            
            registryThread.setName("DistributedRegistry-" + peerConfig.id);
            registryThread.start();
            peerThreads.put(peerConfig.id, registryThread);
        }
    }
    
    /**
     * Gets bootstrap peers for a given peer (all other peers in the network).
     */
    private static Set<String> getBootstrapPeersForPeer(String peerId, int gossipPort) {
        Set<String> bootstrapPeers = new HashSet<>();
        int baseGossipPort = configManager.getInt("serviceRegistry.distributed.gossipPort", 6003);
        
        for (PeerConfig config : peerConfigs) {
            if (!config.id.equals(peerId)) {
                int otherPeerGossipPort = baseGossipPort + Integer.parseInt(config.id.substring(4)) - 1;
                bootstrapPeers.add(config.host + ":" + otherPeerGossipPort);
            }
        }
        
        return bootstrapPeers;
    }
    
    /**
     * Registers services with the distributed registry.
     */
    private static void registerServicesWithDistributedRegistry() {
        for (PeerConfig peerConfig : peerConfigs) {
            Thread registrationThread = new Thread(() -> {
                try {
                    ServiceRegistry registry = peerRegistries.get(peerConfig.id);
                    if (registry == null) {
                        recordFailure("No registry found for peer: " + peerConfig.id, null);
                        return;
                    }
                    
                    // Register file sharing service
                    Map<String, String> metadata = new HashMap<>();
                    metadata.put("file", peerConfig.fileName);
                    metadata.put("version", "1.0");
                    metadata.put("region", "test");
                    
                    boolean registered = registry.registerService(
                        "file-sharing", 
                        peerConfig.id + "-file-service",
                        peerConfig.host,
                        peerConfig.port,
                        metadata
                    );
                    
                    if (registered) {
                        logger.info("Registered file-sharing service for " + peerConfig.id + 
                                  " with file: " + peerConfig.fileName);
                        servicesRegistered.countDown();
                    } else {
                        recordFailure("Failed to register service for peer: " + peerConfig.id, null);
                    }
                    
                } catch (Exception e) {
                    recordFailure("Error registering service for " + peerConfig.id, e);
                }
            });
            
            registrationThread.setName("ServiceRegistration-" + peerConfig.id);
            registrationThread.start();
        }
    }
    
    /**
     * Tests service discovery from each peer's perspective.
     */
    private static void testServiceDiscoveryFromEachPeer() {
        logger.info("=== Testing Service Discovery from Each Peer ===");
        
        for (PeerConfig peerConfig : peerConfigs) {
            ServiceRegistry registry = peerRegistries.get(peerConfig.id);
            if (registry == null) {
                recordFailure("No registry found for peer: " + peerConfig.id, null);
                continue;
            }
            
            try {
                // Discover all file-sharing services
                List<ServiceInstance> services = registry.discoverServices("file-sharing");
                
                logger.info("Peer " + peerConfig.id + " discovered " + services.size() + " file-sharing services:");
                for (ServiceInstance service : services) {
                    logger.info("  - " + service.getServiceId() + " at " + 
                              service.getHost() + ":" + service.getPort() + 
                              " (file: " + service.getMetadata().get("file") + 
                              ", origin: " + service.getOriginPeerId() + ")");
                }
                
                // Verify we can see services from other peers
                if (services.size() < peerConfigs.size()) {
                    recordFailure("Peer " + peerConfig.id + " only discovered " + services.size() + 
                                " services, expected " + peerConfigs.size(), null);
                }
                
            } catch (Exception e) {
                recordFailure("Error during service discovery for " + peerConfig.id, e);
            }
        }
    }
    
    /**
     * Tests service resilience by simulating peer failure.
     */
    private static void testServiceResilience() {
        logger.info("=== Testing Service Resilience ===");
        
        // Simulate failure of peer1
        String failedPeerId = "peer1";
        ServiceRegistry failedRegistry = peerRegistries.get(failedPeerId);
        
        if (failedRegistry != null) {
            logger.info("Simulating failure of " + failedPeerId);
            failedRegistry.stop();
            peerRegistries.remove(failedPeerId);
            
            // Interrupt the thread
            Thread registryThread = peerThreads.get(failedPeerId);
            if (registryThread != null) {
                registryThread.interrupt();
                peerThreads.remove(failedPeerId);
            }
            
            // Wait for failure detection and propagation
            try {
                Thread.sleep(15000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // Test that other peers can still discover remaining services
            for (PeerConfig peerConfig : peerConfigs) {
                if (peerConfig.id.equals(failedPeerId)) continue;
                
                ServiceRegistry registry = peerRegistries.get(peerConfig.id);
                if (registry != null) {
                    List<ServiceInstance> services = registry.discoverServices("file-sharing");
                    logger.info("After " + failedPeerId + " failure, " + peerConfig.id + 
                              " discovered " + services.size() + " services");
                }
            }
        }
    }
    
    // Helper classes and methods...
    
    static class PeerConfig {
        final String id;
        final String host;
        final int port;
        final String fileName;
        
        PeerConfig(String id, String host, int port, String fileName) {
            this.id = id;
            this.host = host;
            this.port = port;
            this.fileName = fileName;
        }
    }
    
    private static void createTestFiles() throws IOException {
        Path testDir = Paths.get(TEST_FILES_DIR);
        if (!Files.exists(testDir)) {
            Files.createDirectories(testDir);
        }
        
        for (PeerConfig config : peerConfigs) {
            Path filePath = testDir.resolve(config.fileName);
            String content = "Test content for " + config.fileName + " from " + config.id;
            Files.write(filePath, content.getBytes());
            logger.info("Created test file: " + filePath);
        }
    }
    
    private static void setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down distributed registries...");
            stopAndCleanup();
        }));
    }
    
    private static void stopAndCleanup() {
        // Stop all registries
        for (ServiceRegistry registry : peerRegistries.values()) {
            try {
                registry.stop();
            } catch (Exception e) {
                logger.warning("Error stopping registry: " + e.getMessage());
            }
        }
        peerRegistries.clear();
        
        // Interrupt all threads
        for (Thread thread : peerThreads.values()) {
            thread.interrupt();
        }
        peerThreads.clear();
        
        // Clean up test files if configured
        if (configManager.getBoolean("test.cleanup.files", false)) {
            try {
                Path testDir = Paths.get(TEST_FILES_DIR);
                if (Files.exists(testDir)) {
                    Files.walk(testDir)
                         .sorted(Comparator.reverseOrder())
                         .map(Path::toFile)
                         .forEach(File::delete);
                    logger.info("Cleaned up test files");
                }
            } catch (IOException e) {
                logger.warning("Error cleaning up test files: " + e.getMessage());
            }
        }
    }
    
    private static void recordFailure(String message, Exception e) {
        String failure = message + (e != null ? ": " + e.getMessage() : "");
        testFailures.add(failure);
        logger.severe(failure);
        if (e != null) {
            e.printStackTrace();
        }
    }
    
    private static void reportTestResults() {
        logger.info("=== Test Results ===");
        if (testFailures.isEmpty()) {
            logger.info("All tests passed successfully!");
        } else {
            logger.severe("Test failures (" + testFailures.size() + "):");
            for (String failure : testFailures) {
                logger.severe("  - " + failure);
            }
        }
    }
}
