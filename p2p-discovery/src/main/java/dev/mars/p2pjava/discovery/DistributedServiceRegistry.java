package dev.mars.p2pjava.discovery;

// import dev.mars.p2pjava.util.ThreadManager; // Temporarily removed due to Java version compatibility

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A distributed implementation of the ServiceRegistry interface that uses
 * gossip protocol for service discovery and maintains eventual consistency
 * across multiple peers in a P2P network.
 */
public class DistributedServiceRegistry implements ServiceRegistry {
    private static final Logger logger = Logger.getLogger(DistributedServiceRegistry.class.getName());
    
    private final String peerId;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong registryVersion = new AtomicLong(0);
    
    // Core registry storage: serviceType -> (serviceId -> ServiceInstance)
    private final Map<String, Map<String, ServiceInstance>> registry = new ConcurrentHashMap<>();
    
    // Distributed components
    private final GossipProtocol gossipProtocol;
    private final ConflictResolver conflictResolver;
    
    // Configuration
    private final int gossipPort;
    private final Set<String> bootstrapPeers;
    
    /**
     * Creates a distributed service registry with default configuration.
     */
    public DistributedServiceRegistry(String peerId) {
        this(peerId, 6003, new HashSet<>());
    }
    
    /**
     * Creates a distributed service registry with custom configuration.
     */
    public DistributedServiceRegistry(String peerId, int gossipPort, Set<String> bootstrapPeers) {
        this.peerId = peerId;
        this.gossipPort = gossipPort;
        this.bootstrapPeers = new HashSet<>(bootstrapPeers);
        
        // Initialize gossip protocol
        this.gossipProtocol = new GossipProtocol(peerId, gossipPort, 5000, 3, 30000);
        
        // Initialize conflict resolver with composite strategy
        this.conflictResolver = new ConflictResolver(ConflictResolver.ResolutionStrategy.COMPOSITE);
        
        // Register message handlers
        setupMessageHandlers();
    }
    
    @Override
    public boolean registerService(String serviceType, String serviceId, String host, int port, 
                                  Map<String, String> metadata) {
        if (!running.get()) {
            logger.warning("Cannot register service: registry is not running");
            return false;
        }
        
        if (serviceType == null || serviceId == null || host == null || 
            serviceType.isEmpty() || serviceId.isEmpty() || host.isEmpty() || 
            port < 0 || port > 65535) {
            logger.warning("Cannot register service: invalid parameters");
            return false;
        }
        
        // Create versioned service instance
        long version = System.currentTimeMillis();
        ServiceInstance instance = new ServiceInstance(serviceType, serviceId, host, port, 
                                                      metadata, version, peerId);
        
        // Register locally
        Map<String, ServiceInstance> serviceMap = registry.computeIfAbsent(
            serviceType, k -> new ConcurrentHashMap<>());
        
        // Check for conflicts
        ServiceInstance existing = serviceMap.get(serviceId);
        if (existing != null) {
            // Resolve conflict
            List<ServiceInstance> conflicting = Arrays.asList(existing, instance);
            ServiceInstance resolved = conflictResolver.resolveConflict(conflicting);
            
            if (resolved.equals(existing)) {
                logger.warning("Cannot register service: conflict resolution favored existing instance");
                return false;
            }
        }
        
        // Add the service instance
        serviceMap.put(serviceId, instance);
        registryVersion.incrementAndGet();
        
        // Broadcast registration to network
        RegistryMessage message = RegistryMessage.createServiceRegister(peerId, instance);
        gossipProtocol.broadcast(message);
        
        logger.info("Registered service: " + instance);
        return true;
    }
    
    @Override
    public boolean deregisterService(String serviceType, String serviceId) {
        if (!running.get()) {
            logger.warning("Cannot deregister service: registry is not running");
            return false;
        }
        
        if (serviceType == null || serviceId == null || 
            serviceType.isEmpty() || serviceId.isEmpty()) {
            logger.warning("Cannot deregister service: invalid parameters");
            return false;
        }
        
        Map<String, ServiceInstance> serviceMap = registry.get(serviceType);
        if (serviceMap == null) {
            logger.fine("Cannot deregister service: service type not found: " + serviceType);
            return false;
        }
        
        ServiceInstance removed = serviceMap.remove(serviceId);
        if (removed == null) {
            logger.fine("Cannot deregister service: service not found: " + serviceId);
            return false;
        }
        
        // Clean up empty service type maps
        if (serviceMap.isEmpty()) {
            registry.remove(serviceType);
        }
        
        registryVersion.incrementAndGet();
        
        // Broadcast deregistration to network
        RegistryMessage message = RegistryMessage.createServiceDeregister(peerId, serviceType, serviceId);
        gossipProtocol.broadcast(message);
        
        logger.info("Deregistered service: " + serviceType + "/" + serviceId);
        return true;
    }
    
    @Override
    public List<ServiceInstance> discoverServices(String serviceType) {
        if (!running.get()) {
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
    
    @Override
    public ServiceInstance getService(String serviceType, String serviceId) {
        if (!running.get()) {
            logger.warning("Cannot get service: registry is not running");
            return null;
        }
        
        if (serviceType == null || serviceId == null || 
            serviceType.isEmpty() || serviceId.isEmpty()) {
            logger.warning("Cannot get service: invalid parameters");
            return null;
        }
        
        Map<String, ServiceInstance> serviceMap = registry.get(serviceType);
        if (serviceMap == null) {
            return null;
        }
        
        return serviceMap.get(serviceId);
    }
    
    @Override
    public void start() {
        if (running.getAndSet(true)) {
            logger.warning("Registry already running");
            return;
        }
        
        logger.info("Starting distributed service registry for peer: " + peerId);
        
        try {
            // Start gossip protocol
            gossipProtocol.start();
            
            // Connect to bootstrap peers
            connectToBootstrapPeers();
            
            logger.info("Distributed service registry started successfully");
            
        } catch (IOException e) {
            running.set(false);
            logger.severe("Failed to start distributed service registry: " + e.getMessage());
            throw new RuntimeException("Failed to start registry", e);
        }
    }
    
    @Override
    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }

        logger.info("Stopping distributed service registry for peer: " + peerId);

        // Stop gossip protocol
        gossipProtocol.stop();

        // Clear registry
        registry.clear();

        logger.info("Distributed service registry stopped");
    }

    @Override
    public boolean isServiceHealthy(String serviceType, String serviceId) {
        ServiceInstance service = getService(serviceType, serviceId);
        return service != null && service.isHealthy();
    }

    @Override
    public boolean updateServiceHealth(String serviceType, String serviceId, boolean healthy) {
        if (!running.get()) {
            logger.warning("Cannot update service health: registry is not running");
            return false;
        }

        ServiceInstance service = getService(serviceType, serviceId);
        if (service == null) {
            logger.warning("Cannot update health: service not found: " + serviceType + "/" + serviceId);
            return false;
        }

        service.setHealthy(healthy);
        registryVersion.incrementAndGet();

        // Broadcast health update
        RegistryMessage message = RegistryMessage.createHeartbeat(peerId, service);
        gossipProtocol.broadcast(message);

        logger.fine("Updated health status for service " + serviceId + " to " + healthy);
        return true;
    }

    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * Gets the current registry version for synchronization.
     */
    public long getRegistryVersion() {
        return registryVersion.get();
    }
    
    /**
     * Gets a snapshot of the current registry state.
     */
    public Map<String, Map<String, ServiceInstance>> getRegistrySnapshot() {
        Map<String, Map<String, ServiceInstance>> snapshot = new HashMap<>();
        registry.forEach((serviceType, serviceMap) -> {
            snapshot.put(serviceType, new HashMap<>(serviceMap));
        });
        return snapshot;
    }
    
    /**
     * Adds a peer to the gossip network.
     */
    public void addPeer(String peerAddress) {
        gossipProtocol.addPeer(peerAddress);
    }
    
    /**
     * Removes a peer from the gossip network.
     */
    public void removePeer(String peerAddress) {
        gossipProtocol.removePeer(peerAddress);
    }
    
    /**
     * Gets the current set of known peers.
     */
    public Set<String> getKnownPeers() {
        return gossipProtocol.getKnownPeers();
    }
    
    /**
     * Gets registry statistics.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("peerId", peerId);
        stats.put("running", running.get());
        stats.put("registryVersion", registryVersion.get());
        stats.put("serviceTypes", registry.size());
        stats.put("totalServices", registry.values().stream().mapToInt(Map::size).sum());
        stats.putAll(gossipProtocol.getStatistics());
        return stats;
    }

    /**
     * Sets up message handlers for different types of registry messages.
     */
    private void setupMessageHandlers() {
        // Service registration handler
        gossipProtocol.registerMessageHandler(RegistryMessage.MessageType.SERVICE_REGISTER,
            (message, senderAddress) -> handleServiceRegister(message));

        // Service deregistration handler
        gossipProtocol.registerMessageHandler(RegistryMessage.MessageType.SERVICE_DEREGISTER,
            (message, senderAddress) -> handleServiceDeregister(message));

        // Service update handler
        gossipProtocol.registerMessageHandler(RegistryMessage.MessageType.SERVICE_UPDATE,
            (message, senderAddress) -> handleServiceUpdate(message));

        // Synchronization request handler
        gossipProtocol.registerMessageHandler(RegistryMessage.MessageType.SYNC_REQUEST,
            (message, senderAddress) -> handleSyncRequest(message, senderAddress));

        // Synchronization response handler
        gossipProtocol.registerMessageHandler(RegistryMessage.MessageType.SYNC_RESPONSE,
            (message, senderAddress) -> handleSyncResponse(message));

        // Heartbeat handler
        gossipProtocol.registerMessageHandler(RegistryMessage.MessageType.HEARTBEAT,
            (message, senderAddress) -> handleHeartbeat(message));

        // Anti-entropy handler
        gossipProtocol.registerMessageHandler(RegistryMessage.MessageType.ANTI_ENTROPY,
            (message, senderAddress) -> handleAntiEntropy(message));
    }

    /**
     * Handles service registration messages from other peers.
     */
    private void handleServiceRegister(RegistryMessage message) {
        ServiceInstance instance = message.getServiceInstance();
        if (instance == null) {
            logger.warning("Received service register message without service instance");
            return;
        }

        String serviceType = instance.getServiceType();
        String serviceId = instance.getServiceId();

        Map<String, ServiceInstance> serviceMap = registry.computeIfAbsent(
            serviceType, k -> new ConcurrentHashMap<>());

        ServiceInstance existing = serviceMap.get(serviceId);
        if (existing != null) {
            // Resolve conflict
            List<ServiceInstance> conflicting = Arrays.asList(existing, instance);
            ServiceInstance resolved = conflictResolver.resolveConflict(conflicting);
            serviceMap.put(serviceId, resolved);

            if (!resolved.equals(existing)) {
                registryVersion.incrementAndGet();
                logger.fine("Updated service after conflict resolution: " + serviceId);
            }
        } else {
            serviceMap.put(serviceId, instance);
            registryVersion.incrementAndGet();
            logger.fine("Registered service from peer: " + serviceId);
        }
    }

    /**
     * Handles service deregistration messages from other peers.
     */
    private void handleServiceDeregister(RegistryMessage message) {
        String serviceType = message.getServiceType();
        String serviceId = message.getServiceId();

        if (serviceType == null || serviceId == null) {
            logger.warning("Received service deregister message with missing information");
            return;
        }

        Map<String, ServiceInstance> serviceMap = registry.get(serviceType);
        if (serviceMap != null) {
            ServiceInstance removed = serviceMap.remove(serviceId);
            if (removed != null) {
                registryVersion.incrementAndGet();
                logger.fine("Deregistered service from peer: " + serviceId);

                // Clean up empty service type maps
                if (serviceMap.isEmpty()) {
                    registry.remove(serviceType);
                }
            }
        }
    }

    /**
     * Handles service update messages from other peers.
     */
    private void handleServiceUpdate(RegistryMessage message) {
        // Service updates are handled the same way as registrations
        handleServiceRegister(message);
    }

    /**
     * Handles synchronization request messages.
     */
    private void handleSyncRequest(RegistryMessage message, String senderAddress) {
        Set<String> requestedTypes = message.getRequestedServiceTypes();
        Map<String, Map<String, ServiceInstance>> snapshot;

        if (requestedTypes == null || requestedTypes.isEmpty()) {
            // Send full registry snapshot
            snapshot = getRegistrySnapshot();
        } else {
            // Send only requested service types
            snapshot = new HashMap<>();
            for (String serviceType : requestedTypes) {
                Map<String, ServiceInstance> serviceMap = registry.get(serviceType);
                if (serviceMap != null) {
                    snapshot.put(serviceType, new HashMap<>(serviceMap));
                }
            }
        }

        // Send sync response
        RegistryMessage response = RegistryMessage.createSyncResponse(
            peerId, snapshot, registryVersion.get());

        // Send directly to requesting peer (not broadcast)
        // This would need to be implemented as a direct send method
        logger.fine("Sending sync response to " + senderAddress);
    }

    /**
     * Handles synchronization response messages.
     */
    private void handleSyncResponse(RegistryMessage message) {
        Map<String, Map<String, ServiceInstance>> snapshot = message.getRegistrySnapshot();
        if (snapshot == null) {
            return;
        }

        // Merge the received registry data
        for (Map.Entry<String, Map<String, ServiceInstance>> typeEntry : snapshot.entrySet()) {
            String serviceType = typeEntry.getKey();
            Map<String, ServiceInstance> remoteServices = typeEntry.getValue();

            Map<String, ServiceInstance> localServices = registry.computeIfAbsent(
                serviceType, k -> new ConcurrentHashMap<>());

            for (Map.Entry<String, ServiceInstance> serviceEntry : remoteServices.entrySet()) {
                String serviceId = serviceEntry.getKey();
                ServiceInstance remoteInstance = serviceEntry.getValue();

                ServiceInstance localInstance = localServices.get(serviceId);
                if (localInstance == null) {
                    localServices.put(serviceId, remoteInstance);
                    registryVersion.incrementAndGet();
                } else if (conflictResolver.isConflict(localInstance, remoteInstance)) {
                    List<ServiceInstance> conflicting = Arrays.asList(localInstance, remoteInstance);
                    ServiceInstance resolved = conflictResolver.resolveConflict(conflicting);
                    localServices.put(serviceId, resolved);

                    if (!resolved.equals(localInstance)) {
                        registryVersion.incrementAndGet();
                    }
                }
            }
        }

        logger.fine("Processed sync response with " + snapshot.size() + " service types");
    }

    /**
     * Handles heartbeat messages from other peers.
     */
    private void handleHeartbeat(RegistryMessage message) {
        ServiceInstance instance = message.getServiceInstance();
        if (instance != null) {
            // Update the health status of the service
            String serviceType = instance.getServiceType();
            String serviceId = instance.getServiceId();

            Map<String, ServiceInstance> serviceMap = registry.get(serviceType);
            if (serviceMap != null) {
                ServiceInstance existing = serviceMap.get(serviceId);
                if (existing != null) {
                    existing.setHealthy(instance.isHealthy());
                    logger.fine("Updated health status for service: " + serviceId);
                }
            }
        }
    }

    /**
     * Handles anti-entropy messages for registry reconciliation.
     */
    private void handleAntiEntropy(RegistryMessage message) {
        // Anti-entropy is handled similarly to sync response
        handleSyncResponse(message);
    }

    /**
     * Connects to bootstrap peers to join the gossip network.
     */
    private void connectToBootstrapPeers() {
        for (String peerAddress : bootstrapPeers) {
            gossipProtocol.addPeer(peerAddress);
            logger.fine("Added bootstrap peer: " + peerAddress);
        }

        if (!bootstrapPeers.isEmpty()) {
            // Request initial synchronization from bootstrap peers
            RegistryMessage syncRequest = RegistryMessage.createSyncRequest(peerId, Collections.emptySet());
            gossipProtocol.broadcast(syncRequest);
        }
    }
}
