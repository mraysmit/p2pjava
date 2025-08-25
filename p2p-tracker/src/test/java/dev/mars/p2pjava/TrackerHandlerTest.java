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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class TrackerHandlerTest {
    private static final int TEST_PORT = 6790;
    private ExecutorService executor;
    private ServerSocket serverSocket;

    @BeforeEach
    void setUp() throws IOException {
        executor = Executors.newSingleThreadExecutor();
        serverSocket = new ServerSocket(TEST_PORT);
        executor.submit(() -> {
            try {
                while (!serverSocket.isClosed()) {
                    Socket socket = serverSocket.accept();
                    new TrackerHandler(socket).run();
                }
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    e.printStackTrace();
                }
            }
        });
    }

    @AfterEach
    void tearDown() throws IOException {
        serverSocket.close();
        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void testValidRegisterCommand() throws IOException {
        try (Socket socket = new Socket("localhost", TEST_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("REGISTER peer1 8001");
            String response = in.readLine();
            assertEquals("REGISTERED peer1", response);
        }
    }

    @Test
    void testDiscoveryAfterRegistration() throws IOException {
        // Register first
        try (Socket socket = new Socket("localhost", TEST_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("REGISTER testPeer 8080");
            in.readLine(); // consume response
        }

        // Then discover
        try (Socket socket = new Socket("localhost", TEST_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("DISCOVER");
            String response = in.readLine();
            assertTrue(response.startsWith("PEERS"));
            assertTrue(response.contains("testPeer"));
        }
    }

    @Test
    void testUnknownCommand() throws IOException {
        try (Socket socket = new Socket("localhost", TEST_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("INVALID_COMMAND");
            String response = in.readLine();
            assertEquals("UNKNOWN_COMMAND", response);
        }
    }

@Test
    void testIncompleteRegisterCommand() throws IOException {
        try (Socket socket = new Socket("localhost", TEST_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Send incomplete REGISTER command
            out.println("REGISTER");

            // Now we expect a proper error message instead of a closed connection
            String response = in.readLine();
            assertEquals("ERROR Insufficient parameters for REGISTER", response);
        }
    }
}