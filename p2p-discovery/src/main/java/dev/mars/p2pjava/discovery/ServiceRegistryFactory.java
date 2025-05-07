package dev.mars.p2pjava.discovery;

import java.util.logging.Logger;

/**
 * Factory class for creating ServiceRegistry instances.
 * This factory supports different types of service registries, including
 * the in-memory implementation and potentially external systems like
 * ZooKeeper or Consul in the future.
 */
public class ServiceRegistryFactory {
    private static final Logger logger = Logger.getLogger(ServiceRegistryFactory.class.getName());
    
    // Singleton instance
    private static ServiceRegistryFactory instance;
    
    // The default registry type
    private static final String DEFAULT_REGISTRY_TYPE = "memory";
    
    // The current registry instance
    private ServiceRegistry registry;
    
    /**
     * Private constructor to enforce singleton pattern.
     */
    private ServiceRegistryFactory() {
    }
    
    /**
     * Gets the singleton instance of the factory.
     *
     * @return The singleton instance
     */
    public static synchronized ServiceRegistryFactory getInstance() {
        if (instance == null) {
            instance = new ServiceRegistryFactory();
        }
        return instance;
    }
    
    /**
     * Gets a ServiceRegistry instance of the specified type.
     * If a registry of the specified type already exists, it will be returned.
     * Otherwise, a new registry will be created.
     *
     * @param type The type of registry to get (e.g., "memory", "zookeeper", "consul")
     * @return A ServiceRegistry instance of the specified type
     * @throws IllegalArgumentException If the specified type is not supported
     */
    public synchronized ServiceRegistry getRegistry(String type) {
        if (registry != null) {
            return registry;
        }
        
        switch (type.toLowerCase()) {
            case "memory":
                registry = InMemoryServiceRegistry.getInstance();
                break;
            // Add support for other registry types here
            default:
                throw new IllegalArgumentException("Unsupported registry type: " + type);
        }
        
        // Start the registry
        registry.start();
        logger.info("Created and started " + type + " service registry");
        
        return registry;
    }
    
    /**
     * Gets a ServiceRegistry instance of the default type.
     * The default type is "memory".
     *
     * @return A ServiceRegistry instance of the default type
     */
    public ServiceRegistry getRegistry() {
        return getRegistry(DEFAULT_REGISTRY_TYPE);
    }
    
    /**
     * Stops and clears the current registry instance.
     * This is useful for testing and when shutting down the application.
     */
    public synchronized void shutdown() {
        if (registry != null) {
            registry.stop();
            registry = null;
            logger.info("Shut down service registry");
        }
    }
}