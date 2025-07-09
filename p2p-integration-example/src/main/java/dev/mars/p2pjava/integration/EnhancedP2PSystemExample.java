package dev.mars.p2pjava.integration;

import dev.mars.p2pjava.Peer;
import dev.mars.p2pjava.Tracker;
import dev.mars.p2pjava.discovery.*;
import dev.mars.p2pjava.storage.EnhancedIndexStorageManager;
import dev.mars.p2pjava.storage.FileBasedIndexStorage;
import dev.mars.p2pjava.config.PeerConfig;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive example demonstrating the enhanced P2P system with:
 * - Distributed service registry with gossip protocol
 * - Enhanced conflict resolution
 * - Vector clock versioning
 * - Intelligent peer and service discovery
 * - Load-balanced index server selection
 */
public class EnhancedP2PSystemExample {
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== Enhanced P2P System Integration Example ===\n");
        
        // Configure system properties for enhanced features
        configureSystemProperties();
        
        // Start the enhanced tracker
        System.out.println("1. Starting Enhanced Tracker...");
        startEnhancedTracker();
        Thread.sleep(2000); // Wait for tracker to initialize
        
        // Start enhanced peers
        System.out.println("\n2. Starting Enhanced Peers...");
        List<Peer> peers = startEnhancedPeers();
        Thread.sleep(3000); // Wait for peers to register and discover each other
        
        // Demonstrate enhanced service discovery
        System.out.println("\n3. Demonstrating Enhanced Service Discovery...");
        demonstrateServiceDiscovery(peers);
        
        // Demonstrate intelligent file indexing
        System.out.println("\n4. Demonstrating Intelligent File Indexing...");
        demonstrateFileIndexing();
        
        // Demonstrate conflict resolution
        System.out.println("\n5. Demonstrating Conflict Resolution...");
        demonstrateConflictResolution();
        
        // Show system metrics
        System.out.println("\n6. System Metrics and Health...");
        showSystemMetrics(peers);
        
        // Cleanup
        System.out.println("\n7. Cleanup...");
        cleanup(peers);
        
        System.out.println("\nEnhanced P2P System Example completed successfully!");
    }
    
    /**
     * Configures system properties for enhanced features.
     */
    private static void configureSystemProperties() {
        // Enable distributed registry for all components
        System.setProperty("tracker.distributed.enabled", "true");
        System.setProperty("peer.distributed.enabled", "true");
        
        // Configure gossip protocol
        System.setProperty("tracker.gossip.port", "6003");
        System.setProperty("peer.gossip.port", "6004");
        System.setProperty("tracker.gossip.interval", "3000");
        System.setProperty("peer.gossip.interval", "5000");
        
        // Configure bootstrap peers (tracker as bootstrap for peers)
        System.setProperty("peer.bootstrap.peers", "localhost:6003");
        
        // Configure regions for testing
        System.setProperty("tracker.region", "us-east");
        System.setProperty("peer.region", "us-east");
        
        System.out.println("System properties configured for enhanced features");
    }
    
    /**
     * Starts the enhanced tracker with distributed registry.
     */
    private static void startEnhancedTracker() {
        // The enhanced tracker will automatically use distributed registry
        // based on system properties
        new Thread(() -> {
            try {
                Tracker.main(new String[]{});
            } catch (Exception e) {
                System.err.println("Error starting tracker: " + e.getMessage());
            }
        }).start();
        
        System.out.println("Enhanced tracker started with distributed service registry");
    }
    
    /**
     * Starts multiple enhanced peers.
     */
    private static List<Peer> startEnhancedPeers() throws Exception {
        List<Peer> peers = new ArrayList<>();
        
        // Create 3 enhanced peers
        for (int i = 1; i <= 3; i++) {
            String peerId = "enhanced-peer-" + i;
            String peerHost = "localhost";
            int peerPort = 8000 + i;
            String trackerHost = "localhost";
            int trackerPort = 6000;
            
            Peer peer = new Peer(peerId, peerHost, peerPort, trackerHost, trackerPort);
            peer.start();
            peers.add(peer);
            
            System.out.println("Started enhanced peer: " + peerId + " on port " + peerPort);
            
            // Wait a bit between peer starts to avoid port conflicts
            Thread.sleep(500);
        }
        
        // Wait for all peers to complete startup
        for (Peer peer : peers) {
            peer.waitForStartup(10000); // 10 second timeout
        }
        
        System.out.println("All enhanced peers started and registered");
        return peers;
    }
    
    /**
     * Demonstrates enhanced service discovery capabilities.
     */
    private static void demonstrateServiceDiscovery(List<Peer> peers) {
        System.out.println("Discovering services using enhanced registry...");
        
        for (int i = 0; i < peers.size(); i++) {
            Peer peer = peers.get(i);
            System.out.println("\nPeer " + peer.getPeerId() + " discoveries:");
            
            // Discover other peers
            List<ServiceInstance> discoveredPeers = peer.discoverPeers();
            System.out.println("  Other peers discovered: " + discoveredPeers.size());
            for (ServiceInstance discoveredPeer : discoveredPeers) {
                System.out.println("    - " + discoveredPeer.getServiceId() + 
                                 " at " + discoveredPeer.getHost() + ":" + discoveredPeer.getPort() +
                                 " (region: " + discoveredPeer.getMetadata().get("region") + ")");
            }
            
            // Discover trackers
            List<ServiceInstance> trackers = peer.discoverTrackers();
            System.out.println("  Trackers discovered: " + trackers.size());
            for (ServiceInstance tracker : trackers) {
                System.out.println("    - " + tracker.getServiceId() + 
                                 " at " + tracker.getHost() + ":" + tracker.getPort() +
                                 " (capabilities: " + tracker.getMetadata().get("capabilities") + ")");
            }
        }
    }
    
    /**
     * Demonstrates intelligent file indexing with enhanced storage manager.
     */
    private static void demonstrateFileIndexing() throws Exception {
        System.out.println("Setting up enhanced file indexing...");
        
        // Create a distributed service registry for the storage manager
        DistributedServiceRegistry storageRegistry = new DistributedServiceRegistry(
            "storage-manager", 6005, Set.of("localhost:6003"));
        storageRegistry.start();
        
        // Create local storage
        FileBasedIndexStorage localStorage = new FileBasedIndexStorage("./test-index.dat");
        localStorage.initialize();
        
        // Create enhanced storage manager
        EnhancedIndexStorageManager storageManager = new EnhancedIndexStorageManager(
            "storage-node-1", storageRegistry, localStorage);
        storageManager.start();
        
        // Simulate file operations
        System.out.println("Performing file operations...");
        
        // Register some files
        dev.mars.p2pjava.storage.PeerInfo peerInfo1 = new dev.mars.p2pjava.storage.PeerInfo("peer1", "192.168.1.10", 8001);
        dev.mars.p2pjava.storage.PeerInfo peerInfo2 = new dev.mars.p2pjava.storage.PeerInfo("peer2", "192.168.1.11", 8002);
        
        storageManager.registerFileAsync("document1.pdf", peerInfo1).get();
        storageManager.registerFileAsync("image1.jpg", peerInfo2).get();
        storageManager.registerFileAsync("video1.mp4", peerInfo1).get();
        
        System.out.println("Files registered with enhanced indexing");
        
        // Search for files
        Map<String, List<dev.mars.p2pjava.storage.PeerInfo>> searchResults = 
            storageManager.searchFilesAsync("*.pdf").get();
        
        System.out.println("Search results for '*.pdf':");
        searchResults.forEach((fileName, peers) -> {
            System.out.println("  " + fileName + " available from " + peers.size() + " peers");
        });
        
        // Show index server metrics
        System.out.println("Index server metrics:");
        storageManager.getServerMetrics().forEach((serverId, metrics) -> {
            System.out.println("  " + serverId + ": " + metrics);
        });
        
        // Cleanup
        storageManager.stop();
        storageRegistry.stop();
        localStorage.cleanup();
    }
    
    /**
     * Demonstrates conflict resolution in action.
     */
    private static void demonstrateConflictResolution() throws Exception {
        System.out.println("Creating conflicting service registrations...");
        
        // Create multiple registries that will have conflicts
        DistributedServiceRegistry registry1 = new DistributedServiceRegistry("conflict-peer-1", 6006, Collections.emptySet());
        DistributedServiceRegistry registry2 = new DistributedServiceRegistry("conflict-peer-2", 6007, Set.of("localhost:6006"));
        
        registry1.start();
        registry2.start();
        
        Thread.sleep(1000); // Let them connect
        
        // Register conflicting services
        Map<String, String> metadata1 = Map.of("version", "1.0", "priority", "low");
        Map<String, String> metadata2 = Map.of("version", "2.0", "priority", "high");
        
        registry1.registerService("test-service", "conflict-service", "host1", 9001, metadata1);
        registry2.registerService("test-service", "conflict-service", "host2", 9002, metadata2);
        
        System.out.println("Conflicting services registered, waiting for resolution...");
        Thread.sleep(3000); // Wait for gossip propagation and conflict resolution
        
        // Check resolved services
        ServiceInstance resolved1 = registry1.getService("test-service", "conflict-service");
        ServiceInstance resolved2 = registry2.getService("test-service", "conflict-service");
        
        System.out.println("Conflict resolution results:");
        System.out.println("  Registry 1 resolved to: " + formatService(resolved1));
        System.out.println("  Registry 2 resolved to: " + formatService(resolved2));
        
        if (resolved1 != null && resolved2 != null && resolved1.getHost().equals(resolved2.getHost())) {
            System.out.println("  ✓ Conflict successfully resolved - both registries agree");
        } else {
            System.out.println("  ⚠ Conflict resolution still in progress or failed");
        }
        
        // Cleanup
        registry1.stop();
        registry2.stop();
    }
    
    /**
     * Shows system metrics and health information.
     */
    private static void showSystemMetrics(List<Peer> peers) {
        System.out.println("System Health and Metrics:");
        
        for (Peer peer : peers) {
            System.out.println("\nPeer " + peer.getPeerId() + ":");
            System.out.println("  Status: Running");
            System.out.println("  Discovered peers: " + peer.discoverPeers().size());
            System.out.println("  Shared files: " + peer.getSharedFiles().size());
            
            // Update some metadata to show dynamic updates
            peer.updatePeerMetadata("lastHealthCheck", String.valueOf(System.currentTimeMillis()));
            peer.updatePeerMetadata("fileCount", String.valueOf(peer.getSharedFiles().size()));
        }
    }
    
    /**
     * Cleanup resources.
     */
    private static void cleanup(List<Peer> peers) {
        for (Peer peer : peers) {
            try {
                peer.stop();
                System.out.println("Stopped peer: " + peer.getPeerId());
            } catch (Exception e) {
                System.err.println("Error stopping peer " + peer.getPeerId() + ": " + e.getMessage());
            }
        }
        
        // Stop tracker (this would need to be implemented in the Tracker class)
        System.out.println("Tracker cleanup would be performed here");
    }
    
    /**
     * Formats a service instance for display.
     */
    private static String formatService(ServiceInstance service) {
        if (service == null) return "null";
        return String.format("%s at %s:%d (version: %s, origin: %s)",
            service.getServiceId(), service.getHost(), service.getPort(),
            service.getMetadata().get("version"), service.getOriginPeerId());
    }
}
