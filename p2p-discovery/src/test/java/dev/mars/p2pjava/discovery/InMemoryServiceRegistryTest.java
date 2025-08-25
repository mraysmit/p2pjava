package dev.mars.p2pjava.discovery;

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


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

// For casting registry to InMemoryServiceRegistry when needed
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Unit tests for the InMemoryServiceRegistry class.
 * These tests verify the service registration, discovery, and deregistration functionality,
 * as well as thread safety and edge cases.
 */
public class InMemoryServiceRegistryTest {

    private InMemoryServiceRegistry registry;
    private static final String TEST_SERVICE_TYPE = "test-service";
    private static final String TEST_SERVICE_ID = "test-service-1";
    private static final String TEST_HOST = "localhost";
    private static final int TEST_PORT = 8080;

    @BeforeEach
    void setUp() {
        // Reset the singleton instance to ensure a clean state for each test
        InMemoryServiceRegistry.reset();

        // Get a fresh instance and start it
        registry = InMemoryServiceRegistry.getInstance();
        registry.start();
    }

    @Test
    void testRegisterService() {
        // Test basic service registration
        boolean registered = registry.registerService(TEST_SERVICE_TYPE, TEST_SERVICE_ID, TEST_HOST, TEST_PORT, null);
        assertTrue(registered, "Service registration should succeed");

        // Verify the service was registered
        List<ServiceInstance> services = registry.discoverServices(TEST_SERVICE_TYPE);
        assertNotNull(services, "Service list should not be null");
        assertEquals(1, services.size(), "Should have one registered service");

        ServiceInstance service = services.get(0);
        assertEquals(TEST_SERVICE_ID, service.getServiceId(), "Service ID should match");
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
        List<ServiceInstance> services = registry.discoverServices(TEST_SERVICE_TYPE);
        assertNotNull(services, "Service list should not be null");
        assertEquals(1, services.size(), "Should have one registered service");

        ServiceInstance service = services.get(0);
        Map<String, String> retrievedMetadata = service.getMetadata();
        assertNotNull(retrievedMetadata, "Metadata should not be null");
        assertEquals("1.0", retrievedMetadata.get("version"), "Version metadata should match");
        assertEquals("test", retrievedMetadata.get("environment"), "Environment metadata should match");
    }

    @Test
    void testRegisterServiceWithNullMetadata() {
        // Test service registration with null metadata
        boolean registered = registry.registerService(TEST_SERVICE_TYPE, TEST_SERVICE_ID, TEST_HOST, TEST_PORT, null);
        assertTrue(registered, "Service registration with null metadata should succeed");

        // Verify the service was registered with empty metadata
        List<ServiceInstance> services = registry.discoverServices(TEST_SERVICE_TYPE);
        assertNotNull(services, "Service list should not be null");
        assertEquals(1, services.size(), "Should have one registered service");

        ServiceInstance service = services.get(0);
        Map<String, String> retrievedMetadata = service.getMetadata();
        assertNotNull(retrievedMetadata, "Metadata should not be null even when registered with null");
        assertTrue(retrievedMetadata.isEmpty(), "Metadata should be empty when registered with null");
    }

    @Test
    void testRegisterServiceWithEmptyMetadata() {
        // Test service registration with empty metadata
        Map<String, String> metadata = new HashMap<>();

        boolean registered = registry.registerService(TEST_SERVICE_TYPE, TEST_SERVICE_ID, TEST_HOST, TEST_PORT, metadata);
        assertTrue(registered, "Service registration with empty metadata should succeed");

        // Verify the service was registered with empty metadata
        List<ServiceInstance> services = registry.discoverServices(TEST_SERVICE_TYPE);
        assertNotNull(services, "Service list should not be null");
        assertEquals(1, services.size(), "Should have one registered service");

        ServiceInstance service = services.get(0);
        Map<String, String> retrievedMetadata = service.getMetadata();
        assertNotNull(retrievedMetadata, "Metadata should not be null");
        assertTrue(retrievedMetadata.isEmpty(), "Metadata should be empty");
    }

    @Test
    void testRegisterServiceWithSpecialCharactersInMetadata() {
        // Test service registration with metadata containing special characters
        Map<String, String> metadata = new HashMap<>();
        metadata.put("special", "!@#$%^&*()_+{}[]|\\:;\"'<>,.?/");
        metadata.put("unicode", "こんにちは世界");

        boolean registered = registry.registerService(TEST_SERVICE_TYPE, TEST_SERVICE_ID, TEST_HOST, TEST_PORT, metadata);
        assertTrue(registered, "Service registration with special characters in metadata should succeed");

        // Verify the service was registered with the special metadata
        List<ServiceInstance> services = registry.discoverServices(TEST_SERVICE_TYPE);
        assertNotNull(services, "Service list should not be null");
        assertEquals(1, services.size(), "Should have one registered service");

        ServiceInstance service = services.get(0);
        Map<String, String> retrievedMetadata = service.getMetadata();
        assertNotNull(retrievedMetadata, "Metadata should not be null");
        assertEquals("!@#$%^&*()_+{}[]|\\:;\"'<>,.?/", retrievedMetadata.get("special"), "Special characters metadata should match");
        assertEquals("こんにちは世界", retrievedMetadata.get("unicode"), "Unicode metadata should match");
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
        List<ServiceInstance> services = registry.discoverServices(TEST_SERVICE_TYPE);
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
        assertTrue(services.isEmpty(), "Service list should be empty after deregistration");
    }

    @Test
    void testDeregisterNonexistentService() {
        // Try to deregister a service that doesn't exist
        boolean deregistered = registry.deregisterService(TEST_SERVICE_TYPE, "nonexistent-service");
        assertFalse(deregistered, "Deregistering nonexistent service should fail");
    }

    @Test
    void testRegisterServiceWithInvalidInputs() {
        // Test with null serviceType
        boolean registered = registry.registerService(null, TEST_SERVICE_ID, TEST_HOST, TEST_PORT, null);
        assertFalse(registered, "Registration with null serviceType should fail");

        // Test with null serviceId
        registered = registry.registerService(TEST_SERVICE_TYPE, null, TEST_HOST, TEST_PORT, null);
        assertFalse(registered, "Registration with null serviceId should fail");

        // Test with null host
        registered = registry.registerService(TEST_SERVICE_TYPE, TEST_SERVICE_ID, null, TEST_PORT, null);
        assertFalse(registered, "Registration with null host should fail");

        // Test with negative port
        registered = registry.registerService(TEST_SERVICE_TYPE, TEST_SERVICE_ID, TEST_HOST, -1, null);
        assertFalse(registered, "Registration with negative port should fail");

        // Test with empty serviceType
        registered = registry.registerService("", TEST_SERVICE_ID, TEST_HOST, TEST_PORT, null);
        assertFalse(registered, "Registration with empty serviceType should fail");

        // Test with empty serviceId
        registered = registry.registerService(TEST_SERVICE_TYPE, "", TEST_HOST, TEST_PORT, null);
        assertFalse(registered, "Registration with empty serviceId should fail");

        // Test with empty host
        registered = registry.registerService(TEST_SERVICE_TYPE, TEST_SERVICE_ID, "", TEST_PORT, null);
        assertFalse(registered, "Registration with empty host should fail");
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
        assertTrue(nonexistentServices.isEmpty(), "Nonexistent service list should be empty");
    }

    @Test
    void testFindServiceInstanceById() {
        // Register a service
        registry.registerService(TEST_SERVICE_TYPE, TEST_SERVICE_ID, TEST_HOST, TEST_PORT, null);

        // Find the service by ID
        ServiceInstance service = registry.findServiceInstanceById(TEST_SERVICE_TYPE, TEST_SERVICE_ID);
        assertNotNull(service, "Service should be found by ID");
        assertEquals(TEST_SERVICE_ID, service.getServiceId(), "Service ID should match");

        // Try to find a nonexistent service
        ServiceInstance nonexistentService = registry.findServiceInstanceById(TEST_SERVICE_TYPE, "nonexistent-service");
        assertNull(nonexistentService, "Nonexistent service should not be found");
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        // Test concurrent registration and deregistration with increased contention
        final int THREAD_COUNT = 50; // Increased thread count for more contention
        final int OPERATIONS_PER_THREAD = 100;
        final int SHARED_SERVICES = 20; // Number of service IDs that will be shared among threads
        final ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        final CountDownLatch startLatch = new CountDownLatch(1); // Used to start all threads at once
        final CountDownLatch completionLatch = new CountDownLatch(THREAD_COUNT);
        final AtomicInteger successfulRegistrations = new AtomicInteger(0);
        final AtomicInteger successfulDeregistrations = new AtomicInteger(0);
        final AtomicInteger failedRegistrations = new AtomicInteger(0);
        final AtomicInteger failedDeregistrations = new AtomicInteger(0);

        // Create a barrier to synchronize threads at specific points to increase contention
        final CyclicBarrier barrier = new CyclicBarrier(10, () -> {
            // This will run each time 10 threads reach the barrier
            // Sleep briefly to ensure threads are competing for resources
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Create and submit tasks
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    // Wait for the signal to start
                    startLatch.await();

                    for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                        // Use a mix of unique and shared service IDs to create contention
                        String serviceId;
                        if (j % 5 == 0) { // 20% of operations use shared IDs
                            // Use a shared service ID to create contention
                            serviceId = "shared-service-" + (j % SHARED_SERVICES);

                            // Synchronize some threads to increase likelihood of contention
                            if (threadId % 5 == 0) {
                                try {
                                    barrier.await(100, TimeUnit.MILLISECONDS);
                                } catch (Exception e) {
                                    // Ignore timeout exceptions
                                }
                            }
                        } else {
                            // Use a unique service ID
                            serviceId = "service-" + threadId + "-" + j;
                        }

                        // Register a service
                        boolean registered = registry.registerService(
                                TEST_SERVICE_TYPE, serviceId, TEST_HOST, TEST_PORT + (j % 1000), null);
                        if (registered) {
                            successfulRegistrations.incrementAndGet();
                        } else {
                            failedRegistrations.incrementAndGet();
                        }

                        // Add a small random delay to increase chance of race conditions
                        if (j % 10 == 0) {
                            Thread.sleep(ThreadLocalRandom.current().nextInt(1, 5));
                        }

                        // Perform some service discovery operations to test concurrent reads
                        if (j % 7 == 0) {
                            registry.findServiceInstances(TEST_SERVICE_TYPE);
                        }

                        if (j % 11 == 0) {
                            registry.findServiceInstanceById(TEST_SERVICE_TYPE, serviceId);
                        }

                        // Deregister the service
                        boolean deregistered = registry.deregisterService(TEST_SERVICE_TYPE, serviceId);
                        if (deregistered) {
                            successfulDeregistrations.incrementAndGet();
                        } else {
                            failedDeregistrations.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Thread " + threadId + " encountered exception: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Start all threads at once
        startLatch.countDown();

        // Wait for all threads to complete with a longer timeout
        assertTrue(completionLatch.await(120, TimeUnit.SECONDS), 
                "All threads should complete within timeout");
        executor.shutdown();

        // Verify no services remain registered
        List<ServiceInstance> remainingServices = registry.findServiceInstances(TEST_SERVICE_TYPE);
        assertTrue(remainingServices.isEmpty(), "No services should remain registered after test");

        System.out.println("Concurrent test results - Successful registrations: " + 
                successfulRegistrations.get() + ", Successful deregistrations: " + 
                successfulDeregistrations.get() + ", Failed registrations: " + 
                failedRegistrations.get() + ", Failed deregistrations: " + 
                failedDeregistrations.get());

        // We don't assert that registrations == deregistrations because with shared service IDs,
        // some registrations will fail due to duplicate IDs, which is expected behavior
    }

    @Test
    void testSingletonBehavior() {
        // Test that getInstance() always returns the same instance
        InMemoryServiceRegistry instance1 = InMemoryServiceRegistry.getInstance();
        InMemoryServiceRegistry instance2 = InMemoryServiceRegistry.getInstance();

        assertSame(instance1, instance2, "getInstance() should always return the same instance");

        // Register a service using one instance
        instance1.registerService(TEST_SERVICE_TYPE, TEST_SERVICE_ID, TEST_HOST, TEST_PORT, null);

        // Verify it's visible through the other instance
        List<ServiceInstance> services = instance2.findServiceInstances(TEST_SERVICE_TYPE);
        assertNotNull(services, "Service registered through one instance should be visible through another");
        assertEquals(1, services.size(), "Should have one registered service");
    }

    @Test
    void testThreadSafeSingletonCreation() throws InterruptedException {
        // Reset the singleton instance to ensure we're testing the creation process
        InMemoryServiceRegistry.reset();

        // Number of threads that will try to get the instance simultaneously
        final int THREAD_COUNT = 50;
        final ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch completionLatch = new CountDownLatch(THREAD_COUNT);

        // Array to hold instances obtained by each thread
        final InMemoryServiceRegistry[] instances = new InMemoryServiceRegistry[THREAD_COUNT];

        // Create and submit tasks
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    // Wait for the signal to start
                    startLatch.await();

                    // Get the instance
                    instances[threadId] = InMemoryServiceRegistry.getInstance();
                } catch (Exception e) {
                    System.err.println("Thread " + threadId + " encountered exception: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Start all threads at once
        startLatch.countDown();

        // Wait for all threads to complete
        assertTrue(completionLatch.await(30, TimeUnit.SECONDS), 
                "All threads should complete within timeout");
        executor.shutdown();

        // Verify that all threads got the same instance
        InMemoryServiceRegistry firstInstance = instances[0];
        assertNotNull(firstInstance, "First instance should not be null");

        for (int i = 1; i < THREAD_COUNT; i++) {
            assertSame(firstInstance, instances[i], 
                    "Thread " + i + " should get the same instance as thread 0");
        }
    }
}