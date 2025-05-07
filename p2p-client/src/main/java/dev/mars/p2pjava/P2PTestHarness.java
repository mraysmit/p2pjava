package dev.mars.p2pjava;

import dev.mars.p2pjava.bootstrap.BootstrapService;
import dev.mars.p2pjava.config.ConfigurationManager;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * P2PTestHarness - Integration Test Harness for the P2P File Sharing System
 *
 * This class serves as a comprehensive integration test harness for the P2P file sharing system,
 * demonstrating and validating the interaction between all components of the system. It provides
 * a controlled environment for testing the end-to-end functionality of the P2P network.
 *
 * Purpose:
 * - Validate the correct interaction between all system components (Tracker, IndexServer, Peers)
 * - Demonstrate the complete workflow of the P2P file sharing system
 * - Test file discovery and download functionality in a multi-peer environment
 * - Verify system resilience and proper cleanup procedures
 *
 * Components Tested:
 * - Tracker: Peer registration and discovery service
 * - IndexServer: File indexing and lookup service
 * - Peers: File sharing nodes that register with the Tracker and IndexServer
 * - P2PClient: Client component that interacts with the P2P network
 *
 * Test Workflow:
 * 1. Creates test directories and files for each peer with different content
 * 2. Uses the BootstrapService to start the Tracker and IndexServer services in the correct order
 * 3. Uses CountDownLatch to ensure services are ready before peers connect
 * 4. Starts multiple peer instances with different shared files
 * 5. Registers the peers with the Tracker service
 * 6. Registers the peers' files with the IndexServer
 * 7. Uses CountDownLatch to ensure peers are registered before tests
 * 8. Verifies that peers are alive and responsive after registration
 * 9. Tests file discovery by querying the IndexServer for specific files
 * 10. Tests file download by transferring files between peers
 * 11. Implements proper cleanup and shutdown procedures
 *
 * Advanced Features:
 * - Uses the ConfigurationManager for centralized configuration
 * - Includes health checks to verify component health during testing
 * - Implements graceful startup with dependency management
 * - Provides fallback mechanisms if the bootstrap service fails
 * - Tracks test failures for reporting
 * - Implements proper resource cleanup on shutdown
 *
 * Usage:
 * Run this class as a Java application to execute the integration test.
 * Configuration can be provided through system properties:
 * - tracker.host: Hostname for the Tracker service (default: localhost)
 * - tracker.port: Port for the Tracker service (default: 6000)
 * - index.host: Hostname for the IndexServer service (default: localhost)
 * - index.port: Port for the IndexServer service (default: 6001)
 * - test.files.dir: Directory for test files (default: files)
 * - test.cleanup.files: Whether to clean up test files after testing (default: false)
 */

public class P2PTestHarness {

    // Configuration manager
    private static ConfigurationManager configManager;

    // Bootstrap service
    private static BootstrapService bootstrapService;

    // ports and paths configurable
    private static final String TRACKER_SERVER_HOST = System.getProperty("tracker.host", "localhost");
    private static final int TRACKER_SERVER_PORT = Integer.getInteger("tracker.port", 6000);
    private static final String INDEX_SERVER_HOST = System.getProperty("index.host", "localhost");
    private static final int INDEX_SERVER_PORT = Integer.getInteger("index.port", 6001);
    private static final String TEST_FILES_DIR = System.getProperty("test.files.dir", "files");
    private static final int COUNTDOWN_LATCH_TIMEOUT = 5000;

    // Protocol constants
    private static final String PROTOCOL_VERSION = "PROTOCOL_V1";
    private static final String FILE_REQUEST = "GET_FILE";
    private static final String SENDING_FILE_RESPONSE = "SENDING_FILE";
    private static final String FILE_NOT_FOUND_RESPONSE = "FILE_NOT_FOUND";

    // Store peer references to prevent garbage collection
    // Enhanced peer management
    private static final Map<String, Peer> activePeers = new ConcurrentHashMap<>();
    private static final Map<String, Thread> peerThreads = new ConcurrentHashMap<>();

    // shutdown mechanism
    private static volatile boolean running = true;

    // CountDownLatch to ensure services are ready
    private static final CountDownLatch trackerStarted = new CountDownLatch(1);
    private static final CountDownLatch indexServerStarted = new CountDownLatch(1);

    // CountDownLatch to ensure peers are registered
    private static final CountDownLatch peersRegistered = new CountDownLatch(2);

    // Test results tracking
    private static final List<String> testFailures = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        // Initialize configuration
        configManager = ConfigurationManager.getInstance();
        configManager.initialize(args);

        // Register with health check
        HealthCheck.ServiceHealth health = HealthCheck.registerService("P2PTestHarness");
        health.addHealthDetail("startTime", System.currentTimeMillis());

        var p2pClient = new P2PClient(TRACKER_SERVER_HOST, TRACKER_SERVER_PORT, INDEX_SERVER_HOST, INDEX_SERVER_PORT);

        // Create thread pool
        ExecutorService executorService = Executors.newCachedThreadPool();

        try {
            // Create test directories and files
            createTestFiles();

            // Set up shutdown hook first to ensure cleanup
            setupShutdownHook();

            // Start tracker and index server using bootstrap service
            try {
                bootstrapService = new BootstrapService();
                bootstrapService.registerService("tracker", Class.forName("dev.mars.p2pjava.Tracker"), "startTracker", "stopTracker");
                bootstrapService.registerService("indexserver", Class.forName("dev.mars.p2pjava.IndexServer"), "startIndexServer", "stopIndexServer");
                bootstrapService.addDependency("indexserver", "tracker");

                System.out.println("Starting services using bootstrap service...");
                if (!bootstrapService.start()) {
                    throw new Exception("Failed to start services using bootstrap service");
                }

                // Signal that services are started
                trackerStarted.countDown();
                indexServerStarted.countDown();
            } catch (Exception e) {
                System.err.println("Failed to start services using bootstrap service: " + e.getMessage());
                System.out.println("Falling back to manual service startup...");

                // Fall back to manual service startup
                executorService.submit(() -> startTracker(TRACKER_SERVER_HOST, TRACKER_SERVER_PORT));
                executorService.submit(() -> startIndexServer(INDEX_SERVER_HOST, INDEX_SERVER_PORT));
            }

            // Wait for services to start with timeout
            if (!trackerStarted.await(COUNTDOWN_LATCH_TIMEOUT, TimeUnit.SECONDS) || !indexServerStarted.await(COUNTDOWN_LATCH_TIMEOUT, TimeUnit.SECONDS)) {
                throw new TimeoutException("Services failed to start within timeout period");
            }
            System.out.println("Services started successfully");

            // Define peer configurations
            List<PeerConfig> peerConfigs = new ArrayList<>();
            peerConfigs.add(new PeerConfig("peer1", "localhost", 8001, TEST_FILES_DIR + "/peer1/file1.txt", TEST_FILES_DIR + "/peer1/file2.txt"));
            peerConfigs.add(new PeerConfig("peer2", "localhost", 8002, TEST_FILES_DIR + "/peer2/file3.txt", TEST_FILES_DIR + "/peer2/file4.txt"));

            // Start all peers from the collection
            for (PeerConfig config : peerConfigs) {
                executorService.submit(() -> startPeer(
                        config.id,
                        config.host,
                        config.port,
                        TRACKER_SERVER_HOST,
                        TRACKER_SERVER_PORT,
                        config.files
                ));
            }

            // Wait for peers to register
            if (!peersRegistered.await(COUNTDOWN_LATCH_TIMEOUT, TimeUnit.SECONDS)) {
                throw new TimeoutException("Peer registration timed out");
            }

            //Validate that peers are started
            // Validate that peers are actually alive after registration
            System.out.println("Verifying peers are alive and responsive...");
            boolean allPeersAlive = true;

            // Check each started peer
            for (PeerConfig config : peerConfigs) {
                String peerId = config.id;
                String host = config.host;
                int port = config.port;

                // First check with tracker if peer is marked as alive
                boolean trackerSaysAlive = isPeerRunning(peerId);

                // Then directly try to connect to the peer
                boolean directConnectionWorks = verifyPeerIsListening(new PeerInfo(peerId, host, port));

                System.out.println("Peer " + peerId + " status - Tracker: " +
                        (trackerSaysAlive ? "ALIVE" : "NOT ALIVE") +
                        ", Direct connection: " +
                        (directConnectionWorks ? "SUCCESS" : "FAILED"));

                if (!trackerSaysAlive || !directConnectionWorks) {
                    allPeersAlive = false;
                    recordFailure("Peer " + peerId + " is not responsive after registration", null);
                }
            }

            if (!allPeersAlive) {
                System.err.println("Not all peers are responsive after registration!");
            } else {
                System.out.println("All peers are alive and responding correctly");
            }

            // Test file discovery
            List<PeerInfo> peers = p2pClient.discoverPeersWithFile("file1.txt");
            if (!verifyFileDiscovery(peers)) {
                recordFailure("File discovery verification failed", null);
            }

            //verify that peers are listening
            for (PeerInfo peer : peers) {
                if (!isPeerRunning(peer.getPeerId())) {
                    recordFailure("Peer " + peer.toString() + " is not running", null);
                }
                else {
                    System.out.println("Peer " + peer.toString() +  " is running");
                }
            }

            // Test file download if peers are available
            if (!peers.isEmpty()) {
                p2pClient.downloadFileFromPeer("file1.txt", TEST_FILES_DIR, peers.get(0));
            }

            if (testFailures.isEmpty()) {
                System.out.println("Test completed successfully");
            } else {
                System.err.println("Tests failed: " + String.join(", ", testFailures));
            }

        } catch (IOException e) {
            recordFailure("Test harness failed", e);
            e.printStackTrace();
        } catch (InterruptedException e) {
            recordFailure("Test harness interrupted", e);
            e.printStackTrace();
        } finally {
            // Clean up
            running = false;
            stopAndCleanup();
            executorService.shutdown();
        }
    }

    private static class PeerConfig {
        final String id;
        final String host;
        final int port;
        final String[] files;

        PeerConfig(String id, String host, int port, String... files) {
            this.id = id;
            this.host = host;
            this.port = port;
            this.files = files;
        }
    }

    static void startTracker(String trackerServerHost, int trackerServerPort) {
        try (ServerSocket serverSocket = new ServerSocket(trackerServerPort, 50, InetAddress.getByName(trackerServerHost))) {
            serverSocket.setSoTimeout(1000); // Timeout for accept
            System.out.println("Tracker started on " + trackerServerPort + ":" + trackerServerHost);
            trackerStarted.countDown();

            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    Socket socket = serverSocket.accept();
                    new Thread(new TrackerHandler(socket)).start();
                } catch (SocketTimeoutException e) {
                    // Expected, just continue loop
                }
            }
        } catch (IOException e) {
            recordFailure("Tracker error", e);
        }
    }

    static void startIndexServer(String indexServerHost, int indexServerPort) {
        try (ServerSocket serverSocket = new ServerSocket(indexServerPort, 50, InetAddress.getByName(indexServerHost))) {
            serverSocket.setSoTimeout(1000); // Timeout for accept
            System.out.println("IndexServer started on port " + indexServerPort + ":" + indexServerHost);
            indexServerStarted.countDown();

            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    Socket socket = serverSocket.accept();
                    new Thread(new IndexServerHandler(socket)).start();
                } catch (SocketTimeoutException e) {
                    // Expected, just continue loop
                }
            }
        } catch (IOException e) {
            recordFailure("IndexServer error", e);
        }
    }

    public static void startPeer(String peerId, String peerHost, int peerPort, String trackerHost, int trackerPort, String... filePaths) {
        try {
            System.out.println("Starting peer: " + peerId + " on port " + peerPort);

            // Create peer with all fields properly set
            Peer peer = new Peer(peerId, peerHost, peerPort, trackerHost, trackerPort);

            // Add shared files before starting
            for (String filePath : filePaths) {
                Path path = Paths.get(filePath);
                String fileName = path.getFileName().toString();
                peer.addSharedFile(filePath);
                registerFileWithIndexServer(fileName, peerId, peerPort);
            }

            // Store in activePeers map
            activePeers.put(peerId, peer);

            // Create thread that will keep running
            Thread peerThread = new Thread(() -> {
                try {
                    // Register with tracker before start
                    peer.registerWithTracker();

                    // Start the peer which blocks on socket accept
                    peer.start();
                } catch (Exception e) {
                    recordFailure("[PEER ERROR] " + peerId, e);
                    e.printStackTrace();
                }
                System.out.println("Peer thread for " + peerId + " exited");
            }, "Peer-" + peerId);

            // Store thread reference
            peerThreads.put(peerId, peerThread);

            // Start the thread
            peerThread.start();

            // Wait for peer to be ready with explicit waitForStartup method
            boolean isReady = waitForPeerStartup(peer, peerHost, peerPort, 3000);

            if (isReady) {
                System.out.println("Peer " + peerId + " is now listening on port " + peerPort);
                // Only signal registration AFTER socket is confirmed listening
                peersRegistered.countDown();
                System.out.println("Peer " + peerId + " registration complete");
            } else {
                recordFailure("Timeout waiting for peer " + peerId + " to start listening", null);
                activePeers.remove(peerId);
                peerThreads.remove(peerId);
                // Don't count down if peer failed to start
            }
        } catch (Exception e) {
            recordFailure("Error starting peer " + peerId, e);
            activePeers.remove(peerId);
            peerThreads.remove(peerId);
        }
    }

    private static boolean waitForPeerStartup(Peer peer, String host, int port, int timeoutMs) {
        long startTime = System.currentTimeMillis();
        boolean isListening = false;

        try {
            while (!isListening && System.currentTimeMillis() - startTime < timeoutMs) {
                isListening = verifyPeerIsListening(new PeerInfo(peer.getPeerId(), host, port));
                if (!isListening) {
                    Thread.sleep(100);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return isListening;
    }

    private static void setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down peers...");
            stopAndCleanup();
        }));
    }

    private static boolean isPeerRunning(String peerId) {
        try (Socket socket = new Socket(TRACKER_SERVER_HOST, TRACKER_SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("IS_PEER_ALIVE " + peerId);
            String response = in.readLine();
            return "ALIVE".equals(response);
        } catch (IOException e) {
            recordFailure("Error checking peer status", e);
            return false;
        }
    }

    private static void registerFileWithIndexServer(String fileName, String peerId, int port) {
        String indexHost = System.getProperty("index.host", "localhost");
        int indexPort = Integer.getInteger("index.port", 6001);
        try (Socket socket = new Socket(indexHost, indexPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("REGISTER_FILE " + fileName + " " + peerId + " " + port);
            String response = in.readLine();
            System.out.println("IndexServer response: " + response);
        } catch (IOException e) {
            recordFailure("Failed to register file with index server", e);
        }
    }



//    private static String extractField(String input, String pattern) {
//        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
//        java.util.regex.Matcher m = p.matcher(input);
//        if (m.find() && m.groupCount() >= 1) {
//            return m.group(1);
//        }
//        return null;
//    }

    // Verify that the file discovery results are as expected
    private static boolean verifyFileDiscovery(List<PeerInfo> peers) {
        if (peers == null || peers.isEmpty()) {
            recordFailure("File discovery failed: No peers found", null);
            return false;
        }

        System.out.println("File discovery verification successful");
        return true;
    }


    private static boolean verifyPeerIsListening(PeerInfo peer) {
        try {
            // Test if the socket is accepting connections
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(peer.getAddress(), peer.getPort()), 1000);
                System.out.println("Peer " + peer.getPeerId() + " is listening on port " + peer.getPort());
                return true;
            }
        } catch (IOException e) {
            System.err.println("Peer " + peer.getPeerId() + " is not listening: " + e.getMessage());
            return false;
        }
    }

    // Add method to properly shut down peers
    public static void stopAllPeers() {
        System.out.println("Stopping all peers...");
        for (Map.Entry<String, Peer> entry : activePeers.entrySet()) {
            try {
                System.out.println("Stopping peer: " + entry.getKey());
                entry.getValue().stop();
            } catch (Exception e) {
                System.err.println("Error stopping peer " + entry.getKey() + ": " + e.getMessage());
            }
        }
    }

    private static void simulateFileDiscoveryAndDownload() {
        try (Socket socket = new Socket("localhost", INDEX_SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Find peers with file
            out.println("GET_PEERS_WITH_FILE file1.txt");
            String response = in.readLine();
            System.out.println("Peers with file1.txt: " + response);

            // Parse response and connect to first peer
            // In a real implementation, you would parse the PeerInfo and connect
            System.out.println("Simulating download from peer1...");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // Cleans up test files created during the test
    private static void cleanupTestFiles() {
        try {
            // Delete test directories recursively
            Path testRoot = Paths.get(TEST_FILES_DIR);
            if (Files.exists(testRoot)) {
                Files.walk(testRoot)
                        .sorted((p1, p2) -> -p1.compareTo(p2)) // Reverse order to delete files before directories
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                System.err.println("Failed to delete: " + path + ": " + e.getMessage());
                            }
                        });
                System.out.println("Test files cleaned up");
            }
        } catch (IOException e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
    }

    public static void stopAndCleanup() {
        System.out.println("Stopping and cleaning up test harness...");

        // Stop all active peers first
        stopAllPeers();

        // Interrupt any remaining peer threads
        for (Map.Entry<String, Thread> entry : peerThreads.entrySet()) {
            try {
                if (entry.getValue().isAlive()) {
                    System.out.println("Interrupting peer thread: " + entry.getKey());
                    entry.getValue().interrupt();
                    // Give it a short time to respond to interrupt
                    entry.getValue().join(1000);
                }
            } catch (Exception e) {
                System.err.println("Error interrupting peer thread " + entry.getKey() + ": " + e.getMessage());
            }
        }

        // Clear collections
        activePeers.clear();
        peerThreads.clear();

        // Set running flag to false to stop service loops
        running = false;

        // Stop bootstrap service if it was used
        if (bootstrapService != null) {
            try {
                System.out.println("Stopping bootstrap service...");
                bootstrapService.stop();
                System.out.println("Bootstrap service stopped");
            } catch (Exception e) {
                System.err.println("Error stopping bootstrap service: " + e.getMessage());
            }
        }

        // Deregister from health check
        try {
            HealthCheck.deregisterService("P2PTestHarness");
        } catch (Exception e) {
            System.err.println("Error deregistering from health check: " + e.getMessage());
        }

        // Optional: clean up test files if needed
        if (Boolean.getBoolean("test.cleanup.files")) {
            cleanupTestFiles();
        }

        System.out.println("Test harness cleanup complete");
    }

    private static void recordFailure(String message, Exception e) {
        String fullMessage = message;
        if (e != null) {
            fullMessage += ": " + e.getMessage();
        }
        testFailures.add(fullMessage);
        System.err.println(fullMessage);
    }

    private static void createTestFiles() throws IOException {
        // Create directories
        Files.createDirectories(Paths.get("files/peer1"));
        Files.createDirectories(Paths.get("files/peer2"));

        // Create test files
        Files.writeString(Paths.get("files/peer1/file1.txt"), "This is file 1 content");
        Files.writeString(Paths.get("files/peer1/file2.txt"), "This is file 2 content");
        Files.writeString(Paths.get("files/peer2/file3.txt"), "This is file 3 content");
        Files.writeString(Paths.get("files/peer2/file4.txt"), "This is file 4 content");
    }
}
