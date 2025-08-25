package dev.mars.p2pjava;

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


import dev.mars.p2pjava.common.PeerInfo;
import dev.mars.p2pjava.config.TrackerConfiguration;
import dev.mars.p2pjava.discovery.ServiceRegistry;
import dev.mars.p2pjava.discovery.ServiceRegistryManager;
import dev.mars.p2pjava.discovery.DistributedServiceRegistry;
import dev.mars.p2pjava.discovery.ServiceInstance;
import dev.mars.p2pjava.util.ThreadPoolManager;
import dev.mars.p2pjava.util.RetryManager;
import dev.mars.p2pjava.util.ErrorHandlingManager;
import dev.mars.p2pjava.util.CircuitBreaker;
import dev.mars.p2pjava.util.HealthCheck;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import java.util.stream.Collectors;

/**
 * Refactored TrackerService that uses dependency injection instead of static state.
 * This service manages peer registration, discovery, and lifecycle in a P2P network.
 */
public class TrackerService {
    private static final Logger logger = Logger.getLogger(TrackerService.class.getName());
    private static final int DEFAULT_TRACKER_PORT = 6000;
    private static final int THREAD_POOL_SIZE = 10;
    private static final long PEER_TIMEOUT_MS = 90000; // 90 seconds

    // Instance variables instead of static
    private final int trackerPort;
    private final Map<String, Long> peerLastSeen = new ConcurrentHashMap<>();
    private final Map<String, PeerInfo> peers = new ConcurrentHashMap<>();
    private volatile boolean running = false;
    private ExecutorService threadPool;
    private ServerSocket serverSocket;

    // Enhanced service registry for distributed service discovery
    private ServiceRegistry serviceRegistry;
    private DistributedServiceRegistry distributedRegistry;

    // Unique ID for this tracker instance
    private final String trackerId;

    // Configuration and dependencies
    private final TrackerConfiguration config;
    private final HealthCheck.ServiceHealth health;

    // Instance-based managers (no more static dependencies)
    private final ThreadPoolManager threadPoolManager;
    private final ServiceRegistryManager serviceRegistryManager;
    private final ErrorHandlingManager errorHandlingManager;

    /**
     * Creates a new TrackerService with default configuration.
     */
    public TrackerService() {
        this(new TrackerConfiguration());
    }

    /**
     * Creates a new TrackerService with the specified configuration.
     *
     * @param config The tracker configuration
     */
    public TrackerService(TrackerConfiguration config) {
        this(config, new ThreadPoolManager(), new ServiceRegistryManager(), new ErrorHandlingManager());
    }

    /**
     * Creates a new TrackerService with full dependency injection.
     *
     * @param config The tracker configuration
     * @param threadPoolManager The thread pool manager
     * @param serviceRegistryManager The service registry manager
     * @param errorHandlingManager The error handling manager
     */
    public TrackerService(TrackerConfiguration config, ThreadPoolManager threadPoolManager,
                         ServiceRegistryManager serviceRegistryManager, ErrorHandlingManager errorHandlingManager) {
        this.config = config;
        this.threadPoolManager = threadPoolManager;
        this.serviceRegistryManager = serviceRegistryManager;
        this.errorHandlingManager = errorHandlingManager;
        this.trackerId = "tracker-" + UUID.randomUUID().toString().substring(0, 8);
        this.trackerPort = config.getTrackerPort();
        
        // Register with health check system
        this.health = HealthCheck.registerService("TrackerService-" + trackerId);
        health.addHealthDetail("trackerId", trackerId);
        health.addHealthDetail("port", trackerPort);

        // Initialize error handling
        initializeErrorHandling();

        logger.info("Created TrackerService with ID: " + trackerId + " on port: " + trackerPort);
    }

    /**
     * Starts the tracker service.
     */
    public void start() throws IOException {
        if (running) {
            logger.warning("TrackerService already running");
            return;
        }

        logger.info("Starting TrackerService: " + trackerId);
        running = true;

        // Initialize thread pool using instance-based ThreadPoolManager
        threadPool = threadPoolManager.getFixedThreadPool(
            "TrackerThreadPool-" + trackerId,
            "Tracker-" + trackerId,
            config.getThreadPoolSize()
        );

        // Initialize enhanced service registry
        initializeServiceRegistry();

        // Register this tracker with the service registry using comprehensive error handling
        errorHandlingManager.operation("service-registry-registration")
            .withAll()
            .execute(() -> {
                registerWithServiceRegistry();
                return null;
            });

        // Start the server socket
        serverSocket = new ServerSocket(trackerPort);
        serverSocket.setSoTimeout(5000); // Add timeout to allow for graceful shutdown
        
        health.setHealthy(true);
        health.addHealthDetail("status", "running");
        health.addHealthDetail("startTime", System.currentTimeMillis());
        
        logger.info("TrackerService started on port " + trackerPort);

        // Accept connections in a loop
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                threadPool.submit(new TrackerRequestHandler(clientSocket, this));
            } catch (SocketTimeoutException e) {
                // This is expected, just continue the loop
            } catch (IOException e) {
                if (running) {
                    logger.log(Level.SEVERE, "Error accepting connection", e);
                    health.addHealthDetail("error", e.getMessage());
                }
            }
        }
    }

    /**
     * Stops the tracker service.
     */
    public void stop() {
        if (!running) {
            return;
        }

        logger.info("Stopping TrackerService: " + trackerId);
        running = false;

        // Close server socket
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error closing server socket", e);
            }
        }

        // Shutdown thread pool manager
        threadPoolManager.shutdownAll();

        // Stop distributed registry if running
        if (distributedRegistry != null) {
            distributedRegistry.stop();
        }

        // Deregister from service registry and stop registry manager
        if (serviceRegistry != null) {
            serviceRegistry.deregisterService("tracker", trackerId);
        }
        serviceRegistryManager.stop();

        // Update health status
        health.setHealthy(false);
        health.addHealthDetail("status", "stopped");
        health.addHealthDetail("stopTime", System.currentTimeMillis());

        logger.info("TrackerService stopped: " + trackerId);
    }

    /**
     * Registers a peer with the tracker.
     */
    public boolean registerPeer(String peerId, String address, int port) {
        if (peerId == null || peerId.isEmpty()) {
            logger.warning("Invalid peer ID for registration");
            return false;
        }

        PeerInfo peerInfo = new PeerInfo(peerId, address, port);
        peers.put(peerId, peerInfo);
        updatePeerLastSeen(peerId);
        
        health.addHealthDetail("totalPeers", peers.size());
        health.addHealthDetail("lastRegisteredPeer", peerId);
        
        logger.info("Registered peer: " + peerInfo);
        return true;
    }

    /**
     * Deregisters a peer from the tracker.
     */
    public boolean deregisterPeer(String peerId) {
        if (peerId == null || peerId.isEmpty()) {
            logger.warning("Invalid peer ID for deregistration");
            return false;
        }

        PeerInfo removed = peers.remove(peerId);
        peerLastSeen.remove(peerId);

        boolean success = removed != null;
        if (success) {
            health.addHealthDetail("totalPeers", peers.size());
            health.addHealthDetail("lastDeregisteredPeer", peerId);
            logger.info("Deregistered peer: " + peerId);
        } else {
            logger.warning("Failed to deregister peer: " + peerId + " (not found)");
        }

        return success;
    }

    /**
     * Updates the last seen time for a peer.
     */
    public void updatePeerLastSeen(String peerId) {
        peerLastSeen.put(peerId, System.currentTimeMillis());
        logger.fine("Updated last seen time for peer: " + peerId);
    }

    /**
     * Checks if a peer is alive based on last seen time.
     */
    public boolean isPeerAlive(String peerId) {
        Long lastSeen = peerLastSeen.get(peerId);
        if (lastSeen == null) return false;
        return (System.currentTimeMillis() - lastSeen) < config.getPeerTimeoutMs();
    }

    /**
     * Gets all active peers.
     */
    public List<String> getActivePeers() {
        long now = System.currentTimeMillis();
        List<String> activePeers = peerLastSeen.entrySet().stream()
                .filter(entry -> (now - entry.getValue()) < config.getPeerTimeoutMs())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        logger.fine("Active peers: " + activePeers.size());
        return activePeers;
    }

    /**
     * Gets all registered peers.
     */
    public Collection<PeerInfo> getAllPeers() {
        return new ArrayList<>(peers.values());
    }

    /**
     * Gets a specific peer by ID.
     */
    public PeerInfo getPeer(String peerId) {
        return peers.get(peerId);
    }

    /**
     * Discovers other tracker instances for load balancing and redundancy.
     */
    public List<ServiceInstance> discoverOtherTrackers() {
        return errorHandlingManager.operation("service-discovery")
            .withAll()
            .execute(() -> {
                if (serviceRegistry == null) {
                    throw new IllegalStateException("Service registry not available");
                }

                List<ServiceInstance> allTrackers = serviceRegistry.discoverServices("tracker");
                // Filter out this tracker instance
                return allTrackers.stream()
                    .filter(tracker -> !trackerId.equals(tracker.getServiceId()))
                    .collect(Collectors.toList());
            });
    }

    /**
     * Gets the tracker ID.
     */
    public String getTrackerId() {
        return trackerId;
    }

    /**
     * Gets the tracker port.
     */
    public int getTrackerPort() {
        return trackerPort;
    }

    /**
     * Checks if the tracker is running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Initializes the service registry.
     */
    private void initializeServiceRegistry() {
        try {
            if (config.isDistributedDiscoveryEnabled()) {
                // Initialize distributed registry with gossip protocol
                initializeDistributedRegistry();
            } else {
                // Fall back to basic registry using instance-based manager
                serviceRegistryManager.start();
                serviceRegistry = serviceRegistryManager.getRegistry();
                logger.info("Using basic service registry (distributed features disabled)");
            }

        } catch (Exception e) {
            logger.warning("Failed to initialize enhanced service registry, falling back to basic: " + e.getMessage());
            serviceRegistryManager.start();
            serviceRegistry = serviceRegistryManager.getRegistry();
        }
    }

    /**
     * Initializes the distributed service registry.
     */
    private void initializeDistributedRegistry() {
        // Get gossip configuration from config
        int gossipPort = config.getGossipPort();
        String bootstrapPeersStr = config.getBootstrapPeers();

        Set<String> bootstrapPeers = new HashSet<>();
        if (bootstrapPeersStr != null && !bootstrapPeersStr.isEmpty()) {
            bootstrapPeers.addAll(Arrays.asList(bootstrapPeersStr.split(",")));
        }

        // Create distributed registry
        distributedRegistry = new DistributedServiceRegistry(
            trackerId,
            gossipPort,
            bootstrapPeers
        );

        distributedRegistry.start();
        serviceRegistry = distributedRegistry;

        logger.info("Initialized enhanced distributed service registry on port " + gossipPort);
    }

    /**
     * Registers this tracker with the service registry.
     */
    private void registerWithServiceRegistry() {
        if (serviceRegistry == null) {
            return;
        }

        try {
            // Get the local host address
            String host;
            try {
                host = InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException e) {
                host = "localhost";
                logger.warning("Could not determine local host address: " + e.getMessage());
            }

            // Create metadata for this tracker
            Map<String, String> metadata = new HashMap<>();
            metadata.put("startTime", String.valueOf(System.currentTimeMillis()));
            metadata.put("version", "2.0");
            metadata.put("capabilities", "peer-tracking,enhanced-discovery");

            boolean registered = serviceRegistry.registerService("tracker", trackerId, host, trackerPort, metadata);
            if (registered) {
                logger.info("Registered tracker with service registry: " + trackerId + " at " + host + ":" + trackerPort);
                health.addHealthDetail("registeredWithServiceRegistry", true);
            } else {
                logger.warning("Failed to register tracker with service registry");
                health.addHealthDetail("registeredWithServiceRegistry", false);
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error registering with service registry", e);
            health.addHealthDetail("serviceRegistryError", e.getMessage());
        }
    }

    /**
     * Initializes error handling with circuit breakers and fallback strategies.
     */
    private void initializeErrorHandling() {
        // Register circuit breakers for critical operations
        errorHandlingManager.registerCircuitBreaker("service-registry-registration",
            CircuitBreaker.builder()
                .name("service-registry-registration")
                .failureThreshold(3)
                .retryTimeout(java.time.Duration.ofSeconds(30))
                .build());

        errorHandlingManager.registerCircuitBreaker("peer-registration",
            CircuitBreaker.builder()
                .name("peer-registration")
                .failureThreshold(5)
                .retryTimeout(java.time.Duration.ofSeconds(10))
                .build());

        errorHandlingManager.registerCircuitBreaker("service-discovery",
            CircuitBreaker.builder()
                .name("service-discovery")
                .failureThreshold(3)
                .retryTimeout(java.time.Duration.ofSeconds(20))
                .build());

        // Register fallback strategies
        errorHandlingManager.registerFallback("service-registry-registration",
            (Exception e) -> {
                logger.warning("Service registry registration failed, continuing without registration: " + e.getMessage());
                health.addHealthDetail("serviceRegistryFallback", true);
                return null;
            });

        errorHandlingManager.registerFallback("peer-registration",
            (Exception e) -> {
                logger.warning("Peer registration failed, using fallback response: " + e.getMessage());
                return false; // Return failure but don't crash
            });

        errorHandlingManager.registerFallback("service-discovery",
            (Exception e) -> {
                logger.warning("Service discovery failed, returning empty list: " + e.getMessage());
                return Collections.emptyList();
            });

        logger.info("Error handling initialized with circuit breakers and fallback strategies");
    }
}
