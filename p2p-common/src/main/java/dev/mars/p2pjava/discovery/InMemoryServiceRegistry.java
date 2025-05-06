package dev.mars.p2pjava.discovery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A simple in-memory implementation of the ServiceRegistry interface.
 * This implementation is suitable for testing and development, or as a fallback
 * when external service registry systems are not available.
 */
public class InMemoryServiceRegistry implements ServiceRegistry {
    private static final Logger logger = Logger.getLogger(InMemoryServiceRegistry.class.getName());
    
    // Map of service type -> (service ID -> service instance)
    private final Map<String, Map<String, ServiceInstance>> registry = new ConcurrentHashMap<>();
    
    // Singleton instance
    private static InMemoryServiceRegistry instance;
    
    // Flag to track if the registry is running
    private volatile boolean running = false;
    
    /**
     * Private constructor to enforce singleton pattern.
     */
    private InMemoryServiceRegistry() {
    }
    
    /**
     * Gets the singleton instance of the registry.
     *
     * @return The singleton instance
     */
    public static synchronized InMemoryServiceRegistry getInstance() {
        if (instance == null) {
            instance = new InMemoryServiceRegistry();
        }
        return instance;
    }
    
    @Override
    public boolean registerService(String serviceType, String serviceId, String host, int port, 
                                  Map<String, String> metadata) {
        if (!running) {
            logger.warning("Cannot register service: registry is not running");
            return false;
        }
        
        if (serviceType == null || serviceId == null || host == null) {
            logger.warning("Cannot register service: missing required parameters");
            return false;
        }
        
        ServiceInstance instance = new ServiceInstance(serviceType, serviceId, host, port, metadata);
        
        // Get or create the map for this service type
        Map<String, ServiceInstance> serviceMap = registry.computeIfAbsent(
            serviceType, k -> new ConcurrentHashMap<>());
        
        // Add the service instance
        serviceMap.put(serviceId, instance);
        
        logger.info("Registered service: " + instance);
        return true;
    }
    
    @Override
    public boolean deregisterService(String serviceType, String serviceId) {
        if (!running) {
            logger.warning("Cannot deregister service: registry is not running");
            return false;
        }
        
        if (serviceType == null || serviceId == null) {
            logger.warning("Cannot deregister service: missing required parameters");
            return false;
        }
        
        Map<String, ServiceInstance> serviceMap = registry.get(serviceType);
        if (serviceMap == null) {
            logger.warning("Cannot deregister service: service type not found: " + serviceType);
            return false;
        }
        
        ServiceInstance removed = serviceMap.remove(serviceId);
        if (removed == null) {
            logger.warning("Cannot deregister service: service ID not found: " + serviceId);
            return false;
        }
        
        logger.info("Deregistered service: " + removed);
        return true;
    }
    
    @Override
    public List<ServiceInstance> discoverServices(String serviceType) {
        if (!running) {
            logger.warning("Cannot discover services: registry is not running");
            return Collections.emptyList();
        }
        
        if (serviceType == null) {
            logger.warning("Cannot discover services: missing service type");
            return Collections.emptyList();
        }
        
        Map<String, ServiceInstance> serviceMap = registry.get(serviceType);
        if (serviceMap == null) {
            logger.fine("No services found for type: " + serviceType);
            return Collections.emptyList();
        }
        
        // Return only healthy services
        List<ServiceInstance> healthyServices = serviceMap.values().stream()
            .filter(ServiceInstance::isHealthy)
            .collect(Collectors.toList());
        
        logger.fine("Discovered " + healthyServices.size() + " healthy services of type: " + serviceType);
        return new ArrayList<>(healthyServices);
    }
    
    @Override
    public ServiceInstance getService(String serviceType, String serviceId) {
        if (!running) {
            logger.warning("Cannot get service: registry is not running");
            return null;
        }
        
        if (serviceType == null || serviceId == null) {
            logger.warning("Cannot get service: missing required parameters");
            return null;
        }
        
        Map<String, ServiceInstance> serviceMap = registry.get(serviceType);
        if (serviceMap == null) {
            logger.fine("Service type not found: " + serviceType);
            return null;
        }
        
        ServiceInstance instance = serviceMap.get(serviceId);
        if (instance == null) {
            logger.fine("Service ID not found: " + serviceId);
            return null;
        }
        
        return instance;
    }
    
    @Override
    public boolean isServiceHealthy(String serviceType, String serviceId) {
        ServiceInstance instance = getService(serviceType, serviceId);
        return instance != null && instance.isHealthy();
    }
    
    @Override
    public boolean updateServiceHealth(String serviceType, String serviceId, boolean healthy) {
        if (!running) {
            logger.warning("Cannot update service health: registry is not running");
            return false;
        }
        
        ServiceInstance instance = getService(serviceType, serviceId);
        if (instance == null) {
            logger.warning("Cannot update service health: service not found");
            return false;
        }
        
        instance.setHealthy(healthy);
        logger.info("Updated health status of service " + serviceType + ":" + serviceId + " to " + healthy);
        return true;
    }
    
    @Override
    public void start() {
        if (running) {
            logger.info("Service registry already running");
            return;
        }
        
        running = true;
        logger.info("Started in-memory service registry");
    }
    
    @Override
    public void stop() {
        if (!running) {
            logger.info("Service registry already stopped");
            return;
        }
        
        running = false;
        registry.clear();
        logger.info("Stopped in-memory service registry");
    }
    
    /**
     * Clears all registered services from the registry.
     * This is useful for testing.
     */
    public void clear() {
        if (running) {
            registry.clear();
            logger.info("Cleared in-memory service registry");
        }
    }
    
    /**
     * Gets the total number of registered services across all types.
     *
     * @return The total number of registered services
     */
    public int getServiceCount() {
        return registry.values().stream()
            .mapToInt(Map::size)
            .sum();
    }
    
    /**
     * Gets the number of registered services of a specific type.
     *
     * @param serviceType The type of service
     * @return The number of registered services of the specified type
     */
    public int getServiceCount(String serviceType) {
        Map<String, ServiceInstance> serviceMap = registry.get(serviceType);
        return serviceMap != null ? serviceMap.size() : 0;
    }
}