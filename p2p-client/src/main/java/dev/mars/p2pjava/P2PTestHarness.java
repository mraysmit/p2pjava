package dev.mars.p2pjava;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/*
This test harness:

Creates test directories and files for each peer
Starts the Tracker and IndexServer services in separate threads using executorService
Uses CountDownLatch to ensure services are ready before peers connect
Uses CountDownLatch to ensure peers are registered before tests
Starts multiple peers with different shared files
Registers the peers and their files with the appropriate services
Simulates file discovery by querying the IndexServer
Shows how you would simulate a file download between peers
Implements an actual download instead of just simulation

 */

public class P2PTestHarness {

// ports and paths configurable
private static final String TRACKER_SERVER_HOST = System.getProperty("tracker.host", "localhost");
private static final int TRACKER_SERVER_PORT = Integer.getInteger("tracker.port", 6000);
private static final String INDEX_SERVER_HOST = System.getProperty("index.host", "localhost");
private static final int INDEX_SERVER_PORT = Integer.getInteger("index.port", 6001);
private static final String TEST_FILES_DIR = System.getProperty("test.files.dir", "files");

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

    // Create thread pool
    ExecutorService executorService = Executors.newCachedThreadPool();

    try {
        // Create test directories and files
        createTestFiles();

        // Start tracker and index server
        // Start tracker and index server
        executorService.submit(() -> startTracker(TRACKER_SERVER_HOST, TRACKER_SERVER_PORT));
        executorService.submit(() -> startIndexServer(INDEX_SERVER_HOST, INDEX_SERVER_PORT));

        // Wait for services to start with timeout
        if (!trackerStarted.await(5, TimeUnit.SECONDS) || !indexServerStarted.await(5, TimeUnit.SECONDS)) {
            throw new TimeoutException("Services failed to start within timeout period");
        }
        System.out.println("Services started successfully");

        // Start peers
        executorService.submit(() -> startPeer("peer1", "localhost",8001, TEST_FILES_DIR + "/peer1/file1.txt", TEST_FILES_DIR + "/peer1/file2.txt"));
        executorService.submit(() -> startPeer("peer2","localhost", 8002, TEST_FILES_DIR + "/peer2"));

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

        // Simulate file discovery and download
        //simulateFileDiscoveryAndDownload();

    } catch (IOException e) {
        System.err.println("Test harness failed: " + e.getMessage());
        e.printStackTrace();

    } catch (InterruptedException e) {
        System.err.println("Test harness failed: " + e.getMessage());
        e.printStackTrace();

    } finally {
        // Clean up
        running = false;
        executorService.shutdown();
        if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
            System.err.println("Executor service did not terminate gracefully");
        }

        // Optional cleanup
        if (Boolean.getBoolean("test.cleanup")) {
            cleanupTestFiles();
        }
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
        System.err.println("Tracker error: " + e.getMessage());
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
        System.err.println("IndexServer error: " + e.getMessage());
    }
}

public static void startPeer(String peerId, String peerHost, int peerPort, String... fileNames) {
    new Thread(() -> {
        try {
            // Create the peer instance
            Peer peer = new Peer(peerId, peerHost, peerPort);

            // Add shared files
            for (String fileName : fileNames) {
                peer.addSharedFile(fileName);
                registerFileWithIndexServer(fileName, peerId, peerPort);
            }

            // Register with tracker and start the peer
            peer.registerWithTracker();
            peer.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }).start();
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
        e.printStackTrace();
    }
}

// Discover peers that have the specified file
private static List<PeerInfo> discoverPeersWithFile(String fileName) {
    List<PeerInfo> result = new ArrayList<>();
    try (Socket socket = new Socket("localhost", INDEX_SERVER_PORT);
         PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
         BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

        out.println("GET_PEERS_WITH_FILE " + fileName);
        String response = in.readLine();
        System.out.println("Peers with " + fileName + ": " + response);

        // Parse response string into PeerInfo objects
        // Expected format: [PeerInfo{peerId='peer1', address='127.0.0.1', port=8001}, ...]
        if (response != null && response.startsWith("[") && response.endsWith("]")) {
            String[] peerStrings = response.substring(1, response.length() - 1).split(", ");
            for (String peerString : peerStrings) {
                // Simple parsing - in production you would use a proper serialization mechanism
                if (peerString.contains("peerId=") && peerString.contains("address=") && peerString.contains("port=")) {
                    String peerId = extractValue(peerString, "peerId=");
                    String address = extractValue(peerString, "address=");
                    int port = Integer.parseInt(extractValue(peerString, "port="));
                    result.add(new PeerInfo(peerId, address, port));
                }
            }
        }
    } catch (IOException e) {
        System.err.println("Error discovering peers: " + e.getMessage());
        testFailures.add("Failed to discover peers: " + e.getMessage());
    }
    return result;
}

    // Helper to extract values from peer string representation
    private static String extractValue(String peerString, String prefix) {
        int startIndex = peerString.indexOf(prefix) + prefix.length();
        int endIndex = peerString.indexOf(",", startIndex);
        if (endIndex == -1) {
            endIndex = peerString.indexOf("}", startIndex);
        }
        return peerString.substring(startIndex, endIndex).replace("'", "");
    }

    // Verify that the file discovery results are as expected
    private static boolean verifyFileDiscovery(List<PeerInfo> peers) {
        if (peers == null || peers.isEmpty()) {
            System.err.println("File discovery failed: No peers found");
            return false;
        }

        // Verify at least one peer is "peer1" as expected
        boolean foundPeer1 = peers.stream()
                .anyMatch(peer -> "peer1".equals(peer.getPeerId()));

        if (!foundPeer1) {
            System.err.println("File discovery verification failed: peer1 not found");
            return false;
        }

        System.out.println("File discovery verification successful");
        return true;
    }

    private static void downloadFileFromPeer(String fileName, PeerInfo peer) {
        System.out.println("Attempting to download " + fileName + " from " + peer.getPeerId());

        try (Socket socket = new Socket(peer.getAddress(), peer.getPort());
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Send download request
            out.println("GET_FILE " + fileName);

            // Create directory for downloaded files
            Path downloadDir = Paths.get(TEST_FILES_DIR, "downloads");
            Files.createDirectories(downloadDir);

            // Create file to save the download
            Path downloadedFile = downloadDir.resolve(fileName);

            // Read file content (in a real implementation, use binary transfer)
            try (BufferedOutputStream fileOut = new BufferedOutputStream(
                    Files.newOutputStream(downloadedFile, StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING))) {

                // Start file transfer
                String line;
                while ((line = in.readLine()) != null && !line.equals("END_OF_FILE")) {
                    fileOut.write((line + "\n").getBytes());
                }
            }

            // Verify download
            if (Files.exists(downloadedFile) && Files.size(downloadedFile) > 0) {
                System.out.println("File downloaded successfully to " + downloadedFile);
            } else {
                throw new IOException("Downloaded file is empty or doesn't exist");
            }

        } catch (IOException e) {
            System.err.println("Error downloading file: " + e.getMessage());
            testFailures.add("Failed to download file: " + e.getMessage());
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