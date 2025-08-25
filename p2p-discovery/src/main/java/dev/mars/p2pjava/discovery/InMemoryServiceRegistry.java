package dev.mars.p2pjava.discovery;

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

    // Singleton instance with volatile for thread-safety
    private static volatile InMemoryServiceRegistry instance;

    // Flag to track if the registry is running
    private volatile boolean running = false;

    /**
     * Private constructor to enforce singleton pattern.
     */
    private InMemoryServiceRegistry() {
    }

    /**
     * Public constructor for dependency injection.
     * Creates a new instance that is not part of the singleton pattern.
     */
    public InMemoryServiceRegistry(boolean newInstance) {
        // This constructor allows creating new instances for dependency injection
    }

    /**
     * Gets the singleton instance of the registry using double-checked locking
     * for thread safety.
     *
     * @return The singleton instance
     */
    public static InMemoryServiceRegistry getInstance() {
        // First check (no synchronization)
        if (instance == null) {
            // Synchronize on class object
            synchronized (InMemoryServiceRegistry.class) {
                // Second check (with synchronization)
                if (instance == null) {
                    instance = new InMemoryServiceRegistry();
                }
            }
        }
        return instance;
    }

    /**
     * Resets the singleton instance for testing purposes.
     * This method should only be used in test environments.
     */
    public static void reset() {
        synchronized (InMemoryServiceRegistry.class) {
            if (instance != null) {
                instance.stop();
                instance = null;
            }
        }
    }

    /**
     * Registers a service instance with the registry.
     * 
     * @param serviceType The type of service (e.g., "tracker", "indexserver"). Must not be null or empty.
     * @param serviceId A unique identifier for this service instance. Must not be null or empty.
     * @param host The hostname or IP address of the service. Must not be null or empty.
     * @param port The port number of the service. Must be a valid port number (0-65535).
     * @param metadata Additional metadata about the service (optional, can be null).
     * @return true if registration was successful, false if:
     *         - The registry is not running
     *         - Any of the required parameters (serviceType, serviceId, host) are null or empty
     *         - The port is invalid
     *         - A service with the same ID is already registered (duplicate registration)
     */
    @Override
    public boolean registerService(String serviceType, String serviceId, String host, int port, 
                                  Map<String, String> metadata) {
        if (!running) {
            logger.warning("Cannot register service: registry is not running");
            return false;
        }

        if (serviceType == null || serviceId == null || host == null || 
            serviceType.isEmpty() || serviceId.isEmpty() || host.isEmpty() || 
            port < 0 || port > 65535) {
            logger.warning("Cannot register service: invalid parameters");
            return false;
        }

        ServiceInstance instance = new ServiceInstance(serviceType, serviceId, host, port, metadata);

        // Get or create the map for this service type
        Map<String, ServiceInstance> serviceMap = registry.computeIfAbsent(
            serviceType, k -> new ConcurrentHashMap<>());

        // Check for duplicate registration
        if (serviceMap.containsKey(serviceId)) {
            logger.warning("Cannot register service: duplicate service ID: " + serviceId);
            return false;
        }

        // Add the service instance
        serviceMap.put(serviceId, instance);

        logger.info("Registered service: " + instance);
        return true;
    }

    /**
     * Deregisters a service instance from the registry.
     * 
     * @param serviceType The type of service. Must not be null or empty.
     * @param serviceId The unique identifier of the service instance. Must not be null or empty.
     * @return true if deregistration was successful, false if:
     *         - The registry is not running
     *         - Any of the required parameters (serviceType, serviceId) are null or empty
     *         - The service type does not exist in the registry
     *         - The service ID does not exist for the specified service type
     */
    @Override
    public boolean deregisterService(String serviceType, String serviceId) {
        if (!running) {
            logger.warning("Cannot deregister service: registry is not running");
            return false;
        }

        if (serviceType == null || serviceId == null || 
            serviceType.isEmpty() || serviceId.isEmpty()) {
            logger.warning("Cannot deregister service: missing or invalid required parameters");
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

    /**
     * Discovers all instances of a specific service type.
     * This method returns only healthy services.
     * 
     * @param serviceType The type of service to discover. Must not be null or empty.
     * @return A list of ServiceInstance objects representing the discovered services.
     *         Returns an empty list if:
     *         - The registry is not running
     *         - The serviceType parameter is null or empty
     *         - No services of the specified type are registered
     *         - No healthy services of the specified type are found
     */
    @Override
    public List<ServiceInstance> discoverServices(String serviceType) {
        if (!running) {
            logger.warning("Cannot discover services: registry is not running");
            return Collections.emptyList();
        }

        if (serviceType == null || serviceType.isEmpty()) {
            logger.warning("Cannot discover services: missing or invalid service type");
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

    /**
     * Alias for discoverServices to maintain backward compatibility with tests.
     * 
     * @param serviceType The type of service to discover
     * @return A list of ServiceInstance objects representing the discovered services
     */
    public List<ServiceInstance> findServiceInstances(String serviceType) {
        return discoverServices(serviceType);
    }

    /**
     * Gets a specific service instance by type and ID.
     * Unlike discoverServices, this method returns the service instance regardless of its health status.
     * 
     * @param serviceType The type of service. Must not be null or empty.
     * @param serviceId The unique identifier of the service instance. Must not be null or empty.
     * @return The ServiceInstance if found, null if:
     *         - The registry is not running
     *         - Any of the required parameters (serviceType, serviceId) are null or empty
     *         - The service type does not exist in the registry
     *         - The service ID does not exist for the specified service type
     */
    @Override
    public ServiceInstance getService(String serviceType, String serviceId) {
        if (!running) {
            logger.warning("Cannot get service: registry is not running");
            return null;
        }

        if (serviceType == null || serviceId == null || 
            serviceType.isEmpty() || serviceId.isEmpty()) {
            logger.warning("Cannot get service: missing or invalid required parameters");
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

    /**
     * Alias for getService to maintain backward compatibility with tests.
     * Unlike discoverServices, this method returns the service instance regardless of its health status.
     * 
     * @param serviceType The type of service. Must not be null or empty.
     * @param serviceId The unique identifier of the service instance. Must not be null or empty.
     * @return The ServiceInstance if found, null if:
     *         - The registry is not running
     *         - Any of the required parameters (serviceType, serviceId) are null or empty
     *         - The service type does not exist in the registry
     *         - The service ID does not exist for the specified service type
     */
    public ServiceInstance findServiceInstanceById(String serviceType, String serviceId) {
        return getService(serviceType, serviceId);
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
