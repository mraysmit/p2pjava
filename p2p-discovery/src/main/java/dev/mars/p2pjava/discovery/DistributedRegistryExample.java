package dev.mars.p2pjava.discovery;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Example demonstrating how to use the distributed service registry
 * with gossip protocol for P2P service discovery.
 */
public class DistributedRegistryExample {
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Distributed Service Registry Example ===\n");
        
        // Create three distributed registries representing different peers
        DistributedServiceRegistry peer1 = createPeer("peer1", 6001, Collections.emptySet());
        DistributedServiceRegistry peer2 = createPeer("peer2", 6002, Set.of("localhost:6001"));
        DistributedServiceRegistry peer3 = createPeer("peer3", 6003, Set.of("localhost:6001", "localhost:6002"));
        
        try {
            // Start all peers
            System.out.println("Starting peers...");
            peer1.start();
            peer2.start();
            peer3.start();
            
            // Wait for peers to connect
            Thread.sleep(1000);
            
            // Register services on different peers
            System.out.println("\n=== Registering Services ===");
            
            // Peer 1 registers a web service
            peer1.registerService("web-service", "web-1", "192.168.1.10", 8080, 
                Map.of("version", "1.0", "region", "us-east"));
            System.out.println("Peer1: Registered web-service/web-1");
            
            // Peer 2 registers a database service
            peer2.registerService("database", "db-primary", "192.168.1.20", 5432, 
                Map.of("type", "postgresql", "role", "primary"));
            System.out.println("Peer2: Registered database/db-primary");
            
            // Peer 3 registers another web service
            peer3.registerService("web-service", "web-2", "192.168.1.30", 8080, 
                Map.of("version", "1.1", "region", "us-west"));
            System.out.println("Peer3: Registered web-service/web-2");
            
            // Wait for gossip propagation
            System.out.println("\nWaiting for gossip propagation...");
            Thread.sleep(2000);
            
            // Discover services from each peer
            System.out.println("\n=== Service Discovery ===");
            
            System.out.println("Peer1 discovers web services:");
            List<ServiceInstance> webServices1 = peer1.discoverServices("web-service");
            webServices1.forEach(service -> 
                System.out.println("  - " + service.getServiceId() + " at " + 
                                 service.getHost() + ":" + service.getPort() + 
                                 " (origin: " + service.getOriginPeerId() + ")"));
            
            System.out.println("\nPeer2 discovers database services:");
            List<ServiceInstance> dbServices2 = peer2.discoverServices("database");
            dbServices2.forEach(service -> 
                System.out.println("  - " + service.getServiceId() + " at " + 
                                 service.getHost() + ":" + service.getPort() + 
                                 " (origin: " + service.getOriginPeerId() + ")"));
            
            System.out.println("\nPeer3 discovers all web services:");
            List<ServiceInstance> webServices3 = peer3.discoverServices("web-service");
            webServices3.forEach(service -> 
                System.out.println("  - " + service.getServiceId() + " at " + 
                                 service.getHost() + ":" + service.getPort() + 
                                 " (version: " + service.getMetadata().get("version") + 
                                 ", region: " + service.getMetadata().get("region") + ")"));
            
            // Demonstrate conflict resolution
            System.out.println("\n=== Conflict Resolution Demo ===");
            
            // Two peers try to register the same service ID
            peer1.registerService("cache", "redis-1", "192.168.1.40", 6379, 
                Map.of("role", "master"));
            System.out.println("Peer1: Registered cache/redis-1 as master");
            
            // Simulate a slight delay
            Thread.sleep(100);
            
            peer2.registerService("cache", "redis-1", "192.168.1.50", 6379, 
                Map.of("role", "slave"));
            System.out.println("Peer2: Registered cache/redis-1 as slave");
            
            // Wait for conflict resolution
            Thread.sleep(1000);
            
            System.out.println("\nAfter conflict resolution:");
            ServiceInstance resolvedCache = peer3.getService("cache", "redis-1");
            if (resolvedCache != null) {
                System.out.println("  Resolved cache/redis-1: " + resolvedCache.getHost() + 
                                 " (role: " + resolvedCache.getMetadata().get("role") + 
                                 ", origin: " + resolvedCache.getOriginPeerId() + 
                                 ", version: " + resolvedCache.getVersion() + ")");
            }
            
            // Demonstrate health updates
            System.out.println("\n=== Health Status Updates ===");
            
            System.out.println("Updating health status of web-1 to unhealthy...");
            peer1.updateServiceHealth("web-service", "web-1", false);
            
            Thread.sleep(500);
            
            System.out.println("Healthy web services from peer2:");
            List<ServiceInstance> healthyWebServices = peer2.discoverServices("web-service");
            healthyWebServices.forEach(service -> 
                System.out.println("  - " + service.getServiceId() + " (healthy: " + 
                                 service.isHealthy() + ")"));
            
            // Show registry statistics
            System.out.println("\n=== Registry Statistics ===");
            
            Map<String, Object> stats1 = peer1.getStatistics();
            System.out.println("Peer1 stats: " + stats1.get("totalServices") + " services, " + 
                             stats1.get("knownPeers") + " known peers");
            
            Map<String, Object> stats2 = peer2.getStatistics();
            System.out.println("Peer2 stats: " + stats2.get("totalServices") + " services, " + 
                             stats2.get("knownPeers") + " known peers");
            
            Map<String, Object> stats3 = peer3.getStatistics();
            System.out.println("Peer3 stats: " + stats3.get("totalServices") + " services, " + 
                             stats3.get("knownPeers") + " known peers");
            
            System.out.println("\n=== Example Complete ===");
            
        } finally {
            // Clean shutdown
            System.out.println("\nShutting down peers...");
            peer1.stop();
            peer2.stop();
            peer3.stop();
        }
    }
    
    /**
     * Creates a distributed service registry peer with the specified configuration.
     */
    private static DistributedServiceRegistry createPeer(String peerId, int gossipPort, 
                                                        Set<String> bootstrapPeers) {
        System.out.println("Creating " + peerId + " on port " + gossipPort + 
                          (bootstrapPeers.isEmpty() ? " (bootstrap peer)" : 
                           " (connecting to: " + bootstrapPeers + ")"));
        
        return new DistributedServiceRegistry(peerId, gossipPort, bootstrapPeers);
    }
}
