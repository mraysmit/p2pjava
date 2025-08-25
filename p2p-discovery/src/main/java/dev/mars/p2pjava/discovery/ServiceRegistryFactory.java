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
            case "distributed":
                // Create distributed registry with default configuration
                String peerId = System.getProperty("peer.id", "peer-" + System.currentTimeMillis());
                registry = new DistributedServiceRegistry(peerId);
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

    /**
     * Creates a distributed service registry with custom configuration.
     *
     * @param peerId The unique identifier for this peer
     * @param gossipPort The port for gossip protocol communication
     * @param bootstrapPeers Set of bootstrap peer addresses to connect to
     * @return A configured DistributedServiceRegistry instance
     */
    public static DistributedServiceRegistry createDistributedRegistry(String peerId,
                                                                      int gossipPort,
                                                                      java.util.Set<String> bootstrapPeers) {
        return new DistributedServiceRegistry(peerId, gossipPort, bootstrapPeers);
    }

    /**
     * Creates a distributed service registry with default configuration.
     *
     * @param peerId The unique identifier for this peer
     * @return A configured DistributedServiceRegistry instance
     */
    public static DistributedServiceRegistry createDistributedRegistry(String peerId) {
        return new DistributedServiceRegistry(peerId);
    }
}