

# Building Your Own Distributed Registry Implementation for P2P-Java Discovery Service

Based on the existing code in the `dev.mars.p2pjava.discovery` package, I'll outline how to build your own custom distributed registry implementation without relying on external systems like ZooKeeper or Consul. This approach will leverage the P2P nature of your application to create a fully distributed service registry.

## Approach: P2P-Based Distributed Registry

Instead of using an external system, we'll create a distributed registry that uses the P2P network itself to maintain service information. This approach has several advantages:

1. No external dependencies required
2. Leverages your existing P2P infrastructure
3. Naturally fits with the distributed nature of your application
4. Can be customized to your specific needs

## Implementation Plan

### 1. Create a P2PDistributedServiceRegistry Class

```java
package dev.mars.p2pjava.discovery;

import dev.mars.p2pjava.util.HealthCheck;
import dev.mars.p2pjava.util.ServiceMonitor;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A distributed implementation of the ServiceRegistry interface that uses
 * P2P communication for service discovery and registration.
 */
public class P2PDistributedServiceRegistry implements ServiceRegistry {
    private static final Logger logger = Logger.getLogger(P2PDistributedServiceRegistry.class.getName());
    
    // Multicast group and port for service announcements
    private static final String MULTICAST_GROUP = "239.255.0.1";
    private static final int MULTICAST_PORT = 8765;
    
    // Time-to-live for multicast packets (number of network hops)
    private static final int MULTICAST_TTL = 4;
    
    // Service announcement interval in milliseconds
    private static final long ANNOUNCEMENT_INTERVAL_MS = 30000; // 30 seconds
    
    // Service timeout in milliseconds (how long before a service is considered dead)
    private static final long SERVICE_TIMEOUT_MS = 90000; // 90 seconds
    
    // Local registry cache
    private final Map<String, Map<String, ServiceInstance>> registry = new ConcurrentHashMap<>();
    
    // Service announcement thread
    private ScheduledExecutorService announcementExecutor;
    
    // Service listener thread
    private ExecutorService listenerExecutor;
    
    // Multicast socket for sending and receiving service announcements
    private MulticastSocket multicastSocket;
    
    // Flag to track if the registry is running
    private volatile boolean running = false;
    
    // Local node information
    private final String localNodeId;
    private final String localHost;
    private final int localPort;
    
    // Singleton instance
    private static P2PDistributedServiceRegistry instance;
    
    // Service metrics
    private final ServiceMonitor.ServiceMetrics metrics;
    
    /**
     * Creates a new P2PDistributedServiceRegistry.
     *
     * @param localNodeId A unique identifier for this node
     * @param localHost The hostname or IP address of this node
     * @param localPort The port number of this node
     */
    private P2PDistributedServiceRegistry(String localNodeId, String localHost, int localPort) {
        this.localNodeId = localNodeId;
        this.localHost = localHost;
        this.localPort = localPort;
        this.metrics = ServiceMonitor.registerService("P2PDistributedServiceRegistry");
    }
    
    /**
     * Gets the singleton instance of the registry.
     *
     * @param localNodeId A unique identifier for this node
     * @param localHost The hostname or IP address of this node
     * @param localPort The port number of this node
     * @return The singleton instance
     */
    public static synchronized P2PDistributedServiceRegistry getInstance(
            String localNodeId, String localHost, int localPort) {
        if (instance == null) {
            instance = new P2PDistributedServiceRegistry(localNodeId, localHost, localPort);
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
            
            // Add to local registry
            Map<String, ServiceInstance> serviceMap = registry.computeIfAbsent(
                serviceType, k -> new ConcurrentHashMap<>());
            serviceMap.put(serviceId, instance);
            
            // Announce the service immediately
            announceService(instance);
            
            metrics.recordOperation("registerService");
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
            // Remove from local registry
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
            
            // Announce the deregistration
            announceServiceDeregistration(serviceType, serviceId);
            
            metrics.recordOperation("deregisterService");
            logger.info("Deregistered service: " + removed);
            return true;
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
        
        Map<String, ServiceInstance> serviceMap = registry.get(serviceType);
        if (serviceMap == null) {
            logger.fine("No services found for type: " + serviceType);
            return Collections.emptyList();
        }
        
        // Return only healthy services
        List<ServiceInstance> healthyServices = new ArrayList<>();
        for (ServiceInstance instance : serviceMap.values()) {
            if (instance.isHealthy() && !isServiceExpired(instance)) {
                healthyServices.add(instance);
            }
        }
        
        metrics.recordOperation("discoverServices");
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
        
        // Check if the service is expired
        if (isServiceExpired(instance)) {
            logger.fine("Service is expired: " + instance);
            serviceMap.remove(serviceId);
            return null;
        }
        
        metrics.recordOperation("getService");
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
            
            // Announce the health update
            announceServiceHealthUpdate(serviceType, serviceId, healthy);
            
            metrics.recordOperation("updateServiceHealth");
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
            logger.info("P2P distributed service registry already running");
            return;
        }
        
        try {
            // Initialize multicast socket
            multicastSocket = new MulticastSocket(MULTICAST_PORT);
            multicastSocket.joinGroup(InetAddress.getByName(MULTICAST_GROUP));
            multicastSocket.setTimeToLive(MULTICAST_TTL);
            
            // Start service announcement thread
            announcementExecutor = Executors.newSingleThreadScheduledExecutor();
            announcementExecutor.scheduleAtFixedRate(
                this::announceAllServices, 
                0, 
                ANNOUNCEMENT_INTERVAL_MS, 
                TimeUnit.MILLISECONDS
            );
            
            // Start service listener thread
            listenerExecutor = Executors.newSingleThreadExecutor();
            listenerExecutor.submit(this::listenForServiceAnnouncements);
            
            running = true;
            logger.info("Started P2P distributed service registry on " + localHost + ":" + localPort);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to start P2P distributed service registry: " + e.getMessage(), e);
            stop();
        }
    }
    
    @Override
    public void stop() {
        if (!running) {
            logger.info("P2P distributed service registry already stopped");
            return;
        }
        
        try {
            // Announce deregistration of all local services
            for (Map<String, ServiceInstance> serviceMap : registry.values()) {
                for (ServiceInstance instance : serviceMap.values()) {
                    if (isLocalService(instance)) {
                        announceServiceDeregistration(instance.getServiceType(), instance.getServiceId());
                    }
                }
            }
            
            // Shutdown executors
            if (announcementExecutor != null) {
                announcementExecutor.shutdownNow();
                announcementExecutor = null;
            }
            
            if (listenerExecutor != null) {
                listenerExecutor.shutdownNow();
                listenerExecutor = null;
            }
            
            // Close multicast socket
            if (multicastSocket != null) {
                multicastSocket.leaveGroup(InetAddress.getByName(MULTICAST_GROUP));
                multicastSocket.close();
                multicastSocket = null;
            }
            
            // Clear registry
            registry.clear();
            
            running = false;
            logger.info("Stopped P2P distributed service registry");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to stop P2P distributed service registry: " + e.getMessage(), e);
        }
    }
    
    /**
     * Announces all local services to the network.
     */
    private void announceAllServices() {
        try {
            for (Map<String, ServiceInstance> serviceMap : registry.values()) {
                for (ServiceInstance instance : serviceMap.values()) {
                    if (isLocalService(instance)) {
                        announceService(instance);
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to announce services: " + e.getMessage(), e);
        }
    }
    
    /**
     * Announces a service to the network.
     *
     * @param instance The service instance to announce
     */
    private void announceService(ServiceInstance instance) {
        try {
            // Create announcement message
            String message = "ANNOUNCE|" + 
                    instance.getServiceType() + "|" + 
                    instance.getServiceId() + "|" + 
                    instance.getHost() + "|" + 
                    instance.getPort() + "|" + 
                    instance.isHealthy() + "|" + 
                    instance.getLastUpdated() + "|" + 
                    serializeMetadata(instance.getMetadata());
            
            // Send announcement
            byte[] buffer = message.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(
                buffer, buffer.length, 
                InetAddress.getByName(MULTICAST_GROUP), 
                MULTICAST_PORT
            );
            multicastSocket.send(packet);
            
            metrics.incrementCounter("announcementsSent");
            logger.fine("Announced service: " + instance);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to announce service: " + e.getMessage(), e);
        }
    }
    
    /**
     * Announces a service deregistration to the network.
     *
     * @param serviceType The type of service
     * @param serviceId The unique identifier of the service instance
     */
    private void announceServiceDeregistration(String serviceType, String serviceId) {
        try {
            // Create deregistration message
            String message = "DEREGISTER|" + serviceType + "|" + serviceId;
            
            // Send announcement
            byte[] buffer = message.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(
                buffer, buffer.length, 
                InetAddress.getByName(MULTICAST_GROUP), 
                MULTICAST_PORT
            );
            multicastSocket.send(packet);
            
            metrics.incrementCounter("deregistrationsSent");
            logger.fine("Announced deregistration of service: " + serviceType + ":" + serviceId);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to announce service deregistration: " + e.getMessage(), e);
        }
    }
    
    /**
     * Announces a service health update to the network.
     *
     * @param serviceType The type of service
     * @param serviceId The unique identifier of the service instance
     * @param healthy Whether the service is healthy
     */
    private void announceServiceHealthUpdate(String serviceType, String serviceId, boolean healthy) {
        try {
            // Create health update message
            String message = "HEALTH|" + serviceType + "|" + serviceId + "|" + healthy;
            
            // Send announcement
            byte[] buffer = message.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(
                buffer, buffer.length, 
                InetAddress.getByName(MULTICAST_GROUP), 
                MULTICAST_PORT
            );
            multicastSocket.send(packet);
            
            metrics.incrementCounter("healthUpdatesSent");
            logger.fine("Announced health update of service: " + serviceType + ":" + serviceId + " to " + healthy);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to announce service health update: " + e.getMessage(), e);
        }
    }
    
    /**
     * Listens for service announcements from the network.
     */
    private void listenForServiceAnnouncements() {
        byte[] buffer = new byte[4096];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        
        while (running) {
            try {
                // Receive announcement
                multicastSocket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                
                // Process announcement
                processAnnouncement(message);
                
                metrics.incrementCounter("announcementsReceived");
            } catch (IOException e) {
                if (running) {
                    logger.log(Level.WARNING, "Failed to receive service announcement: " + e.getMessage(), e);
                }
            }
        }
    }
    
    /**
     * Processes a service announcement message.
     *
     * @param message The announcement message
     */
    private void processAnnouncement(String message) {
        try {
            String[] parts = message.split("\\|");
            String type = parts[0];
            
            switch (type) {
                case "ANNOUNCE":
                    processServiceAnnouncement(parts);
                    break;
                case "DEREGISTER":
                    processServiceDeregistration(parts);
                    break;
                case "HEALTH":
                    processServiceHealthUpdate(parts);
                    break;
                default:
                    logger.warning("Unknown announcement type: " + type);
                    break;
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to process announcement: " + e.getMessage(), e);
        }
    }
    
    /**
     * Processes a service announcement message.
     *
     * @param parts The parts of the announcement message
     */
    private void processServiceAnnouncement(String[] parts) {
        try {
            // Parse announcement
            String serviceType = parts[1];
            String serviceId = parts[2];
            String host = parts[3];
            int port = Integer.parseInt(parts[4]);
            boolean healthy = Boolean.parseBoolean(parts[5]);
            long lastUpdated = Long.parseLong(parts[6]);
            Map<String, String> metadata = deserializeMetadata(parts[7]);
            
            // Skip if this is our own service
            if (isOwnService(serviceType, serviceId, host, port)) {
                return;
            }
            
            // Update registry
            Map<String, ServiceInstance> serviceMap = registry.computeIfAbsent(
                serviceType, k -> new ConcurrentHashMap<>());
            
            ServiceInstance existingInstance = serviceMap.get(serviceId);
            if (existingInstance == null || existingInstance.getLastUpdated() < lastUpdated) {
                // Create new instance or update existing one
                ServiceInstance instance = new ServiceInstance(serviceType, serviceId, host, port, metadata);
                instance.setHealthy(healthy);
                
                // Use reflection to set lastUpdated field
                try {
                    java.lang.reflect.Field field = ServiceInstance.class.getDeclaredField("lastUpdated");
                    field.setAccessible(true);
                    field.set(instance, lastUpdated);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Failed to set lastUpdated field: " + e.getMessage(), e);
                }
                
                serviceMap.put(serviceId, instance);
                logger.fine("Added/updated service from announcement: " + instance);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to process service announcement: " + e.getMessage(), e);
        }
    }
    
    /**
     * Processes a service deregistration message.
     *
     * @param parts The parts of the deregistration message
     */
    private void processServiceDeregistration(String[] parts) {
        try {
            // Parse deregistration
            String serviceType = parts[1];
            String serviceId = parts[2];
            
            // Update registry
            Map<String, ServiceInstance> serviceMap = registry.get(serviceType);
            if (serviceMap != null) {
                ServiceInstance removed = serviceMap.remove(serviceId);
                if (removed != null) {
                    logger.fine("Removed service from deregistration announcement: " + removed);
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to process service deregistration: " + e.getMessage(), e);
        }
    }
    
    /**
     * Processes a service health update message.
     *
     * @param parts The parts of the health update message
     */
    private void processServiceHealthUpdate(String[] parts) {
        try {
            // Parse health update
            String serviceType = parts[1];
            String serviceId = parts[2];
            boolean healthy = Boolean.parseBoolean(parts[3]);
            
            // Update registry
            Map<String, ServiceInstance> serviceMap = registry.get(serviceType);
            if (serviceMap != null) {
                ServiceInstance instance = serviceMap.get(serviceId);
                if (instance != null) {
                    instance.setHealthy(healthy);
                    logger.fine("Updated service health from announcement: " + instance);
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to process service health update: " + e.getMessage(), e);
        }
    }
    
    /**
     * Checks if a service is expired.
     *
     * @param instance The service instance to check
     * @return true if the service is expired, false otherwise
     */
    private boolean isServiceExpired(ServiceInstance instance) {
        // Local services are never expired
        if (isLocalService(instance)) {
            return false;
        }
        
        // Check if the service has been updated recently
        long now = System.currentTimeMillis();
        return now - instance.getLastUpdated() > SERVICE_TIMEOUT_MS;
    }
    
    /**
     * Checks if a service is local to this node.
     *
     * @param instance The service instance to check
     * @return true if the service is local, false otherwise
     */
    private boolean isLocalService(ServiceInstance instance) {
        return instance.getHost().equals(localHost) && instance.getPort() == localPort;
    }
    
    /**
     * Checks if a service is our own service.
     *
     * @param serviceType The type of service
     * @param serviceId The unique identifier of the service instance
     * @param host The hostname or IP address of the service
     * @param port The port number of the service
     * @return true if the service is our own, false otherwise
     */
    private boolean isOwnService(String serviceType, String serviceId, String host, int port) {
        return host.equals(localHost) && port == localPort;
    }
    
    /**
     * Serializes metadata to a string.
     *
     * @param metadata The metadata to serialize
     * @return The serialized metadata
     */
    private String serializeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        return sb.toString();
    }
    
    /**
     * Deserializes metadata from a string.
     *
     * @param serialized The serialized metadata
     * @return The deserialized metadata
     */
    private Map<String, String> deserializeMetadata(String serialized) {
        Map<String, String> metadata = new HashMap<>();
        if (serialized == null || serialized.isEmpty()) {
            return metadata;
        }
        
        String[] pairs = serialized.split(",");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                metadata.put(keyValue[0], keyValue[1]);
            }
        }
        return metadata;
    }
    
    /**
     * Gets the number of registered services.
     *
     * @return The number of registered services
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
    
    /**
     * Gets the metrics for this registry.
     *
     * @return The metrics for this registry
     */
    public ServiceMonitor.ServiceMetrics getMetrics() {
        return metrics;
    }
}
```

### 2. Update ServiceRegistryFactory

```java
package dev.mars.p2pjava.discovery;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Factory class for creating ServiceRegistry instances.
 * This factory supports different types of service registries, including
 * the in-memory implementation and distributed implementations.
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
     * @param type The type of registry to get (e.g., "memory", "p2p")
     * @param config Configuration parameters for the registry
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
            case "p2p":
                // Get configuration parameters
                String nodeId = config.getOrDefault("nodeId", "node-" + System.currentTimeMillis());
                String host = config.getOrDefault("host", "localhost");
                int port = Integer.parseInt(config.getOrDefault("port", "8080"));
                
                registry = P2PDistributedServiceRegistry.getInstance(nodeId, host, port);
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
     * @param type The type of registry to get (e.g., "memory", "p2p")
     * @return A ServiceRegistry instance of the specified type
     */
    public ServiceRegistry getRegistry(String type) {
        return getRegistry(type, Map.of());
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

## How It Works

### 1. Multicast-Based Service Discovery

The implementation uses IP multicast to announce and discover services across the network:

- **Service Announcements**: Each node periodically announces its services to a multicast group
- **Service Discovery**: Nodes listen for announcements from other nodes
- **Health Updates**: Nodes announce health status changes
- **Deregistration**: Nodes announce when services are deregistered

### 2. Local Registry Cache

Each node maintains a local cache of all discovered services:

- Services are added when announcements are received
- Services are removed when deregistration announcements are received
- Services are marked as healthy/unhealthy based on health updates
- Services are expired if not updated within a timeout period

### 3. Fault Tolerance

The implementation includes several fault tolerance mechanisms:

- **Service Timeout**: Services that haven't been announced recently are considered expired
- **Health Monitoring**: Services can be marked as unhealthy
- **Periodic Announcements**: Services are periodically re-announced to handle missed messages
- **Local Services**: Local services are never expired

## Example Usage

Here's how to use the P2P distributed service registry:

```java
// Create configuration for the P2P registry
Map<String, String> config = new HashMap<>();
config.put("nodeId", "peer-1");
config.put("host", "192.168.1.100");
config.put("port", "8080");

// Get the P2P distributed service registry
ServiceRegistry registry = ServiceRegistryFactory.getInstance().getRegistry("p2p", config);

// Register a service
registry.registerService("fileserver", "fileserver-1", "192.168.1.100", 8080, null);

// Discover services
List<ServiceInstance> fileServers = registry.discoverServices("fileserver");

// Use the ServiceLocator with the P2P registry
ServiceLocator locator = new ServiceLocator(registry);
ServiceInstance fileServer = locator.locateService("fileserver");
```

## Advantages of This Approach

1. **No External Dependencies**: Uses your existing P2P infrastructure
2. **Fully Distributed**: No single point of failure
3. **Self-Healing**: Automatically handles node failures
4. **