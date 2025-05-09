package dev.mars.p2pjava;

import dev.mars.p2pjava.common.PeerInfo;
import dev.mars.p2pjava.discovery.ServiceRegistry;
import dev.mars.p2pjava.discovery.ServiceRegistryFactory;
import dev.mars.p2pjava.storage.FileBasedIndexStorage;
import dev.mars.p2pjava.storage.FileIndexStorage;
import dev.mars.p2pjava.cache.CacheManager;
import dev.mars.p2pjava.connection.ConnectionPool;
import dev.mars.p2pjava.util.HealthCheck;
import dev.mars.p2pjava.util.ThreadManager;

import java.io.*;
import java.net.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

/**
 * IndexServer is a server that maintains a file index and provides methods to register,
 * deregister, and search for files across multiple peers.
 * It uses a file-based storage system and supports caching for improved performance.
 * Currently, it is designed to run as a single instance application.
 */

public class IndexServer {
    private static final Logger logger = Logger.getLogger(IndexServer.class.getName());
    private static final int DEFAULT_INDEX_SERVER_PORT = 6001;
    private static int indexServerPort;
    private static final int THREAD_POOL_SIZE = 10;
    private static final String STORAGE_DIR = "data";
    private static final String STORAGE_FILE = "file_index.dat";

    // Cache configuration
    private static final long CACHE_TTL_MS = 60000; // 1 minute
    private static final long CACHE_REFRESH_MS = 300000; // 5 minutes

    // Connection pool configuration
    private static final int MAX_CONNECTIONS = 100;
    private static final long CONNECTION_TIMEOUT_MS = 5000; // 5 seconds

    private static FileIndexStorage fileIndexStorage;
    private static CacheManager<String, List<PeerInfo>> fileCache;
    private static ConnectionPool connectionPool;
    private static volatile boolean running = true;
    private static ExecutorService threadPool;
    private static HealthCheck.ServiceHealth health;

    public static void main(String[] args) {
        configureLogging();
        startIndexServer();
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

            logger.addHandler(handler);
            logger.setLevel(Level.INFO);
        } catch (Exception e) {
            System.err.println("Error setting up logger: " + e.getMessage());
        }
    }

    public static void startIndexServer() {
        // Initialize index server port from system property or use default
        String indexServerPortStr = System.getProperty("indexserver.port");
        if (indexServerPortStr != null && !indexServerPortStr.isEmpty()) {
            try {
                indexServerPort = Integer.parseInt(indexServerPortStr);
                logger.info("Using dynamic port for index server: " + indexServerPort);
            } catch (NumberFormatException e) {
                logger.warning("Invalid indexserver.port system property: " + indexServerPortStr + ". Using default port.");
                indexServerPort = DEFAULT_INDEX_SERVER_PORT;
            }
        } else {
            indexServerPort = DEFAULT_INDEX_SERVER_PORT;
            logger.info("No dynamic port specified for index server. Using default port: " + indexServerPort);
        }
        // Register with health check system
        health = HealthCheck.registerService("IndexServer");
        health.addHealthDetail("status", "starting");
        health.addHealthDetail("port", indexServerPort);
        health.setHealthy(false); // Will be set to true when fully initialized

        // Initialize thread pool using ThreadManager for standardized thread management
        threadPool = ThreadManager.getFixedThreadPool(
            "IndexServerThreadPool", 
            "IndexServer", 
            THREAD_POOL_SIZE
        );

        // Initialize connection pool
        connectionPool = new ConnectionPool(MAX_CONNECTIONS, CONNECTION_TIMEOUT_MS);
        logger.info("Connection pool initialized");

        // Initialize file index storage
        try {
            // Create storage directory if it doesn't exist
            File storageDir = new File(STORAGE_DIR);
            if (!storageDir.exists()) {
                storageDir.mkdirs();
            }

            String storagePath = Paths.get(STORAGE_DIR, STORAGE_FILE).toString();
            fileIndexStorage = new FileBasedIndexStorage(storagePath);

            if (!fileIndexStorage.initialize()) {
                logger.severe("Failed to initialize file index storage");
                return;
            }

            logger.info("File index storage initialized successfully");

            // Initialize file cache with a loader function that uses the storage
            fileCache = new CacheManager<>(CACHE_TTL_MS, CACHE_REFRESH_MS, 
                    fileName -> fileIndexStorage.getPeersWithFile(fileName));
            logger.info("File cache initialized");

            // Update health status with storage and cache information
            health.addHealthDetail("storageInitialized", true);
            health.addHealthDetail("cacheInitialized", true);
            health.addHealthDetail("storageDir", STORAGE_DIR);
            health.addHealthDetail("storageFile", STORAGE_FILE);
            health.addHealthDetail("fileCount", fileIndexStorage.getFileCount());
            health.addHealthDetail("peerCount", fileIndexStorage.getPeerCount());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error initializing file index storage", e);
            // Update health status to reflect the error
            health.setHealthy(false);
            health.addHealthDetail("status", "error");
            health.addHealthDetail("errorMessage", e.getMessage());
            // Properly shut down the thread pool to prevent thread leaks
            stopIndexServer();
            return;
        }

        // Set up shutdown hook for graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down IndexServer...");
            stopIndexServer();
        }));

        try (ServerSocket serverSocket = new ServerSocket(indexServerPort)) {
            serverSocket.setSoTimeout(5000); // Add timeout to allow for graceful shutdown
            logger.info("IndexServer is running on port " + indexServerPort);

            // Update health status to indicate server is running
            health.setHealthy(true);
            health.addHealthDetail("status", "running");
            health.addHealthDetail("startTime", System.currentTimeMillis());

            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    threadPool.submit(new IndexServerHandler(socket));
                } catch (SocketTimeoutException e) {
                    // This is expected, just continue the loop
                } catch (IOException e) {
                    if (running) {
                        logger.log(Level.SEVERE, "Error accepting connection", e);
                        // Update health status to reflect the error
                        health.addHealthDetail("lastError", e.getMessage());
                        health.addHealthDetail("lastErrorTime", System.currentTimeMillis());
                    }
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error starting IndexServer", e);
            // Update health status to reflect the critical error
            health.setHealthy(false);
            health.addHealthDetail("status", "failed");
            health.addHealthDetail("criticalError", e.getMessage());
            health.addHealthDetail("errorTime", System.currentTimeMillis());
        } finally {
            stopIndexServer();
        }
    }

    public static void stopIndexServer() {
        if (!running) {
            return;
        }

        running = false;

        // Update health status to indicate server is shutting down
        if (health != null) {
            health.setHealthy(false);
            health.addHealthDetail("status", "shutting_down");
            health.addHealthDetail("shutdownTime", System.currentTimeMillis());
        }

        // Shutdown thread pool using ThreadManager
        if (threadPool != null && !threadPool.isShutdown()) {
            try {
                logger.info("Shutting down IndexServer thread pool");
                ThreadManager.shutdownThreadPool("IndexServerThreadPool", 5, TimeUnit.SECONDS);
                logger.info("IndexServer thread pool shut down successfully");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error shutting down thread pool", e);
            }
        }

        // Shutdown file index storage
        if (fileIndexStorage != null) {
            try {
                if (fileIndexStorage.shutdown()) {
                    logger.info("File index storage shut down successfully");
                } else {
                    logger.warning("Failed to shut down file index storage");
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error shutting down file index storage", e);
            }
        }

        // Shutdown file cache
        if (fileCache != null) {
            try {
                fileCache.shutdown();
                logger.info("File cache shut down successfully");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error shutting down file cache", e);
            }
        }

        // Shutdown connection pool
        if (connectionPool != null) {
            try {
                connectionPool.shutdown();
                logger.info("Connection pool shut down successfully");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error shutting down connection pool", e);
            }
        }

        // Deregister from health check system
        if (health != null) {
            HealthCheck.deregisterService("IndexServer");
            logger.info("Deregistered from health check system");
        }

        logger.info("IndexServer stopped");
    }

    public static void registerFile(String fileName, PeerInfo peerInfo) {
        if (fileIndexStorage == null) {
            logger.warning("File index storage not initialized");
            if (health != null) {
                health.addHealthDetail("lastOperationError", "File index storage not initialized");
                health.addHealthDetail("lastOperationTime", System.currentTimeMillis());
            }
            return;
        }

        // Update health with operation information
        if (health != null) {
            health.addHealthDetail("lastOperation", "registerFile");
            health.addHealthDetail("lastOperationTime", System.currentTimeMillis());
            health.addHealthDetail("lastFileName", fileName);
        }

        // Convert from tracker PeerInfo to common PeerInfo if needed
        dev.mars.p2pjava.common.PeerInfo commonPeerInfo;
        if (peerInfo instanceof dev.mars.p2pjava.common.PeerInfo) {
            commonPeerInfo = (dev.mars.p2pjava.common.PeerInfo) peerInfo;
        } else {
            commonPeerInfo = new dev.mars.p2pjava.common.PeerInfo(
                peerInfo.getPeerId(), peerInfo.getAddress(), peerInfo.getPort());
        }

        boolean success = fileIndexStorage.registerFile(fileName, commonPeerInfo);
        if (success) {
            logger.info("Registered file " + fileName + " with peer " + commonPeerInfo);

            // Invalidate cache entry for this file to ensure fresh data on next request
            if (fileCache != null) {
                fileCache.remove(fileName);
                logger.fine("Invalidated cache entry for file: " + fileName);
            }

            // Update health with success information
            if (health != null) {
                health.addHealthDetail("lastOperationSuccess", true);
                health.addHealthDetail("fileCount", fileIndexStorage.getFileCount());
                health.addHealthDetail("peerCount", fileIndexStorage.getPeerCount());
            }
        } else {
            logger.warning("Failed to register file " + fileName + " with peer " + commonPeerInfo);

            // Update health with failure information
            if (health != null) {
                health.addHealthDetail("lastOperationSuccess", false);
                health.addHealthDetail("lastOperationError", "Failed to register file");
            }
        }
    }

    /**
     * Deregisters a file from a peer in the index.
     *
     * @param fileName The name of the file
     * @param peerInfo The peer that no longer has the file
     * @return true if deregistration was successful, false otherwise
     */
    public static boolean deregisterFile(String fileName, PeerInfo peerInfo) {
        if (fileIndexStorage == null) {
            logger.warning("File index storage not initialized");
            if (health != null) {
                health.addHealthDetail("lastOperationError", "File index storage not initialized");
                health.addHealthDetail("lastOperationTime", System.currentTimeMillis());
            }
            return false;
        }

        // Update health with operation information
        if (health != null) {
            health.addHealthDetail("lastOperation", "deregisterFile");
            health.addHealthDetail("lastOperationTime", System.currentTimeMillis());
            health.addHealthDetail("lastFileName", fileName);
        }

        // Convert from tracker PeerInfo to common PeerInfo if needed
        dev.mars.p2pjava.common.PeerInfo commonPeerInfo;
        if (peerInfo instanceof dev.mars.p2pjava.common.PeerInfo) {
            commonPeerInfo = (dev.mars.p2pjava.common.PeerInfo) peerInfo;
        } else {
            commonPeerInfo = new dev.mars.p2pjava.common.PeerInfo(
                peerInfo.getPeerId(), peerInfo.getAddress(), peerInfo.getPort());
        }

        boolean success = fileIndexStorage.deregisterFile(fileName, commonPeerInfo);
        if (success) {
            logger.info("Deregistered file " + fileName + " from peer " + commonPeerInfo);

            // Invalidate cache entry for this file to ensure fresh data on next request
            if (fileCache != null) {
                fileCache.remove(fileName);
                logger.fine("Invalidated cache entry for file: " + fileName);
            }

            // Update health with success information
            if (health != null) {
                health.addHealthDetail("lastOperationSuccess", true);
                health.addHealthDetail("fileCount", fileIndexStorage.getFileCount());
                health.addHealthDetail("peerCount", fileIndexStorage.getPeerCount());
            }
        } else {
            logger.warning("Failed to deregister file " + fileName + " from peer " + commonPeerInfo);

            // Update health with failure information
            if (health != null) {
                health.addHealthDetail("lastOperationSuccess", false);
                health.addHealthDetail("lastOperationError", "Failed to deregister file");
            }
        }

        return success;
    }

    /**
     * Deregisters all files associated with a peer.
     *
     * @param peerInfo The peer to deregister
     * @return true if deregistration was successful, false otherwise
     */
    public static boolean deregisterPeer(PeerInfo peerInfo) {
        if (fileIndexStorage == null) {
            logger.warning("File index storage not initialized");
            if (health != null) {
                health.addHealthDetail("lastOperationError", "File index storage not initialized");
                health.addHealthDetail("lastOperationTime", System.currentTimeMillis());
            }
            return false;
        }

        // Update health with operation information
        if (health != null) {
            health.addHealthDetail("lastOperation", "deregisterPeer");
            health.addHealthDetail("lastOperationTime", System.currentTimeMillis());
            health.addHealthDetail("lastPeerId", peerInfo.getPeerId());
        }

        // Convert from tracker PeerInfo to common PeerInfo if needed
        dev.mars.p2pjava.common.PeerInfo commonPeerInfo;
        if (peerInfo instanceof dev.mars.p2pjava.common.PeerInfo) {
            commonPeerInfo = (dev.mars.p2pjava.common.PeerInfo) peerInfo;
        } else {
            commonPeerInfo = new dev.mars.p2pjava.common.PeerInfo(
                peerInfo.getPeerId(), peerInfo.getAddress(), peerInfo.getPort());
        }

        boolean success = fileIndexStorage.deregisterPeer(commonPeerInfo);
        if (success) {
            logger.info("Deregistered all files for peer " + commonPeerInfo);

            // Invalidate all cache entries since we don't know which files were affected
            if (fileCache != null) {
                fileCache.clear();
                logger.fine("Cleared file cache after peer deregistration");
            }

            // Update health with success information
            if (health != null) {
                health.addHealthDetail("lastOperationSuccess", true);
                health.addHealthDetail("fileCount", fileIndexStorage.getFileCount());
                health.addHealthDetail("peerCount", fileIndexStorage.getPeerCount());
            }
        } else {
            logger.warning("Failed to deregister peer " + commonPeerInfo);

            // Update health with failure information
            if (health != null) {
                health.addHealthDetail("lastOperationSuccess", false);
                health.addHealthDetail("lastOperationError", "Failed to deregister peer");
            }
        }

        return success;
    }

    public static List<PeerInfo> getPeersWithFile(String fileName) {
        if (fileIndexStorage == null) {
            logger.warning("File index storage not initialized");
            if (health != null) {
                health.addHealthDetail("lastOperationError", "File index storage not initialized");
                health.addHealthDetail("lastOperationTime", System.currentTimeMillis());
            }
            return Collections.emptyList();
        }

        // Update health with operation information
        if (health != null) {
            health.addHealthDetail("lastOperation", "getPeersWithFile");
            health.addHealthDetail("lastOperationTime", System.currentTimeMillis());
            health.addHealthDetail("lastFileName", fileName);
        }

        // Use cache if available, otherwise fall back to direct storage access
        List<PeerInfo> result;
        boolean cacheHit = false;

        if (fileCache != null) {
            try {
                result = fileCache.get(fileName);
                cacheHit = true;

                // Update health with cache hit information
                if (health != null) {
                    health.addHealthDetail("lastOperationSuccess", true);
                    health.addHealthDetail("cacheHit", true);
                    health.addHealthDetail("resultCount", result.size());
                }

                return result;
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error getting peers from cache, falling back to storage", e);

                // Update health with cache error information
                if (health != null) {
                    health.addHealthDetail("cacheError", e.getMessage());
                }
            }
        }

        // Direct storage access as fallback
        result = fileIndexStorage.getPeersWithFile(fileName);

        // Update health with storage access information
        if (health != null) {
            health.addHealthDetail("lastOperationSuccess", true);
            health.addHealthDetail("cacheHit", cacheHit);
            health.addHealthDetail("resultCount", result.size());
        }

        return result;
    }

    public static Map<String, List<PeerInfo>> getFileMap() {
        if (fileIndexStorage == null) {
            logger.warning("File index storage not initialized");
            if (health != null) {
                health.addHealthDetail("lastOperation", "getFileMap");
                health.addHealthDetail("lastOperationTime", System.currentTimeMillis());
                health.addHealthDetail("lastOperationError", "File index storage not initialized");
                health.addHealthDetail("lastOperationSuccess", false);
            }
            return Collections.emptyMap();
        }

        // Update health with operation information
        if (health != null) {
            health.addHealthDetail("lastOperation", "getFileMap");
            health.addHealthDetail("lastOperationTime", System.currentTimeMillis());
        }

        Map<String, List<PeerInfo>> results = fileIndexStorage.getAllFiles();

        // Update health with results information
        if (health != null) {
            health.addHealthDetail("lastOperationSuccess", true);
            health.addHealthDetail("fileMapSize", results.size());

            // Count total peers across all files
            int totalPeers = 0;
            for (List<PeerInfo> peers : results.values()) {
                totalPeers += peers.size();
            }
            health.addHealthDetail("fileMapTotalPeers", totalPeers);
        }

        return results;
    }

    /**
     * Searches for files matching a pattern.
     *
     * @param pattern The search pattern (can be a partial file name)
     * @return A map of matching file names to lists of peers that have those files
     */
    public static Map<String, List<PeerInfo>> searchFiles(String pattern) {
        if (fileIndexStorage == null) {
            logger.warning("File index storage not initialized");
            if (health != null) {
                health.addHealthDetail("lastOperationError", "File index storage not initialized");
                health.addHealthDetail("lastOperationTime", System.currentTimeMillis());
            }
            return Collections.emptyMap();
        }

        // Update health with operation information
        if (health != null) {
            health.addHealthDetail("lastOperation", "searchFiles");
            health.addHealthDetail("lastOperationTime", System.currentTimeMillis());
            health.addHealthDetail("lastSearchPattern", pattern);
        }

        logger.info("Searching for files matching pattern: " + pattern);
        Map<String, List<PeerInfo>> results = fileIndexStorage.searchFiles(pattern);
        logger.info("Found " + results.size() + " files matching pattern: " + pattern);

        // Update health with search results information
        if (health != null) {
            health.addHealthDetail("lastOperationSuccess", true);
            health.addHealthDetail("searchResultCount", results.size());

            // Count total peers across all results
            int totalPeers = 0;
            for (List<PeerInfo> peers : results.values()) {
                totalPeers += peers.size();
            }
            health.addHealthDetail("searchResultTotalPeers", totalPeers);
        }

        return results;
    }
}
