package dev.mars.p2pjava.bootstrap;

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

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for BootstrapService circular dependency detection.
 */
class BootstrapServiceCircularDependencyTest {

    private BootstrapService bootstrapService;

    // Test service classes
    public static class TestService1 {
        public static boolean startCalled = false;
        public static boolean stopCalled = false;

        public void start() {
            startCalled = true;
        }

        public void stop() {
            stopCalled = true;
        }
    }

    public static class TestService2 {
        public static boolean startCalled = false;
        public static boolean stopCalled = false;

        public void start() {
            startCalled = true;
        }

        public void stop() {
            stopCalled = true;
        }
    }

    public static class TestService3 {
        public static boolean startCalled = false;
        public static boolean stopCalled = false;

        public void start() {
            startCalled = true;
        }

        public void stop() {
            stopCalled = true;
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        bootstrapService = new BootstrapService();
        
        // Reset test service states
        TestService1.startCalled = false;
        TestService1.stopCalled = false;
        TestService2.startCalled = false;
        TestService2.stopCalled = false;
        TestService3.startCalled = false;
        TestService3.stopCalled = false;
    }

    @Test
    void testValidDependencyChain() {
        // Setup: service1 -> service2 -> service3 (valid chain)
        bootstrapService.registerService("service1", TestService1.class, "start", "stop");
        bootstrapService.registerService("service2", TestService2.class, "start", "stop");
        bootstrapService.registerService("service3", TestService3.class, "start", "stop");
        
        bootstrapService.addDependency("service1", "service2");
        bootstrapService.addDependency("service2", "service3");

        // Should start successfully without throwing exception
        assertDoesNotThrow(() -> {
            boolean started = bootstrapService.start();
            assertTrue(started, "Bootstrap service should start successfully");
        });

        // Verify all services were started
        assertTrue(TestService1.startCalled, "Service1 should be started");
        assertTrue(TestService2.startCalled, "Service2 should be started");
        assertTrue(TestService3.startCalled, "Service3 should be started");
    }

    @Test
    void testSimpleCircularDependency() {
        // Setup: service1 -> service2 -> service1 (circular)
        bootstrapService.registerService("service1", TestService1.class, "start", "stop");
        bootstrapService.registerService("service2", TestService2.class, "start", "stop");
        
        bootstrapService.addDependency("service1", "service2");
        bootstrapService.addDependency("service2", "service1");

        // Should throw CircularDependencyException
        CircularDependencyException exception = assertThrows(CircularDependencyException.class, () -> {
            bootstrapService.start();
        });

        assertNotNull(exception.getMessage());
        assertFalse(exception.getCircularDependencies().isEmpty());
        
        // Verify services were not started due to circular dependency
        assertFalse(TestService1.startCalled, "Service1 should not be started");
        assertFalse(TestService2.startCalled, "Service2 should not be started");
    }

    @Test
    void testThreeNodeCircularDependency() {
        // Setup: service1 -> service2 -> service3 -> service1 (3-node cycle)
        bootstrapService.registerService("service1", TestService1.class, "start", "stop");
        bootstrapService.registerService("service2", TestService2.class, "start", "stop");
        bootstrapService.registerService("service3", TestService3.class, "start", "stop");
        
        bootstrapService.addDependency("service1", "service2");
        bootstrapService.addDependency("service2", "service3");
        bootstrapService.addDependency("service3", "service1");

        // Should throw CircularDependencyException
        CircularDependencyException exception = assertThrows(CircularDependencyException.class, () -> {
            bootstrapService.start();
        });

        assertNotNull(exception.getMessage());
        assertFalse(exception.getCircularDependencies().isEmpty());
        
        // Verify the cycle contains all three services
        boolean foundThreeNodeCycle = false;
        for (var cycle : exception.getCircularDependencies()) {
            if (cycle.size() == 3 && 
                cycle.contains("service1") && 
                cycle.contains("service2") && 
                cycle.contains("service3")) {
                foundThreeNodeCycle = true;
                break;
            }
        }
        assertTrue(foundThreeNodeCycle, "Should detect 3-node cycle");
        
        // Verify services were not started
        assertFalse(TestService1.startCalled, "Service1 should not be started");
        assertFalse(TestService2.startCalled, "Service2 should not be started");
        assertFalse(TestService3.startCalled, "Service3 should not be started");
    }

    @Test
    void testSelfDependency() {
        // Setup: service1 depends on itself
        bootstrapService.registerService("service1", TestService1.class, "start", "stop");
        bootstrapService.addDependency("service1", "service1");

        // Should throw CircularDependencyException
        CircularDependencyException exception = assertThrows(CircularDependencyException.class, () -> {
            bootstrapService.start();
        });

        assertNotNull(exception.getMessage());
        assertFalse(exception.getCircularDependencies().isEmpty());
        
        // Verify self-dependency is detected
        boolean foundSelfDependency = false;
        for (var cycle : exception.getCircularDependencies()) {
            if (cycle.size() == 1 && cycle.contains("service1")) {
                foundSelfDependency = true;
                break;
            }
        }
        assertTrue(foundSelfDependency, "Should detect self-dependency");
        
        // Verify service was not started
        assertFalse(TestService1.startCalled, "Service1 should not be started");
    }

    @Test
    void testMixedValidAndCircularDependencies() {
        // Setup: service1 -> service2 (valid), service3 -> service3 (self-circular)
        bootstrapService.registerService("service1", TestService1.class, "start", "stop");
        bootstrapService.registerService("service2", TestService2.class, "start", "stop");
        bootstrapService.registerService("service3", TestService3.class, "start", "stop");
        
        bootstrapService.addDependency("service1", "service2");
        bootstrapService.addDependency("service3", "service3"); // Self-dependency

        // Should throw CircularDependencyException
        CircularDependencyException exception = assertThrows(CircularDependencyException.class, () -> {
            bootstrapService.start();
        });

        assertNotNull(exception.getMessage());
        assertFalse(exception.getCircularDependencies().isEmpty());
        
        // Verify services were not started due to circular dependency
        assertFalse(TestService1.startCalled, "Service1 should not be started");
        assertFalse(TestService2.startCalled, "Service2 should not be started");
        assertFalse(TestService3.startCalled, "Service3 should not be started");
    }

    @Test
    void testExceptionMessageContainsUsefulInformation() {
        // Setup: Simple circular dependency
        bootstrapService.registerService("serviceA", TestService1.class, "start", "stop");
        bootstrapService.registerService("serviceB", TestService2.class, "start", "stop");
        
        bootstrapService.addDependency("serviceA", "serviceB");
        bootstrapService.addDependency("serviceB", "serviceA");

        CircularDependencyException exception = assertThrows(CircularDependencyException.class, () -> {
            bootstrapService.start();
        });

        String message = exception.getMessage();
        String formattedDeps = exception.getFormattedCircularDependencies();
        
        // Verify message contains useful information
        assertTrue(message.contains("circular"), "Message should mention circular dependency");
        assertTrue(formattedDeps.contains("serviceA"), "Formatted dependencies should contain serviceA");
        assertTrue(formattedDeps.contains("serviceB"), "Formatted dependencies should contain serviceB");
        assertTrue(formattedDeps.contains("->"), "Formatted dependencies should show dependency arrows");
    }

    @Test
    void testNoServicesRegistered() {
        // Should not throw exception with no services
        assertDoesNotThrow(() -> {
            boolean started = bootstrapService.start();
            assertTrue(started, "Bootstrap service should start successfully with no services");
        });
    }

    @Test
    void testSingleServiceNoDependencies() {
        // Setup: Single service with no dependencies
        bootstrapService.registerService("service1", TestService1.class, "start", "stop");

        // Should start successfully
        assertDoesNotThrow(() -> {
            boolean started = bootstrapService.start();
            assertTrue(started, "Bootstrap service should start successfully");
        });

        assertTrue(TestService1.startCalled, "Service1 should be started");
    }
}
