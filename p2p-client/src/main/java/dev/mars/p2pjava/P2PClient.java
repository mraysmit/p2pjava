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
import dev.mars.p2pjava.config.ConfigurationManager;
import dev.mars.p2pjava.util.ChecksumUtil;
import dev.mars.p2pjava.util.HealthCheck;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * P2PClient - Client for the P2P file sharing system
 *
 * This class provides methods for interacting with the P2P file sharing system,
 * including discovering peers with specific files and downloading files from peers.
 * 
 * This implementation uses the modular architecture of the P2P system, leveraging
 * components from various modules like config, common-api, and util.
 */
public class P2PClient {
    private final ConfigurationManager config;
    private final String indexServerHost;
    private final int indexServerPort;
    private final HealthCheck.ServiceHealth health;

    // Test results tracking
    private static final List<String> testFailures = new ArrayList<>();

    /**
     * Creates a new P2PClient with default configuration.
     */
    public P2PClient() {
        this(ConfigurationManager.getInstance());
    }

    /**
     * Creates a new P2PClient with the specified configuration.
     * 
     * @param config The configuration manager
     */
    public P2PClient(ConfigurationManager config) {
        this.config = config;

        // Get configuration values
        this.indexServerHost = config.get("indexserver.host", "localhost");
        this.indexServerPort = config.getInt("indexserver.port", 6001);

        // Register with health check
        this.health = HealthCheck.registerService("P2PClient");
        this.health.addHealthDetail("startTime", System.currentTimeMillis());
        this.health.addHealthDetail("indexServerHost", indexServerHost);
        this.health.addHealthDetail("indexServerPort", indexServerPort);
    }

    /**
     * Creates a new P2PClient with the specified configuration.
     * This constructor is maintained for backward compatibility.
     * 
     * @param trackerHost The tracker host
     * @param trackerPort The tracker port
     * @param indexServerHost The index server host
     * @param indexServerPort The index server port
     */
    public P2PClient(String trackerHost, int trackerPort, String indexServerHost, int indexServerPort) {
        // Create configuration manager
        this.config = ConfigurationManager.getInstance();

        // Set configuration values
        this.config.set("tracker.host", trackerHost);
        this.config.set("tracker.port", String.valueOf(trackerPort));
        this.config.set("indexserver.host", indexServerHost);
        this.config.set("indexserver.port", String.valueOf(indexServerPort));

        // Store values
        this.indexServerHost = indexServerHost;
        this.indexServerPort = indexServerPort;

        // Register with health check
        this.health = HealthCheck.registerService("P2PClient");
        this.health.addHealthDetail("startTime", System.currentTimeMillis());
        this.health.addHealthDetail("trackerHost", trackerHost);
        this.health.addHealthDetail("trackerPort", trackerPort);
        this.health.addHealthDetail("indexServerHost", indexServerHost);
        this.health.addHealthDetail("indexServerPort", indexServerPort);
    }

    /**
     * Discovers peers that have the specified file.
     * 
     * @param fileName The name of the file to search for
     * @return A list of peers that have the file
     */
    public List<PeerInfo> discoverPeersWithFile(String fileName) {
        List<PeerInfo> result = new ArrayList<>();

        // Update health status
        health.addHealthDetail("lastOperation", "discoverPeersWithFile");
        health.addHealthDetail("fileName", fileName);

        try (Socket socket = new Socket(indexServerHost, indexServerPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Set socket timeout from configuration
            socket.setSoTimeout(config.getInt("client.socket.timeout.ms", 5000));

            System.out.println("Sending query for file: " + fileName);
            out.println("GET_PEERS_WITH_FILE " + fileName);
            String response = in.readLine();
            System.out.println("Raw response from IndexServer: '" + response + "'");

            if (response == null || response.isEmpty() || "[]".equals(response)) {
                System.out.println("No peers found with file: " + fileName);
                return result;
            }

            // Parse the list format [PeerInfo{...}, PeerInfo{...}]
            if (response.startsWith("[") && response.endsWith("]")) {
                response = response.substring(1, response.length() - 1).trim();

                // If after removing brackets the string is empty, return empty list
                if (response.isEmpty()) {
                    return result;
                }

                // Find all PeerInfo objects - each starts with "PeerInfo" and ends with "}"
                int startIdx = 0;
                while (startIdx < response.length()) {
                    int endIdx = response.indexOf("}", startIdx);
                    if (endIdx == -1) break;

                    // Extract one PeerInfo string
                    String peerInfoStr = response.substring(startIdx, endIdx + 1);
                    startIdx = endIdx + 2; // Skip past this PeerInfo and the comma/space

                    // Parse the PeerInfo object
                    try {
                        // Extract fields using regex patterns
                        String peerId = extractField(peerInfoStr, "peerId=(.*?)[,}]");
                        String address = extractField(peerInfoStr, "address=(.*?)[,}]");
                        String portStr = extractField(peerInfoStr, "port=(\\d+)[,}]");

                        if (peerId != null && address != null && portStr != null) {
                            int port = Integer.parseInt(portStr);
                            result.add(new PeerInfo(peerId, address, port));
                            System.out.println("Added peer: " + peerId + " at " + address + ":" + port);
                        }
                    } catch (Exception e) {
                        recordFailure("Failed to parse peer info: " + peerInfoStr, e);
                    }
                }
            } else {
                recordFailure("Unexpected response format: " + response, null);
            }

            // Update health status with result
            health.addHealthDetail("peersFound", result.size());

        } catch (IOException e) {
            recordFailure("Error discovering peers", e);
            health.addHealthDetail("error", e.getMessage());
        }

        System.out.println("Discovered " + result.size() + " peers with file: " + fileName);
        return result;
    }

    /**
     * Registers a file with the index server.
     * 
     * @param fileName The name of the file to register
     * @param peerId The ID of the peer that has the file
     * @param port The port number the peer is listening on
     * @return true if the file was registered successfully, false otherwise
     */
    public boolean registerFileWithIndexServer(String fileName, String peerId, int port) {
        // Update health status
        health.addHealthDetail("lastOperation", "registerFileWithIndexServer");
        health.addHealthDetail("fileName", fileName);
        health.addHealthDetail("peerId", peerId);
        health.addHealthDetail("port", port);

        try (Socket socket = new Socket(indexServerHost, indexServerPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Set socket timeout from configuration
            socket.setSoTimeout(config.getInt("client.socket.timeout.ms", 5000));

            out.println("REGISTER_FILE " + fileName + " " + peerId + " " + port);
            String response = in.readLine();
            System.out.println("IndexServer response: " + response);

            boolean success = response != null && response.contains("SUCCESS");
            health.addHealthDetail("registrationSuccess", success);
            return success;
        } catch (IOException e) {
            recordFailure("Failed to register file with index server", e);
            health.addHealthDetail("error", e.getMessage());
            return false;
        }
    }

    /**
     * Downloads a file from a peer.
     * 
     * @param fileName The name of the file to download
     * @param downloadDir The directory to download the file to
     * @param peer The peer to download the file from
     * @return An empty string if successful, or an error message if the download failed
     */
    public String downloadFileFromPeer(String fileName, String downloadDir, PeerInfo peer) {
        System.out.println("Attempting to download " + fileName + " from peer: " + peer.getPeerId());

        // Update health status
        health.addHealthDetail("lastOperation", "downloadFileFromPeer");
        health.addHealthDetail("fileName", fileName);
        health.addHealthDetail("downloadDir", downloadDir);
        health.addHealthDetail("peer", peer.toString());

        // Get configuration values
        final int MAX_RETRIES = config.getInt("client.download.max.retries", 3);
        final int CONNECTION_TIMEOUT = config.getInt("client.connection.timeout.ms", 3000);
        final int READ_TIMEOUT = config.getInt("client.socket.timeout.ms", 10000);
        final int BUFFER_SIZE = config.getInt("client.download.buffer.size", 8192);
        final int RETRY_DELAY = config.getInt("client.download.retry.delay.ms", 2000);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try (Socket socket = new Socket()) {
                // Add connection details for debugging
                System.out.println("Connecting to " + peer.getAddress() + ":" + peer.getPort() +
                        " (attempt " + attempt + ")");

                // Update health status with attempt information
                health.addHealthDetail("downloadAttempt", attempt);

                // Set connection timeout
                socket.connect(new InetSocketAddress(peer.getAddress(), peer.getPort()), CONNECTION_TIMEOUT);
                socket.setSoTimeout(READ_TIMEOUT); // Read timeout

                // Use DataOutputStream for more reliable binary communication
                try (DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                     DataInputStream in = new DataInputStream(socket.getInputStream())) {

                    // Send protocol version and command
                    String protocolVersion = config.get("client.protocol.version", "PROTOCOL_V1");
                    out.writeUTF(protocolVersion);
                    out.writeUTF("GET_FILE");
                    out.writeUTF(fileName);
                    out.flush();

                    // Check response code
                    String response = in.readUTF();
                    System.out.println("Peer " + peer.getPeerId() + " response: " + response);

                    if ("FILE_NOT_FOUND".equals(response)) {
                        String errorMsg = "Peer does not have the file";
                        health.addHealthDetail("error", errorMsg);
                        throw new IOException(errorMsg);
                    } else if (!"SENDING_FILE".equals(response)) {
                        String errorMsg = "Unexpected response: " + response;
                        health.addHealthDetail("error", errorMsg);
                        throw new IOException(errorMsg);
                    }

                    // Get file size
                    long fileSize = in.readLong();
                    System.out.println("Expected file size: " + fileSize + " bytes");
                    health.addHealthDetail("fileSize", fileSize);

                    // Get file checksum
                    String expectedChecksum = in.readUTF();
                    System.out.println("Expected checksum: " + expectedChecksum);
                    health.addHealthDetail("expectedChecksum", expectedChecksum);

                    // Create download directory
                    Path downloadDirPath = Paths.get(downloadDir, "downloads");
                    Files.createDirectories(downloadDirPath);
                    Path downloadedFile = downloadDirPath.resolve(fileName);

                    // Download file content
                    try (FileOutputStream fileOut = new FileOutputStream(downloadedFile.toFile())) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int bytesRead;
                        long totalRead = 0;

                        while (totalRead < fileSize &&
                                (bytesRead = in.read(buffer, 0, (int)Math.min(buffer.length, fileSize - totalRead))) != -1) {
                            fileOut.write(buffer, 0, bytesRead);
                            totalRead += bytesRead;
                            System.out.print("\rDownloaded: " + totalRead + "/" + fileSize + " bytes");

                            // Update health status periodically (every 10%)
                            if (totalRead % (fileSize / 10) < BUFFER_SIZE) {
                                health.addHealthDetail("downloadProgress", 
                                    String.format("%.1f%%", (double)totalRead / fileSize * 100));
                            }
                        }
                        System.out.println();
                    }

                    System.out.println("File downloaded successfully: " + downloadedFile);

                    // Verify file integrity
                    boolean checksumValid = ChecksumUtil.verifyChecksum(downloadedFile.toString(), expectedChecksum);
                    health.addHealthDetail("checksumValid", checksumValid);

                    if (checksumValid) {
                        System.out.println("File integrity verified: Checksum matches");
                        health.addHealthDetail("downloadStatus", "SUCCESS");
                        return ""; // Success!
                    } else {
                        String errorMsg = "File integrity check failed: Checksum mismatch";
                        System.err.println(errorMsg);
                        health.addHealthDetail("downloadStatus", "CHECKSUM_FAILURE");
                        return errorMsg;
                    }
                }
            } catch (Exception e) {
                System.err.println("Download attempt " + attempt + " failed: " + e.getClass().getName() + ": " + e.getMessage());
                e.printStackTrace();

                health.addHealthDetail("error", e.getMessage());

                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    String errorMsg = "Failed to download " + fileName + " after " + MAX_RETRIES + " attempts: " + e.getMessage();
                    health.addHealthDetail("downloadStatus", "FAILED");
                    return errorMsg;
                }
            }
        }

        String errorMsg = "Failed to download " + fileName + ": Max retries exceeded";
        health.addHealthDetail("downloadStatus", "MAX_RETRIES_EXCEEDED");
        return errorMsg;
    }


    /**
     * Extracts a field from a string using a regular expression pattern.
     * 
     * @param input The input string
     * @param pattern The regular expression pattern
     * @return The extracted field, or null if not found
     */
    private String extractField(String input, String pattern) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(input);
        if (m.find() && m.groupCount() >= 1) {
            return m.group(1);
        }
        return null;
    }

    /**
     * Records a test failure.
     * 
     * @param message The failure message
     * @param e The exception that caused the failure, or null if none
     */
    private void recordFailure(String message, Exception e) {
        String fullMessage = message;
        if (e != null) {
            fullMessage += ": " + e.getMessage();
        }
        testFailures.add(fullMessage);
        System.err.println(fullMessage);

        // Update health status
        health.addHealthDetail("lastError", fullMessage);
        health.addHealthDetail("errorTime", System.currentTimeMillis());
    }

    /**
     * Verifies that a peer is listening on its port.
     * 
     * @param peer The peer to verify
     * @return true if the peer is listening, false otherwise
     */
    public boolean verifyPeerIsListening(PeerInfo peer) {
        // Update health status
        health.addHealthDetail("lastOperation", "verifyPeerIsListening");
        health.addHealthDetail("peer", peer.toString());

        try {
            // Test if the socket is accepting connections
            try (Socket socket = new Socket()) {
                int timeout = config.getInt("client.connection.timeout.ms", 1000);
                socket.connect(new InetSocketAddress(peer.getAddress(), peer.getPort()), timeout);
                System.out.println("Peer " + peer.getPeerId() + " is listening on port " + peer.getPort());
                health.addHealthDetail("peerListening", true);
                return true;
            }
        } catch (IOException e) {
            System.err.println("Peer " + peer.getPeerId() + " is not listening: " + e.getMessage());
            health.addHealthDetail("peerListening", false);
            health.addHealthDetail("error", e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a peer is running by querying the tracker.
     * 
     * @param peerId The ID of the peer to check
     * @return true if the peer is running, false otherwise
     */
    public boolean isPeerRunning(String peerId) {
        // Update health status
        health.addHealthDetail("lastOperation", "isPeerRunning");
        health.addHealthDetail("peerId", peerId);

        String trackerHost = config.get("tracker.host", "localhost");
        int trackerPort = config.getInt("tracker.port", 6000);

        try (Socket socket = new Socket(trackerHost, trackerPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Set socket timeout from configuration
            socket.setSoTimeout(config.getInt("client.socket.timeout.ms", 5000));

            out.println("IS_PEER_ALIVE " + peerId);
            String response = in.readLine();

            boolean isAlive = "ALIVE".equals(response);
            health.addHealthDetail("peerRunning", isAlive);
            return isAlive;
        } catch (IOException e) {
            recordFailure("Error checking peer status", e);
            health.addHealthDetail("peerRunning", false);
            return false;
        }
    }

    /**
     * Cleans up resources when the client is no longer needed.
     */
    public void shutdown() {
        // Deregister from health check
        HealthCheck.deregisterService("P2PClient");
        System.out.println("P2PClient shutdown complete");
    }
}
