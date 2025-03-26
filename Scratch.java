
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class P2PTestHarness {
    // Make ports and paths configurable
    private static final int TRACKER_PORT = Integer.getInteger("tracker.port", 6000);
    private static final int INDEX_SERVER_PORT = Integer.getInteger("index.port", 6001);
    private static final String TEST_FILES_DIR = System.getProperty("test.files.dir", "files");

    // Add shutdown mechanism
    private static volatile boolean running = true;

    // Better synchronization
    private static final CountDownLatch trackerStarted = new CountDownLatch(1);
    private static final CountDownLatch indexServerStarted = new CountDownLatch(1);
    private static final CountDownLatch peersRegistered = new CountDownLatch(2);

    // Test results tracking
    private static final List<String> testFailures = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        ExecutorService executorService = Executors.newCachedThreadPool();

        try {
            // Create test directories and files
            createTestFiles();

            // Start tracker and index server
            executorService.submit(() -> startTracker());
            executorService.submit(() -> startIndexServer());

            // Wait for services to start with timeout
            if (!trackerStarted.await(5, TimeUnit.SECONDS) || !indexServerStarted.await(5, TimeUnit.SECONDS)) {
                throw new TimeoutException("Services failed to start within timeout period");
            }
            System.out.println("Services started successfully");

            // Start peers
            executorService.submit(() -> startPeer("peer1", 8001, TEST_FILES_DIR + "/peer1"));
            executorService.submit(() -> startPeer("peer2", 8002, TEST_FILES_DIR + "/peer2"));

            // Wait for peers to register
            if (!peersRegistered.await(5, TimeUnit.SECONDS)) {
                throw new TimeoutException("Peer registration timed out");
            }

            // Test file discovery
            List<PeerInfo> peers = discoverPeersWithFile("file1.txt");
            if (!verifyFileDiscovery(peers)) {
                testFailures.add("File discovery verification failed");
            }

            // Test file download if peers are available
            if (!peers.isEmpty()) {
                downloadFileFromPeer("file1.txt", peers.get(0));
            }

            if (testFailures.isEmpty()) {
                System.out.println("Test completed successfully");
            } else {
                System.err.println("Tests failed: " + String.join(", ", testFailures));
            }
        } catch (Exception e) {
            System.err.println("Test harness failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Clean shutdown
            running = false;
            executorService.shutdownNow();
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("Executor service did not terminate gracefully");
            }

            // Optional cleanup
            if (Boolean.getBoolean("test.cleanup")) {
                cleanupTestFiles();
            }
        }
    }

    private static void startTracker() {
        try (ServerSocket serverSocket = new ServerSocket(TRACKER_PORT)) {
            serverSocket.setSoTimeout(1000); // Timeout for accept
            System.out.println("Tracker started on port " + TRACKER_PORT);
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
            System.err.println("Tracker error: " + e.getMessage());
        }
    }

    private static void startIndexServer() {
        try (ServerSocket serverSocket = new ServerSocket(INDEX_SERVER_PORT)) {
            serverSocket.setSoTimeout(1000); // Timeout for accept
            System.out.println("IndexServer started on port " + INDEX_SERVER_PORT);
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
            System.err.println("IndexServer error: " + e.getMessage());
        }
    }

    private static void startPeer(String peerId, int port, String directory) {
        try {
            Peer peer = new Peer(peerId, port);

            // Register files in directory
            Files.