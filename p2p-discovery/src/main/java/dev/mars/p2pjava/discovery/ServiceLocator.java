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


import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * A utility class for locating and connecting to services.
 * This class provides load balancing and failover capabilities for service discovery.
 */
public class ServiceLocator {
    private static final Logger logger = Logger.getLogger(ServiceLocator.class.getName());
    
    // Load balancing strategies
    public enum LoadBalancingStrategy {
        RANDOM,     // Select a random service instance
        ROUND_ROBIN // Select service instances in a round-robin fashion
    }
    
    // The service registry to use for service discovery
    private final ServiceRegistry registry;
    
    // The load balancing strategy to use
    private final LoadBalancingStrategy loadBalancingStrategy;
    
    // Counter for round-robin load balancing
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);
    
    // Random number generator for random load balancing
    private final Random random = new Random();
    
    /**
     * Creates a new ServiceLocator with the specified registry and load balancing strategy.
     *
     * @param registry The service registry to use
     * @param loadBalancingStrategy The load balancing strategy to use
     */
    public ServiceLocator(ServiceRegistry registry, LoadBalancingStrategy loadBalancingStrategy) {
        this.registry = registry;
        this.loadBalancingStrategy = loadBalancingStrategy;
    }
    
    /**
     * Creates a new ServiceLocator with the specified registry and the default load balancing strategy (RANDOM).
     *
     * @param registry The service registry to use
     */
    public ServiceLocator(ServiceRegistry registry) {
        this(registry, LoadBalancingStrategy.RANDOM);
    }
    
    /**
     * Creates a new ServiceLocator with the default registry and load balancing strategy.
     */
    public ServiceLocator() {
        this(ServiceRegistryFactory.getInstance().getRegistry(), LoadBalancingStrategy.RANDOM);
    }
    
    /**
     * Locates a service instance of the specified type.
     * If multiple instances are available, one will be selected based on the load balancing strategy.
     *
     * @param serviceType The type of service to locate
     * @return A ServiceInstance if found, null otherwise
     */
    public ServiceInstance locateService(String serviceType) {
        List<ServiceInstance> services = registry.discoverServices(serviceType);
        
        if (services.isEmpty()) {
            logger.warning("No healthy services found of type: " + serviceType);
            return null;
        }
        
        // Select a service instance based on the load balancing strategy
        ServiceInstance selected;
        switch (loadBalancingStrategy) {
            case RANDOM:
                selected = services.get(random.nextInt(services.size()));
                break;
            case ROUND_ROBIN:
                int index = roundRobinCounter.getAndIncrement() % services.size();
                selected = services.get(index);
                break;
            default:
                selected = services.get(0);
                break;
        }
        
        logger.fine("Located service: " + selected);
        return selected;
    }
    
    /**
     * Locates a specific service instance by type and ID.
     *
     * @param serviceType The type of service
     * @param serviceId The unique identifier of the service instance
     * @return The ServiceInstance if found, null otherwise
     */
    public ServiceInstance locateService(String serviceType, String serviceId) {
        return registry.getService(serviceType, serviceId);
    }
    
    /**
     * Gets all healthy instances of a specific service type.
     *
     * @param serviceType The type of service to discover
     * @return A list of ServiceInstance objects representing the discovered services
     */
    public List<ServiceInstance> getAllServices(String serviceType) {
        return registry.discoverServices(serviceType);
    }
    
    /**
     * Checks if a service instance is healthy.
     *
     * @param serviceType The type of service
     * @param serviceId The unique identifier of the service instance
     * @return true if the service is healthy, false otherwise
     */
    public boolean isServiceHealthy(String serviceType, String serviceId) {
        return registry.isServiceHealthy(serviceType, serviceId);
    }
    
    /**
     * Updates the health status of a service instance.
     *
     * @param serviceType The type of service
     * @param serviceId The unique identifier of the service instance
     * @param healthy Whether the service is healthy
     * @return true if the update was successful, false otherwise
     */
    public boolean updateServiceHealth(String serviceType, String serviceId, boolean healthy) {
        return registry.updateServiceHealth(serviceType, serviceId, healthy);
    }
}