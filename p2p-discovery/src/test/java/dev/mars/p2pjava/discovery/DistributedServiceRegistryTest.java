package dev.mars.p2pjava.discovery;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the DistributedServiceRegistry class.
 * Tests basic functionality and distributed behavior.
 */
public class DistributedServiceRegistryTest {

    private DistributedServiceRegistry registry1;
    private DistributedServiceRegistry registry2;
    private static final String TEST_SERVICE_TYPE = "test-service";
    private static final String TEST_SERVICE_ID = "test-service-1";
    private static final String TEST_HOST = "localhost";
    private static final int TEST_PORT = 8080;

    @BeforeEach
    void setUp() {
        // Create two registry instances with different gossip ports
        registry1 = new DistributedServiceRegistry("peer1", 6010, Collections.emptySet());
        registry2 = new DistributedServiceRegistry("peer2", 6011, Collections.emptySet());
    }

    @AfterEach
    void tearDown() {
        if (registry1 != null && registry1.isRunning()) {
            registry1.stop();
        }
        if (registry2 != null && registry2.isRunning()) {
            registry2.stop();
        }
    }

    @Test
    void testBasicServiceRegistration() {
        registry1.start();
        
        assertTrue(registry1.isRunning());
        
        // Register a service
        boolean registered = registry1.registerService(TEST_SERVICE_TYPE, TEST_SERVICE_ID, TEST_HOST, TEST_PORT, null);
        assertTrue(registered, "Service registration should succeed");
        
        // Discover the service
        List<ServiceInstance> services = registry1.discoverServices(TEST_SERVICE_TYPE);
        assertNotNull(services);
        assertEquals(1, services.size());
        
        ServiceInstance service = services.get(0);
        assertEquals(TEST_SERVICE_ID, service.getServiceId());
        assertEquals(TEST_HOST, service.getHost());
        assertEquals(TEST_PORT, service.getPort());
        assertEquals("peer1", service.getOriginPeerId());
    }

    @Test
    void testServiceDeregistration() {
        registry1.start();
        
        // Register a service
        registry1.registerService(TEST_SERVICE_TYPE, TEST_SERVICE_ID, TEST_HOST, TEST_PORT, null);
        
        // Verify it's registered
        List<ServiceInstance> services = registry1.discoverServices(TEST_SERVICE_TYPE);
        assertEquals(1, services.size());
        
        // Deregister the service
        boolean deregistered = registry1.deregisterService(TEST_SERVICE_TYPE, TEST_SERVICE_ID);
        assertTrue(deregistered, "Service deregistration should succeed");
        
        // Verify it's gone
        services = registry1.discoverServices(TEST_SERVICE_TYPE);
        assertTrue(services.isEmpty());
    }

    @Test
    void testGetService() {
        registry1.start();
        
        // Register a service
        registry1.registerService(TEST_SERVICE_TYPE, TEST_SERVICE_ID, TEST_HOST, TEST_PORT, null);
        
        // Get the specific service
        ServiceInstance service = registry1.getService(TEST_SERVICE_TYPE, TEST_SERVICE_ID);
        assertNotNull(service);
        assertEquals(TEST_SERVICE_ID, service.getServiceId());
        assertEquals(TEST_HOST, service.getHost());
        assertEquals(TEST_PORT, service.getPort());
        
        // Try to get non-existent service
        ServiceInstance nonExistent = registry1.getService(TEST_SERVICE_TYPE, "non-existent");
        assertNull(nonExistent);
    }

    @Test
    void testRegistryVersionIncrement() {
        registry1.start();
        
        long initialVersion = registry1.getRegistryVersion();
        
        // Register a service - should increment version
        registry1.registerService(TEST_SERVICE_TYPE, TEST_SERVICE_ID, TEST_HOST, TEST_PORT, null);
        assertTrue(registry1.getRegistryVersion() > initialVersion);
        
        long afterRegister = registry1.getRegistryVersion();
        
        // Deregister the service - should increment version again
        registry1.deregisterService(TEST_SERVICE_TYPE, TEST_SERVICE_ID);
        assertTrue(registry1.getRegistryVersion() > afterRegister);
    }

    @Test
    void testRegistrySnapshot() {
        registry1.start();
        
        // Register multiple services
        registry1.registerService(TEST_SERVICE_TYPE, TEST_SERVICE_ID, TEST_HOST, TEST_PORT, null);
        registry1.registerService(TEST_SERVICE_TYPE, "service-2", TEST_HOST, TEST_PORT + 1, null);
        registry1.registerService("another-service", "service-3", TEST_HOST, TEST_PORT + 2, null);
        
        Map<String, Map<String, ServiceInstance>> snapshot = registry1.getRegistrySnapshot();
        
        assertNotNull(snapshot);
        assertEquals(2, snapshot.size()); // Two service types
        assertTrue(snapshot.containsKey(TEST_SERVICE_TYPE));
        assertTrue(snapshot.containsKey("another-service"));
        
        Map<String, ServiceInstance> testServices = snapshot.get(TEST_SERVICE_TYPE);
        assertEquals(2, testServices.size());
        assertTrue(testServices.containsKey(TEST_SERVICE_ID));
        assertTrue(testServices.containsKey("service-2"));
        
        Map<String, ServiceInstance> anotherServices = snapshot.get("another-service");
        assertEquals(1, anotherServices.size());
        assertTrue(anotherServices.containsKey("service-3"));
    }

    @Test
    void testPeerManagement() {
        registry1.start();
        
        // Initially no known peers
        Set<String> knownPeers = registry1.getKnownPeers();
        assertTrue(knownPeers.isEmpty());
        
        // Add a peer
        registry1.addPeer("peer2:6011");
        knownPeers = registry1.getKnownPeers();
        assertEquals(1, knownPeers.size());
        assertTrue(knownPeers.contains("peer2:6011"));
        
        // Remove the peer
        registry1.removePeer("peer2:6011");
        knownPeers = registry1.getKnownPeers();
        assertTrue(knownPeers.isEmpty());
    }

    @Test
    void testStatistics() {
        registry1.start();
        
        Map<String, Object> stats = registry1.getStatistics();
        
        assertNotNull(stats);
        assertEquals("peer1", stats.get("peerId"));
        assertEquals(true, stats.get("running"));
        assertTrue(stats.containsKey("registryVersion"));
        assertTrue(stats.containsKey("serviceTypes"));
        assertTrue(stats.containsKey("totalServices"));
        
        // Register a service and check stats update
        registry1.registerService(TEST_SERVICE_TYPE, TEST_SERVICE_ID, TEST_HOST, TEST_PORT, null);
        
        stats = registry1.getStatistics();
        assertEquals(1, stats.get("serviceTypes"));
        assertEquals(1, stats.get("totalServices"));
    }

    @Test
    void testInvalidParameters() {
        registry1.start();
        
        // Test invalid service registration
        assertFalse(registry1.registerService(null, TEST_SERVICE_ID, TEST_HOST, TEST_PORT, null));
        assertFalse(registry1.registerService(TEST_SERVICE_TYPE, null, TEST_HOST, TEST_PORT, null));
        assertFalse(registry1.registerService(TEST_SERVICE_TYPE, TEST_SERVICE_ID, null, TEST_PORT, null));
        assertFalse(registry1.registerService(TEST_SERVICE_TYPE, TEST_SERVICE_ID, "", TEST_PORT, null));
        assertFalse(registry1.registerService(TEST_SERVICE_TYPE, TEST_SERVICE_ID, TEST_HOST, -1, null));
        assertFalse(registry1.registerService(TEST_SERVICE_TYPE, TEST_SERVICE_ID, TEST_HOST, 70000, null));
        
        // Test invalid service deregistration
        assertFalse(registry1.deregisterService(null, TEST_SERVICE_ID));
        assertFalse(registry1.deregisterService(TEST_SERVICE_TYPE, null));
        assertFalse(registry1.deregisterService("", TEST_SERVICE_ID));
        assertFalse(registry1.deregisterService(TEST_SERVICE_TYPE, ""));
        
        // Test invalid service discovery
        assertTrue(registry1.discoverServices(null).isEmpty());
        assertTrue(registry1.discoverServices("").isEmpty());
        
        // Test invalid service retrieval
        assertNull(registry1.getService(null, TEST_SERVICE_ID));
        assertNull(registry1.getService(TEST_SERVICE_TYPE, null));
        assertNull(registry1.getService("", TEST_SERVICE_ID));
        assertNull(registry1.getService(TEST_SERVICE_TYPE, ""));
    }

    @Test
    void testOperationsWhenNotRunning() {
        // Registry not started
        assertFalse(registry1.isRunning());
        
        // Operations should fail when not running
        assertFalse(registry1.registerService(TEST_SERVICE_TYPE, TEST_SERVICE_ID, TEST_HOST, TEST_PORT, null));
        assertFalse(registry1.deregisterService(TEST_SERVICE_TYPE, TEST_SERVICE_ID));
        assertTrue(registry1.discoverServices(TEST_SERVICE_TYPE).isEmpty());
        assertNull(registry1.getService(TEST_SERVICE_TYPE, TEST_SERVICE_ID));
    }

    @Test
    void testStartStop() {
        assertFalse(registry1.isRunning());
        
        // Start the registry
        registry1.start();
        assertTrue(registry1.isRunning());
        
        // Starting again should not cause issues
        registry1.start(); // Should log warning but not fail
        assertTrue(registry1.isRunning());
        
        // Stop the registry
        registry1.stop();
        assertFalse(registry1.isRunning());
        
        // Stopping again should not cause issues
        registry1.stop(); // Should return immediately
        assertFalse(registry1.isRunning());
    }

    @Test
    void testServiceWithMetadata() {
        registry1.start();
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("version", "1.0");
        metadata.put("environment", "test");
        
        // Register service with metadata
        boolean registered = registry1.registerService(TEST_SERVICE_TYPE, TEST_SERVICE_ID, TEST_HOST, TEST_PORT, metadata);
        assertTrue(registered);
        
        // Retrieve and verify metadata
        ServiceInstance service = registry1.getService(TEST_SERVICE_TYPE, TEST_SERVICE_ID);
        assertNotNull(service);
        
        Map<String, String> retrievedMetadata = service.getMetadata();
        assertEquals("1.0", retrievedMetadata.get("version"));
        assertEquals("test", retrievedMetadata.get("environment"));
    }

    @Test
    void testMultipleServiceTypes() {
        registry1.start();
        
        // Register services of different types
        registry1.registerService("type1", "service1", TEST_HOST, 8080, null);
        registry1.registerService("type1", "service2", TEST_HOST, 8081, null);
        registry1.registerService("type2", "service3", TEST_HOST, 8082, null);
        
        // Discover services by type
        List<ServiceInstance> type1Services = registry1.discoverServices("type1");
        assertEquals(2, type1Services.size());
        
        List<ServiceInstance> type2Services = registry1.discoverServices("type2");
        assertEquals(1, type2Services.size());
        
        List<ServiceInstance> nonExistentServices = registry1.discoverServices("type3");
        assertTrue(nonExistentServices.isEmpty());
    }
}
