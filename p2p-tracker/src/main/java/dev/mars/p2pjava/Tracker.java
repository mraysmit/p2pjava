package dev.mars.p2pjava;




import dev.mars.p2pjava.discovery.ServiceRegistry;
import dev.mars.p2pjava.discovery.ServiceRegistryFactory;

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
    private static final int TRACKER_PORT = 6000;
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
        // Initialize thread pool with custom thread factory for better debugging
        threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE, r -> {
            Thread t = new Thread(r, "Tracker-" + UUID.randomUUID().toString().substring(0, 8));
            t.setDaemon(true);
            return t;
        });

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
        Map<String, String> metadata = new HashMap<>();
        metadata.put("startTime", String.valueOf(System.currentTimeMillis()));

        boolean registered = serviceRegistry.registerService("tracker", trackerId, host, TRACKER_PORT, metadata);
        if (registered) {
            logger.info("Registered tracker with service registry: " + trackerId + " at " + host + ":" + TRACKER_PORT);
        } else {
            logger.warning("Failed to register tracker with service registry");
        }

        // Set up shutdown hook for graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down Tracker...");
            stopTracker();
        }));

        try (ServerSocket serverSocket = new ServerSocket(TRACKER_PORT)) {
            serverSocket.setSoTimeout(5000); // Add timeout to allow for graceful shutdown
            logger.info("Tracker started on port " + TRACKER_PORT);

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

        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdown();
            try {
                // Wait for existing tasks to terminate
                if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    // Force shutdown if tasks don't terminate
                    threadPool.shutdownNow();
                    if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                        logger.warning("Thread pool did not terminate");
                    }
                }
            } catch (InterruptedException e) {
                // (Re-)Cancel if current thread also interrupted
                threadPool.shutdownNow();
                Thread.currentThread().interrupt();
                logger.warning("Shutdown interrupted");
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
