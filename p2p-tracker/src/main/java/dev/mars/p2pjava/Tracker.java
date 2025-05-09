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

    // Service registry for service discovery
    private static ServiceRegistry serviceRegistry;

    // Unique ID for this tracker instance
    private static String trackerId;

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

        // Initialize service registry
        serviceRegistry = ServiceRegistryFactory.getInstance().getRegistry();

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

        // Register this tracker instance with the service registry
        Map<String, String> metadata = new ConcurrentHashMap<>();
        metadata.put("startTime", String.valueOf(System.currentTimeMillis()));

        boolean registered = serviceRegistry.registerService("tracker", trackerId, host, trackerPort, metadata);
        if (registered) {
            logger.info("Registered tracker with service registry: " + trackerId + " at " + host + ":" + trackerPort);
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

        logger.info("Tracker stopped");
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
