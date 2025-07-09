package dev.mars.p2pjava;

/**
 * 1. **Single Instance Design**: The Tracker is designed to run as a single instance application. It manages global state like active peers, and there's no need to create multiple instances of the Tracker class.
 * 2. **Utility Class Pattern**: The class functions as a utility class providing tracker functionality. All shared resources (like map, thread pool, and service registry) are static fields. `peerLastSeen`
 * 3. **Centralized Service**: The Tracker serves as a centralized service with:
 *     - A single server socket accepting connections on a configurable port (default 6000)
 *     - A shared thread pool managing connections
 *     - A global peer tracking mechanism
 *
 * 4. **Stateless Operations**: Methods like `updatePeerLastSeen()`, `isPeerAlive()`, and `getActivePeers()` are essentially stateless operations that work on the shared static state.
 * 5. **Startup/Shutdown Logic**: The `main()`, `startTracker()`, and `stopTracker()` methods manage the lifecycle of the single Tracker instance.
 **/

import dev.mars.p2pjava.discovery.ServiceRegistry;
import dev.mars.p2pjava.discovery.ServiceRegistryFactory;
import dev.mars.p2pjava.discovery.DistributedServiceRegistry;
import dev.mars.p2pjava.discovery.ConflictResolutionStrategy;
import dev.mars.p2pjava.discovery.ServiceInstance;
import dev.mars.p2pjava.config.PeerConfig;
import dev.mars.p2pjava.util.ThreadManager;

import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.logging.*;
import java.util.stream.Collectors;

public class Tracker {
    private static final Logger logger = Logger.getLogger(Tracker.class.getName());
    private static final int DEFAULT_TRACKER_PORT = 6000;
    private static int trackerPort;
    private static final int THREAD_POOL_SIZE = 10;
    private static final Map<String, Long> peerLastSeen = new ConcurrentHashMap<>();
    private static final long PEER_TIMEOUT_MS = 90000; // 90 seconds
    private static volatile boolean running = true;
    private static ExecutorService threadPool;

    // Enhanced service registry for distributed service discovery
    private static ServiceRegistry serviceRegistry;
    private static DistributedServiceRegistry distributedRegistry;

    // Unique ID for this tracker instance
    private static String trackerId;

    // Configuration for enhanced features
    private static PeerConfig.GossipConfig gossipConfig;

    public static void main(String[] args) {
        configureLogging();
        startTracker();
    }

    private static void configureLogging() {
        try {
            Logger rootLogger = Logger.getLogger("");
            Handler[] handlers = rootLogger.getHandlers();
            for (Handler handler : handlers) {
                rootLogger.removeHandler(handler);
            }

            ConsoleHandler handler = new ConsoleHandler();
            handler.setFormatter(new SimpleFormatter());
            handler.setLevel(Level.INFO);

            rootLogger.addHandler(handler);
            rootLogger.setLevel(Level.INFO);
        } catch (Exception e) {
            System.err.println("Error setting up logger: " + e.getMessage());
        }
    }

    public static void startTracker() {
        // Initialize tracker port from system property or use default
        String trackerPortStr = System.getProperty("tracker.port");
        if (trackerPortStr != null && !trackerPortStr.isEmpty()) {
            try {
                trackerPort = Integer.parseInt(trackerPortStr);
                logger.info("Using dynamic port for tracker: " + trackerPort);
            } catch (NumberFormatException e) {
                logger.warning("Invalid tracker.port system property: " + trackerPortStr + ". Using default port.");
                trackerPort = DEFAULT_TRACKER_PORT;
            }
        } else {
            trackerPort = DEFAULT_TRACKER_PORT;
            logger.info("No dynamic port specified for tracker. Using default port: " + trackerPort);
        }
        // Initialize thread pool using ThreadManager for standardized thread management
        threadPool = ThreadManager.getFixedThreadPool(
            "TrackerThreadPool", 
            "Tracker", 
            THREAD_POOL_SIZE
        );

        // Initialize enhanced service registry
        initializeEnhancedServiceRegistry();

        // Generate a unique ID for this tracker instance
        trackerId = "tracker-" + UUID.randomUUID().toString().substring(0, 8);

        // Get the local host address
        String host;
        try {
            host = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            host = "localhost";
            logger.warning("Could not determine local host address: " + e.getMessage());
        }

        // Register this tracker instance with enhanced metadata
        Map<String, String> metadata = new ConcurrentHashMap<>();
        metadata.put("startTime", String.valueOf(System.currentTimeMillis()));
        metadata.put("version", "2.0");
        metadata.put("capabilities", "enhanced-gossip,conflict-resolution,vector-clocks");
        metadata.put("region", System.getProperty("tracker.region", "default"));
        metadata.put("priority", "high"); // Trackers are critical infrastructure

        boolean registered = serviceRegistry.registerService("tracker", trackerId, host, trackerPort, metadata);
        if (registered) {
            logger.info("Registered enhanced tracker with service registry: " + trackerId + " at " + host + ":" + trackerPort);

            // If using distributed registry, register with high priority
            if (distributedRegistry != null) {
                // Note: This would require extending the API to support priority registration
                logger.info("Tracker registered with distributed registry using high priority");
            }
        } else {
            logger.warning("Failed to register tracker with service registry");
        }

        // Set up shutdown hook for graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down Tracker...");
            stopTracker();
        }));

        try (ServerSocket serverSocket = new ServerSocket(trackerPort)) {
            serverSocket.setSoTimeout(5000); // Add timeout to allow for graceful shutdown
            logger.info("Tracker started on port " + trackerPort);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    threadPool.submit(new TrackerHandler(clientSocket));
                } catch (SocketTimeoutException e) {
                    // This is expected, just continue the loop
                } catch (IOException e) {
                    if (running) {
                        logger.log(Level.SEVERE, "Error accepting connection", e);
                    }
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error starting Tracker", e);
        } finally {
            stopTracker();
        }
    }

    public static void stopTracker() {
        if (!running) {
            return;
        }

        running = false;

        // Shutdown thread pool using ThreadManager
        if (threadPool != null && !threadPool.isShutdown()) {
            try {
                logger.info("Shutting down Tracker thread pool");
                ThreadManager.shutdownThreadPool("TrackerThreadPool", 5, TimeUnit.SECONDS);
                logger.info("Tracker thread pool shut down successfully");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error shutting down thread pool", e);
            }
        }

        // Deregister from service registry
        if (serviceRegistry != null && trackerId != null) {
            boolean deregistered = serviceRegistry.deregisterService("tracker", trackerId);
            if (deregistered) {
                logger.info("Deregistered tracker from service registry: " + trackerId);
            } else {
                logger.warning("Failed to deregister tracker from service registry: " + trackerId);
            }
        }

        // Stop distributed registry if used
        if (distributedRegistry != null) {
            distributedRegistry.stop();
            logger.info("Stopped distributed service registry");
        }

        logger.info("Tracker stopped");
    }

    /**
     * Initializes the enhanced service registry with distributed capabilities.
     */
    private static void initializeEnhancedServiceRegistry() {
        try {
            // Check if distributed registry is enabled
            boolean useDistributed = Boolean.parseBoolean(
                System.getProperty("tracker.distributed.enabled", "true"));

            if (useDistributed) {
                // Create enhanced gossip configuration
                gossipConfig = new PeerConfig.GossipConfig();
                gossipConfig.setPort(Integer.parseInt(System.getProperty("tracker.gossip.port", "6003")));
                gossipConfig.setAdaptiveFanout(true);
                gossipConfig.setPriorityPropagation(true);
                gossipConfig.setCompressionEnabled(true);
                gossipConfig.setIntervalMs(Long.parseLong(System.getProperty("tracker.gossip.interval", "5000")));

                // Get bootstrap peers from system property
                String bootstrapPeersStr = System.getProperty("tracker.bootstrap.peers", "");
                Set<String> bootstrapPeers = new HashSet<>();
                if (!bootstrapPeersStr.isEmpty()) {
                    bootstrapPeers.addAll(Arrays.asList(bootstrapPeersStr.split(",")));
                }

                // Create conflict resolution strategy with tracker priority
                Map<String, Integer> peerPriorities = new HashMap<>();
                peerPriorities.put("tracker", 100); // Highest priority for trackers
                peerPriorities.put("indexserver", 50);
                peerPriorities.put("peer", 10);

                ConflictResolutionStrategy conflictResolver = new ConflictResolutionStrategy(
                    ConflictResolutionStrategy.ResolutionPolicy.COMPOSITE,
                    peerPriorities,
                    service -> service.isHealthy()
                );

                // Create distributed registry
                distributedRegistry = new DistributedServiceRegistry(
                    "tracker-" + System.currentTimeMillis(),
                    gossipConfig.getPort(),
                    bootstrapPeers
                );

                distributedRegistry.start();
                serviceRegistry = distributedRegistry;

                logger.info("Initialized enhanced distributed service registry on port " + gossipConfig.getPort());
            } else {
                // Fall back to basic registry
                serviceRegistry = ServiceRegistryFactory.getInstance().getRegistry();
                logger.info("Using basic service registry (distributed features disabled)");
            }

        } catch (Exception e) {
            logger.warning("Failed to initialize enhanced service registry, falling back to basic: " + e.getMessage());
            serviceRegistry = ServiceRegistryFactory.getInstance().getRegistry();
        }
    }

    /**
     * Discovers other tracker instances for load balancing and redundancy.
     */
    public static List<ServiceInstance> discoverOtherTrackers() {
        if (serviceRegistry == null) {
            return Collections.emptyList();
        }

        try {
            List<ServiceInstance> allTrackers = serviceRegistry.discoverServices("tracker");
            // Filter out this tracker instance
            return allTrackers.stream()
                .filter(tracker -> !trackerId.equals(tracker.getServiceId()))
                .collect(Collectors.toList());
        } catch (Exception e) {
            logger.warning("Failed to discover other trackers: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public static void updatePeerLastSeen(String peerId) {
        peerLastSeen.put(peerId, System.currentTimeMillis());
        logger.fine("Updated last seen time for peer: " + peerId);
    }

    public static boolean isPeerAlive(String peerId) {
        Long lastSeen = peerLastSeen.get(peerId);
        if (lastSeen == null) return false;
        return (System.currentTimeMillis() - lastSeen) < PEER_TIMEOUT_MS;
    }

    public static List<String> getActivePeers() {
        long now = System.currentTimeMillis();
        List<String> activePeers = peerLastSeen.entrySet().stream()
                .filter(entry -> (now - entry.getValue()) < PEER_TIMEOUT_MS)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        logger.fine("Active peers: " + activePeers.size());
        return activePeers;
    }

    /**
     * Deregisters a peer from the tracker.
     * This removes the peer from the last seen map and the peers map in TrackerHandler.
     *
     * @param peerId The ID of the peer to deregister
     * @return true if the peer was deregistered, false otherwise
     */
    public static boolean deregisterPeer(String peerId) {
        if (peerId == null || peerId.isEmpty()) {
            logger.warning("Invalid peer ID for deregistration");
            return false;
        }

        // Remove from last seen map
        Long removed = peerLastSeen.remove(peerId);

        // Notify TrackerHandler to remove from peers map
        boolean handlerRemoved = TrackerHandler.removePeer(peerId);

        boolean success = (removed != null) || handlerRemoved;

        if (success) {
            logger.info("Deregistered peer: " + peerId);
        } else {
            logger.warning("Failed to deregister peer: " + peerId + " (not found)");
        }

        return success;
    }
}
