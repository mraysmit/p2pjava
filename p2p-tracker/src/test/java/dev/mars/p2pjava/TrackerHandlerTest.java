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

class TrackerHandlerTest {
    private static final int TEST_PORT = 6789;
    private ExecutorService executor;
    private ServerSocket serverSocket;

    @BeforeEach
    void setUp() throws IOException {
        // Start a test server socket
        executor = Executors.newSingleThreadExecutor();
        serverSocket = new ServerSocket(TEST_PORT);
        executor.submit(() -> {
            try {
                Socket socket = serverSocket.accept();
                new TrackerHandler(socket).run();
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
    void testRegisterPeer() throws IOException {
        try (Socket socket = new Socket("localhost", TEST_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("REGISTER peer1 8001");
            String response = in.readLine();

            assertEquals("REGISTERED peer1", response);
        }
    }

    @Test
    void testDiscoverPeers() throws IOException {
        try (Socket socket = new Socket("localhost", TEST_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Register a peer first
            out.println("REGISTER peer1 8001");
            in.readLine(); // consume response

            // Now try to discover
            out.println("DISCOVER");
            String response = in.readLine();

            assertTrue(response.startsWith("PEERS ["));
            assertTrue(response.contains("peer1"));
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
}