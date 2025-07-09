package dev.mars.p2pjava.discovery;

import dev.mars.p2pjava.config.PeerConfig;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Example demonstrating the enhanced distributed service registry with:
 * - Gossip protocol improvements
 * - Advanced conflict resolution
 * - Vector clock versioning
 * - Priority-based propagation
 */
public class EnhancedDistributedRegistryExample {
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Enhanced Distributed Service Registry Example ===\n");
        
        // Create enhanced registries with different configurations
        EnhancedDistributedRegistry peer1 = createEnhancedPeer("peer1", 7001, Collections.emptySet());
        EnhancedDistributedRegistry peer2 = createEnhancedPeer("peer2", 7002, Set.of("localhost:7001"));
        EnhancedDistributedRegistry peer3 = createEnhancedPeer("peer3", 7003, Set.of("localhost:7001", "localhost:7002"));
        
        try {
            // Start all peers
            System.out.println("Starting enhanced peers...");
            peer1.start();
            peer2.start();
            peer3.start();
            
            // Wait for peers to connect and stabilize
            Thread.sleep(2000);
            
            // Demonstrate priority-based service registration
            System.out.println("\n=== Priority-Based Service Registration ===");
            
            // High priority: Critical infrastructure services
            peer1.registerServiceWithPriority("database", "primary-db", "192.168.1.10", 5432, 
                Map.of("role", "primary", "region", "us-east"), ServicePriority.HIGH);
            System.out.println("Peer1: Registered HIGH priority database service");
            
            // Normal priority: Application services
            peer2.registerServiceWithPriority("web-service", "web-1", "192.168.1.20", 8080, 
                Map.of("version", "2.0", "region", "us-east"), ServicePriority.NORMAL);
            System.out.println("Peer2: Registered NORMAL priority web service");
            
            // Low priority: Monitoring services
            peer3.registerServiceWithPriority("monitoring", "metrics-1", "192.168.1.30", 9090, 
                Map.of("type", "prometheus", "region", "us-east"), ServicePriority.LOW);
            System.out.println("Peer3: Registered LOW priority monitoring service");
            
            // Wait for gossip propagation
            System.out.println("\nWaiting for enhanced gossip propagation...");
            Thread.sleep(3000);
            
            // Demonstrate conflict resolution
            System.out.println("\n=== Conflict Resolution Demonstration ===");
            
            // Create conflicting service registrations
            peer1.registerServiceWithPriority("web-service", "web-conflict", "192.168.1.100", 8080, 
                Map.of("version", "1.0", "priority", "low"), ServicePriority.NORMAL);
            
            peer2.registerServiceWithPriority("web-service", "web-conflict", "192.168.1.101", 8081, 
                Map.of("version", "2.0", "priority", "high"), ServicePriority.HIGH);
            
            peer3.registerServiceWithPriority("web-service", "web-conflict", "192.168.1.102", 8082, 
                Map.of("version", "1.5", "priority", "medium"), ServicePriority.NORMAL);
            
            System.out.println("Created conflicting service registrations across peers");
            
            // Wait for conflict resolution
            Thread.sleep(5000);
            
            // Check resolved service
            System.out.println("\n=== Conflict Resolution Results ===");
            ServiceInstance resolvedService1 = peer1.getService("web-service", "web-conflict");
            ServiceInstance resolvedService2 = peer2.getService("web-service", "web-conflict");
            ServiceInstance resolvedService3 = peer3.getService("web-service", "web-conflict");
            
            System.out.println("Peer1 resolved service: " + formatServiceInstance(resolvedService1));
            System.out.println("Peer2 resolved service: " + formatServiceInstance(resolvedService2));
            System.out.println("Peer3 resolved service: " + formatServiceInstance(resolvedService3));
            
            // Demonstrate vector clock causality
            System.out.println("\n=== Vector Clock Causality ===");
            if (resolvedService1 != null && resolvedService2 != null) {
                System.out.println("Service1 vector clock: " + resolvedService1.getVectorClock());
                System.out.println("Service2 vector clock: " + resolvedService2.getVectorClock());
                
                if (resolvedService1.happensBefore(resolvedService2)) {
                    System.out.println("Service1 happened before Service2");
                } else if (resolvedService2.happensBefore(resolvedService1)) {
                    System.out.println("Service2 happened before Service1");
                } else if (resolvedService1.isConcurrentWith(resolvedService2)) {
                    System.out.println("Service1 and Service2 are concurrent");
                } else {
                    System.out.println("Services are identical");
                }
            }
            
            // Display gossip metrics
            System.out.println("\n=== Gossip Protocol Metrics ===");
            displayGossipMetrics("Peer1", peer1.getGossipMetrics());
            displayGossipMetrics("Peer2", peer2.getGossipMetrics());
            displayGossipMetrics("Peer3", peer3.getGossipMetrics());
            
            // Demonstrate service discovery with filtering
            System.out.println("\n=== Enhanced Service Discovery ===");
            
            List<ServiceInstance> allWebServices = peer1.discoverServices("web-service");
            System.out.println("All web services discovered by Peer1:");
            allWebServices.forEach(service -> 
                System.out.println("  - " + formatServiceInstance(service)));
            
            // Filter by region
            List<ServiceInstance> eastRegionServices = peer1.discoverServicesWithFilter("web-service", 
                service -> "us-east".equals(service.getMetadata().get("region")));
            System.out.println("\nWeb services in us-east region:");
            eastRegionServices.forEach(service -> 
                System.out.println("  - " + formatServiceInstance(service)));
            
            // Demonstrate health-based conflict resolution
            System.out.println("\n=== Health-Based Conflict Resolution ===");
            
            // Mark one service as unhealthy
            peer2.updateServiceHealth("web-service", "web-conflict", false);
            System.out.println("Marked web-conflict service as unhealthy on Peer2");
            
            Thread.sleep(2000);
            
            // Check if healthy service is preferred
            ServiceInstance healthyService = peer1.getService("web-service", "web-conflict");
            System.out.println("Resolved healthy service: " + formatServiceInstance(healthyService));
            
        } finally {
            // Cleanup
            System.out.println("\n=== Cleanup ===");
            peer1.stop();
            peer2.stop();
            peer3.stop();
            System.out.println("All peers stopped");
        }
    }
    
    /**
     * Creates an enhanced distributed registry with optimized configuration.
     */
    private static EnhancedDistributedRegistry createEnhancedPeer(String peerId, int gossipPort, Set<String> bootstrapPeers) {
        // Create enhanced gossip configuration
        PeerConfig.GossipConfig gossipConfig = new PeerConfig.GossipConfig();
        gossipConfig.setPort(gossipPort);
        gossipConfig.setIntervalMs(2000); // Faster gossip for demo
        gossipConfig.setFanout(2);
        gossipConfig.setAdaptiveFanout(true);
        gossipConfig.setPriorityPropagation(true);
        gossipConfig.setCompressionEnabled(true);
        gossipConfig.setBatchSize(5);
        
        // Create conflict resolution strategy with composite approach
        Map<String, Integer> peerPriorities = Map.of(
            "peer1", 10,  // Highest priority
            "peer2", 5,   // Medium priority
            "peer3", 1    // Lowest priority
        );
        
        ConflictResolutionStrategy conflictResolver = new ConflictResolutionStrategy(
            ConflictResolutionStrategy.ResolutionPolicy.COMPOSITE,
            peerPriorities,
            service -> service.isHealthy() // Simple health checker
        );
        
        return new EnhancedDistributedRegistry(peerId, gossipConfig, bootstrapPeers, conflictResolver);
    }
    
    /**
     * Formats a service instance for display.
     */
    private static String formatServiceInstance(ServiceInstance service) {
        if (service == null) return "null";
        
        return String.format("%s at %s:%d (origin: %s, version: %d, priority: %d, healthy: %s)",
            service.getServiceId(),
            service.getHost(),
            service.getPort(),
            service.getOriginPeerId(),
            service.getVersion(),
            service.getPriority(),
            service.isHealthy()
        );
    }
    
    /**
     * Displays gossip protocol metrics.
     */
    private static void displayGossipMetrics(String peerName, GossipMetrics metrics) {
        System.out.printf("%s: %s%n", peerName, metrics);
    }
    
    /**
     * Service priority enumeration.
     */
    public enum ServicePriority {
        HIGH, NORMAL, LOW
    }
}
