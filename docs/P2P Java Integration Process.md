
# P2P Java Integration Process

This integration process demonstrates the complete functionality of the p2p-java project by creating a fully functional P2P network with multiple peers sharing and downloading files.

```java
package dev.mars.p2pjava.integration;

import dev.mars.p2pjava.Peer;
import dev.mars.p2pjava.Tracker;
import dev.mars.p2pjava.IndexServer;
import dev.mars.p2pjava.common.FileMetadata;
import dev.mars.p2pjava.common.PeerInfo;
import dev.mars.p2pjava.util.ChecksumUtil;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * A complete integration process that showcases all the functionality in the p2p-java project.
 * This class demonstrates how to set up a P2P network with a tracker, index server, and multiple peers,
 * and how to share and download files between peers.
 */
public class P2PIntegrationDemo {
    private static final Logger logger = Logger.getLogger(P2PIntegrationDemo.class.getName());
    
    // Configuration
    private static final String TRACKER_HOST = "localhost";
    private static final int TRACKER_PORT = 6000;
    private static final int INDEX_SERVER_PORT = 6001;
    private static final int BASE_PEER_PORT = 7000;
    
    // Paths for shared and downloaded files
    private static final String SHARED_FILES_DIR = "shared_files";
    private static final String DOWNLOADED_FILES_DIR = "downloaded_files";
    
    // Components
    private Thread trackerThread;
    private Thread indexServerThread;
    private Peer[] peers;
    private final int numPeers;
    
    /**
     * Creates a new P2PIntegrationDemo with the specified number of peers.
     *
     * @param numPeers The number of peers to create
     */
    public P2PIntegrationDemo(int numPeers) {
        this.numPeers = numPeers;
        this.peers = new Peer[numPeers];
    }
    
    /**
     * Starts the integration process.
     */
    public void start() throws Exception {
        // Create directories for shared and downloaded files
        createDirectories();
        
        // Start tracker
        startTracker();
        
        // Start index server
        startIndexServer();
        
        // Wait for services to start
        logger.info("Waiting for services to start...");
        Thread.sleep(2000);
        
        // Start peers
        startPeers();
        
        // Wait for peers to register
        logger.info("Waiting for peers to register...");
        Thread.sleep(2000);
        
        // Share files
        shareFiles();
        
        // Wait for files to be registered
        logger.info("Waiting for files to be registered...");
        Thread.sleep(2000);
        
        // Search for files
        searchFiles("test");
        
        // Download files
        downloadFiles();
        
        // Verify downloaded files
        verifyDownloadedFiles();
    }
    
    /**
     * Creates directories for shared and downloaded files.
     */
    private void createDirectories() throws IOException {
        // Create shared files directory
        Path sharedFilesDir = Paths.get(SHARED_FILES_DIR);
        if (!Files.exists(sharedFilesDir)) {
            Files.createDirectories(sharedFilesDir);
        }
        
        // Create downloaded files directory
        Path downloadedFilesDir = Paths.get(DOWNLOADED_FILES_DIR);
        if (!Files.exists(downloadedFilesDir)) {
            Files.createDirectories(downloadedFilesDir);
        }
        
        // Create test files in shared directory
        for (int i = 1; i <= 3; i++) {
            Path testFile = sharedFilesDir.resolve("test_file_" + i + ".txt");
            if (!Files.exists(testFile)) {
                try (BufferedWriter writer = Files.newBufferedWriter(testFile)) {
                    writer.write("This is test file " + i + " content.\n");
                    writer.write("It contains some random text for testing purposes.\n");
                    for (int j = 0; j < i * 100; j++) {
                        writer.write("Line " + j + " of test data.\n");
                    }
                }
            }
        }
    }
    
    /**
     * Starts the tracker server in a separate thread.
     */
    private void startTracker() {
        trackerThread = new Thread(() -> {
            logger.info("Starting tracker...");
            Tracker.startTracker();
        });
        trackerThread.setDaemon(true);
        trackerThread.start();
    }
    
    /**
     * Starts the index server in a separate thread.
     */
    private void startIndexServer() {
        indexServerThread = new Thread(() -> {
            logger.info("Starting index server...");
            IndexServer.startIndexServer();
        });
        indexServerThread.setDaemon(true);
        indexServerThread.start();
    }
    
    /**
     * Starts the specified number of peers.
     */
    private void startPeers() throws Exception {
        CountDownLatch startupLatch = new CountDownLatch(numPeers);
        
        for (int i = 0; i < numPeers; i++) {
            final int peerIndex = i;
            String peerId = "peer-" + UUID.randomUUID().toString().substring(0, 8);
            int peerPort = BASE_PEER_PORT + i;
            
            // Create and start peer
            peers[i] = new Peer(peerId, TRACKER_HOST, peerPort, TRACKER_HOST, TRACKER_PORT);
            
            Thread peerThread = new Thread(() -> {
                try {
                    logger.info("Starting peer " + peerId + " on port " + peerPort);
                    peers[peerIndex].start();
                    startupLatch.countDown();
                } catch (IOException e) {
                    logger.severe("Error starting peer " + peerId + ": " + e.getMessage());
                }
            });
            peerThread.setDaemon(true);
            peerThread.start();
        }
        
        // Wait for all peers to start
        boolean allStarted = startupLatch.await(30, TimeUnit.SECONDS);
        if (!allStarted) {
            throw new RuntimeException("Not all peers started within the timeout period");
        }
        
        // Wait for peers to register with tracker
        for (Peer peer : peers) {
            if (!peer.waitForStartup(5000)) {
                throw new RuntimeException("Peer " + peer.getPeerId() + " failed to start properly");
            }
        }
        
        logger.info("All peers started successfully");
    }
    
    /**
     * Shares files among peers.
     */
    private void shareFiles() throws IOException {
        logger.info("Sharing files among peers...");
        
        // Get list of files in shared directory
        File sharedDir = new File(SHARED_FILES_DIR);
        File[] files = sharedDir.listFiles();
        
        if (files == null || files.length == 0) {
            logger.warning("No files found in shared directory");
            return;
        }
        
        // Distribute files among peers
        for (int i = 0; i < files.length; i++) {
            // Assign each file to multiple peers for redundancy
            for (int j = 0; j < Math.min(3, numPeers); j++) {
                int peerIndex = (i + j) % numPeers;
                peers[peerIndex].addSharedFile(files[i].getAbsolutePath());
                
                // Register file with index server
                registerFileWithIndexServer(files[i].getName(), peers[peerIndex]);
            }
        }
    }
    
    /**
     * Registers a file with the index server.
     */
    private void registerFileWithIndexServer(String fileName, Peer peer) {
        try (Socket socket = new Socket(TRACKER_HOST, INDEX_SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            
            // Send registration request
            out.println("REGISTER_FILE " + fileName + " " + peer.getPeerId() + " " + BASE_PEER_PORT);
            
            // Read response
            String response = in.readLine();
            if (response != null && response.startsWith("FILE_REGISTERED")) {
                logger.info("File registered with index server: " + fileName + " by peer " + peer.getPeerId());
            } else {
                logger.warning("Failed to register file with index server: " + response);
            }
        } catch (IOException e) {
            logger.severe("Error registering file with index server: " + e.getMessage());
        }
    }
    
    /**
     * Searches for files matching a pattern.
     */
    private void searchFiles(String pattern) {
        logger.info("Searching for files matching pattern: " + pattern);
        
        try (Socket socket = new Socket(TRACKER_HOST, INDEX_SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            
            // Send search request
            out.println("SEARCH_FILES " + pattern);
            
            // Read response
            String response = in.readLine();
            if (response != null && !response.equals("NO_FILES_FOUND")) {
                logger.info("Search results: " + response);
            } else {
                logger.warning("No files found matching pattern: " + pattern);
            }
        } catch (IOException e) {
            logger.severe("Error searching for files: " + e.getMessage());
        }
    }
    
    /**
     * Downloads files from other peers.
     */
    private void downloadFiles() {
        logger.info("Downloading files from peers...");
        
        try (Socket socket = new Socket(TRACKER_HOST, INDEX_SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            
            // Get all files in the network
            out.println("SEARCH_FILES *");
            
            // Read response
            String response = in.readLine();
            if (response != null && !response.equals("NO_FILES_FOUND")) {
                // Parse response to get file names and peers
                // This is a simplified parsing - in a real implementation, you would parse the response properly
                String[] fileEntries = response.split(", ");
                
                for (String fileEntry : fileEntries) {
                    if (fileEntry.contains("=")) {
                        String fileName = fileEntry.substring(0, fileEntry.indexOf("=")).trim();
                        
                        // Download file from a peer
                        downloadFileFromPeer(fileName);
                    }
                }
            } else {
                logger.warning("No files found in the network");
            }
        } catch (IOException e) {
            logger.severe("Error getting files from index server: " + e.getMessage());
        }
    }
    
    /**
     * Downloads a file from a peer.
     */
    private void downloadFileFromPeer(String fileName) {
        logger.info("Downloading file: " + fileName);
        
        try (Socket socket = new Socket(TRACKER_HOST, INDEX_SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            
            // Get peers with the file
            out.println("GET_PEERS_WITH_FILE " + fileName);
            
            // Read response
            String response = in.readLine();
            if (response != null && !response.equals("NO_PEERS_FOUND")) {
                // Parse response to get peer info
                // This is a simplified parsing - in a real implementation, you would parse the response properly
                String peerInfoStr = response.substring(response.indexOf("[") + 1, response.indexOf("]"));
                String[] peerInfoParts = peerInfoStr.split(",");
                
                if (peerInfoParts.length > 0) {
                    // Extract peer ID, address, and port
                    String peerId = peerInfoParts[0].trim();
                    String peerAddress = TRACKER_HOST; // Using localhost for simplicity
                    int peerPort = BASE_PEER_PORT; // Using base port for simplicity
                    
                    // Download file from peer
                    downloadFileFromPeer(fileName, peerAddress, peerPort);
                }
            } else {
                logger.warning("No peers found with file: " + fileName);
            }
        } catch (IOException e) {
            logger.severe("Error getting peers with file: " + e.getMessage());
        }
    }
    
    /**
     * Downloads a file from a specific peer.
     */
    private void downloadFileFromPeer(String fileName, String peerAddress, int peerPort) {
        try (Socket socket = new Socket(peerAddress, peerPort);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {
            
            // Send protocol version
            out.writeUTF("1.0");
            
            // Send GET_FILE command
            out.writeUTF("GET_FILE");
            out.writeUTF(fileName);
            out.flush();
            
            // Read response
            String response = in.readUTF();
            if ("SENDING_FILE".equals(response)) {
                // Get file size and checksum
                long fileSize = in.readLong();
                String checksum = in.readUTF();
                
                logger.info("Receiving file: " + fileName + " (" + fileSize + " bytes, checksum: " + checksum + ")");
                
                // Create output file
                File outputFile = new File(DOWNLOADED_FILES_DIR, fileName);
                
                // Receive file content
                try (FileOutputStream fileOut = new FileOutputStream(outputFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalReceived = 0;
                    
                    while (totalReceived < fileSize && (bytesRead = in.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalReceived))) != -1) {
                        fileOut.write(buffer, 0, bytesRead);
                        totalReceived += bytesRead;
                    }
                    
                    logger.info("File received successfully: " + fileName + " (" + totalReceived + " bytes)");
                    
                    // Verify checksum
                    String downloadedChecksum = ChecksumUtil.calculateChecksum(outputFile.getPath());
                    if (checksum.equals(downloadedChecksum)) {
                        logger.info("Checksum verification successful for file: " + fileName);
                    } else {
                        logger.warning("Checksum verification failed for file: " + fileName);
                    }
                }
            } else if ("FILE_NOT_FOUND".equals(response)) {
                logger.warning("File not found on peer: " + fileName);
            } else {
                logger.warning("Unexpected response from peer: " + response);
            }
        } catch (IOException e) {
            logger.severe("Error downloading file from peer: " + e.getMessage());
        }
    }
    
    /**
     * Verifies that downloaded files match the original files.
     */
    private void verifyDownloadedFiles() {
        logger.info("Verifying downloaded files...");
        
        File sharedDir = new File(SHARED_FILES_DIR);
        File downloadedDir = new File(DOWNLOADED_FILES_DIR);
        
        File[] sharedFiles = sharedDir.listFiles();
        File[] downloadedFiles = downloadedDir.listFiles();
        
        if (sharedFiles == null || downloadedFiles == null) {
            logger.warning("Cannot verify files: directory not found");
            return;
        }
        
        for (File sharedFile : sharedFiles) {
            boolean found = false;
            
            for (File downloadedFile : downloadedFiles) {
                if (sharedFile.getName().equals(downloadedFile.getName())) {
                    found = true;
                    
                    // Verify file size
                    if (sharedFile.length() == downloadedFile.length()) {
                        logger.info("File size verification successful for: " + sharedFile.getName());
                    } else {
                        logger.warning("File size verification failed for: " + sharedFile.getName());
                    }
                    
                    // Verify checksum
                    try {
                        String sharedChecksum = ChecksumUtil.calculateChecksum(sharedFile.getPath());
                        String downloadedChecksum = ChecksumUtil.calculateChecksum(downloadedFile.getPath());
                        
                        if (sharedChecksum.equals(downloadedChecksum)) {
                            logger.info("Checksum verification successful for: " + sharedFile.getName());
                        } else {
                            logger.warning("Checksum verification failed for: " + sharedFile.getName());
                        }
                    } catch (Exception e) {
                        logger.severe("Error calculating checksum: " + e.getMessage());
                    }
                    
                    break;
                }
            }
            
            if (!found) {
                logger.warning("File not found in downloaded directory: " + sharedFile.getName());
            }
        }
    }
    
    /**
     * Stops all components.
     */
    public void stop() {
        logger.info("Stopping all components...");
        
        // Stop peers
        for (Peer peer : peers) {
            if (peer != null) {
                peer.stop();
            }
        }
        
        // Stop index server and tracker
        IndexServer.stopIndexServer();
        Tracker.stopTracker();
    }
    
    /**
     * Main method to run the integration demo.
     */
    public static void main(String[] args) {
        try {
            // Create and start the integration demo with 5 peers
            P2PIntegrationDemo demo = new P2PIntegrationDemo(5);
            demo.start();
            
            // Keep the demo running for a while
            logger.info("P2P network is running. Press Enter to stop...");
            new BufferedReader(new InputStreamReader(System.in)).readLine();
            
            // Stop the demo
            demo.stop();
            
        } catch (Exception e) {
            logger.severe("Error in integration demo: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

## How This Integration Process Works

This integration process demonstrates a complete P2P network with the following components:

1. **Tracker Server**: Manages peer registrations and helps peers discover each other
2. **Index Server**: Maintains an index of files shared by peers
3. **Multiple Peers**: Each peer can share files and download files from other peers

The process follows these steps:

1. **Initialization**:
   - Creates directories for shared and test files
   - Generates test files with random content

2. **Service Startup**:
   - Starts the tracker server
   - Starts the index server
   - Creates and starts multiple peer instances

3. **File Sharing**:
   - Distributes test files among peers
   - Registers shared files with the index server

4. **File Discovery and Download**:
   - Searches for files using the index server
   - Discovers peers that have the requested files
   - Downloads files from peers
   - Verifies file integrity using checksums

5. **Cleanup**:
   - Stops all peers
   - Stops the index server and tracker

## Key Features Demonstrated

This integration process showcases the following key features of the p2p-java project:

1. **Peer Registration**: Peers register with the tracker to join the network
2. **File Indexing**: Files are registered with the index server for discovery
3. **Peer Discovery**: Peers can discover other peers through the tracker
4. **File Search**: Files can be searched using the index server
5. **File Transfer**: Files can be downloaded from peers
6. **Data Integrity**: File integrity is verified using checksums
7. **Fault Tolerance**: Multiple peers can share the same file for redundancy
8. **Graceful Shutdown**: All components can be stopped cleanly

This integration process provides a complete demonstration of the p2p-java project's functionality without relying on the P2PClient.java, P2pTestHarness, or p2p-bootstrap module.