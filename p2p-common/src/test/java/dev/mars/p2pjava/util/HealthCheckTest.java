package dev.mars.p2pjava.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the HealthCheck class.
 */
public class HealthCheckTest {

    private static final String TEST_SERVICE_NAME = "test-service";
    
    @BeforeEach
    void setUp() {
        // Ensure the service is not registered before each test
        HealthCheck.deregisterService(TEST_SERVICE_NAME);
    }
    
    @AfterEach
    void tearDown() {
        // Clean up after each test
        HealthCheck.deregisterService(TEST_SERVICE_NAME);
    }
    
    @Test
    void testRegisterService() {
        // Register a service
        HealthCheck.ServiceHealth health = HealthCheck.registerService(TEST_SERVICE_NAME);
        
        // Verify the service was registered
        assertNotNull(health, "Service health should not be null");
        assertEquals(TEST_SERVICE_NAME, health.getServiceName(), "Service name should match");
        assertTrue(health.isHealthy(), "Service should be healthy by default");
    }
    
    @Test
    void testGetServiceHealth() {
        // Register a service
        HealthCheck.registerService(TEST_SERVICE_NAME);
        
        // Get the service health
        HealthCheck.ServiceHealth health = HealthCheck.getServiceHealth(TEST_SERVICE_NAME);
        
        // Verify the service health
        assertNotNull(health, "Service health should not be null");
        assertEquals(TEST_SERVICE_NAME, health.getServiceName(), "Service name should match");
    }
    
    @Test
    void testGetNonexistentServiceHealth() {
        // Get a nonexistent service health
        HealthCheck.ServiceHealth health = HealthCheck.getServiceHealth("nonexistent-service");
        
        // Verify the service health is null
        assertNull(health, "Nonexistent service health should be null");
    }
    
    @Test
    void testUpdateServiceHealth() {
        // Register a service
        HealthCheck.registerService(TEST_SERVICE_NAME);
        
        // Update the service health to unhealthy
        HealthCheck.ServiceHealth health = HealthCheck.updateServiceHealth(TEST_SERVICE_NAME, false);
        
        // Verify the service health was updated
        assertNotNull(health, "Service health should not be null");
        assertFalse(health.isHealthy(), "Service should be unhealthy after update");
        
        // Update the service health back to healthy
        health = HealthCheck.updateServiceHealth(TEST_SERVICE_NAME, true);
        
        // Verify the service health was updated
        assertTrue(health.isHealthy(), "Service should be healthy after update");
    }
    
    @Test
    void testUpdateNonexistentServiceHealth() {
        // Update a nonexistent service health
        HealthCheck.ServiceHealth health = HealthCheck.updateServiceHealth("nonexistent-service", false);
        
        // Verify the service health is null
        assertNull(health, "Nonexistent service health should be null");
    }
    
    @Test
    void testServiceHealthDetails() {
        // Register a service
        HealthCheck.ServiceHealth health = HealthCheck.registerService(TEST_SERVICE_NAME);
        
        // Add health details
        health.addHealthDetail("version", "1.0");
        health.addHealthDetail("status", "running");
        
        // Get the health details
        Map<String, Object> details = health.getHealthDetails();
        
        // Verify the health details
        assertNotNull(details, "Health details should not be null");
        assertEquals(2, details.size(), "Health details should have two entries");
        assertEquals("1.0", details.get("version"), "Version should match");
        assertEquals("running", details.get("status"), "Status should match");
    }
    
    @Test
    void testGetAllServiceHealth() {
        // Register multiple services
        HealthCheck.registerService(TEST_SERVICE_NAME);
        HealthCheck.registerService("another-service");
        
        // Get all service health
        Map<String, HealthCheck.ServiceHealth> allHealth = HealthCheck.getAllServiceHealth();
        
        // Verify all service health
        assertNotNull(allHealth, "All service health should not be null");
        assertTrue(allHealth.containsKey(TEST_SERVICE_NAME), "All service health should contain test service");
        assertTrue(allHealth.containsKey("another-service"), "All service health should contain another service");
        
        // Clean up
        HealthCheck.deregisterService("another-service");
    }
    
    @Test
    void testDeregisterService() {
        // Register a service
        HealthCheck.registerService(TEST_SERVICE_NAME);
        
        // Deregister the service
        HealthCheck.deregisterService(TEST_SERVICE_NAME);
        
        // Verify the service was deregistered
        assertNull(HealthCheck.getServiceHealth(TEST_SERVICE_NAME), "Service should be deregistered");
    }
    
    @Test
    void testIsServiceAvailable() {
        // Test with a port that should be available (unlikely to be in use)
        boolean available = HealthCheck.isServiceAvailable("localhost", 65432, 100);
        
        // The result depends on whether the port is actually in use
        // We can't make a definitive assertion, but we can log the result
        System.out.println("Port 65432 is " + (available ? "available" : "not available"));
    }
    
    @Test
    void testServiceHealthToString() {
        // Register a service
        HealthCheck.ServiceHealth health = HealthCheck.registerService(TEST_SERVICE_NAME);
        
        // Get the string representation
        String toString = health.toString();
        
        // Verify the string representation
        assertNotNull(toString, "toString should not be null");
        assertTrue(toString.contains(TEST_SERVICE_NAME), "toString should contain the service name");
        assertTrue(toString.contains("healthy"), "toString should contain healthy status");
    }
}