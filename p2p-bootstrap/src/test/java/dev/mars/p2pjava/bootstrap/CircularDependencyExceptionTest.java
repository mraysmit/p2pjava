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


import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the CircularDependencyException class.
 */
class CircularDependencyExceptionTest {

    @Test
    void testExceptionWithMessage() {
        String message = "Circular dependency detected";
        CircularDependencyException exception = new CircularDependencyException(message);

        assertEquals(message, exception.getMessage());
        assertTrue(exception.getCircularDependencies().isEmpty());
        assertEquals("No circular dependency details available", 
                    exception.getFormattedCircularDependencies());
    }

    @Test
    void testExceptionWithMessageAndDependencies() {
        String message = "Multiple circular dependencies found";
        List<List<String>> circularDeps = Arrays.asList(
            Arrays.asList("service1", "service2"),
            Arrays.asList("service3", "service4", "service5")
        );

        CircularDependencyException exception = new CircularDependencyException(message, circularDeps);

        assertEquals(message, exception.getMessage());
        assertEquals(2, exception.getCircularDependencies().size());
        assertEquals(circularDeps, exception.getCircularDependencies());
    }

    @Test
    void testFormattedCircularDependenciesWithSingleCycle() {
        List<List<String>> circularDeps = Arrays.asList(
            Arrays.asList("service1", "service2")
        );

        CircularDependencyException exception = new CircularDependencyException(
            "Single cycle detected", circularDeps);

        String formatted = exception.getFormattedCircularDependencies();
        
        assertTrue(formatted.contains("Circular dependencies detected:"));
        assertTrue(formatted.contains("Cycle 1:"));
        assertTrue(formatted.contains("service1 -> service2 -> service1"));
    }

    @Test
    void testFormattedCircularDependenciesWithMultipleCycles() {
        List<List<String>> circularDeps = Arrays.asList(
            Arrays.asList("service1", "service2"),
            Arrays.asList("service3", "service4", "service5")
        );

        CircularDependencyException exception = new CircularDependencyException(
            "Multiple cycles detected", circularDeps);

        String formatted = exception.getFormattedCircularDependencies();
        
        assertTrue(formatted.contains("Circular dependencies detected:"));
        assertTrue(formatted.contains("Cycle 1:"));
        assertTrue(formatted.contains("Cycle 2:"));
        assertTrue(formatted.contains("service1 -> service2 -> service1"));
        assertTrue(formatted.contains("service3 -> service4 -> service5 -> service3"));
    }

    @Test
    void testFormattedCircularDependenciesWithSelfDependency() {
        List<List<String>> circularDeps = Arrays.asList(
            Arrays.asList("service1")
        );

        CircularDependencyException exception = new CircularDependencyException(
            "Self dependency detected", circularDeps);

        String formatted = exception.getFormattedCircularDependencies();
        
        assertTrue(formatted.contains("Circular dependencies detected:"));
        assertTrue(formatted.contains("Cycle 1:"));
        assertTrue(formatted.contains("service1 -> service1"));
    }

    @Test
    void testFormattedCircularDependenciesWithEmptyList() {
        CircularDependencyException exception = new CircularDependencyException(
            "No details", Collections.emptyList());

        String formatted = exception.getFormattedCircularDependencies();
        assertEquals("No circular dependency details available", formatted);
    }

    @Test
    void testFormattedCircularDependenciesWithNullList() {
        CircularDependencyException exception = new CircularDependencyException(
            "Null details", null);

        String formatted = exception.getFormattedCircularDependencies();
        assertEquals("No circular dependency details available", formatted);
    }

    @Test
    void testToStringIncludesFormattedDependencies() {
        List<List<String>> circularDeps = Arrays.asList(
            Arrays.asList("service1", "service2")
        );

        CircularDependencyException exception = new CircularDependencyException(
            "Test exception", circularDeps);

        String toString = exception.toString();
        
        assertTrue(toString.contains("Test exception"));
        assertTrue(toString.contains("Circular dependencies detected:"));
        assertTrue(toString.contains("service1 -> service2 -> service1"));
    }

    @Test
    void testImmutabilityOfCircularDependencies() {
        List<List<String>> originalDeps = Arrays.asList(
            Arrays.asList("service1", "service2")
        );

        CircularDependencyException exception = new CircularDependencyException(
            "Test", originalDeps);

        List<List<String>> returnedDeps = exception.getCircularDependencies();
        
        // Should not be able to modify the returned list
        assertThrows(UnsupportedOperationException.class, () -> {
            returnedDeps.add(Arrays.asList("service3"));
        });

        // Should not be able to modify the inner lists
        assertThrows(UnsupportedOperationException.class, () -> {
            returnedDeps.get(0).add("service3");
        });
    }

    @Test
    void testFormattedCircularDependenciesWithEmptyCycle() {
        List<List<String>> circularDeps = Arrays.asList(
            Collections.emptyList()
        );

        CircularDependencyException exception = new CircularDependencyException(
            "Empty cycle", circularDeps);

        String formatted = exception.getFormattedCircularDependencies();
        
        assertTrue(formatted.contains("Circular dependencies detected:"));
        assertTrue(formatted.contains("Cycle 1:"));
        // Should handle empty cycle gracefully
        assertFalse(formatted.contains("null"));
    }
}
