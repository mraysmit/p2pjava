package dev.mars.p2pjava.bootstrap;

import java.util.List;
import java.util.Collections;

/**
 * Exception thrown when circular dependencies are detected in the service dependency graph.
 * This exception provides detailed information about the circular dependency chains
 * to help with debugging and resolution.
 */
public class CircularDependencyException extends Exception {
    private final List<List<String>> circularDependencies;

    /**
     * Creates a new CircularDependencyException.
     *
     * @param message The error message describing the circular dependency
     * @param circularDependencies List of circular dependency chains detected
     */
    public CircularDependencyException(String message, List<List<String>> circularDependencies) {
        super(message);
        this.circularDependencies = circularDependencies != null ? 
            List.copyOf(circularDependencies) : Collections.emptyList();
    }

    /**
     * Creates a new CircularDependencyException with a simple message.
     *
     * @param message The error message describing the circular dependency
     */
    public CircularDependencyException(String message) {
        this(message, Collections.emptyList());
    }

    /**
     * Gets the circular dependency chains that were detected.
     *
     * @return An unmodifiable list of circular dependency chains, where each chain
     *         is represented as a list of service IDs forming a cycle
     */
    public List<List<String>> getCircularDependencies() {
        return circularDependencies;
    }

    /**
     * Gets a formatted string representation of all circular dependencies.
     *
     * @return A formatted string showing all circular dependency chains
     */
    public String getFormattedCircularDependencies() {
        if (circularDependencies.isEmpty()) {
            return "No circular dependency details available";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Circular dependencies detected:\n");
        
        for (int i = 0; i < circularDependencies.size(); i++) {
            List<String> cycle = circularDependencies.get(i);
            sb.append("  Cycle ").append(i + 1).append(": ");
            
            for (int j = 0; j < cycle.size(); j++) {
                sb.append(cycle.get(j));
                if (j < cycle.size() - 1) {
                    sb.append(" -> ");
                }
            }
            
            // Show the cycle completion
            if (!cycle.isEmpty()) {
                sb.append(" -> ").append(cycle.get(0));
            }
            
            if (i < circularDependencies.size() - 1) {
                sb.append("\n");
            }
        }
        
        return sb.toString();
    }

    @Override
    public String toString() {
        return super.toString() + "\n" + getFormattedCircularDependencies();
    }
}
