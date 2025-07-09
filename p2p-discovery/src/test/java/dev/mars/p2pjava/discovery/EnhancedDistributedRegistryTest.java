package dev.mars.p2pjava.discovery;

import dev.mars.p2pjava.config.PeerConfig;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for enhanced distributed registry features.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EnhancedDistributedRegistryTest {
    
    private static final int BASE_PORT = 8000;
    private static final long PROPAGATION_DELAY = 2000; // 2 seconds
    
    private List<DistributedServiceRegistry> registries;
    private ConflictResolutionStrategy conflictResolver;
    
    @BeforeEach
    void setUp() {
        registries = new ArrayList<>();
        
        // Create conflict resolution strategy
        Map<String, Integer> peerPriorities = Map.of(
            "peer1", 10,
            "peer2", 5,
            "peer3", 1
        );
        
        conflictResolver = new ConflictResolutionStrategy(
            ConflictResolutionStrategy.ResolutionPolicy.COMPOSITE,
            peerPriorities,
            service -> service.isHealthy()
        );
    }
    
    @AfterEach
    void tearDown() {
        for (DistributedServiceRegistry registry : registries) {
            try {
                registry.stop();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
        registries.clear();
    }
    
    @Test
    @Order(1)
    @DisplayName("Test Vector Clock Causality")
    void testVectorClockCausality() {
        // Create vector clocks
        VectorClock clock1 = VectorClock.create("peer1");
        VectorClock clock2 = clock1.increment("peer2");
        VectorClock clock3 = clock2.increment("peer1");
        
        // Test happens-before relationships
        assertTrue(clock1.isBefore(clock2), "Clock1 should happen before Clock2");
        assertTrue(clock2.isBefore(clock3), "Clock2 should happen before Clock3");
        assertTrue(clock1.isBefore(clock3), "Clock1 should happen before Clock3");
        
        // Test concurrent clocks
        VectorClock clock4 = clock1.increment("peer3");
        assertTrue(clock2.isConcurrent(clock4), "Clock2 and Clock4 should be concurrent");
        
        // Test equality
        VectorClock clock5 = new VectorClock(clock1);
        assertEquals(clock1, clock5, "Clocks should be equal");
    }
    
    @Test
    @Order(2)
    @DisplayName("Test Conflict Resolution Strategies")
    void testConflictResolutionStrategies() {
        // Create conflicting service instances
        ServiceInstance service1 = new ServiceInstance("web", "test", "host1", 8080, 
            Map.of("version", "1.0"), 1000, "peer1");
        
        ServiceInstance service2 = new ServiceInstance("web", "test", "host2", 8081, 
            Map.of("version", "2.0"), 2000, "peer2");
        
        ServiceInstance service3 = new ServiceInstance("web", "test", "host3", 8082, 
            Map.of("version", "1.5"), 1500, "peer3");
        
        List<ServiceInstance> conflicting = Arrays.asList(service1, service2, service3);
        
        // Test Last Write Wins
        ConflictResolutionStrategy lwwResolver = new ConflictResolutionStrategy(
            ConflictResolutionStrategy.ResolutionPolicy.LAST_WRITE_WINS);
        ServiceInstance resolved = lwwResolver.resolveConflict(conflicting);
        assertEquals(service2, resolved, "Should resolve to service with latest timestamp");
        
        // Test Peer Priority
        ConflictResolutionStrategy priorityResolver = new ConflictResolutionStrategy(
            ConflictResolutionStrategy.ResolutionPolicy.PEER_PRIORITY,
            Map.of("peer1", 10, "peer2", 5, "peer3", 1),
            null);
        resolved = priorityResolver.resolveConflict(conflicting);
        assertEquals(service1, resolved, "Should resolve to service from highest priority peer");
    }
    
    @Test
    @Order(3)
    @DisplayName("Test Enhanced Service Instance Features")
    void testEnhancedServiceInstance() {
        ServiceInstance service = new ServiceInstance("web", "test", "localhost", 8080, 
            Map.of("version", "1.0"), 1000, "peer1");
        
        // Test vector clock functionality
        assertNotNull(service.getVectorClock(), "Service should have vector clock");
        assertEquals("peer1", service.getOriginPeerId(), "Origin peer should be set");
        
        // Test clock increment
        ServiceInstance updated = service.withIncrementedClock("peer2");
        assertTrue(service.getVectorClock().isBefore(updated.getVectorClock()), 
            "Updated service should have later vector clock");
        
        // Test causality methods
        assertFalse(service.happensBefore(service), "Service should not happen before itself");
        assertFalse(service.isConcurrentWith(service), "Service should not be concurrent with itself");
        
        // Test priority
        service.setPriority(5);
        assertEquals(5, service.getPriority(), "Priority should be set correctly");
    }
    
    @Test
    @Order(4)
    @DisplayName("Test Peer Metrics")
    void testPeerMetrics() {
        PeerMetrics metrics = new PeerMetrics();
        
        // Initially healthy
        assertTrue(metrics.isHealthy(), "New peer should be healthy");
        assertEquals(1.0, metrics.getSuccessRate(), 0.01, "Initial success rate should be 1.0");
        
        // Record successful operations
        metrics.recordOperation(true, 100);
        metrics.recordOperation(true, 200);
        metrics.recordOperation(true, 150);
        
        assertEquals(3, metrics.getTotalOperations(), "Should have 3 total operations");
        assertEquals(3, metrics.getSuccessfulOperations(), "Should have 3 successful operations");
        assertTrue(metrics.getAverageResponseTime() > 0, "Should have positive average response time");
        
        // Record failures
        metrics.recordOperation(false, 0);
        metrics.recordOperation(false, 0);
        
        assertEquals(5, metrics.getTotalOperations(), "Should have 5 total operations");
        assertEquals(3, metrics.getSuccessfulOperations(), "Should still have 3 successful operations");
        assertTrue(metrics.getSuccessRate() < 1.0, "Success rate should decrease");
        
        // Test reliability score
        double score = metrics.getReliabilityScore();
        assertTrue(score >= 0.0 && score <= 1.0, "Reliability score should be between 0 and 1");
    }
    
    @Test
    @Order(5)
    @DisplayName("Test Registry Merging")
    void testRegistryMerging() {
        // Create multiple registry states
        Map<String, Map<String, ServiceInstance>> registry1 = new HashMap<>();
        Map<String, Map<String, ServiceInstance>> registry2 = new HashMap<>();
        
        // Add services to first registry
        ServiceInstance service1 = new ServiceInstance("web", "service1", "host1", 8080, 
            Map.of(), 1000, "peer1");
        registry1.put("web", Map.of("service1", service1));
        
        // Add conflicting service to second registry
        ServiceInstance service2 = new ServiceInstance("web", "service1", "host2", 8081, 
            Map.of(), 2000, "peer2");
        registry2.put("web", Map.of("service1", service2));
        
        // Merge registries
        List<Map<String, Map<String, ServiceInstance>>> registries = Arrays.asList(registry1, registry2);
        Map<String, Map<String, ServiceInstance>> merged = conflictResolver.mergeRegistries(registries);
        
        // Verify merge result
        assertTrue(merged.containsKey("web"), "Merged registry should contain web services");
        assertTrue(merged.get("web").containsKey("service1"), "Should contain service1");
        
        ServiceInstance resolvedService = merged.get("web").get("service1");
        assertNotNull(resolvedService, "Resolved service should not be null");
        
        // Should resolve to service2 (higher timestamp) or service1 (higher peer priority)
        // depending on conflict resolution strategy
        assertTrue(resolvedService.equals(service1) || resolvedService.equals(service2),
            "Resolved service should be one of the conflicting services");
    }
    
    @Test
    @Order(6)
    @DisplayName("Test Gossip Metrics")
    void testGossipMetrics() {
        GossipMetrics metrics = new GossipMetrics(5, 100, 95, 1024, 3);
        
        assertEquals(5, metrics.getActivePeerCount(), "Should have 5 active peers");
        assertEquals(100, metrics.getMessagesSent(), "Should have sent 100 messages");
        assertEquals(95, metrics.getMessagesReceived(), "Should have received 95 messages");
        assertEquals(1024, metrics.getBytesCompressed(), "Should have compressed 1024 bytes");
        assertEquals(3, metrics.getPendingMessages(), "Should have 3 pending messages");
        
        double ratio = metrics.getMessageRatio();
        assertTrue(ratio > 1.0, "Message ratio should be greater than 1.0");
        
        String metricsString = metrics.toString();
        assertNotNull(metricsString, "Metrics string should not be null");
        assertTrue(metricsString.contains("peers=5"), "Should contain peer count");
    }
    
    @Test
    @Order(7)
    @DisplayName("Test Vector Clock Merge Operations")
    void testVectorClockMerge() {
        VectorClock clock1 = VectorClock.create("peer1").increment("peer1");
        VectorClock clock2 = VectorClock.create("peer2").increment("peer2");
        
        VectorClock merged = clock1.merge(clock2);
        
        assertEquals(1, merged.getClock("peer1"), "Should have peer1 clock value");
        assertEquals(1, merged.getClock("peer2"), "Should have peer2 clock value");
        
        // Test with overlapping peers
        VectorClock clock3 = clock1.increment("peer1").increment("peer2");
        VectorClock merged2 = clock2.merge(clock3);
        
        assertEquals(2, merged2.getClock("peer1"), "Should take maximum peer1 value");
        assertEquals(1, merged2.getClock("peer2"), "Should take maximum peer2 value");
    }
}
