package dev.mars.p2pjava.discovery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the RegistryMessage class.
 * Tests message creation, gossip protocol functionality, and serialization.
 */
public class RegistryMessageTest {

    private ServiceInstance testService;
    private String senderId;
    private Map<String, Map<String, ServiceInstance>> testSnapshot;

    @BeforeEach
    void setUp() {
        senderId = "test-peer-1";
        testService = new ServiceInstance("test-service", "service-1", "localhost", 8080, 
                                        null, 1000L, senderId);
        
        // Create test registry snapshot
        testSnapshot = new HashMap<>();
        Map<String, ServiceInstance> serviceMap = new HashMap<>();
        serviceMap.put("service-1", testService);
        testSnapshot.put("test-service", serviceMap);
    }

    @Test
    void testCreateServiceRegister() {
        RegistryMessage message = RegistryMessage.createServiceRegister(senderId, testService);
        
        assertEquals(RegistryMessage.MessageType.SERVICE_REGISTER, message.getType());
        assertEquals(senderId, message.getSenderId());
        assertEquals(testService, message.getServiceInstance());
        assertEquals("test-service", message.getServiceType());
        assertEquals("service-1", message.getServiceId());
        assertNotNull(message.getMessageId());
        assertTrue(message.getTimestamp() > 0);
    }

    @Test
    void testCreateServiceDeregister() {
        RegistryMessage message = RegistryMessage.createServiceDeregister(senderId, "test-service", "service-1");
        
        assertEquals(RegistryMessage.MessageType.SERVICE_DEREGISTER, message.getType());
        assertEquals(senderId, message.getSenderId());
        assertEquals("test-service", message.getServiceType());
        assertEquals("service-1", message.getServiceId());
        assertNull(message.getServiceInstance());
        assertNotNull(message.getMessageId());
    }

    @Test
    void testCreateServiceUpdate() {
        RegistryMessage message = RegistryMessage.createServiceUpdate(senderId, testService);
        
        assertEquals(RegistryMessage.MessageType.SERVICE_UPDATE, message.getType());
        assertEquals(senderId, message.getSenderId());
        assertEquals(testService, message.getServiceInstance());
        assertEquals("test-service", message.getServiceType());
        assertEquals("service-1", message.getServiceId());
    }

    @Test
    void testCreateSyncRequest() {
        Set<String> requestedTypes = new HashSet<>(Arrays.asList("service-type-1", "service-type-2"));
        RegistryMessage message = RegistryMessage.createSyncRequest(senderId, requestedTypes);
        
        assertEquals(RegistryMessage.MessageType.SYNC_REQUEST, message.getType());
        assertEquals(senderId, message.getSenderId());
        assertEquals(requestedTypes, message.getRequestedServiceTypes());
        assertNull(message.getServiceInstance());
    }

    @Test
    void testCreateSyncResponse() {
        long syncVersion = 12345L;
        RegistryMessage message = RegistryMessage.createSyncResponse(senderId, testSnapshot, syncVersion);
        
        assertEquals(RegistryMessage.MessageType.SYNC_RESPONSE, message.getType());
        assertEquals(senderId, message.getSenderId());
        assertEquals(testSnapshot, message.getRegistrySnapshot());
        assertEquals(syncVersion, message.getSyncVersion());
    }

    @Test
    void testCreateHeartbeat() {
        RegistryMessage message = RegistryMessage.createHeartbeat(senderId, testService);
        
        assertEquals(RegistryMessage.MessageType.HEARTBEAT, message.getType());
        assertEquals(senderId, message.getSenderId());
        assertEquals(testService, message.getServiceInstance());
        assertEquals("test-service", message.getServiceType());
        assertEquals("service-1", message.getServiceId());
    }

    @Test
    void testCreateAntiEntropy() {
        RegistryMessage message = RegistryMessage.createAntiEntropy(senderId, testSnapshot);
        
        assertEquals(RegistryMessage.MessageType.ANTI_ENTROPY, message.getType());
        assertEquals(senderId, message.getSenderId());
        assertEquals(testSnapshot, message.getRegistrySnapshot());
    }

    @Test
    void testGossipProtocolMethods() {
        RegistryMessage message = RegistryMessage.createServiceRegister(senderId, testService);
        
        // Initial state
        assertEquals(0, message.getHopCount());
        assertEquals(5, message.getMaxHops()); // Default max hops
        assertTrue(message.canPropagate());
        assertFalse(message.hasVisited("peer-1"));
        assertTrue(message.getVisitedPeers().isEmpty());
        
        // Increment hop
        RegistryMessage propagated = message.incrementHop("peer-1");
        assertEquals(1, propagated.getHopCount());
        assertTrue(propagated.hasVisited("peer-1"));
        assertEquals(1, propagated.getVisitedPeers().size());
        assertTrue(propagated.canPropagate());
        
        // Original message should be unchanged
        assertEquals(0, message.getHopCount());
        assertFalse(message.hasVisited("peer-1"));
    }

    @Test
    void testMaxHopsReached() {
        RegistryMessage message = RegistryMessage.createServiceRegister(senderId, testService);
        message.setMaxHops(2);
        
        // First hop
        RegistryMessage hop1 = message.incrementHop("peer-1");
        assertTrue(hop1.canPropagate());
        
        // Second hop (at max)
        RegistryMessage hop2 = hop1.incrementHop("peer-2");
        assertFalse(hop2.canPropagate());
        assertEquals(2, hop2.getHopCount());
    }

    @Test
    void testSetMaxHops() {
        RegistryMessage message = RegistryMessage.createServiceRegister(senderId, testService);
        
        message.setMaxHops(10);
        assertEquals(10, message.getMaxHops());
        
        // Should not allow max hops less than 1
        message.setMaxHops(0);
        assertEquals(1, message.getMaxHops());
        
        message.setMaxHops(-5);
        assertEquals(1, message.getMaxHops());
    }

    @Test
    void testIsExpired() {
        RegistryMessage message = RegistryMessage.createServiceRegister(senderId, testService);

        // Should not be expired immediately with long TTL
        assertFalse(message.isExpired(10000));

        // Should be expired with negative TTL (any elapsed time > negative TTL)
        assertTrue(message.isExpired(-1));
    }

    @Test
    void testMessageEquality() {
        RegistryMessage message1 = RegistryMessage.createServiceRegister(senderId, testService);
        RegistryMessage message2 = RegistryMessage.createServiceRegister(senderId, testService);
        
        // Different messages should not be equal (different message IDs)
        assertNotEquals(message1, message2);
        assertNotEquals(message1.hashCode(), message2.hashCode());
        
        // Same message should be equal to itself
        assertEquals(message1, message1);
        assertEquals(message1.hashCode(), message1.hashCode());
    }

    @Test
    void testMessageToString() {
        RegistryMessage message = RegistryMessage.createServiceRegister(senderId, testService);
        String toString = message.toString();
        
        assertTrue(toString.contains("SERVICE_REGISTER"));
        assertTrue(toString.contains(senderId));
        assertTrue(toString.contains("test-service"));
        assertTrue(toString.contains("service-1"));
        assertTrue(toString.contains(message.getMessageId()));
    }

    @Test
    void testRegistrySnapshotImmutability() {
        RegistryMessage message = RegistryMessage.createSyncResponse(senderId, testSnapshot, 1000L);
        
        Map<String, Map<String, ServiceInstance>> retrievedSnapshot = message.getRegistrySnapshot();
        assertNotNull(retrievedSnapshot);
        
        // Modifying the retrieved snapshot should not affect the original
        retrievedSnapshot.clear();
        
        // Get snapshot again - should still contain original data
        Map<String, Map<String, ServiceInstance>> freshSnapshot = message.getRegistrySnapshot();
        assertFalse(freshSnapshot.isEmpty());
        assertTrue(freshSnapshot.containsKey("test-service"));
    }

    @Test
    void testRequestedServiceTypesImmutability() {
        Set<String> requestedTypes = new HashSet<>(Arrays.asList("type1", "type2"));
        RegistryMessage message = RegistryMessage.createSyncRequest(senderId, requestedTypes);
        
        Set<String> retrievedTypes = message.getRequestedServiceTypes();
        assertNotNull(retrievedTypes);
        assertEquals(2, retrievedTypes.size());
        
        // Modifying the retrieved set should not affect the original
        retrievedTypes.clear();
        
        // Get types again - should still contain original data
        Set<String> freshTypes = message.getRequestedServiceTypes();
        assertEquals(2, freshTypes.size());
        assertTrue(freshTypes.contains("type1"));
        assertTrue(freshTypes.contains("type2"));
    }

    @Test
    void testVisitedPeersImmutability() {
        RegistryMessage message = RegistryMessage.createServiceRegister(senderId, testService);
        RegistryMessage propagated = message.incrementHop("peer-1");
        
        Set<String> visitedPeers = propagated.getVisitedPeers();
        assertNotNull(visitedPeers);
        assertEquals(1, visitedPeers.size());
        
        // Modifying the retrieved set should not affect the original
        visitedPeers.clear();
        
        // Get visited peers again - should still contain original data
        Set<String> freshVisited = propagated.getVisitedPeers();
        assertEquals(1, freshVisited.size());
        assertTrue(freshVisited.contains("peer-1"));
    }

    @Test
    void testNullHandling() {
        // Test with null service instance - should throw exception
        assertThrows(NullPointerException.class, () -> {
            RegistryMessage.createServiceRegister(senderId, null);
        });

        // Test with null requested service types - should handle gracefully
        RegistryMessage syncRequest = RegistryMessage.createSyncRequest(senderId, null);
        assertNull(syncRequest.getRequestedServiceTypes());

        // Test with null registry snapshot - should handle gracefully
        RegistryMessage syncResponse = RegistryMessage.createSyncResponse(senderId, null, 1000L);
        assertNull(syncResponse.getRegistrySnapshot());
    }
}
