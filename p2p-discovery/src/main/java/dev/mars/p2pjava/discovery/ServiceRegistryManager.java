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


import java.util.Set;
import java.util.logging.Logger;

/**
 * Instance-based service registry manager that replaces the singleton ServiceRegistryFactory.
 * Provides proper dependency injection support and lifecycle management.
 */
public class ServiceRegistryManager {
    private static final Logger logger = Logger.getLogger(ServiceRegistryManager.class.getName());
    
    public enum RegistryType {
        IN_MEMORY,
        DISTRIBUTED,
        HYBRID
    }
    
    private final RegistryType defaultType;
    private ServiceRegistry currentRegistry;
    private boolean started = false;
    
    /**
     * Creates a ServiceRegistryManager with in-memory registry as default.
     */
    public ServiceRegistryManager() {
        this(RegistryType.IN_MEMORY);
    }
    
    /**
     * Creates a ServiceRegistryManager with specified default type.
     */
    public ServiceRegistryManager(RegistryType defaultType) {
        this.defaultType = defaultType;
        logger.info("ServiceRegistryManager created with default type: " + defaultType);
    }
    
    /**
     * Starts the service registry manager.
     */
    public void start() {
        if (started) {
            logger.warning("ServiceRegistryManager already started");
            return;
        }
        
        currentRegistry = createRegistry(defaultType);
        currentRegistry.start();
        started = true;
        
        logger.info("ServiceRegistryManager started with " + defaultType + " registry");
    }
    
    /**
     * Stops the service registry manager.
     */
    public void stop() {
        if (!started) {
            return;
        }
        
        if (currentRegistry != null) {
            currentRegistry.stop();
            currentRegistry = null;
        }
        
        started = false;
        logger.info("ServiceRegistryManager stopped");
    }
    
    /**
     * Gets the current service registry.
     */
    public ServiceRegistry getRegistry() {
        if (!started) {
            throw new IllegalStateException("ServiceRegistryManager not started");
        }
        return currentRegistry;
    }
    
    /**
     * Creates a new in-memory service registry.
     */
    public ServiceRegistry createInMemoryRegistry() {
        return new InMemoryServiceRegistry(true);
    }
    
    /**
     * Creates a new distributed service registry.
     */
    public ServiceRegistry createDistributedRegistry(String peerId, int gossipPort, Set<String> bootstrapPeers) {
        return new DistributedServiceRegistry(peerId, gossipPort, bootstrapPeers);
    }
    
    /**
     * Creates a new distributed service registry with conflict resolution.
     */
    public ServiceRegistry createDistributedRegistry(String peerId, int gossipPort, 
                                                   Set<String> bootstrapPeers,
                                                   ConflictResolutionStrategy conflictResolver) {
        // Note: This would require extending DistributedServiceRegistry to accept conflict resolver
        // For now, create basic distributed registry
        return new DistributedServiceRegistry(peerId, gossipPort, bootstrapPeers);
    }
    
    /**
     * Switches to a different registry type.
     */
    public void switchRegistry(RegistryType newType) {
        if (!started) {
            throw new IllegalStateException("ServiceRegistryManager not started");
        }
        
        logger.info("Switching registry from " + getRegistryType(currentRegistry) + " to " + newType);
        
        // Stop current registry
        if (currentRegistry != null) {
            currentRegistry.stop();
        }
        
        // Create and start new registry
        currentRegistry = createRegistry(newType);
        currentRegistry.start();
        
        logger.info("Successfully switched to " + newType + " registry");
    }
    
    /**
     * Switches to a specific registry instance.
     */
    public void switchRegistry(ServiceRegistry newRegistry) {
        if (!started) {
            throw new IllegalStateException("ServiceRegistryManager not started");
        }
        
        if (newRegistry == null) {
            throw new IllegalArgumentException("New registry cannot be null");
        }
        
        logger.info("Switching to custom registry: " + newRegistry.getClass().getSimpleName());
        
        // Stop current registry
        if (currentRegistry != null) {
            currentRegistry.stop();
        }
        
        // Use new registry
        currentRegistry = newRegistry;
        if (!isRegistryStarted(newRegistry)) {
            currentRegistry.start();
        }
        
        logger.info("Successfully switched to custom registry");
    }
    
    /**
     * Gets the current registry type.
     */
    public RegistryType getCurrentRegistryType() {
        if (currentRegistry == null) {
            return null;
        }
        return getRegistryType(currentRegistry);
    }
    
    /**
     * Checks if the manager is started.
     */
    public boolean isStarted() {
        return started;
    }
    
    /**
     * Gets the default registry type.
     */
    public RegistryType getDefaultType() {
        return defaultType;
    }
    
    // Helper methods
    
    private ServiceRegistry createRegistry(RegistryType type) {
        switch (type) {
            case IN_MEMORY:
                return new InMemoryServiceRegistry(true);
                
            case DISTRIBUTED:
                // Create with default settings - in real usage, these would be injected
                return new DistributedServiceRegistry(
                    "default-peer-" + System.currentTimeMillis(),
                    6003,
                    Set.of()
                );
                
            case HYBRID:
                // For now, return in-memory. In a real implementation, this would be a hybrid registry
                logger.warning("Hybrid registry not yet implemented, using in-memory");
                return new InMemoryServiceRegistry(true);
                
            default:
                throw new IllegalArgumentException("Unknown registry type: " + type);
        }
    }
    
    private RegistryType getRegistryType(ServiceRegistry registry) {
        if (registry instanceof InMemoryServiceRegistry) {
            return RegistryType.IN_MEMORY;
        } else if (registry instanceof DistributedServiceRegistry) {
            return RegistryType.DISTRIBUTED;
        } else {
            return RegistryType.HYBRID; // Assume hybrid for unknown types
        }
    }
    
    private boolean isRegistryStarted(ServiceRegistry registry) {
        // This is a simple heuristic - in a real implementation, 
        // ServiceRegistry interface might have an isStarted() method
        try {
            // Try to perform a simple operation to see if registry is active
            registry.discoverServices("test");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Builder for ServiceRegistryManager.
     */
    public static class Builder {
        private RegistryType defaultType = RegistryType.IN_MEMORY;
        
        public Builder defaultType(RegistryType type) {
            this.defaultType = type;
            return this;
        }
        
        public ServiceRegistryManager build() {
            return new ServiceRegistryManager(defaultType);
        }
    }
    
    /**
     * Creates a builder for ServiceRegistryManager.
     */
    public static Builder builder() {
        return new Builder();
    }
}
