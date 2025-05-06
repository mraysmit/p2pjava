package dev.mars.p2pjava.discovery;

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