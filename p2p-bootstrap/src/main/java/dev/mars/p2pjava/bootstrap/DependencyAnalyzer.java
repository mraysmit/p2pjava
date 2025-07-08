package dev.mars.p2pjava.bootstrap;

import java.util.*;
import java.util.logging.Logger;

/**
 * Analyzes service dependencies to detect circular dependencies and provide
 * topological ordering for safe service startup.
 * 
 * This class implements Depth-First Search (DFS) based algorithms to:
 * 1. Detect circular dependencies in the service dependency graph
 * 2. Provide a topologically sorted order for service startup
 * 3. Generate detailed reports about dependency issues
 */
public class DependencyAnalyzer {
    private static final Logger logger = Logger.getLogger(DependencyAnalyzer.class.getName());

    private final Set<String> services;
    private final List<BootstrapService.ServiceDependency> dependencies;
    private final Map<String, Set<String>> dependencyGraph;
    private final Map<String, Set<String>> reverseDependencyGraph;

    /**
     * Creates a new dependency analyzer.
     *
     * @param services The set of all service IDs
     * @param dependencies The list of service dependencies
     */
    public DependencyAnalyzer(Set<String> services, List<BootstrapService.ServiceDependency> dependencies) {
        this.services = new HashSet<>(services);
        this.dependencies = new ArrayList<>(dependencies);
        this.dependencyGraph = buildDependencyGraph();
        this.reverseDependencyGraph = buildReverseDependencyGraph();
    }

    /**
     * Analyzes the dependency graph for circular dependencies and generates
     * a topological ordering.
     *
     * @return The analysis result containing validation status and ordering information
     */
    public AnalysisResult analyze() {
        logger.info("Starting dependency analysis for " + services.size() + " services");

        // Detect circular dependencies
        List<List<String>> circularDependencies = detectCircularDependencies();
        
        if (!circularDependencies.isEmpty()) {
            String errorMessage = formatCircularDependencyError(circularDependencies);
            logger.severe("Circular dependencies detected: " + errorMessage);
            return new AnalysisResult(false, errorMessage, circularDependencies, Collections.emptyList());
        }

        // Generate topological order
        List<String> topologicalOrder = generateTopologicalOrder();
        
        logger.info("Dependency analysis completed successfully. Startup order: " + topologicalOrder);
        return new AnalysisResult(true, "No circular dependencies detected", 
                                Collections.emptyList(), topologicalOrder);
    }

    /**
     * Builds the forward dependency graph (service -> its dependencies).
     */
    private Map<String, Set<String>> buildDependencyGraph() {
        Map<String, Set<String>> graph = new HashMap<>();
        
        // Initialize all services with empty dependency sets
        for (String service : services) {
            graph.put(service, new HashSet<>());
        }
        
        // Add dependencies
        for (BootstrapService.ServiceDependency dependency : dependencies) {
            String dependent = dependency.getDependentServiceId();
            String dependsOn = dependency.getDependencyServiceId();
            
            // Only add if both services are registered
            if (services.contains(dependent) && services.contains(dependsOn)) {
                graph.get(dependent).add(dependsOn);
            }
        }
        
        return graph;
    }

    /**
     * Builds the reverse dependency graph (service -> services that depend on it).
     */
    private Map<String, Set<String>> buildReverseDependencyGraph() {
        Map<String, Set<String>> reverseGraph = new HashMap<>();
        
        // Initialize all services with empty dependent sets
        for (String service : services) {
            reverseGraph.put(service, new HashSet<>());
        }
        
        // Add reverse dependencies
        for (BootstrapService.ServiceDependency dependency : dependencies) {
            String dependent = dependency.getDependentServiceId();
            String dependsOn = dependency.getDependencyServiceId();
            
            // Only add if both services are registered
            if (services.contains(dependent) && services.contains(dependsOn)) {
                reverseGraph.get(dependsOn).add(dependent);
            }
        }
        
        return reverseGraph;
    }

    /**
     * Detects circular dependencies using DFS with cycle detection.
     */
    private List<List<String>> detectCircularDependencies() {
        List<List<String>> cycles = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        Map<String, String> parent = new HashMap<>();

        for (String service : services) {
            if (!visited.contains(service)) {
                List<String> cycle = detectCycleDFS(service, visited, recursionStack, parent, new ArrayList<>());
                if (cycle != null && !cycle.isEmpty()) {
                    cycles.add(cycle);
                }
            }
        }

        return cycles;
    }

    /**
     * DFS-based cycle detection helper method.
     */
    private List<String> detectCycleDFS(String current, Set<String> visited, Set<String> recursionStack, 
                                       Map<String, String> parent, List<String> path) {
        visited.add(current);
        recursionStack.add(current);
        path.add(current);

        Set<String> dependencies = dependencyGraph.get(current);
        if (dependencies != null) {
            for (String dependency : dependencies) {
                if (!visited.contains(dependency)) {
                    parent.put(dependency, current);
                    List<String> cycle = detectCycleDFS(dependency, visited, recursionStack, parent, new ArrayList<>(path));
                    if (cycle != null) {
                        return cycle;
                    }
                } else if (recursionStack.contains(dependency)) {
                    // Found a cycle - reconstruct it
                    List<String> cycle = new ArrayList<>();
                    int startIndex = path.indexOf(dependency);
                    for (int i = startIndex; i < path.size(); i++) {
                        cycle.add(path.get(i));
                    }
                    return cycle;
                }
            }
        }

        recursionStack.remove(current);
        return null;
    }

    /**
     * Generates a topological ordering of services using Kahn's algorithm.
     */
    private List<String> generateTopologicalOrder() {
        List<String> result = new ArrayList<>();
        Map<String, Integer> inDegree = calculateInDegrees();
        Queue<String> queue = new LinkedList<>();

        // Find all services with no dependencies (in-degree 0)
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }

        // Process services in topological order
        while (!queue.isEmpty()) {
            String current = queue.poll();
            result.add(current);

            // Reduce in-degree for all dependents
            Set<String> dependents = reverseDependencyGraph.get(current);
            if (dependents != null) {
                for (String dependent : dependents) {
                    int newInDegree = inDegree.get(dependent) - 1;
                    inDegree.put(dependent, newInDegree);

                    if (newInDegree == 0) {
                        queue.offer(dependent);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Calculates the in-degree (number of dependencies) for each service.
     */
    private Map<String, Integer> calculateInDegrees() {
        Map<String, Integer> inDegree = new HashMap<>();

        // Initialize all services with in-degree 0
        for (String service : services) {
            inDegree.put(service, 0);
        }

        // Count dependencies for each service
        for (String service : services) {
            Set<String> deps = dependencyGraph.get(service);
            if (deps != null) {
                inDegree.put(service, deps.size());
            }
        }

        return inDegree;
    }

    /**
     * Formats circular dependency error message.
     */
    private String formatCircularDependencyError(List<List<String>> circularDependencies) {
        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(circularDependencies.size()).append(" circular dependency chain(s): ");

        for (int i = 0; i < circularDependencies.size(); i++) {
            List<String> cycle = circularDependencies.get(i);
            sb.append("[");
            for (int j = 0; j < cycle.size(); j++) {
                sb.append(cycle.get(j));
                if (j < cycle.size() - 1) {
                    sb.append(" -> ");
                }
            }
            if (!cycle.isEmpty()) {
                sb.append(" -> ").append(cycle.get(0));
            }
            sb.append("]");

            if (i < circularDependencies.size() - 1) {
                sb.append(", ");
            }
        }

        return sb.toString();
    }

    /**
     * Result of dependency analysis.
     */
    public static class AnalysisResult {
        private final boolean valid;
        private final String errorMessage;
        private final List<List<String>> circularDependencies;
        private final List<String> topologicalOrder;

        public AnalysisResult(boolean valid, String errorMessage,
                            List<List<String>> circularDependencies, List<String> topologicalOrder) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.circularDependencies = circularDependencies != null ?
                List.copyOf(circularDependencies) : Collections.emptyList();
            this.topologicalOrder = topologicalOrder != null ?
                List.copyOf(topologicalOrder) : Collections.emptyList();
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public List<List<String>> getCircularDependencies() {
            return circularDependencies;
        }

        public List<String> getTopologicalOrder() {
            return topologicalOrder;
        }
    }
}
