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

/**
 * Interface for service registry implementations.
 * The service registry is responsible for registering and discovering services.
 */
public interface ServiceRegistry {
    
    /**
     * Registers a service instance with the registry.
     *
     * @param serviceType The type of service (e.g., "tracker", "indexserver")
     * @param serviceId A unique identifier for this service instance
     * @param host The hostname or IP address of the service
     * @param port The port number of the service
     * @param metadata Additional metadata about the service (optional)
     * @return true if registration was successful, false otherwise
     */
    boolean registerService(String serviceType, String serviceId, String host, int port, 
                           java.util.Map<String, String> metadata);
    
    /**
     * Deregisters a service instance from the registry.
     *
     * @param serviceType The type of service
     * @param serviceId The unique identifier of the service instance
     * @return true if deregistration was successful, false otherwise
     */
    boolean deregisterService(String serviceType, String serviceId);
    
    /**
     * Discovers all instances of a specific service type.
     *
     * @param serviceType The type of service to discover
     * @return A list of ServiceInstance objects representing the discovered services
     */
    List<ServiceInstance> discoverServices(String serviceType);
    
    /**
     * Gets a specific service instance by type and ID.
     *
     * @param serviceType The type of service
     * @param serviceId The unique identifier of the service instance
     * @return The ServiceInstance if found, null otherwise
     */
    ServiceInstance getService(String serviceType, String serviceId);
    
    /**
     * Checks if a service instance is healthy.
     *
     * @param serviceType The type of service
     * @param serviceId The unique identifier of the service instance
     * @return true if the service is healthy, false otherwise
     */
    boolean isServiceHealthy(String serviceType, String serviceId);
    
    /**
     * Updates the health status of a service instance.
     *
     * @param serviceType The type of service
     * @param serviceId The unique identifier of the service instance
     * @param healthy Whether the service is healthy
     * @return true if the update was successful, false otherwise
     */
    boolean updateServiceHealth(String serviceType, String serviceId, boolean healthy);
    
    /**
     * Starts the service registry.
     */
    void start();
    
    /**
     * Stops the service registry.
     */
    void stop();
}