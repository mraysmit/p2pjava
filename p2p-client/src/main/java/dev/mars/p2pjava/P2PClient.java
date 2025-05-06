package dev.mars.p2pjava;

import dev.mars.p2pjava.util.ChecksumUtil;

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
 */
public class P2PClient {
    private final String indexServerHost;
    private final int indexServerPort;

    // Test results tracking
    private static final List<String> testFailures = new ArrayList<>();

    // Constructor with needed configuration
    public P2PClient(String trackerHost, int trackerPort, String indexServerHost, int indexServerPort) {
        // Connection properties
        this.indexServerHost = indexServerHost;
        this.indexServerPort = indexServerPort;
    }

    // Move these methods:

    // Discover peers that have the specified file
    List<PeerInfo> discoverPeersWithFile(String fileName) {
        List<PeerInfo> result = new ArrayList<>();
        try (Socket socket = new Socket("localhost", indexServerPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

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
        } catch (IOException e) {
            recordFailure("Error discovering peers", e);
        }

        System.out.println("Discovered " + result.size() + " peers with file: " + fileName);
        return result;
    }

    private void registerFileWithIndexServer(String fileName, String peerId, int port) {
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

    public String downloadFileFromPeer(String fileName, String testFilesDir, PeerInfo peer) {
        System.out.println("Attempting to download " + fileName + " from peer: " + peer.getPeerId());

        final int MAX_RETRIES = 3;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try (Socket socket = new Socket()) {
                // Add connection details for debugging
                System.out.println("Connecting to " + peer.getAddress() + ":" + peer.getPort() +
                        " (attempt " + attempt + ")");

                // Set connection timeout
                socket.connect(new InetSocketAddress(peer.getAddress(), peer.getPort()), 3000);
                socket.setSoTimeout(10000); // Read timeout

                // Use DataOutputStream for more reliable binary communication
                try (DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                     DataInputStream in = new DataInputStream(socket.getInputStream())) {

                    // Send protocol version and command
                    out.writeUTF("PROTOCOL_V1");
                    out.writeUTF("GET_FILE");
                    out.writeUTF(fileName);
                    out.flush();

                    // Check response code
                    String response = in.readUTF();
                    System.out.println("Peer " + peer.getPeerId() + " response: " + response);

                    if ("FILE_NOT_FOUND".equals(response)) {
                        throw new IOException("Peer does not have the file");
                    } else if (!"SENDING_FILE".equals(response)) {
                        throw new IOException("Unexpected response: " + response);
                    }

                    // Get file size
                    long fileSize = in.readLong();
                    System.out.println("Expected file size: " + fileSize + " bytes");

                    // Get file checksum
                    String expectedChecksum = in.readUTF();
                    System.out.println("Expected checksum: " + expectedChecksum);

                    // Create download directory
                    Path downloadDir = Paths.get(testFilesDir, "downloads");
                    Files.createDirectories(downloadDir);
                    Path downloadedFile = downloadDir.resolve(fileName);

                    // Download file content
                    try (FileOutputStream fileOut = new FileOutputStream(downloadedFile.toFile())) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        long totalRead = 0;

                        while (totalRead < fileSize &&
                                (bytesRead = in.read(buffer, 0, (int)Math.min(buffer.length, fileSize - totalRead))) != -1) {
                            fileOut.write(buffer, 0, bytesRead);
                            totalRead += bytesRead;
                            System.out.print("\rDownloaded: " + totalRead + "/" + fileSize + " bytes");
                        }
                        System.out.println();
                    }

                    System.out.println("File downloaded successfully: " + downloadedFile);

                    // Verify file integrity
                    boolean checksumValid = ChecksumUtil.verifyChecksum(downloadedFile.toString(), expectedChecksum);
                    if (checksumValid) {
                        System.out.println("File integrity verified: Checksum matches");
                        return ""; // Success!
                    } else {
                        System.err.println("File integrity check failed: Checksum mismatch");
                        return "File integrity check failed: Checksum mismatch";
                    }
                }
            } catch (Exception e) {
                System.err.println("Download attempt " + attempt + " failed: " + e.getClass().getName() + ": " + e.getMessage());
                e.printStackTrace();

                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    return "Failed to download " + fileName + " after " + MAX_RETRIES + " attempts: " + e.getMessage();
                }
            }
        }
        return "Failed to download " + fileName + ": Max retries exceeded";
    }


    private String extractField(String input, String pattern) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(input);
        if (m.find() && m.groupCount() >= 1) {
            return m.group(1);
        }
        return null;
    }

    private static void recordFailure(String message, Exception e) {
        String fullMessage = message;
        if (e != null) {
            fullMessage += ": " + e.getMessage();
        }
        testFailures.add(fullMessage);
        System.err.println(fullMessage);
    }


    //public boolean verifyPeerIsListening(PeerInfo peer) { ... }
    //public boolean isPeerRunning(String peerId) { ... }
    //public void registerFileWithIndexServer(String fileName, String peerId, int port) { ... }
}
