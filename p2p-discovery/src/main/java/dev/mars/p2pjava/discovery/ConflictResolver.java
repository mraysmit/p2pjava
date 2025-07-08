package dev.mars.p2pjava.discovery;

import java.util.*;
import java.util.logging.Logger;

/**
 * Resolves conflicts in service registry information between different peers.
 * Uses various strategies to determine which service instance should take precedence
 * when conflicts arise in a distributed environment.
 */
public class ConflictResolver {
    private static final Logger logger = Logger.getLogger(ConflictResolver.class.getName());
    
    /**
     * Conflict resolution strategies
     */
    public enum ResolutionStrategy {
        LAST_WRITE_WINS,    // Use the service instance with the highest version
        ORIGIN_PRIORITY,    // Prefer instances from specific origin peers
        HEALTH_PRIORITY,    // Prefer healthy instances over unhealthy ones
        COMPOSITE           // Use multiple strategies in combination
    }
    
    private final ResolutionStrategy strategy;
    private final Set<String> priorityPeers;
    
    /**
     * Creates a conflict resolver with the specified strategy.
     */
    public ConflictResolver(ResolutionStrategy strategy) {
        this.strategy = strategy;
        this.priorityPeers = new HashSet<>();
    }
    
    /**
     * Creates a conflict resolver with priority peers for ORIGIN_PRIORITY strategy.
     */
    public ConflictResolver(ResolutionStrategy strategy, Set<String> priorityPeers) {
        this.strategy = strategy;
        this.priorityPeers = new HashSet<>(priorityPeers);
    }
    
    /**
     * Resolves conflicts between multiple service instances with the same service ID.
     * 
     * @param conflictingInstances List of conflicting service instances
     * @return The resolved service instance that should be used
     */
    public ServiceInstance resolveConflict(List<ServiceInstance> conflictingInstances) {
        if (conflictingInstances == null || conflictingInstances.isEmpty()) {
            return null;
        }
        
        if (conflictingInstances.size() == 1) {
            return conflictingInstances.get(0);
        }
        
        logger.info("Resolving conflict between " + conflictingInstances.size() + 
                   " service instances using strategy: " + strategy);
        
        switch (strategy) {
            case LAST_WRITE_WINS:
                return resolveByLastWrite(conflictingInstances);
            case ORIGIN_PRIORITY:
                return resolveByOriginPriority(conflictingInstances);
            case HEALTH_PRIORITY:
                return resolveByHealthPriority(conflictingInstances);
            case COMPOSITE:
                return resolveByComposite(conflictingInstances);
            default:
                logger.warning("Unknown resolution strategy: " + strategy + ", falling back to LAST_WRITE_WINS");
                return resolveByLastWrite(conflictingInstances);
        }
    }
    
    /**
     * Resolves conflict by selecting the instance with the highest version (last write wins).
     */
    private ServiceInstance resolveByLastWrite(List<ServiceInstance> instances) {
        return instances.stream()
                .max(Comparator.comparingLong(ServiceInstance::getVersion))
                .orElse(instances.get(0));
    }
    
    /**
     * Resolves conflict by preferring instances from priority peers.
     */
    private ServiceInstance resolveByOriginPriority(List<ServiceInstance> instances) {
        // First, try to find an instance from a priority peer
        Optional<ServiceInstance> priorityInstance = instances.stream()
                .filter(instance -> priorityPeers.contains(instance.getOriginPeerId()))
                .max(Comparator.comparingLong(ServiceInstance::getVersion));
        
        if (priorityInstance.isPresent()) {
            logger.fine("Resolved conflict using priority peer: " + priorityInstance.get().getOriginPeerId());
            return priorityInstance.get();
        }
        
        // If no priority peer instance found, fall back to last write wins
        logger.fine("No priority peer instance found, falling back to last write wins");
        return resolveByLastWrite(instances);
    }
    
    /**
     * Resolves conflict by preferring healthy instances over unhealthy ones.
     */
    private ServiceInstance resolveByHealthPriority(List<ServiceInstance> instances) {
        // Separate healthy and unhealthy instances
        List<ServiceInstance> healthyInstances = instances.stream()
                .filter(ServiceInstance::isHealthy)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        
        List<ServiceInstance> unhealthyInstances = instances.stream()
                .filter(instance -> !instance.isHealthy())
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        
        // Prefer healthy instances
        if (!healthyInstances.isEmpty()) {
            logger.fine("Resolved conflict using healthy instance");
            return resolveByLastWrite(healthyInstances);
        }
        
        // If no healthy instances, use unhealthy ones
        logger.fine("No healthy instances found, using unhealthy instance");
        return resolveByLastWrite(unhealthyInstances);
    }
    
    /**
     * Resolves conflict using a composite strategy that combines multiple approaches.
     */
    private ServiceInstance resolveByComposite(List<ServiceInstance> instances) {
        // Step 1: Filter by health (prefer healthy instances)
        List<ServiceInstance> healthyInstances = instances.stream()
                .filter(ServiceInstance::isHealthy)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        
        List<ServiceInstance> candidateInstances = healthyInstances.isEmpty() ? instances : healthyInstances;
        
        // Step 2: Apply origin priority if available
        if (!priorityPeers.isEmpty()) {
            Optional<ServiceInstance> priorityInstance = candidateInstances.stream()
                    .filter(instance -> priorityPeers.contains(instance.getOriginPeerId()))
                    .max(Comparator.comparingLong(ServiceInstance::getVersion));
            
            if (priorityInstance.isPresent()) {
                logger.fine("Composite resolution: selected priority peer instance");
                return priorityInstance.get();
            }
        }
        
        // Step 3: Fall back to last write wins
        logger.fine("Composite resolution: falling back to last write wins");
        return resolveByLastWrite(candidateInstances);
    }
    
    /**
     * Merges metadata from conflicting instances, preferring newer values.
     */
    public Map<String, String> mergeMetadata(List<ServiceInstance> instances) {
        Map<String, String> mergedMetadata = new HashMap<>();
        
        // Sort instances by version (oldest first)
        instances.stream()
                .sorted(Comparator.comparingLong(ServiceInstance::getVersion))
                .forEach(instance -> {
                    // Newer metadata overwrites older metadata
                    mergedMetadata.putAll(instance.getMetadata());
                });
        
        return mergedMetadata;
    }
    
    /**
     * Determines if two service instances are in conflict (same service ID but different details).
     */
    public boolean isConflict(ServiceInstance instance1, ServiceInstance instance2) {
        if (instance1 == null || instance2 == null) {
            return false;
        }
        
        // Same service ID but different versions, hosts, ports, or health status
        return instance1.getServiceId().equals(instance2.getServiceId()) &&
               instance1.getServiceType().equals(instance2.getServiceType()) &&
               (instance1.getVersion() != instance2.getVersion() ||
                !instance1.getHost().equals(instance2.getHost()) ||
                instance1.getPort() != instance2.getPort() ||
                instance1.isHealthy() != instance2.isHealthy());
    }
    
    /**
     * Adds a priority peer for ORIGIN_PRIORITY and COMPOSITE strategies.
     */
    public void addPriorityPeer(String peerId) {
        priorityPeers.add(peerId);
    }
    
    /**
     * Removes a priority peer.
     */
    public void removePriorityPeer(String peerId) {
        priorityPeers.remove(peerId);
    }
    
    /**
     * Gets the current set of priority peers.
     */
    public Set<String> getPriorityPeers() {
        return new HashSet<>(priorityPeers);
    }
    
    /**
     * Gets the current resolution strategy.
     */
    public ResolutionStrategy getStrategy() {
        return strategy;
    }
}
