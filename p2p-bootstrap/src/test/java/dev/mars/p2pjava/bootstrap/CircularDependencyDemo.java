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


import java.util.*;

/**
 * Standalone demonstration of circular dependency detection functionality.
 * This class shows how the DependencyAnalyzer works without requiring external dependencies.
 */
public class CircularDependencyDemo {

    public static void main(String[] args) {
        System.out.println("=== Circular Dependency Detection Demo ===\n");
        
        // Demo 1: Valid dependency chain
        demonstrateValidDependencies();
        
        // Demo 2: Simple circular dependency
        demonstrateSimpleCircularDependency();
        
        // Demo 3: Complex circular dependency
        demonstrateComplexCircularDependency();
        
        // Demo 4: Self dependency
        demonstrateSelfDependency();
        
        // Demo 5: Multiple circular dependencies
        demonstrateMultipleCircularDependencies();
        
        System.out.println("=== Demo Complete ===");
    }

    private static void demonstrateValidDependencies() {
        System.out.println("1. Valid Dependency Chain Demo");
        System.out.println("   Dependencies: service1 -> service2 -> service3");
        
        Set<String> services = Set.of("service1", "service2", "service3");
        List<BootstrapService.ServiceDependency> dependencies = Arrays.asList(
            new BootstrapService.ServiceDependency("service1", "service2"),
            new BootstrapService.ServiceDependency("service2", "service3")
        );
        
        DependencyAnalyzer analyzer = new DependencyAnalyzer(services, dependencies);
        DependencyAnalyzer.AnalysisResult result = analyzer.analyze();
        
        System.out.println("   Result: " + (result.isValid() ? "✅ VALID" : "❌ INVALID"));
        System.out.println("   Startup Order: " + result.getTopologicalOrder());
        System.out.println();
    }

    private static void demonstrateSimpleCircularDependency() {
        System.out.println("2. Simple Circular Dependency Demo");
        System.out.println("   Dependencies: service1 -> service2 -> service1");
        
        Set<String> services = Set.of("service1", "service2");
        List<BootstrapService.ServiceDependency> dependencies = Arrays.asList(
            new BootstrapService.ServiceDependency("service1", "service2"),
            new BootstrapService.ServiceDependency("service2", "service1")
        );
        
        DependencyAnalyzer analyzer = new DependencyAnalyzer(services, dependencies);
        DependencyAnalyzer.AnalysisResult result = analyzer.analyze();
        
        System.out.println("   Result: " + (result.isValid() ? "✅ VALID" : "❌ INVALID"));
        if (!result.isValid()) {
            System.out.println("   Error: " + result.getErrorMessage());
            
            try {
                throw new CircularDependencyException(result.getErrorMessage(), result.getCircularDependencies());
            } catch (CircularDependencyException e) {
                System.out.println("   Formatted Error:");
                String[] lines = e.getFormattedCircularDependencies().split("\n");
                for (String line : lines) {
                    System.out.println("     " + line);
                }
            }
        }
        System.out.println();
    }

    private static void demonstrateComplexCircularDependency() {
        System.out.println("3. Complex Circular Dependency Demo");
        System.out.println("   Dependencies: service1 -> service2 -> service3 -> service1");
        
        Set<String> services = Set.of("service1", "service2", "service3");
        List<BootstrapService.ServiceDependency> dependencies = Arrays.asList(
            new BootstrapService.ServiceDependency("service1", "service2"),
            new BootstrapService.ServiceDependency("service2", "service3"),
            new BootstrapService.ServiceDependency("service3", "service1")
        );
        
        DependencyAnalyzer analyzer = new DependencyAnalyzer(services, dependencies);
        DependencyAnalyzer.AnalysisResult result = analyzer.analyze();
        
        System.out.println("   Result: " + (result.isValid() ? "✅ VALID" : "❌ INVALID"));
        if (!result.isValid()) {
            System.out.println("   Error: " + result.getErrorMessage());
            
            try {
                throw new CircularDependencyException(result.getErrorMessage(), result.getCircularDependencies());
            } catch (CircularDependencyException e) {
                System.out.println("   Formatted Error:");
                String[] lines = e.getFormattedCircularDependencies().split("\n");
                for (String line : lines) {
                    System.out.println("     " + line);
                }
            }
        }
        System.out.println();
    }

    private static void demonstrateSelfDependency() {
        System.out.println("4. Self Dependency Demo");
        System.out.println("   Dependencies: service1 -> service1");
        
        Set<String> services = Set.of("service1");
        List<BootstrapService.ServiceDependency> dependencies = Arrays.asList(
            new BootstrapService.ServiceDependency("service1", "service1")
        );
        
        DependencyAnalyzer analyzer = new DependencyAnalyzer(services, dependencies);
        DependencyAnalyzer.AnalysisResult result = analyzer.analyze();
        
        System.out.println("   Result: " + (result.isValid() ? "✅ VALID" : "❌ INVALID"));
        if (!result.isValid()) {
            System.out.println("   Error: " + result.getErrorMessage());
            
            try {
                throw new CircularDependencyException(result.getErrorMessage(), result.getCircularDependencies());
            } catch (CircularDependencyException e) {
                System.out.println("   Formatted Error:");
                String[] lines = e.getFormattedCircularDependencies().split("\n");
                for (String line : lines) {
                    System.out.println("     " + line);
                }
            }
        }
        System.out.println();
    }

    private static void demonstrateMultipleCircularDependencies() {
        System.out.println("5. Multiple Circular Dependencies Demo");
        System.out.println("   Dependencies: service1 -> service2 -> service1, service3 -> service4 -> service3");
        
        Set<String> services = Set.of("service1", "service2", "service3", "service4");
        List<BootstrapService.ServiceDependency> dependencies = Arrays.asList(
            new BootstrapService.ServiceDependency("service1", "service2"),
            new BootstrapService.ServiceDependency("service2", "service1"),
            new BootstrapService.ServiceDependency("service3", "service4"),
            new BootstrapService.ServiceDependency("service4", "service3")
        );
        
        DependencyAnalyzer analyzer = new DependencyAnalyzer(services, dependencies);
        DependencyAnalyzer.AnalysisResult result = analyzer.analyze();
        
        System.out.println("   Result: " + (result.isValid() ? "✅ VALID" : "❌ INVALID"));
        if (!result.isValid()) {
            System.out.println("   Error: " + result.getErrorMessage());
            
            try {
                throw new CircularDependencyException(result.getErrorMessage(), result.getCircularDependencies());
            } catch (CircularDependencyException e) {
                System.out.println("   Formatted Error:");
                String[] lines = e.getFormattedCircularDependencies().split("\n");
                for (String line : lines) {
                    System.out.println("     " + line);
                }
            }
        }
        System.out.println();
    }
}
