package dev.mars.p2pjava;

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

class TrackerHandlerExtendedTest {
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
                // Ignore if closed during shutdown
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
    void testRegisterMultiplePeers() throws IOException {
        // Register first peer
        try (Socket socket = new Socket("localhost", TEST_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("REGISTER peer1 8001");
            String response = in.readLine();
            assertEquals("REGISTERED peer1", response);
        }

        // Register second peer
        try (Socket socket = new Socket("localhost", TEST_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("REGISTER peer2 8002");
            String response = in.readLine();
            assertEquals("REGISTERED peer2", response);
        }

        // Verify both peers are discovered
        try (Socket socket = new Socket("localhost", TEST_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("DISCOVER");
            String response = in.readLine();
            assertTrue(response.contains("peer1"));
            assertTrue(response.contains("peer2"));
        }
    }

    @Test
    void testPersistenceAcrossConnections() throws IOException {
        // Register a peer
        try (Socket socket = new Socket("localhost", TEST_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("REGISTER peer3 8003");
            String response = in.readLine();
            assertEquals("REGISTERED peer3", response);
        }

        // Create a new connection and verify peer is still registered
        try (Socket socket = new Socket("localhost", TEST_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("DISCOVER");
            String response = in.readLine();
            assertTrue(response.contains("peer3"));
        }
    }

    @Test
    void testMalformedCommands() throws IOException {
        try (Socket socket = new Socket("localhost", TEST_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Send incomplete REGISTER command
            out.println("REGISTER");
            String response = in.readLine();
            assertEquals("ERROR Insufficient parameters for REGISTER", response);
        }
    }

    @Test
    void testMultipleCommandsInSameSession() throws IOException {
        try (Socket socket = new Socket("localhost", TEST_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Register first
            out.println("REGISTER peer4 8004");
            String response1 = in.readLine();
            assertEquals("REGISTERED peer4", response1);

            // Then discover in same connection
            out.println("DISCOVER");
            String response2 = in.readLine();
            assertTrue(response2.contains("peer4"));
        }
    }
}