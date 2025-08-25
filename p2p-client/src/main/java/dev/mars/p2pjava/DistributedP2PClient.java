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


import dev.mars.p2pjava.common.PeerInfo;
import dev.mars.p2pjava.config.ConfigurationManager;
import dev.mars.p2pjava.discovery.DistributedServiceRegistry;
import dev.mars.p2pjava.discovery.ServiceInstance;
import dev.mars.p2pjava.discovery.ServiceRegistry;
import dev.mars.p2pjava.discovery.ServiceRegistryFactory;
import dev.mars.p2pjava.util.HealthCheck;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;
import java.util.logging.Logger;

/**
 * DistributedP2PClient - Enhanced P2P Client with Distributed Service Discovery
 *
 * This enhanced P2P client supports both centralized and distributed service discovery modes.
 * When distributed mode is enabled, it uses the DistributedServiceRegistry with gossip protocol
 * for decentralized peer and service discovery.
 *
 * Features:
 * - Supports both centralized (IndexServer/Tracker) and distributed service discovery
 * - Uses gossip protocol for resilient service propagation
 * - Automatic fallback between discovery modes
 * - Enhanced peer discovery with metadata support
 * - Configurable discovery strategies
 */
public class DistributedP2PClient {
    private static final Logger logger = Logger.getLogger(DistributedP2PClient.class.getName());
    
    private final ConfigurationManager config;
    private final HealthCheck.ServiceHealth health;
    
    // Service discovery components
    private ServiceRegistry serviceRegistry;
    private final boolean useDistributedDiscovery;
    private final String peerId;
    
    // Centralized discovery fallback
    private final P2PClient centralizedClient;
    
    // Test results tracking
    private static final List<String> testFailures = new ArrayList<>();
    
    /**
     * Creates a new DistributedP2PClient with default configuration.
     */
    public DistributedP2PClient() {
        this(ConfigurationManager.getInstance());
    }
    
    /**
     * Creates a new DistributedP2PClient with the specified configuration.
     * 
     * @param config The configuration manager
     */
    public DistributedP2PClient(ConfigurationManager config) {
        this.config = config;
        this.peerId = config.get("peer.id", "client-" + System.currentTimeMillis());
        this.useDistributedDiscovery = config.getBoolean("serviceRegistry.distributed.enabled", false);
        
        // Initialize centralized client as fallback
        this.centralizedClient = new P2PClient(config);
        
        // Register with health check
        this.health = HealthCheck.registerService("DistributedP2PClient");
        this.health.addHealthDetail("startTime", System.currentTimeMillis());
        this.health.addHealthDetail("peerId", peerId);
        this.health.addHealthDetail("distributedMode", useDistributedDiscovery);
        
        // Initialize service registry
        initializeServiceRegistry();
    }
    
    /**
     * Initializes the appropriate service registry based on configuration.
     */
    private void initializeServiceRegistry() {
        try {
            if (useDistributedDiscovery) {
                logger.info("Initializing distributed service registry for peer: " + peerId);
                
                // Get configuration for distributed registry
                int gossipPort = config.getInt("serviceRegistry.distributed.gossipPort", 6003);
                Set<String> bootstrapPeers = getBootstrapPeers();
                
                // Create distributed registry
                this.serviceRegistry = new DistributedServiceRegistry(peerId, gossipPort, bootstrapPeers);
                this.serviceRegistry.start();
                
                logger.info("Distributed service registry started on port " + gossipPort + 
                          " with bootstrap peers: " + bootstrapPeers);
                
                health.addHealthDetail("registryType", "distributed");
                health.addHealthDetail("gossipPort", gossipPort);
                
            } else {
                logger.info("Using in-memory service registry (centralized mode)");
                this.serviceRegistry = ServiceRegistryFactory.getInstance().getRegistry("memory");
                health.addHealthDetail("registryType", "memory");
            }
            
        } catch (Exception e) {
            logger.severe("Failed to initialize service registry: " + e.getMessage());
            recordFailure("Service registry initialization failed", e);
            
            // Fallback to in-memory registry
            try {
                this.serviceRegistry = ServiceRegistryFactory.getInstance().getRegistry("memory");
                health.addHealthDetail("registryType", "memory-fallback");
            } catch (Exception fallbackError) {
                logger.severe("Fallback registry initialization also failed: " + fallbackError.getMessage());
                throw new RuntimeException("Cannot initialize any service registry", fallbackError);
            }
        }
    }
    
    /**
     * Gets bootstrap peers from configuration.
     */
    private Set<String> getBootstrapPeers() {
        Set<String> bootstrapPeers = new HashSet<>();
        
        // Get from configuration
        String bootstrapPeersConfig = config.get("serviceRegistry.distributed.bootstrapPeers", "");
        if (!bootstrapPeersConfig.isEmpty()) {
            String[] peers = bootstrapPeersConfig.split(",");
            for (String peer : peers) {
                peer = peer.trim();
                if (!peer.isEmpty()) {
                    bootstrapPeers.add(peer);
                }
            }
        }
        
        // If no bootstrap peers configured, try to discover from peer discovery
        if (bootstrapPeers.isEmpty()) {
            String[] defaultPeers = {
                "localhost:6003",
                "localhost:6004", 
                "localhost:6005"
            };
            bootstrapPeers.addAll(Arrays.asList(defaultPeers));
        }
        
        return bootstrapPeers;
    }
    
    /**
     * Discovers peers that have the specified file using distributed service discovery.
     * 
     * @param fileName The name of the file to search for
     * @return A list of peers that have the file
     */
    public List<PeerInfo> discoverPeersWithFile(String fileName) {
        List<PeerInfo> result = new ArrayList<>();
        
        health.addHealthDetail("lastOperation", "discoverPeersWithFile");
        health.addHealthDetail("fileName", fileName);
        
        try {
            if (useDistributedDiscovery && serviceRegistry != null) {
                // Use distributed service discovery
                result = discoverPeersWithFileDistributed(fileName);
            } else {
                // Fallback to centralized discovery
                logger.info("Using centralized discovery for file: " + fileName);
                result = centralizedClient.discoverPeersWithFile(fileName);
            }
            
            health.addHealthDetail("peersFound", result.size());
            logger.info("Discovered " + result.size() + " peers with file: " + fileName);
            
        } catch (Exception e) {
            recordFailure("Error discovering peers with file: " + fileName, e);
            health.addHealthDetail("error", e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Discovers peers with a file using the distributed service registry.
     */
    private List<PeerInfo> discoverPeersWithFileDistributed(String fileName) {
        List<PeerInfo> result = new ArrayList<>();
        
        try {
            // Discover all file-sharing services
            List<ServiceInstance> services = serviceRegistry.discoverServices("file-sharing");
            
            logger.info("Found " + services.size() + " file-sharing services");
            
            // Filter services that have the requested file
            for (ServiceInstance service : services) {
                Map<String, String> metadata = service.getMetadata();
                if (metadata != null && fileName.equals(metadata.get("file"))) {
                    PeerInfo peerInfo = new PeerInfo(
                        service.getOriginPeerId(),
                        service.getHost(),
                        service.getPort()
                    );
                    result.add(peerInfo);
                    
                    logger.info("Found peer with file " + fileName + ": " + 
                              service.getOriginPeerId() + " at " + 
                              service.getHost() + ":" + service.getPort());
                }
            }
            
        } catch (Exception e) {
            logger.severe("Error in distributed peer discovery: " + e.getMessage());
            throw e;
        }
        
        return result;
    }
    
    /**
     * Registers a file-sharing service with the distributed registry.
     * 
     * @param serviceId Unique service identifier
     * @param host Service host
     * @param port Service port
     * @param fileName File being shared
     * @param additionalMetadata Additional service metadata
     * @return true if registration was successful
     */
    public boolean registerFileService(String serviceId, String host, int port, 
                                     String fileName, Map<String, String> additionalMetadata) {
        if (serviceRegistry == null) {
            logger.warning("Cannot register service: no service registry available");
            return false;
        }
        
        try {
            // Prepare metadata
            Map<String, String> metadata = new HashMap<>();
            metadata.put("file", fileName);
            metadata.put("registeredAt", String.valueOf(System.currentTimeMillis()));
            metadata.put("clientVersion", "1.0");
            
            if (additionalMetadata != null) {
                metadata.putAll(additionalMetadata);
            }
            
            // Register the service
            boolean registered = serviceRegistry.registerService(
                "file-sharing", serviceId, host, port, metadata);
            
            if (registered) {
                logger.info("Registered file-sharing service: " + serviceId + 
                          " for file: " + fileName);
                health.addHealthDetail("lastRegistration", serviceId);
            } else {
                logger.warning("Failed to register file-sharing service: " + serviceId);
            }
            
            return registered;
            
        } catch (Exception e) {
            recordFailure("Error registering file service: " + serviceId, e);
            return false;
        }
    }
    
    /**
     * Deregisters a file-sharing service from the distributed registry.
     * 
     * @param serviceId Service identifier to deregister
     * @return true if deregistration was successful
     */
    public boolean deregisterFileService(String serviceId) {
        if (serviceRegistry == null) {
            logger.warning("Cannot deregister service: no service registry available");
            return false;
        }
        
        try {
            boolean deregistered = serviceRegistry.deregisterService("file-sharing", serviceId);
            
            if (deregistered) {
                logger.info("Deregistered file-sharing service: " + serviceId);
                health.addHealthDetail("lastDeregistration", serviceId);
            } else {
                logger.warning("Failed to deregister file-sharing service: " + serviceId);
            }
            
            return deregistered;
            
        } catch (Exception e) {
            recordFailure("Error deregistering file service: " + serviceId, e);
            return false;
        }
    }
    
    /**
     * Downloads a file from a peer (delegates to centralized client).
     *
     * @param fileName The name of the file to download
     * @param downloadDir The directory to save the file
     * @param peer The peer to download from
     * @return Empty string if successful, error message if failed
     */
    public String downloadFileFromPeer(String fileName, String downloadDir, PeerInfo peer) {
        // Delegate to centralized client for file transfer
        return centralizedClient.downloadFileFromPeer(fileName, downloadDir, peer);
    }
    
    /**
     * Verifies that a peer is listening on its port.
     * 
     * @param peer The peer to verify
     * @return true if the peer is listening
     */
    public boolean verifyPeerIsListening(PeerInfo peer) {
        return centralizedClient.verifyPeerIsListening(peer);
    }
    
    /**
     * Gets all discovered services of a specific type.
     * 
     * @param serviceType The type of service to discover
     * @return List of discovered service instances
     */
    public List<ServiceInstance> discoverServices(String serviceType) {
        if (serviceRegistry == null) {
            logger.warning("Cannot discover services: no service registry available");
            return new ArrayList<>();
        }
        
        try {
            List<ServiceInstance> services = serviceRegistry.discoverServices(serviceType);
            logger.info("Discovered " + services.size() + " services of type: " + serviceType);
            return services;
            
        } catch (Exception e) {
            recordFailure("Error discovering services of type: " + serviceType, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Gets the service registry instance.
     * 
     * @return The service registry
     */
    public ServiceRegistry getServiceRegistry() {
        return serviceRegistry;
    }
    
    /**
     * Checks if distributed discovery is enabled.
     * 
     * @return true if using distributed discovery
     */
    public boolean isUsingDistributedDiscovery() {
        return useDistributedDiscovery;
    }
    
    /**
     * Gets the peer ID.
     * 
     * @return The peer ID
     */
    public String getPeerId() {
        return peerId;
    }
    
    /**
     * Shuts down the client and cleans up resources.
     */
    public void shutdown() {
        try {
            if (serviceRegistry != null) {
                serviceRegistry.stop();
                logger.info("Service registry stopped");
            }
        } catch (Exception e) {
            logger.warning("Error stopping service registry: " + e.getMessage());
        }
        
        health.addHealthDetail("shutdownTime", System.currentTimeMillis());
    }
    
    /**
     * Gets test failures for debugging.
     * 
     * @return List of test failures
     */
    public static List<String> getTestFailures() {
        return new ArrayList<>(testFailures);
    }
    
    private static void recordFailure(String message, Exception e) {
        String failure = message + (e != null ? ": " + e.getMessage() : "");
        testFailures.add(failure);
        logger.severe(failure);
        if (e != null) {
            e.printStackTrace();
        }
    }
}
