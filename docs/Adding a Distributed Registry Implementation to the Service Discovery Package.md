
# Adding a Distributed Registry Implementation to the Service Discovery Package

Based on the review of the existing code and the scalability improvements document, I'll outline how to add a distributed registry implementation to the `dev.mars.p2pjava.discovery` service. I'll focus on implementing a ZooKeeper-based service registry, as it's a widely used solution for distributed service discovery.

## Implementation Plan

### 1. Add ZooKeeper Dependencies

First, add the Apache ZooKeeper and Curator (a high-level ZooKeeper client) dependencies to the `p2p-peer/pom.xml` file:

```xml
<!-- ZooKeeper -->
<dependency>
    <groupId>org.apache.zookeeper</groupId>
    <artifactId>zookeeper</artifactId>
    <version>3.8.3</version>
    <exclusions>
        <exclusion>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-log4j12</artifactId>
        </exclusion>
    </exclusions>
</dependency>

<!-- Curator Framework - High-level ZooKeeper client -->
<dependency>
    <groupId>org.apache.curator</groupId>
    <artifactId>curator-framework</artifactId>
    <version>5.5.0</version>
</dependency>

<!-- Curator Recipes - Additional utilities for ZooKeeper -->
<dependency>
    <groupId>org.apache.curator</groupId>
    <artifactId>curator-recipes</artifactId>
    <version>5.5.0</version>
</dependency>
```

### 2. Create ZooKeeperServiceRegistry Class

Create a new class that implements the `ServiceRegistry` interface using ZooKeeper:

```java
package dev.mars.p2pjava.discovery;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A ZooKeeper-based implementation of the ServiceRegistry interface.
 * This implementation uses ZooKeeper for distributed service discovery.
 */
public class ZooKeeperServiceRegistry implements ServiceRegistry {
    private static final Logger logger = Logger.getLogger(ZooKeeperServiceRegistry.class.getName());
    
    // Base path for all services in ZooKeeper
    private static final String BASE_PATH = "/services";
    
    // ZooKeeper client
    private final CuratorFramework client;
    
    // Cache of service instances (for faster lookups)
    private final Map<String, Map<String, ServiceInstance>> serviceCache = new ConcurrentHashMap<>();
    
    // Path caches for watching service changes
    private final Map<String, PathChildrenCache> pathCaches = new ConcurrentHashMap<>();
    
    // Flag to track if the registry is running
    private volatile boolean running = false;
    
    // Singleton instance
    private static ZooKeeperServiceRegistry instance;
    
    /**
     * Creates a new ZooKeeperServiceRegistry with the specified connection string.
     *
     * @param connectionString The ZooKeeper connection string (e.g., "localhost:2181")
     */
    private ZooKeeperServiceRegistry(String connectionString) {
        // Create a CuratorFramework client with exponential backoff retry policy
        client = CuratorFrameworkFactory.builder()
                .connectString(connectionString)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .namespace("p2pjava") // Use a namespace to isolate our znodes
                .build();
    }
    
    /**
     * Gets the singleton instance of the registry.
     *
     * @param connectionString The ZooKeeper connection string
     * @return The singleton instance
     */
    public static synchronized ZooKeeperServiceRegistry getInstance(String connectionString) {
        if (instance == null) {
            instance = new ZooKeeperServiceRegistry(connectionString);
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
        
        try {
            // Create service instance
            ServiceInstance instance = new ServiceInstance(serviceType, serviceId, host, port, metadata);
            
            // Serialize service instance to JSON
            String json = serializeServiceInstance(instance);
            
            // Create the service path if it doesn't exist
            String servicePath = BASE_PATH + "/" + serviceType;
            try {
                client.create()
                      .creatingParentsIfNeeded()
                      .withMode(CreateMode.PERSISTENT)
                      .forPath(servicePath);
            } catch (KeeperException.NodeExistsException e) {
                // Path already exists, which is fine
            }
            
            // Register the service instance as an ephemeral node
            String instancePath = servicePath + "/" + serviceId;
            client.create()
                  .orSetData()
                  .withMode(CreateMode.EPHEMERAL)
                  .forPath(instancePath, json.getBytes(StandardCharsets.UTF_8));
            
            // Update local cache
            Map<String, ServiceInstance> serviceMap = serviceCache.computeIfAbsent(
                serviceType, k -> new ConcurrentHashMap<>());
            serviceMap.put(serviceId, instance);
            
            logger.info("Registered service: " + instance);
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to register service: " + e.getMessage(), e);
            return false;
        }
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
        
        try {
            // Remove the service instance from ZooKeeper
            String instancePath = BASE_PATH + "/" + serviceType + "/" + serviceId;
            client.delete().forPath(instancePath);
            
            // Update local cache
            Map<String, ServiceInstance> serviceMap = serviceCache.get(serviceType);
            if (serviceMap != null) {
                ServiceInstance removed = serviceMap.remove(serviceId);
                if (removed != null) {
                    logger.info("Deregistered service: " + removed);
                    return true;
                }
            }
            
            logger.warning("Cannot deregister service: service not found in cache");
            return false;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to deregister service: " + e.getMessage(), e);
            return false;
        }
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
        
        // Get from local cache for faster lookups
        Map<String, ServiceInstance> serviceMap = serviceCache.get(serviceType);
        if (serviceMap == null) {
            logger.fine("No services found for type: " + serviceType);
            return Collections.emptyList();
        }
        
        // Return only healthy services
        List<ServiceInstance> healthyServices = new ArrayList<>();
        for (ServiceInstance instance : serviceMap.values()) {
            if (instance.isHealthy()) {
                healthyServices.add(instance);
            }
        }
        
        logger.fine("Discovered " + healthyServices.size() + " healthy services of type: " + serviceType);
        return healthyServices;
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
        
        // Get from local cache for faster lookups
        Map<String, ServiceInstance> serviceMap = serviceCache.get(serviceType);
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
        
        try {
            // Update instance health status
            instance.setHealthy(healthy);
            
            // Update ZooKeeper
            String instancePath = BASE_PATH + "/" + serviceType + "/" + serviceId;
            String json = serializeServiceInstance(instance);
            client.setData().forPath(instancePath, json.getBytes(StandardCharsets.UTF_8));
            
            logger.info("Updated health status of service " + serviceType + ":" + serviceId + " to " + healthy);
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to update service health: " + e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public void start() {
        if (running) {
            logger.info("ZooKeeper service registry already running");
            return;
        }
        
        try {
            // Start the ZooKeeper client
            client.start();
            
            // Create the base path if it doesn't exist
            try {
                client.create()
                      .creatingParentsIfNeeded()
                      .withMode(CreateMode.PERSISTENT)
                      .forPath(BASE_PATH);
            } catch (KeeperException.NodeExistsException e) {
                // Path already exists, which is fine
            }
            
            // Initialize the service cache from ZooKeeper
            initializeServiceCache();
            
            running = true;
            logger.info("Started ZooKeeper service registry");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to start ZooKeeper service registry: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void stop() {
        if (!running) {
            logger.info("ZooKeeper service registry already stopped");
            return;
        }
        
        try {
            // Close all path caches
            for (PathChildrenCache cache : pathCaches.values()) {
                cache.close();
            }
            pathCaches.clear();
            
            // Close the ZooKeeper client
            client.close();
            
            // Clear the service cache
            serviceCache.clear();
            
            running = false;
            logger.info("Stopped ZooKeeper service registry");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to stop ZooKeeper service registry: " + e.getMessage(), e);
        }
    }
    
    /**
     * Initializes the service cache from ZooKeeper.
     */
    private void initializeServiceCache() throws Exception {
        // Get all service types
        List<String> serviceTypes = client.getChildren().forPath(BASE_PATH);
        
        for (String serviceType : serviceTypes) {
            // Create a path cache for this service type
            String servicePath = BASE_PATH + "/" + serviceType;
            PathChildrenCache pathCache = new PathChildrenCache(client, servicePath, true);
            
            // Add listener for service instance changes
            pathCache.getListenable().addListener((client, event) -> {
                switch (event.getType()) {
                    case CHILD_ADDED:
                    case CHILD_UPDATED:
                        handleServiceInstanceUpdate(serviceType, event);
                        break;
                    case CHILD_REMOVED:
                        handleServiceInstanceRemoval(serviceType, event);
                        break;
                    default:
                        // Ignore other events
                        break;
                }
            });
            
            // Start the path cache
            pathCache.start();
            pathCaches.put(serviceType, pathCache);
            
            // Load initial service instances
            List<String> serviceIds = client.getChildren().forPath(servicePath);
            Map<String, ServiceInstance> serviceMap = new ConcurrentHashMap<>();
            
            for (String serviceId : serviceIds) {
                String instancePath = servicePath + "/" + serviceId;
                byte[] data = client.getData().forPath(instancePath);
                ServiceInstance instance = deserializeServiceInstance(new String(data, StandardCharsets.UTF_8));
                if (instance != null) {
                    serviceMap.put(serviceId, instance);
                }
            }
            
            serviceCache.put(serviceType, serviceMap);
        }
    }
    
    /**
     * Handles a service instance update event.
     */
    private void handleServiceInstanceUpdate(String serviceType, PathChildrenCacheEvent event) {
        try {
            String serviceId = event.getData().getPath().substring(event.getData().getPath().lastIndexOf('/') + 1);
            byte[] data = event.getData().getData();
            ServiceInstance instance = deserializeServiceInstance(new String(data, StandardCharsets.UTF_8));
            
            if (instance != null) {
                Map<String, ServiceInstance> serviceMap = serviceCache.computeIfAbsent(
                    serviceType, k -> new ConcurrentHashMap<>());
                serviceMap.put(serviceId, instance);
                logger.fine("Updated service instance: " + instance);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to handle service instance update: " + e.getMessage(), e);
        }
    }
    
    /**
     * Handles a service instance removal event.
     */
    private void handleServiceInstanceRemoval(String serviceType, PathChildrenCacheEvent event) {
        try {
            String serviceId = event.getData().getPath().substring(event.getData().getPath().lastIndexOf('/') + 1);
            Map<String, ServiceInstance> serviceMap = serviceCache.get(serviceType);
            
            if (serviceMap != null) {
                ServiceInstance removed = serviceMap.remove(serviceId);
                if (removed != null) {
                    logger.fine("Removed service instance: " + removed);
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to handle service instance removal: " + e.getMessage(), e);
        }
    }
    
    /**
     * Serializes a ServiceInstance to JSON.
     * Note: In a real implementation, you would use a proper JSON library like Jackson or Gson.
     */
    private String serializeServiceInstance(ServiceInstance instance) {
        // Simple JSON serialization for demonstration purposes
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"serviceType\":\"").append(instance.getServiceType()).append("\",");
        json.append("\"serviceId\":\"").append(instance.getServiceId()).append("\",");
        json.append("\"host\":\"").append(instance.getHost()).append("\",");
        json.append("\"port\":").append(instance.getPort()).append(",");
        json.append("\"healthy\":").append(instance.isHealthy()).append(",");
        json.append("\"lastUpdated\":").append(instance.getLastUpdated()).append(",");
        json.append("\"metadata\":{");
        
        Map<String, String> metadata = instance.getMetadata();
        if (!metadata.isEmpty()) {
            boolean first = true;
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                if (!first) {
                    json.append(",");
                }
                json.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
                first = false;
            }
        }
        
        json.append("}}");
        return json.toString();
    }
    
    /**
     * Deserializes a ServiceInstance from JSON.
     * Note: In a real implementation, you would use a proper JSON library like Jackson or Gson.
     */
    private ServiceInstance deserializeServiceInstance(String json) {
        // This is a very simplified JSON parsing for demonstration purposes
        // In a real implementation, use a proper JSON library
        try {
            // Extract service type
            String serviceType = extractJsonValue(json, "serviceType");
            
            // Extract service ID
            String serviceId = extractJsonValue(json, "serviceId");
            
            // Extract host
            String host = extractJsonValue(json, "host");
            
            // Extract port
            String portStr = extractJsonValue(json, "port");
            int port = Integer.parseInt(portStr);
            
            // Extract metadata
            Map<String, String> metadata = new HashMap<>();
            // Simplified metadata extraction - in a real implementation, use a proper JSON parser
            
            // Create service instance
            ServiceInstance instance = new ServiceInstance(serviceType, serviceId, host, port, metadata);
            
            // Extract healthy status
            String healthyStr = extractJsonValue(json, "healthy");
            boolean healthy = Boolean.parseBoolean(healthyStr);
            instance.setHealthy(healthy);
            
            return instance;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to deserialize service instance: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Extracts a value from a JSON string.
     * This is a very simplified method for demonstration purposes.
     */
    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) {
            // Try without quotes (for numeric values)
            pattern = "\"" + key + "\":";
            start = json.indexOf(pattern);
            if (start == -1) {
                return null;
            }
            start += pattern.length();
            int end = json.indexOf(",", start);
            if (end == -1) {
                end = json.indexOf("}", start);
            }
            return json.substring(start, end).trim();
        }
        start += pattern.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
}
```

### 3. Update ServiceRegistryFactory

Update the `ServiceRegistryFactory` class to support the ZooKeeper implementation:

```java
package dev.mars.p2pjava.discovery;

import java.util.logging.Logger;

/**
 * Factory class for creating ServiceRegistry instances.
 * This factory supports different types of service registries, including
 * the in-memory implementation and external systems like ZooKeeper.
 */
public class ServiceRegistryFactory {
    private static final Logger logger = Logger.getLogger(ServiceRegistryFactory.class.getName());
    
    // Singleton instance
    private static ServiceRegistryFactory instance;
    
    // The default registry type
    private static final String DEFAULT_REGISTRY_TYPE = "memory";
    
    // The current registry instance
    private ServiceRegistry registry;
    
    // Default ZooKeeper connection string
    private static final String DEFAULT_ZOOKEEPER_CONNECTION = "localhost:2181";
    
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
     * @param type The type of registry to get (e.g., "memory", "zookeeper")
     * @param config Configuration parameters for the registry (e.g., connection string for ZooKeeper)
     * @return A ServiceRegistry instance of the specified type
     * @throws IllegalArgumentException If the specified type is not supported
     */
    public synchronized ServiceRegistry getRegistry(String type, Map<String, String> config) {
        if (registry != null) {
            return registry;
        }
        
        switch (type.toLowerCase()) {
            case "memory":
                registry = InMemoryServiceRegistry.getInstance();
                break;
            case "zookeeper":
                String connectionString = config != null && config.containsKey("connectionString") 
                    ? config.get("connectionString") 
                    : DEFAULT_ZOOKEEPER_CONNECTION;
                registry = ZooKeeperServiceRegistry.getInstance(connectionString);
                break;
            default:
                throw new IllegalArgumentException("Unsupported registry type: " + type);
        }
        
        // Start the registry
        registry.start();
        logger.info("Created and started " + type + " service registry");
        
        return registry;
    }
    
    /**
     * Gets a ServiceRegistry instance of the specified type with default configuration.
     *
     * @param type The type of registry to get (e.g., "memory", "zookeeper")
     * @return A ServiceRegistry instance of the specified type
     */
    public ServiceRegistry getRegistry(String type) {
        return getRegistry(type, null);
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
```

### 4. Example Usage

Here's how you would use the ZooKeeper-based service registry:

```java
// Create a ZooKeeper-based service registry
Map<String, String> config = new HashMap<>();
config.put("connectionString", "zookeeper1:2181,zookeeper2:2181,zookeeper3:2181");
ServiceRegistry registry = ServiceRegistryFactory.getInstance().getRegistry("zookeeper", config);

// Register a service
registry.registerService("tracker", "tracker-1", "tracker1.example.com", 8080, null);

// Discover services
List<ServiceInstance> trackers = registry.discoverServices("tracker");
```

## Benefits of a Distributed Registry

1. **High Availability**: ZooKeeper provides high availability through its distributed nature and leader election mechanism.
2. **Automatic Failover**: If a node goes down, ZooKeeper will automatically detect it and remove it from the registry.
3. **Consistency**: ZooKeeper ensures that all clients see the same view of the registry.
4. **Ephemeral Nodes**: Services are registered as ephemeral nodes, which are automatically removed if the client disconnects.
5. **Watches**: ZooKeeper provides a notification mechanism to alert clients when services change.

## Considerations

1. **ZooKeeper Cluster**: For production use, you should set up a ZooKeeper cluster with at least 3 nodes for high availability.
2. **Connection Management**: The implementation should handle ZooKeeper connection loss and session expiration gracefully.
3. **Serialization**: In a real implementation, use a proper JSON library like Jackson or Gson for serializing and deserializing service instances.
4. **Security**: Consider adding authentication and access control for the ZooKeeper cluster.
5. **Monitoring**: Implement monitoring for the ZooKeeper cluster and the service registry.

## Alternative Implementations

While this example uses ZooKeeper, you could also implement service registries using other distributed systems:

1. **Consul**: A service mesh solution with a built-in service registry
2. **etcd**: A distributed key-value store used by Kubernetes
3. **Redis**: Can be used for simpler service discovery needs
4. **Eureka**: Netflix's service discovery server

Each has its own strengths and would require a different implementation of the ServiceRegistry interface.