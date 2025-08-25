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


import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class PeerConnectionHandlingTest {

    private static final String PEER_ID = "testPeer";
    private static final String PEER_HOST = "localhost";
    private static final int PEER_PORT = 9001;
    private static final String TRACKER_HOST = "localhost";
    private static final int TRACKER_PORT = 8001;

    private static ExecutorService executorService;
    private static Peer peer;
    private static MockTracker mockTracker;

    @TempDir
    static Path tempDir;

    @BeforeAll
    public static void setupAll() throws IOException, InterruptedException {
        executorService = Executors.newCachedThreadPool();

        // Start mock tracker
        mockTracker = new MockTracker(TRACKER_PORT);
        executorService.submit(mockTracker);

        // Create test files
        Path testFile = tempDir.resolve("testfile.txt");
        Files.writeString(testFile, "This is test file content");

        // Start peer with shared file
        peer = new Peer(PEER_ID, PEER_HOST, PEER_PORT, TRACKER_HOST, TRACKER_PORT);
        peer.addSharedFile(testFile.toString());

        // Start peer in background
        executorService.submit(() -> {
            try {
                peer.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Wait for peer to start
        TimeUnit.SECONDS.sleep(1);
    }

    @AfterAll
    public static void teardownAll() throws InterruptedException {
        if (peer != null) {
            peer.stop();
        }

        if (mockTracker != null) {
            mockTracker.stop();
        }

        executorService.shutdownNow();
        executorService.awaitTermination(2, TimeUnit.SECONDS);
    }

    @Test
    public void testFileDownload() throws IOException {
        // Connect to peer
        try (Socket socket = new Socket(PEER_HOST, PEER_PORT);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            // Request file
            out.writeUTF("PROTOCOL_V1");
            out.writeUTF("GET_FILE");
            out.writeUTF("testfile.txt");
            out.flush();

            // Get response
            String response = in.readUTF();
            assertEquals("SENDING_FILE", response, "Peer should send the requested file");

            // Read file size
            long fileSize = in.readLong();
            assertTrue(fileSize > 0, "File size should be positive");

            // Read checksum (added to match PeerHandler implementation)
            String checksum = in.readUTF();
            assertNotNull(checksum, "Checksum should not be null");

            // Read file content
            byte[] buffer = new byte[(int)fileSize];
            in.readFully(buffer);

            // Verify content
            String fileContent = new String(buffer);
            assertEquals("This is test file content", fileContent,
                    "File content should match what was created");
        }
    }

    @Test
    public void testNonexistentFileRequest() throws IOException {
        // Connect to peer
        try (Socket socket = new Socket(PEER_HOST, PEER_PORT);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            // Request nonexistent file
            out.writeUTF("PROTOCOL_V1");
            out.writeUTF("GET_FILE");
            out.writeUTF("nonexistent.txt");
            out.flush();

            // Get response
            String response = in.readUTF();
            assertEquals("FILE_NOT_FOUND", response,
                    "Peer should return FILE_NOT_FOUND for nonexistent files");
        }
    }

    @Test
    public void testInvalidCommand() throws IOException {
        // Connect to peer
        try (Socket socket = new Socket(PEER_HOST, PEER_PORT);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            // Send invalid command
            out.writeUTF("PROTOCOL_V1");
            out.writeUTF("INVALID_COMMAND");
            out.flush();

            // Get response
            String response = in.readUTF();
            assertEquals("UNKNOWN_COMMAND", response,
                    "Peer should return UNKNOWN_COMMAND for invalid commands");
        }
    }

    // Mock tracker for testing
    static class MockTracker implements Runnable {
        private final ServerSocket serverSocket;
        private volatile boolean running = true;

        public MockTracker(int port) throws IOException {
            this.serverSocket = new ServerSocket(port);
        }

        @Override
        public void run() {
            try {
                while (running && !serverSocket.isClosed()) {
                    try (Socket clientSocket = serverSocket.accept();
                         BufferedReader in = new BufferedReader(
                                 new InputStreamReader(clientSocket.getInputStream()));
                         PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

                        String request = in.readLine();
                        if (request.startsWith("REGISTER")) {
                            out.println("REGISTERED");
                        } else if (request.startsWith("HEARTBEAT")) {
                            out.println("HEARTBEAT_ACK");
                        } else if (request.startsWith("DISCOVER")) {
                            out.println("[]");
                        } else {
                            out.println("UNKNOWN");
                        }
                    } catch (IOException e) {
                        if (running) {
                            e.printStackTrace();
                        }
                    }
                }
            } finally {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void stop() {
            running = false;
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
