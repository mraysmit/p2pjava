package dev.mars.p2pjava.discovery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the InMemoryServiceRegistry class.
 * These tests verify the service registration, discovery, and deregistration functionality,
 * as well as thread safety and edge cases.
 */
public class InMemoryServiceRegistryTest {

    private ServiceRegistry registry;
    private static final String TEST_SERVICE_TYPE = "test-service";
    private static final String TEST_SERVICE_ID = "test-service-1";
    private static final String TEST_HOST = "localhost";
    private static final int TEST_PORT = 8080;

    @BeforeEach
    void setUp() {
        // Get a fresh instance for each test
        registry = InMemoryServiceRegistry.getInstance();
        
        // Clear any existing registrations
        List<ServiceInstance> services = registry.findServiceInstances(TEST_SERVICE_TYPE);
        if (services != null) {
            for (ServiceInstance service : services) {
                registry.deregisterService(TEST_SERVICE_TYPE, service.getId());
            }
        }
    }

    @Test
    void testRegisterService() {
        // Test basic service registration
        boolean registered = registry.registerService(TEST_SERVICE_TYPE, TEST_SERVICE_ID, TEST_HOST, TEST_PORT, null);
        assertTrue(registered, "Service registration should succeed");
        
        // Verify the service was registered
        List<ServiceInstance> services = registry.findServiceInstances(TEST_SERVICE_TYPE);
        assertNotNull(services, "Service list should not be null");
        assertEquals(1, services.size(), "Should have one registered service");
        
        ServiceInstance service = services.get(0);
        assertEquals(TEST_SERVICE_ID, service.getId(), "Service ID should match");
        assertEquals(TEST_HOST, service.getHost(), "Service host should match");
        assertEquals(TEST_PORT, service.getPort(), "Service port should match");
    }

    @Test
    void testRegisterServiceWithMetadata() {
        // Test service registration with metadata
        Map<String, String> metadata = new HashMap<>();
        metadata.put("version", "1.0");
        metadata.put("environment", "test");
        
        boolean registered = registry.registerService(TEST_SERVICE_TYPE, TEST_SERVICE_ID, TEST_HOST, TEST_PORT, metadata);
        assertTrue(registered, "Service registration with metadata should succeed");
        
        // Verify the service was registered with metadata
        List<ServiceInstance> services = registry.findServiceInstances(TEST_SERVICE_TYPE);
        assertNotNull(services, "Service list should not be null");
        assertEquals(1, services.size(), "Should have one registered service");
        
        ServiceInstance service = services.get(0);
        Map<String, String> retrievedMetadata = service.getMetadata();
        assertNotNull(retrievedMetadata, "Metadata should not be null");
        assertEquals("1.0", retrievedMetadata.get("version"), "Version metadata should match");
        assertEquals("test", retrievedMetadata.get("environment"), "Environment metadata should match");
    }

    @Test
    void testRegisterDuplicateService() {
        // Register a service
        boolean registered = registry.registerService(TEST_SERVICE_TYPE, TEST_SERVICE_ID, TEST_HOST, TEST_PORT, null);
        assertTrue(registered, "First service registration should succeed");
        
        // Try to register the same service again
        boolean registeredAgain = registry.registerService(TEST_SERVICE_TYPE, TEST_SERVICE_ID, TEST_HOST, TEST_PORT, null);
        assertFalse(registeredAgain, "Duplicate service registration should fail");
        
        // Verify only one instance exists
        List<ServiceInstance> services = registry.findServiceInstances(TEST_SERVICE_TYPE);
        assertEquals(1, services.size(), "Should still have only one registered service");
    }

    @Test
    void testDeregisterService() {
        // Register a service
        registry.registerService(TEST_SERVICE_TYPE, TEST_SERVICE_ID, TEST_HOST, TEST_PORT, null);
        
        // Deregister the service
        boolean deregistered = registry.deregisterService(TEST_SERVICE_TYPE, TEST_SERVICE_ID);
        assertTrue(deregistered, "Service deregistration should succeed");
        
        // Verify the service was deregistered
        List<ServiceInstance> services = registry.findServiceInstances(TEST_SERVICE_TYPE);
        assertTrue(services == null || services.isEmpty(), "Service list should be empty after deregistration");
    }

    @Test
    void testDeregisterNonexistentService() {
        // Try to deregister a service that doesn't exist
        boolean deregistered = registry.deregisterService(TEST_SERVICE_TYPE, "nonexistent-service");
        assertFalse(deregistered, "Deregistering nonexistent service should fail");
    }

    @Test
    void testFindServiceInstances() {
        // Register multiple services of different types
        registry.registerService(TEST_SERVICE_TYPE, TEST_SERVICE_ID, TEST_HOST, TEST_PORT, null);
        registry.registerService(TEST_SERVICE_TYPE, "test-service-2", TEST_HOST, TEST_PORT + 1, null);
        registry.registerService("another-service", "another-1", TEST_HOST, 9000, null);
        
        // Find services of the test type
        List<ServiceInstance> testServices = registry.findServiceInstances(TEST_SERVICE_TYPE);
        assertNotNull(testServices, "Test service list should not be null");
        assertEquals(2, testServices.size(), "Should have two test services");
        
        // Find services of the other type
        List<ServiceInstance> otherServices = registry.findServiceInstances("another-service");
        assertNotNull(otherServices, "Other service list should not be null");
        assertEquals(1, otherServices.size(), "Should have one other service");
        
        // Find services of a nonexistent type
        List<ServiceInstance> nonexistentServices = registry.findServiceInstances("nonexistent-service");
        assertTrue(nonexistentServices == null || nonexistentServices.isEmpty(), 
                "Nonexistent service list should be empty");
    }

    @Test
    void testFindServiceInstanceById() {
        // Register a service
        registry.registerService(TEST_SERVICE_TYPE, TEST_SERVICE_ID, TEST_HOST, TEST_PORT, null);
        
        // Find the service by ID
        ServiceInstance service = registry.findServiceInstanceById(TEST_SERVICE_TYPE, TEST_SERVICE_ID);
        assertNotNull(service, "Service should be found by ID");
        assertEquals(TEST_SERVICE_ID, service.getId(), "Service ID should match");
        
        // Try to find a nonexistent service
        ServiceInstance nonexistentService = registry.findServiceInstanceById(TEST_SERVICE_TYPE, "nonexistent-service");
        assertNull(nonexistentService, "Nonexistent service should not be found");
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        // Test concurrent registration and deregistration
        final int THREAD_COUNT = 10;
        final int OPERATIONS_PER_THREAD = 100;
        final ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        final CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        final AtomicInteger successfulRegistrations = new AtomicInteger(0);
        final AtomicInteger successfulDeregistrations = new AtomicInteger(0);
        
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                        String serviceId = "service-" + threadId + "-" + j;
                        
                        // Register a service
                        boolean registered = registry.registerService(
                                TEST_SERVICE_TYPE, serviceId, TEST_HOST, TEST_PORT + j, null);
                        if (registered) {
                            successfulRegistrations.incrementAndGet();
                        }
                        
                        // Small delay to increase chance of concurrent access
                        Thread.sleep(1);
                        
                        // Deregister the service
                        boolean deregistered = registry.deregisterService(TEST_SERVICE_TYPE, serviceId);
                        if (deregistered) {
                            successfulDeregistrations.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Wait for all threads to complete
        assertTrue(latch.await(30, TimeUnit.SECONDS), "All threads should complete within timeout");
        executor.shutdown();
        
        // Verify the results
        assertEquals(successfulRegistrations.get(), successfulDeregistrations.get(), 
                "All successfully registered services should be successfully deregistered");
        
        // Verify no services remain registered
        List<ServiceInstance> remainingServices = registry.findServiceInstances(TEST_SERVICE_TYPE);
        assertTrue(remainingServices == null || remainingServices.isEmpty(), 
                "No services should remain registered after test");
        
        System.out.println("Concurrent test results - Successful registrations: " + 
                successfulRegistrations.get() + ", Successful deregistrations: " + 
                successfulDeregistrations.get());
    }

    @Test
    void testSingletonBehavior() {
        // Test that getInstance() always returns the same instance
        ServiceRegistry instance1 = InMemoryServiceRegistry.getInstance();
        ServiceRegistry instance2 = InMemoryServiceRegistry.getInstance();
        
        assertSame(instance1, instance2, "getInstance() should always return the same instance");
        
        // Register a service using one instance
        instance1.registerService(TEST_SERVICE_TYPE, TEST_SERVICE_ID, TEST_HOST, TEST_PORT, null);
        
        // Verify it's visible through the other instance
        List<ServiceInstance> services = instance2.findServiceInstances(TEST_SERVICE_TYPE);
        assertNotNull(services, "Service registered through one instance should be visible through another");
        assertEquals(1, services.size(), "Should have one registered service");
    }
}