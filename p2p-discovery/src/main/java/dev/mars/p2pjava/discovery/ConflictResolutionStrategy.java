package dev.mars.p2pjava.discovery;

import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

/**
 * Advanced conflict resolution strategy for distributed service registry.
 * Implements multiple resolution algorithms to handle registry inconsistencies.
 */
public class ConflictResolutionStrategy {
    private static final Logger logger = Logger.getLogger(ConflictResolutionStrategy.class.getName());
    
    public enum ResolutionPolicy {
        LAST_WRITE_WINS,        // Use timestamp to resolve conflicts
        VECTOR_CLOCK,           // Use vector clocks for causality
        PEER_PRIORITY,          // Use peer priority/weight
        HEALTH_BASED,           // Prefer healthy services
        COMPOSITE               // Combine multiple strategies
    }
    
    private final ResolutionPolicy policy;
    private final Map<String, Integer> peerPriorities;
    private final ServiceHealthChecker healthChecker;
    
    public ConflictResolutionStrategy(ResolutionPolicy policy) {
        this(policy, Collections.emptyMap(), null);
    }
    
    public ConflictResolutionStrategy(ResolutionPolicy policy, 
                                    Map<String, Integer> peerPriorities,
                                    ServiceHealthChecker healthChecker) {
        this.policy = policy;
        this.peerPriorities = new HashMap<>(peerPriorities);
        this.healthChecker = healthChecker;
    }
    
    /**
     * Resolves conflicts between multiple service instances.
     */
    public ServiceInstance resolveConflict(List<ServiceInstance> conflictingInstances) {
        if (conflictingInstances == null || conflictingInstances.isEmpty()) {
            return null;
        }
        
        if (conflictingInstances.size() == 1) {
            return conflictingInstances.get(0);
        }
        
        logger.fine("Resolving conflict between " + conflictingInstances.size() + " instances using " + policy);
        
        switch (policy) {
            case LAST_WRITE_WINS:
                return resolveByTimestamp(conflictingInstances);
            case VECTOR_CLOCK:
                return resolveByVectorClock(conflictingInstances);
            case PEER_PRIORITY:
                return resolveByPeerPriority(conflictingInstances);
            case HEALTH_BASED:
                return resolveByHealth(conflictingInstances);
            case COMPOSITE:
                return resolveComposite(conflictingInstances);
            default:
                logger.warning("Unknown resolution policy: " + policy + ", falling back to LAST_WRITE_WINS");
                return resolveByTimestamp(conflictingInstances);
        }
    }
    
    /**
     * Resolves conflicts using timestamp (Last Write Wins).
     */
    private ServiceInstance resolveByTimestamp(List<ServiceInstance> instances) {
        return instances.stream()
            .max(Comparator.comparing(ServiceInstance::getLastUpdated))
            .orElse(instances.get(0));
    }
    
    /**
     * Resolves conflicts using vector clocks for causality.
     */
    private ServiceInstance resolveByVectorClock(List<ServiceInstance> instances) {
        // For now, fall back to timestamp if vector clocks aren't available
        // TODO: Implement proper vector clock comparison
        ServiceInstance latest = null;
        VectorClock latestClock = null;
        
        for (ServiceInstance instance : instances) {
            VectorClock clock = instance.getVectorClock();
            if (clock != null) {
                if (latestClock == null || clock.isAfter(latestClock)) {
                    latest = instance;
                    latestClock = clock;
                }
            }
        }
        
        return latest != null ? latest : resolveByTimestamp(instances);
    }
    
    /**
     * Resolves conflicts based on peer priority/weight.
     */
    private ServiceInstance resolveByPeerPriority(List<ServiceInstance> instances) {
        return instances.stream()
            .max(Comparator.comparing(instance -> 
                peerPriorities.getOrDefault(instance.getOriginPeerId(), 0)))
            .orElse(instances.get(0));
    }
    
    /**
     * Resolves conflicts by preferring healthy services.
     */
    private ServiceInstance resolveByHealth(List<ServiceInstance> instances) {
        if (healthChecker == null) {
            return resolveByTimestamp(instances);
        }
        
        // First, try to find healthy instances
        List<ServiceInstance> healthyInstances = instances.stream()
            .filter(instance -> healthChecker.isHealthy(instance))
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        
        if (!healthyInstances.isEmpty()) {
            // Among healthy instances, use timestamp
            return resolveByTimestamp(healthyInstances);
        }
        
        // If no healthy instances, fall back to timestamp
        return resolveByTimestamp(instances);
    }
    
    /**
     * Composite resolution strategy combining multiple approaches.
     */
    private ServiceInstance resolveComposite(List<ServiceInstance> instances) {
        // 1. First filter by health if available
        List<ServiceInstance> candidates = instances;
        if (healthChecker != null) {
            List<ServiceInstance> healthy = instances.stream()
                .filter(instance -> healthChecker.isHealthy(instance))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            if (!healthy.isEmpty()) {
                candidates = healthy;
            }
        }
        
        // 2. Among remaining candidates, use peer priority
        if (!peerPriorities.isEmpty()) {
            int maxPriority = candidates.stream()
                .mapToInt(instance -> peerPriorities.getOrDefault(instance.getOriginPeerId(), 0))
                .max()
                .orElse(0);
            
            List<ServiceInstance> highPriority = candidates.stream()
                .filter(instance -> peerPriorities.getOrDefault(instance.getOriginPeerId(), 0) == maxPriority)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            
            if (!highPriority.isEmpty()) {
                candidates = highPriority;
            }
        }
        
        // 3. Finally, use timestamp among remaining candidates
        return resolveByTimestamp(candidates);
    }
    
    /**
     * Merges registry states from multiple peers, resolving conflicts.
     */
    public Map<String, Map<String, ServiceInstance>> mergeRegistries(
            List<Map<String, Map<String, ServiceInstance>>> registries) {
        
        Map<String, Map<String, ServiceInstance>> merged = new HashMap<>();
        
        // Collect all service types
        Set<String> allServiceTypes = new HashSet<>();
        for (Map<String, Map<String, ServiceInstance>> registry : registries) {
            allServiceTypes.addAll(registry.keySet());
        }
        
        // For each service type, merge instances
        for (String serviceType : allServiceTypes) {
            Map<String, ServiceInstance> mergedServices = new HashMap<>();
            
            // Collect all service IDs for this type
            Set<String> allServiceIds = new HashSet<>();
            for (Map<String, Map<String, ServiceInstance>> registry : registries) {
                Map<String, ServiceInstance> services = registry.get(serviceType);
                if (services != null) {
                    allServiceIds.addAll(services.keySet());
                }
            }
            
            // For each service ID, resolve conflicts
            for (String serviceId : allServiceIds) {
                List<ServiceInstance> conflictingInstances = new ArrayList<>();
                
                for (Map<String, Map<String, ServiceInstance>> registry : registries) {
                    Map<String, ServiceInstance> services = registry.get(serviceType);
                    if (services != null && services.containsKey(serviceId)) {
                        conflictingInstances.add(services.get(serviceId));
                    }
                }
                
                ServiceInstance resolved = resolveConflict(conflictingInstances);
                if (resolved != null) {
                    mergedServices.put(serviceId, resolved);
                }
            }
            
            if (!mergedServices.isEmpty()) {
                merged.put(serviceType, mergedServices);
            }
        }
        
        return merged;
    }
    
    /**
     * Interface for checking service health during conflict resolution.
     */
    public interface ServiceHealthChecker {
        boolean isHealthy(ServiceInstance instance);
    }
}
