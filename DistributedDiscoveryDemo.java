import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * Standalone demonstration of distributed service discovery concepts
 * This demo shows how the gossip protocol would work without requiring
 * the full P2P system to be compiled and running.
 */
public class DistributedDiscoveryDemo {
    private static final Logger logger = Logger.getLogger(DistributedDiscoveryDemo.class.getName());
    
    // Simulated service registry entries
    static class ServiceEntry {
        String serviceId;
        String serviceType;
        String host;
        int port;
        String peerId;
        Map<String, String> metadata;
        long timestamp;
        
        ServiceEntry(String serviceId, String serviceType, String host, int port, 
                    String peerId, Map<String, String> metadata) {
            this.serviceId = serviceId;
            this.serviceType = serviceType;
            this.host = host;
            this.port = port;
            this.peerId = peerId;
            this.metadata = new HashMap<>(metadata);
            this.timestamp = System.currentTimeMillis();
        }
        
        @Override
        public String toString() {
            return String.format("Service[%s] %s:%d from %s (file: %s)", 
                serviceId, host, port, peerId, metadata.get("file"));
        }
    }
    
    // Simulated distributed registry for a peer
    static class SimulatedDistributedRegistry {
        private final String peerId;
        private final Map<String, ServiceEntry> services = new ConcurrentHashMap<>();
        private final List<SimulatedDistributedRegistry> peers = new ArrayList<>();
        
        SimulatedDistributedRegistry(String peerId) {
            this.peerId = peerId;
        }
        
        void addPeer(SimulatedDistributedRegistry peer) {
            peers.add(peer);
        }
        
        boolean registerService(String serviceType, String serviceId, String host, int port, 
                               Map<String, String> metadata) {
            ServiceEntry entry = new ServiceEntry(serviceId, serviceType, host, port, peerId, metadata);
            services.put(serviceId, entry);
            
            logger.info(peerId + ": Registered service " + serviceId + " for file: " + metadata.get("file"));
            
            // Simulate gossip propagation
            gossipServiceRegistration(entry);
            return true;
        }
        
        List<ServiceEntry> discoverServices(String serviceType) {
            return services.values().stream()
                    .filter(s -> serviceType.equals(s.serviceType))
                    .toList();
        }
        
        List<ServiceEntry> discoverPeersWithFile(String fileName) {
            return services.values().stream()
                    .filter(s -> "file-sharing".equals(s.serviceType))
                    .filter(s -> fileName.equals(s.metadata.get("file")))
                    .toList();
        }
        
        private void gossipServiceRegistration(ServiceEntry entry) {
            // Simulate gossip protocol - propagate to random subset of peers
            int fanout = Math.min(2, peers.size());
            Collections.shuffle(peers);
            
            for (int i = 0; i < fanout; i++) {
                SimulatedDistributedRegistry peer = peers.get(i);
                peer.receiveGossipMessage(entry);
            }
        }
        
        void receiveGossipMessage(ServiceEntry entry) {
            // Simulate receiving a gossip message about a service
            if (!services.containsKey(entry.serviceId)) {
                services.put(entry.serviceId, entry);
                logger.info(peerId + ": Learned about service " + entry.serviceId + 
                          " from " + entry.peerId + " via gossip");
                
                // Continue propagation (with TTL to prevent infinite loops)
                if (entry.timestamp > System.currentTimeMillis() - 10000) { // 10 second TTL
                    gossipServiceRegistration(entry);
                }
            }
        }
        
        boolean deregisterService(String serviceId) {
            ServiceEntry removed = services.remove(serviceId);
            if (removed != null) {
                logger.info(peerId + ": Deregistered service " + serviceId);
                return true;
            }
            return false;
        }
        
        void simulateFailure() {
            logger.info(peerId + ": SIMULATING PEER FAILURE");
            services.clear();
            // In real implementation, other peers would detect this failure via gossip
        }
    }
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Distributed Service Discovery Demo ===");
        System.out.println("This demo simulates the gossip protocol for distributed service discovery");
        System.out.println();
        
        // Create 3 simulated peers
        SimulatedDistributedRegistry peer1 = new SimulatedDistributedRegistry("peer1");
        SimulatedDistributedRegistry peer2 = new SimulatedDistributedRegistry("peer2");
        SimulatedDistributedRegistry peer3 = new SimulatedDistributedRegistry("peer3");
        
        // Connect peers in a mesh network
        peer1.addPeer(peer2);
        peer1.addPeer(peer3);
        peer2.addPeer(peer1);
        peer2.addPeer(peer3);
        peer3.addPeer(peer1);
        peer3.addPeer(peer2);
        
        System.out.println("✓ Created 3-peer gossip network");
        System.out.println();
        
        // Phase 1: Service Registration
        System.out.println("=== Phase 1: Service Registration ===");
        
        peer1.registerService("file-sharing", "peer1-file-service", "localhost", 8080, 
                             Map.of("file", "file1.txt", "size", "1024"));
        
        Thread.sleep(100); // Allow gossip propagation
        
        peer2.registerService("file-sharing", "peer2-file-service", "localhost", 8081, 
                             Map.of("file", "file2.txt", "size", "2048"));
        
        Thread.sleep(100);
        
        peer3.registerService("file-sharing", "peer3-file-service", "localhost", 8082, 
                             Map.of("file", "file3.txt", "size", "4096"));
        
        Thread.sleep(500); // Allow full gossip propagation
        System.out.println();
        
        // Phase 2: Service Discovery
        System.out.println("=== Phase 2: Service Discovery ===");
        
        for (SimulatedDistributedRegistry peer : Arrays.asList(peer1, peer2, peer3)) {
            List<ServiceEntry> services = peer.discoverServices("file-sharing");
            System.out.println(peer.peerId + " discovered " + services.size() + " file-sharing services:");
            for (ServiceEntry service : services) {
                System.out.println("  - " + service);
            }
            System.out.println();
        }
        
        // Phase 3: File-specific Discovery
        System.out.println("=== Phase 3: File-specific Discovery ===");
        
        String searchFile = "file2.txt";
        System.out.println("Searching for peers with file: " + searchFile);
        
        for (SimulatedDistributedRegistry peer : Arrays.asList(peer1, peer2, peer3)) {
            List<ServiceEntry> peersWithFile = peer.discoverPeersWithFile(searchFile);
            System.out.println(peer.peerId + " found " + peersWithFile.size() + " peers with " + searchFile + ":");
            for (ServiceEntry service : peersWithFile) {
                System.out.println("  - " + service);
            }
        }
        System.out.println();
        
        // Phase 4: Failure Simulation
        System.out.println("=== Phase 4: Failure Simulation ===");
        
        peer2.simulateFailure();
        Thread.sleep(100);
        
        System.out.println("After peer2 failure, remaining services:");
        for (SimulatedDistributedRegistry peer : Arrays.asList(peer1, peer3)) {
            List<ServiceEntry> services = peer.discoverServices("file-sharing");
            System.out.println(peer.peerId + " now sees " + services.size() + " services:");
            for (ServiceEntry service : services) {
                System.out.println("  - " + service);
            }
        }
        System.out.println();
        
        // Phase 5: New Peer Joining
        System.out.println("=== Phase 5: New Peer Joining ===");
        
        SimulatedDistributedRegistry peer4 = new SimulatedDistributedRegistry("peer4");
        peer4.addPeer(peer1);
        peer4.addPeer(peer3);
        peer1.addPeer(peer4);
        peer3.addPeer(peer4);
        
        peer4.registerService("file-sharing", "peer4-file-service", "localhost", 8083, 
                             Map.of("file", "file4.txt", "size", "8192"));
        
        Thread.sleep(500);
        
        System.out.println("After peer4 joins:");
        for (SimulatedDistributedRegistry peer : Arrays.asList(peer1, peer3, peer4)) {
            List<ServiceEntry> services = peer.discoverServices("file-sharing");
            System.out.println(peer.peerId + " now sees " + services.size() + " services:");
            for (ServiceEntry service : services) {
                System.out.println("  - " + service);
            }
        }
        System.out.println();
        
        // Summary
        System.out.println("=== Demo Summary ===");
        System.out.println("✓ Demonstrated decentralized service registration");
        System.out.println("✓ Showed gossip protocol propagation");
        System.out.println("✓ Tested service discovery from multiple peers");
        System.out.println("✓ Demonstrated file-specific peer discovery");
        System.out.println("✓ Simulated peer failure and network resilience");
        System.out.println("✓ Showed new peer joining the network");
        System.out.println();
        System.out.println("This demonstrates the core concepts of your distributed");
        System.out.println("service discovery implementation with gossip protocol!");
    }
}
