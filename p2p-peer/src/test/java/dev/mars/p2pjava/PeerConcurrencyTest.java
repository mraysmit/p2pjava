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
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class PeerConcurrencyTest {

    private static final String PEER_ID = "testPeer";
    private static final String PEER_HOST = "localhost";
    private static final int PEER_PORT = 9002;
    private static final int NUM_CONCURRENT_CLIENTS = 10;

    private static ExecutorService executorService;
    private static Peer peer;


    @TempDir
    static Path tempDir;

    @BeforeAll
    public static void setupAll() throws IOException, InterruptedException {
        executorService = Executors.newFixedThreadPool(NUM_CONCURRENT_CLIENTS + 2);

        // Create test file
        Path testFile = tempDir.resolve("concurrent_test.txt");
        Files.writeString(testFile, "This is a test file for concurrent access");

        // Start peer with test file
        peer = new Peer(PEER_ID, PEER_HOST, PEER_PORT, "localhost", 8002);
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

        executorService.shutdownNow();
        executorService.awaitTermination(2, TimeUnit.SECONDS);
    }

    @Test
    public void testConcurrentFileRequests() throws InterruptedException {
        // Create a list to hold the futures
        List<Future<Boolean>> futures = new ArrayList<>();

        // Submit concurrent file requests
        for (int i = 0; i < NUM_CONCURRENT_CLIENTS; i++) {
            futures.add(executorService.submit(new FileRequestTask("concurrent_test.txt")));
        }

        // Check results
        int successCount = 0;
        for (Future<Boolean> future : futures) {
            try {
                if (future.get(5, TimeUnit.SECONDS)) {
                    successCount++;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        assertEquals(NUM_CONCURRENT_CLIENTS, successCount,
                "All concurrent file requests should succeed");
    }

    // Task for requesting a file from the peer
    private static class FileRequestTask implements Callable<Boolean> {
        private final String fileName;

        public FileRequestTask(String fileName) {
            this.fileName = fileName;
        }

        @Override
        public Boolean call() {
            try (Socket socket = new Socket(PEER_HOST, PEER_PORT);
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                 DataInputStream in = new DataInputStream(socket.getInputStream())) {

                // Request file
                out.writeUTF("PROTOCOL_V1");
                out.writeUTF("GET_FILE");
                out.writeUTF(fileName);
                out.flush();

                // Read response
                String response = in.readUTF();
                if (!"SENDING_FILE".equals(response)) {
                    return false;
                }

                // Read file size
                long fileSize = in.readLong();

                // Read checksum (added to match PeerHandler implementation)
                String checksum = in.readUTF();
                if (checksum == null) {
                    return false;
                }

                // Read file content
                byte[] buffer = new byte[(int)fileSize];
                in.readFully(buffer);

                // Verify some content
                String content = new String(buffer);
                return content.contains("test file");
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
    }
}
