package dev.mars.p2pjava.discovery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the ConflictResolver class.
 * Tests various conflict resolution strategies and edge cases.
 */
public class ConflictResolverTest {

    private ConflictResolver resolver;
    private ServiceInstance instance1;
    private ServiceInstance instance2;
    private ServiceInstance instance3;

    @BeforeEach
    void setUp() {
        // Create test service instances with different versions and origins
        instance1 = new ServiceInstance("test-service", "service-1", "host1", 8080, 
                                       null, 1000L, "peer1");
        instance2 = new ServiceInstance("test-service", "service-1", "host2", 8081, 
                                       null, 2000L, "peer2");
        instance3 = new ServiceInstance("test-service", "service-1", "host3", 8082, 
                                       null, 1500L, "peer3");
        
        // Set different health statuses
        instance1.setHealthy(true);
        instance2.setHealthy(false);
        instance3.setHealthy(true);
    }

    @Test
    void testLastWriteWinsStrategy() {
        resolver = new ConflictResolver(ConflictResolver.ResolutionStrategy.LAST_WRITE_WINS);
        
        List<ServiceInstance> conflicting = Arrays.asList(instance1, instance2, instance3);
        ServiceInstance resolved = resolver.resolveConflict(conflicting);
        
        // Should select instance2 as it has the highest version (2000L)
        assertEquals(instance2, resolved);
        assertEquals(2000L, resolved.getVersion());
    }

    @Test
    void testOriginPriorityStrategy() {
        Set<String> priorityPeers = new HashSet<>(Arrays.asList("peer1", "peer3"));
        resolver = new ConflictResolver(ConflictResolver.ResolutionStrategy.ORIGIN_PRIORITY, priorityPeers);
        
        List<ServiceInstance> conflicting = Arrays.asList(instance1, instance2, instance3);
        ServiceInstance resolved = resolver.resolveConflict(conflicting);
        
        // Should select instance3 as it's from a priority peer and has higher version than instance1
        assertEquals(instance3, resolved);
        assertEquals("peer3", resolved.getOriginPeerId());
    }

    @Test
    void testOriginPriorityFallbackToLastWrite() {
        Set<String> priorityPeers = new HashSet<>(Arrays.asList("peer4", "peer5")); // No matching peers
        resolver = new ConflictResolver(ConflictResolver.ResolutionStrategy.ORIGIN_PRIORITY, priorityPeers);
        
        List<ServiceInstance> conflicting = Arrays.asList(instance1, instance2, instance3);
        ServiceInstance resolved = resolver.resolveConflict(conflicting);
        
        // Should fall back to last write wins and select instance2
        assertEquals(instance2, resolved);
        assertEquals(2000L, resolved.getVersion());
    }

    @Test
    void testHealthPriorityStrategy() {
        resolver = new ConflictResolver(ConflictResolver.ResolutionStrategy.HEALTH_PRIORITY);
        
        List<ServiceInstance> conflicting = Arrays.asList(instance1, instance2, instance3);
        ServiceInstance resolved = resolver.resolveConflict(conflicting);
        
        // Should select instance3 as it's healthy and has higher version than instance1
        assertEquals(instance3, resolved);
        assertTrue(resolved.isHealthy());
    }

    @Test
    void testHealthPriorityWithOnlyUnhealthyInstances() {
        // Make all instances unhealthy
        instance1.setHealthy(false);
        instance2.setHealthy(false);
        instance3.setHealthy(false);
        
        resolver = new ConflictResolver(ConflictResolver.ResolutionStrategy.HEALTH_PRIORITY);
        
        List<ServiceInstance> conflicting = Arrays.asList(instance1, instance2, instance3);
        ServiceInstance resolved = resolver.resolveConflict(conflicting);
        
        // Should fall back to last write wins and select instance2
        assertEquals(instance2, resolved);
        assertFalse(resolved.isHealthy());
    }

    @Test
    void testCompositeStrategy() {
        Set<String> priorityPeers = new HashSet<>(Arrays.asList("peer1"));
        resolver = new ConflictResolver(ConflictResolver.ResolutionStrategy.COMPOSITE, priorityPeers);
        
        List<ServiceInstance> conflicting = Arrays.asList(instance1, instance2, instance3);
        ServiceInstance resolved = resolver.resolveConflict(conflicting);
        
        // Should select instance1: it's healthy and from a priority peer
        assertEquals(instance1, resolved);
        assertTrue(resolved.isHealthy());
        assertEquals("peer1", resolved.getOriginPeerId());
    }

    @Test
    void testCompositeStrategyWithUnhealthyPriorityPeer() {
        // Make priority peer instance unhealthy
        instance1.setHealthy(false);
        
        Set<String> priorityPeers = new HashSet<>(Arrays.asList("peer1"));
        resolver = new ConflictResolver(ConflictResolver.ResolutionStrategy.COMPOSITE, priorityPeers);
        
        List<ServiceInstance> conflicting = Arrays.asList(instance1, instance2, instance3);
        ServiceInstance resolved = resolver.resolveConflict(conflicting);
        
        // Should select instance3: it's healthy (instance2 is unhealthy)
        assertEquals(instance3, resolved);
        assertTrue(resolved.isHealthy());
    }

    @Test
    void testResolveConflictWithSingleInstance() {
        resolver = new ConflictResolver(ConflictResolver.ResolutionStrategy.LAST_WRITE_WINS);
        
        List<ServiceInstance> single = Arrays.asList(instance1);
        ServiceInstance resolved = resolver.resolveConflict(single);
        
        assertEquals(instance1, resolved);
    }

    @Test
    void testResolveConflictWithEmptyList() {
        resolver = new ConflictResolver(ConflictResolver.ResolutionStrategy.LAST_WRITE_WINS);
        
        List<ServiceInstance> empty = Collections.emptyList();
        ServiceInstance resolved = resolver.resolveConflict(empty);
        
        assertNull(resolved);
    }

    @Test
    void testResolveConflictWithNullList() {
        resolver = new ConflictResolver(ConflictResolver.ResolutionStrategy.LAST_WRITE_WINS);
        
        ServiceInstance resolved = resolver.resolveConflict(null);
        
        assertNull(resolved);
    }

    @Test
    void testIsConflict() {
        resolver = new ConflictResolver(ConflictResolver.ResolutionStrategy.LAST_WRITE_WINS);
        
        // Same service ID but different versions should be a conflict
        assertTrue(resolver.isConflict(instance1, instance2));
        
        // Same service ID but different hosts should be a conflict
        ServiceInstance sameVersion = new ServiceInstance("test-service", "service-1", "host1", 8080, 
                                                          null, 1000L, "peer1");
        ServiceInstance differentHost = new ServiceInstance("test-service", "service-1", "different-host", 8080, 
                                                           null, 1000L, "peer1");
        assertTrue(resolver.isConflict(sameVersion, differentHost));
        
        // Different service IDs should not be a conflict
        ServiceInstance differentId = new ServiceInstance("test-service", "different-id", "host1", 8080, 
                                                         null, 1000L, "peer1");
        assertFalse(resolver.isConflict(instance1, differentId));
        
        // Null instances should not be a conflict
        assertFalse(resolver.isConflict(null, instance1));
        assertFalse(resolver.isConflict(instance1, null));
        assertFalse(resolver.isConflict(null, null));
    }

    @Test
    void testMergeMetadata() {
        resolver = new ConflictResolver(ConflictResolver.ResolutionStrategy.LAST_WRITE_WINS);
        
        // Create instances with different metadata
        Map<String, String> metadata1 = new HashMap<>();
        metadata1.put("version", "1.0");
        metadata1.put("env", "test");
        
        Map<String, String> metadata2 = new HashMap<>();
        metadata2.put("version", "2.0");
        metadata2.put("region", "us-east");
        
        ServiceInstance withMetadata1 = new ServiceInstance("test-service", "service-1", "host1", 8080, 
                                                           metadata1, 1000L, "peer1");
        ServiceInstance withMetadata2 = new ServiceInstance("test-service", "service-1", "host2", 8081, 
                                                           metadata2, 2000L, "peer2");
        
        List<ServiceInstance> instances = Arrays.asList(withMetadata1, withMetadata2);
        Map<String, String> merged = resolver.mergeMetadata(instances);
        
        // Should have all keys, with newer values taking precedence
        assertEquals("2.0", merged.get("version")); // Newer version
        assertEquals("test", merged.get("env")); // Only in older instance
        assertEquals("us-east", merged.get("region")); // Only in newer instance
    }

    @Test
    void testPriorityPeerManagement() {
        resolver = new ConflictResolver(ConflictResolver.ResolutionStrategy.ORIGIN_PRIORITY);
        
        // Initially no priority peers
        assertTrue(resolver.getPriorityPeers().isEmpty());
        
        // Add priority peers
        resolver.addPriorityPeer("peer1");
        resolver.addPriorityPeer("peer2");
        
        Set<String> priorityPeers = resolver.getPriorityPeers();
        assertEquals(2, priorityPeers.size());
        assertTrue(priorityPeers.contains("peer1"));
        assertTrue(priorityPeers.contains("peer2"));
        
        // Remove priority peer
        resolver.removePriorityPeer("peer1");
        priorityPeers = resolver.getPriorityPeers();
        assertEquals(1, priorityPeers.size());
        assertTrue(priorityPeers.contains("peer2"));
        assertFalse(priorityPeers.contains("peer1"));
    }

    @Test
    void testGetStrategy() {
        resolver = new ConflictResolver(ConflictResolver.ResolutionStrategy.HEALTH_PRIORITY);
        assertEquals(ConflictResolver.ResolutionStrategy.HEALTH_PRIORITY, resolver.getStrategy());
        
        resolver = new ConflictResolver(ConflictResolver.ResolutionStrategy.COMPOSITE);
        assertEquals(ConflictResolver.ResolutionStrategy.COMPOSITE, resolver.getStrategy());
    }
}
