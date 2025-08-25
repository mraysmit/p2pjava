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


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TrackerIntegrationTest {
    private static ExecutorService trackerExecutor;
    private static final int TRACKER_PORT = 6000;

    @BeforeAll
    static void startTracker() {
        trackerExecutor = Executors.newSingleThreadExecutor();
        trackerExecutor.submit(() -> Tracker.startTracker());

        // Wait for tracker to start
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @AfterAll
    static void stopTracker() {
        trackerExecutor.shutdownNow();
        try {
            trackerExecutor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void testRegisterAndDiscoverPeers() throws IOException {
        // Register a peer
        try (Socket socket = new Socket("localhost", TRACKER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("REGISTER test-peer 8888");
            String response = in.readLine();
            assertEquals("REGISTERED test-peer", response);
        }

        // Discover peers
        try (Socket socket = new Socket("localhost", TRACKER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("DISCOVER");
            String response = in.readLine();

            assertTrue(response.startsWith("PEERS ["));
            assertTrue(response.contains("test-peer"));
        }
    }

    @Test
    void testConcurrentRegistrations() throws Exception {
        ExecutorService clientPool = Executors.newFixedThreadPool(5);

        for (int i = 0; i < 5; i++) {
            final int peerId = i;
            clientPool.submit(() -> {
                try (Socket socket = new Socket("localhost", TRACKER_PORT);
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                     BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                    out.println("REGISTER concurrent-peer" + peerId + " " + (9000 + peerId));
                    String response = in.readLine();
                    assertEquals("REGISTERED concurrent-peer" + peerId, response);
                } catch (IOException e) {
                    fail("Exception in client thread: " + e.getMessage());
                }
            });
        }

        clientPool.shutdown();
        clientPool.awaitTermination(2, TimeUnit.SECONDS);

        // Verify all peers were registered
        try (Socket socket = new Socket("localhost", TRACKER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("DISCOVER");
            String response = in.readLine();

            for (int i = 0; i < 5; i++) {
                assertTrue(response.contains("concurrent-peer" + i));
            }
        }
    }
}