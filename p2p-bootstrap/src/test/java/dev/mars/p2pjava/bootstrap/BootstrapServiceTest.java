package dev.mars.p2pjava.bootstrap;

import dev.mars.p2pjava.config.ConfigurationManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the BootstrapService class.
 */
public class BootstrapServiceTest {

    private BootstrapService bootstrapService;

    @BeforeEach
    void setUp() throws IOException {
        // Enable dynamic ports for tests to avoid port conflicts
        ConfigurationManager.getInstance().set("bootstrap.dynamic.ports", "true");

        // Create a new bootstrap service for each test
        bootstrapService = new BootstrapService();
    }

    @AfterEach
    void tearDown() {
        // Stop the bootstrap service after each test
        bootstrapService.stop();
    }

    @Test
    void testRegisterService() {
        // Register a test service
        BootstrapService result = bootstrapService.registerService(
                "test-service", 
                TestService.class, 
                "start", 
                "stop");

        // Verify the result is the same instance (for method chaining)
        assertSame(bootstrapService, result, "registerService should return the same instance");
    }

    @Test
    void testAddDependency() {
        // Register test services
        bootstrapService.registerService("service1", TestService.class, "start", "stop");
        bootstrapService.registerService("service2", TestService.class, "start", "stop");

        // Add a dependency
        BootstrapService result = bootstrapService.addDependency("service2", "service1");

        // Verify the result is the same instance (for method chaining)
        assertSame(bootstrapService, result, "addDependency should return the same instance");
    }

    @Test
    void testStartWithoutDependencies() throws CircularDependencyException {
        // Register a test service
        TestService.startCalled = false;
        bootstrapService.registerService("test-service", TestService.class, "start", "stop");

        // Start the bootstrap service
        boolean started = bootstrapService.start();

        // Verify the service was started
        assertTrue(started, "Bootstrap service should start successfully");
        assertTrue(TestService.startCalled, "Service start method should be called");
    }

    @Test
    void testStartWithDependencies() throws CircularDependencyException {
        // Register test services with dependencies
        TestService.startCalled = false;
        DependentService.startCalled = false;

        bootstrapService.registerService("service1", TestService.class, "start", "stop");
        bootstrapService.registerService("service2", DependentService.class, "start", "stop");
        bootstrapService.addDependency("service2", "service1");

        // Start the bootstrap service
        boolean started = bootstrapService.start();

        // Verify the services were started in the correct order
        assertTrue(started, "Bootstrap service should start successfully");
        assertTrue(TestService.startCalled, "Service1 start method should be called");
        assertTrue(DependentService.startCalled, "Service2 start method should be called");
    }

    @Test
    void testStartWithCircularDependencies() {
        // Register test services with circular dependencies
        bootstrapService.registerService("service1", TestService.class, "start", "stop");
        bootstrapService.registerService("service2", DependentService.class, "start", "stop");
        bootstrapService.addDependency("service2", "service1");
        bootstrapService.addDependency("service1", "service2");

        // Start the bootstrap service - should throw CircularDependencyException
        assertThrows(CircularDependencyException.class, () -> {
            bootstrapService.start();
        }, "Bootstrap service should throw CircularDependencyException with circular dependencies");
    }

    @Test
    void testStartWithNonexistentDependency() throws CircularDependencyException {
        // Register a test service with a nonexistent dependency
        bootstrapService.registerService("service1", TestService.class, "start", "stop");
        bootstrapService.addDependency("service1", "nonexistent-service");

        // Start the bootstrap service
        boolean started = bootstrapService.start();

        // Verify the bootstrap service failed to start due to nonexistent dependency
        assertFalse(started, "Bootstrap service should fail to start with nonexistent dependency");
    }

    @Test
    void testStartWithFailingService() throws CircularDependencyException {
        // Register a test service that fails to start
        bootstrapService.registerService("failing-service", FailingService.class, "start", "stop");

        // Start the bootstrap service
        boolean started = bootstrapService.start();

        // Verify the bootstrap service failed to start due to failing service
        assertFalse(started, "Bootstrap service should fail to start with failing service");
    }

    @Test
    void testStop() throws CircularDependencyException {
        // Register a test service
        TestService.stopCalled = false;
        bootstrapService.registerService("test-service", TestService.class, "start", "stop");

        // Start and then stop the bootstrap service
        bootstrapService.start();
        bootstrapService.stop();

        // Verify the service was stopped
        assertTrue(TestService.stopCalled, "Service stop method should be called");
    }

    // Test service classes

    public static class TestService {
        public static boolean startCalled = false;
        public static boolean stopCalled = false;

        public void start() {
            startCalled = true;
        }

        public void stop() {
            stopCalled = true;
        }
    }

    public static class DependentService {
        public static boolean startCalled = false;
        public static boolean stopCalled = false;

        public void start() {
            startCalled = true;
        }

        public void stop() {
            stopCalled = true;
        }
    }

    public static class FailingService {
        public void start() {
            throw new RuntimeException("Service failed to start");
        }

        public void stop() {
            // Do nothing
        }
    }
}