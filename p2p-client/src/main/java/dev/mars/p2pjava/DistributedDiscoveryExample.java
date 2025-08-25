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
import dev.mars.p2pjava.discovery.ServiceInstance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * DistributedDiscoveryExample - Simple example demonstrating distributed service discovery
 *
 * This example shows how to use the DistributedP2PClient with gossip protocol for
 * decentralized service discovery in a P2P network.
 *
 * Usage:
 * 1. Enable distributed discovery in configuration
 * 2. Run multiple instances with different peer IDs
 * 3. Observe gossip propagation and service discovery
 */
public class DistributedDiscoveryExample {
    private static final Logger logger = Logger.getLogger(DistributedDiscoveryExample.class.getName());
    
    public static void main(String[] args) throws Exception {
        // Parse command line arguments
        String peerId = args.length > 0 ? args[0] : "example-peer-" + System.currentTimeMillis();
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;
        String fileName = args.length > 2 ? args[2] : "example-file.txt";
        
        logger.info("Starting Distributed Discovery Example");
        logger.info("Peer ID: " + peerId);
        logger.info("Port: " + port);
        logger.info("File: " + fileName);
        
        // Configure for distributed discovery
        ConfigurationManager config = ConfigurationManager.getInstance();
        config.set("peer.id", peerId);
        config.set("serviceRegistry.distributed.enabled", "true");
        config.set("serviceRegistry.distributed.gossipPort", String.valueOf(6003 + (peerId.hashCode() % 100)));
        
        // Create distributed P2P client
        DistributedP2PClient client = new DistributedP2PClient(config);
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down...");
            client.shutdown();
        }));
        
        try {
            // Wait for gossip network to establish
            logger.info("Waiting for gossip network to establish...");
            Thread.sleep(5000);
            
            // Register a file-sharing service
            logger.info("Registering file-sharing service...");
            Map<String, String> metadata = new HashMap<>();
            metadata.put("description", "Example file shared by " + peerId);
            metadata.put("size", "1024");
            metadata.put("type", "text");
            
            boolean registered = client.registerFileService(
                peerId + "-file-service",
                "localhost",
                port,
                fileName,
                metadata
            );
            
            if (registered) {
                logger.info("Successfully registered file-sharing service");
            } else {
                logger.severe("Failed to register file-sharing service");
                return;
            }
            
            // Wait for gossip propagation
            logger.info("Waiting for gossip propagation...");
            Thread.sleep(10000);
            
            // Discover all file-sharing services
            logger.info("Discovering all file-sharing services...");
            List<ServiceInstance> services = client.discoverServices("file-sharing");
            
            logger.info("Found " + services.size() + " file-sharing services:");
            for (ServiceInstance service : services) {
                logger.info("  - Service: " + service.getServiceId());
                logger.info("    Host: " + service.getHost() + ":" + service.getPort());
                logger.info("    Origin: " + service.getOriginPeerId());
                logger.info("    File: " + service.getMetadata().get("file"));
                logger.info("    Description: " + service.getMetadata().get("description"));
                logger.info("    ---");
            }
            
            // Test peer discovery for specific file
            logger.info("Searching for peers with file: " + fileName);
            List<PeerInfo> peersWithFile = client.discoverPeersWithFile(fileName);
            
            logger.info("Found " + peersWithFile.size() + " peers with file " + fileName + ":");
            for (PeerInfo peer : peersWithFile) {
                logger.info("  - Peer: " + peer.getPeerId() + " at " + 
                          peer.getAddress() + ":" + peer.getPort());
            }
            
            // Search for a different file to test filtering
            String otherFile = "other-file.txt";
            logger.info("Searching for peers with file: " + otherFile);
            List<PeerInfo> peersWithOtherFile = client.discoverPeersWithFile(otherFile);
            
            logger.info("Found " + peersWithOtherFile.size() + " peers with file " + otherFile);
            
            // Demonstrate continuous discovery
            logger.info("Starting continuous discovery loop (press Ctrl+C to stop)...");
            
            int iteration = 0;
            while (true) {
                Thread.sleep(30000); // Wait 30 seconds between discoveries
                iteration++;
                
                logger.info("=== Discovery Iteration " + iteration + " ===");
                
                // Rediscover services
                services = client.discoverServices("file-sharing");
                logger.info("Current services in network: " + services.size());
                
                // Show service health
                for (ServiceInstance service : services) {
                    boolean healthy = client.getServiceRegistry().isServiceHealthy(
                        service.getServiceType(), service.getServiceId());
                    logger.info("  - " + service.getServiceId() + " (healthy: " + healthy + ")");
                }
                
                // Test specific file discovery
                peersWithFile = client.discoverPeersWithFile(fileName);
                logger.info("Peers with " + fileName + ": " + peersWithFile.size());
                
                // Verify peer connectivity
                for (PeerInfo peer : peersWithFile) {
                    if (!peer.getPeerId().equals(peerId)) { // Don't test ourselves
                        boolean listening = client.verifyPeerIsListening(peer);
                        logger.info("  - " + peer.getPeerId() + " listening: " + listening);
                    }
                }
            }
            
        } catch (InterruptedException e) {
            logger.info("Example interrupted");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.severe("Example failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            client.shutdown();
        }
    }
    
    /**
     * Prints usage information.
     */
    private static void printUsage() {
        System.out.println("Usage: java DistributedDiscoveryExample [peerId] [port] [fileName]");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  peerId   - Unique identifier for this peer (default: auto-generated)");
        System.out.println("  port     - Port number for this peer's services (default: 8080)");
        System.out.println("  fileName - Name of file to share (default: example-file.txt)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java DistributedDiscoveryExample peer1 8080 file1.txt");
        System.out.println("  java DistributedDiscoveryExample peer2 8081 file2.txt");
        System.out.println("  java DistributedDiscoveryExample peer3 8082 file3.txt");
        System.out.println();
        System.out.println("Configuration:");
        System.out.println("  Set serviceRegistry.distributed.enabled=true in configuration");
        System.out.println("  Configure bootstrap peers if needed");
    }
}
