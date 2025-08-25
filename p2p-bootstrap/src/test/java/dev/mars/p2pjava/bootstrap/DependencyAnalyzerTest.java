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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the DependencyAnalyzer class.
 * Tests circular dependency detection and topological sorting functionality.
 */
class DependencyAnalyzerTest {

    private Set<String> services;
    private List<BootstrapService.ServiceDependency> dependencies;

    @BeforeEach
    void setUp() {
        services = new HashSet<>();
        dependencies = new ArrayList<>();
    }

    @Test
    void testNoDependencies() {
        // Setup: Three services with no dependencies
        services.addAll(Arrays.asList("service1", "service2", "service3"));

        DependencyAnalyzer analyzer = new DependencyAnalyzer(services, dependencies);
        DependencyAnalyzer.AnalysisResult result = analyzer.analyze();

        assertTrue(result.isValid(), "Should be valid with no dependencies");
        assertTrue(result.getCircularDependencies().isEmpty(), "Should have no circular dependencies");
        assertEquals(3, result.getTopologicalOrder().size(), "Should have all services in topological order");
        assertTrue(result.getTopologicalOrder().containsAll(services), "Should contain all services");
    }

    @Test
    void testLinearDependencyChain() {
        // Setup: service1 -> service2 -> service3
        services.addAll(Arrays.asList("service1", "service2", "service3"));
        dependencies.add(new BootstrapService.ServiceDependency("service1", "service2"));
        dependencies.add(new BootstrapService.ServiceDependency("service2", "service3"));

        DependencyAnalyzer analyzer = new DependencyAnalyzer(services, dependencies);
        DependencyAnalyzer.AnalysisResult result = analyzer.analyze();

        assertTrue(result.isValid(), "Should be valid with linear dependencies");
        assertTrue(result.getCircularDependencies().isEmpty(), "Should have no circular dependencies");
        
        List<String> order = result.getTopologicalOrder();
        assertEquals(3, order.size(), "Should have all services in topological order");
        
        // service3 should come before service2, service2 before service1
        assertTrue(order.indexOf("service3") < order.indexOf("service2"), 
                  "service3 should come before service2");
        assertTrue(order.indexOf("service2") < order.indexOf("service1"), 
                  "service2 should come before service1");
    }

    @Test
    void testSimpleCircularDependency() {
        // Setup: service1 -> service2 -> service1 (circular)
        services.addAll(Arrays.asList("service1", "service2"));
        dependencies.add(new BootstrapService.ServiceDependency("service1", "service2"));
        dependencies.add(new BootstrapService.ServiceDependency("service2", "service1"));

        DependencyAnalyzer analyzer = new DependencyAnalyzer(services, dependencies);
        DependencyAnalyzer.AnalysisResult result = analyzer.analyze();

        assertFalse(result.isValid(), "Should be invalid due to circular dependency");
        assertFalse(result.getCircularDependencies().isEmpty(), "Should detect circular dependencies");
        assertEquals(1, result.getCircularDependencies().size(), "Should detect exactly one cycle");
        
        List<String> cycle = result.getCircularDependencies().get(0);
        assertEquals(2, cycle.size(), "Cycle should contain 2 services");
        assertTrue(cycle.contains("service1") && cycle.contains("service2"), 
                  "Cycle should contain both services");
    }

    @Test
    void testComplexCircularDependency() {
        // Setup: service1 -> service2 -> service3 -> service1 (3-node cycle)
        services.addAll(Arrays.asList("service1", "service2", "service3"));
        dependencies.add(new BootstrapService.ServiceDependency("service1", "service2"));
        dependencies.add(new BootstrapService.ServiceDependency("service2", "service3"));
        dependencies.add(new BootstrapService.ServiceDependency("service3", "service1"));

        DependencyAnalyzer analyzer = new DependencyAnalyzer(services, dependencies);
        DependencyAnalyzer.AnalysisResult result = analyzer.analyze();

        assertFalse(result.isValid(), "Should be invalid due to circular dependency");
        assertFalse(result.getCircularDependencies().isEmpty(), "Should detect circular dependencies");
        assertEquals(1, result.getCircularDependencies().size(), "Should detect exactly one cycle");
        
        List<String> cycle = result.getCircularDependencies().get(0);
        assertEquals(3, cycle.size(), "Cycle should contain 3 services");
        assertTrue(cycle.containsAll(Arrays.asList("service1", "service2", "service3")), 
                  "Cycle should contain all three services");
    }

    @Test
    void testMultipleCircularDependencies() {
        // Setup: Two separate cycles
        // Cycle 1: service1 -> service2 -> service1
        // Cycle 2: service3 -> service4 -> service3
        services.addAll(Arrays.asList("service1", "service2", "service3", "service4"));
        dependencies.add(new BootstrapService.ServiceDependency("service1", "service2"));
        dependencies.add(new BootstrapService.ServiceDependency("service2", "service1"));
        dependencies.add(new BootstrapService.ServiceDependency("service3", "service4"));
        dependencies.add(new BootstrapService.ServiceDependency("service4", "service3"));

        DependencyAnalyzer analyzer = new DependencyAnalyzer(services, dependencies);
        DependencyAnalyzer.AnalysisResult result = analyzer.analyze();

        assertFalse(result.isValid(), "Should be invalid due to circular dependencies");
        assertFalse(result.getCircularDependencies().isEmpty(), "Should detect circular dependencies");
        
        // Note: The exact number of cycles detected may vary depending on the DFS traversal order
        // but there should be at least one cycle detected
        assertTrue(result.getCircularDependencies().size() >= 1, "Should detect at least one cycle");
    }

    @Test
    void testMixedValidAndCircularDependencies() {
        // Setup: service1 -> service2 (valid), service3 -> service4 -> service3 (circular)
        services.addAll(Arrays.asList("service1", "service2", "service3", "service4"));
        dependencies.add(new BootstrapService.ServiceDependency("service1", "service2"));
        dependencies.add(new BootstrapService.ServiceDependency("service3", "service4"));
        dependencies.add(new BootstrapService.ServiceDependency("service4", "service3"));

        DependencyAnalyzer analyzer = new DependencyAnalyzer(services, dependencies);
        DependencyAnalyzer.AnalysisResult result = analyzer.analyze();

        assertFalse(result.isValid(), "Should be invalid due to circular dependency");
        assertFalse(result.getCircularDependencies().isEmpty(), "Should detect circular dependencies");
        
        // Should detect the cycle between service3 and service4
        boolean foundCycle = false;
        for (List<String> cycle : result.getCircularDependencies()) {
            if (cycle.contains("service3") && cycle.contains("service4")) {
                foundCycle = true;
                break;
            }
        }
        assertTrue(foundCycle, "Should detect cycle between service3 and service4");
    }

    @Test
    void testSelfDependency() {
        // Setup: service1 depends on itself
        services.add("service1");
        dependencies.add(new BootstrapService.ServiceDependency("service1", "service1"));

        DependencyAnalyzer analyzer = new DependencyAnalyzer(services, dependencies);
        DependencyAnalyzer.AnalysisResult result = analyzer.analyze();

        assertFalse(result.isValid(), "Should be invalid due to self-dependency");
        assertFalse(result.getCircularDependencies().isEmpty(), "Should detect circular dependency");
        
        List<String> cycle = result.getCircularDependencies().get(0);
        assertEquals(1, cycle.size(), "Self-dependency cycle should contain 1 service");
        assertEquals("service1", cycle.get(0), "Cycle should contain service1");
    }

    @Test
    void testComplexValidDependencyGraph() {
        // Setup: Complex but valid dependency graph
        // service1 -> service2, service3
        // service2 -> service4
        // service3 -> service4
        // service4 -> service5
        services.addAll(Arrays.asList("service1", "service2", "service3", "service4", "service5"));
        dependencies.add(new BootstrapService.ServiceDependency("service1", "service2"));
        dependencies.add(new BootstrapService.ServiceDependency("service1", "service3"));
        dependencies.add(new BootstrapService.ServiceDependency("service2", "service4"));
        dependencies.add(new BootstrapService.ServiceDependency("service3", "service4"));
        dependencies.add(new BootstrapService.ServiceDependency("service4", "service5"));

        DependencyAnalyzer analyzer = new DependencyAnalyzer(services, dependencies);
        DependencyAnalyzer.AnalysisResult result = analyzer.analyze();

        assertTrue(result.isValid(), "Should be valid with complex dependencies");
        assertTrue(result.getCircularDependencies().isEmpty(), "Should have no circular dependencies");
        
        List<String> order = result.getTopologicalOrder();
        assertEquals(5, order.size(), "Should have all services in topological order");
        
        // Verify ordering constraints
        assertTrue(order.indexOf("service5") < order.indexOf("service4"), 
                  "service5 should come before service4");
        assertTrue(order.indexOf("service4") < order.indexOf("service2"), 
                  "service4 should come before service2");
        assertTrue(order.indexOf("service4") < order.indexOf("service3"), 
                  "service4 should come before service3");
        assertTrue(order.indexOf("service2") < order.indexOf("service1"), 
                  "service2 should come before service1");
        assertTrue(order.indexOf("service3") < order.indexOf("service1"), 
                  "service3 should come before service1");
    }
}
