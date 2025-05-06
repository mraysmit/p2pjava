package dev.mars.p2pjava;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Assertions;

public class PeerTest {

    private Peer peer;
    private final String peerId = "testPeer";
    private final String peerHost = "localhost";
    private final int peerPort = 9000;
    private final String trackerHost = "localhost";
    private final int trackerPort = 8000;

    @TempDir
    Path tempDir;

    private ExecutorService executor;
    private ServerSocket mockedTrackerSocket;

    @BeforeEach
    public void setup() throws IOException {
        // Create a fake tracker server
        mockedTrackerSocket = new ServerSocket(trackerPort);

        // Create test executor for background tasks
        executor = Executors.newCachedThreadPool();

        // Create peer instance
        peer = new Peer(peerId, peerHost, peerPort, trackerHost, trackerPort);
    }

    @AfterEach
    public void teardown() throws InterruptedException, IOException {
        if (peer != null) {
            peer.stop();
        }

        if (mockedTrackerSocket != null && !mockedTrackerSocket.isClosed()) {
            mockedTrackerSocket.close();
        }

        executor.shutdownNow();
        executor.awaitTermination(2, TimeUnit.SECONDS);
    }

    @Test
    public void testPeerInitialization() {
        assertNotNull(peer, "Peer should be initialized");
    }

    @Test
    public void testAddSharedFile() throws IOException {
        // Create a test file
        Path testFile = tempDir.resolve("testFile.txt");
        Files.writeString(testFile, "Test file content");

        // Add file to peer
        peer.addSharedFile(testFile.toString());

        // Verify the file was added
        assertTrue(peer.findSharedFilePath("testFile.txt") != null,
                "Shared file should be found by name");
    }

    @Test
    public void testRegisterWithTracker() throws IOException, ExecutionException, InterruptedException, TimeoutException {
        // Start a fake tracker in the background that accepts one connection
        CompletableFuture<String> receivedMessage = new CompletableFuture<>();
        executor.submit(() -> {
            try (Socket clientSocket = mockedTrackerSocket.accept();
                 BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

                // Read the registration message
                String message = in.readLine();
                receivedMessage.complete(message);

                // Send back fake success response
                out.println("REGISTERED");
            } catch (IOException e) {
                receivedMessage.completeExceptionally(e);
            }
        });

        // Execute registration
        peer.registerWithTracker();

        // Verify the message format
        String message = receivedMessage.get(2, TimeUnit.SECONDS);
        assertTrue(message.contains("REGISTER " + peerId),
                "Registration message should contain peer ID");
    }

    @Test
    public void testSendHeartbeat() throws Exception {
        // Set up reflected access to the private method
        java.lang.reflect.Method sendHeartbeatMethod =
                Peer.class.getDeclaredMethod("sendHeartbeat");
        sendHeartbeatMethod.setAccessible(true);

        // Start a fake tracker in the background
        CompletableFuture<String> receivedMessage = new CompletableFuture<>();
        executor.submit(() -> {
            try (Socket clientSocket = mockedTrackerSocket.accept();
                 BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

                // Read the heartbeat message
                String message = in.readLine();
                receivedMessage.complete(message);

                // Send back fake success response
                out.println("HEARTBEAT_ACK");
            } catch (IOException e) {
                receivedMessage.completeExceptionally(e);
            }
        });

        // Send heartbeat
        sendHeartbeatMethod.invoke(peer);

        // Verify heartbeat message format
        String message = receivedMessage.get(2, TimeUnit.SECONDS);
        assertTrue(message.contains("HEARTBEAT " + peerId),
                "Heartbeat message should contain peer ID");
    }

    @Test
    public void testHandleClientConnection() throws Exception {
        // Create a test file
        String fileName = "testFile.txt";
        Path testFile = tempDir.resolve(fileName);
        Files.writeString(testFile, "Test file content");
        peer.addSharedFile(testFile.toString());

        // Create connected socket pair (using loopback)
        ServerSocket server = new ServerSocket(0); // Use any free port
        int port = server.getLocalPort();

        executor.submit(() -> {
            try {
                Socket serverSide = server.accept();
                // Create and run a PeerHandler directly
                PeerHandler handler = new PeerHandler(serverSide, peer);
                handler.run(); // Direct call to run method
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Connect client and send request
        try (Socket clientSide = new Socket("localhost", port)) {
            // Send file request
            DataOutputStream out = new DataOutputStream(clientSide.getOutputStream());
            out.writeUTF("PROTOCOL_V1");
            out.writeUTF("GET_FILE");
            out.writeUTF(fileName);
            out.flush();

            // Read response
            DataInputStream in = new DataInputStream(clientSide.getInputStream());
            String response = in.readUTF();

            // Verify response
            assertEquals("SENDING_FILE", response, "Should respond with SENDING_FILE for existing file");

            // Read and verify file content
            long fileSize = in.readLong();

            // Read checksum (added to match PeerHandler implementation)
            String checksum = in.readUTF();
            assertNotNull(checksum, "Checksum should not be null");

            byte[] buffer = new byte[(int)fileSize];
            in.readFully(buffer);
            assertEquals("Test file content", new String(buffer));
        } finally {
            server.close();
        }
    }

    @Test
    public void testPeerStart() throws Exception {
        // Record start time for performance measurement
        long startTime = System.currentTimeMillis();
        long setupStartTime = startTime;

        // Set up a mock tracker to accept the registration request
        CompletableFuture<String> registrationFuture = new CompletableFuture<>();

        // Use a shorter timeout for the test
        final int TEST_TIMEOUT_MS = 500;

        // Start the mock tracker with a shorter timeout
        executor.submit(() -> {
            try {
                // Set a shorter accept timeout to avoid blocking
                mockedTrackerSocket.setSoTimeout(TEST_TIMEOUT_MS);

                Socket clientSocket = mockedTrackerSocket.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

                // Read the registration message
                String message = in.readLine();
                registrationFuture.complete(message);

                // Send back fake success response immediately
                out.println("REGISTERED");

                // Close resources
                out.close();
                in.close();
                clientSocket.close();
            } catch (IOException e) {
                registrationFuture.completeExceptionally(e);
            }
        });

        long setupDuration = System.currentTimeMillis() - setupStartTime;

        // Start timing the peer start operation
        long peerStartTime = System.currentTimeMillis();

        // Start the peer directly (not in a background thread)
        // This allows us to test the start method without waiting for background tasks
        peer.start();

        long peerStartDuration = System.currentTimeMillis() - peerStartTime;

        // Start timing the registration verification
        long registrationVerifyTime = System.currentTimeMillis();

        // Wait for registration to complete with a shorter timeout
        String registrationMessage = registrationFuture.get(1, TimeUnit.SECONDS);
        assertTrue(registrationMessage.contains("REGISTER " + peerId), 
                "Registration message should contain peer ID");

        long registrationVerifyDuration = System.currentTimeMillis() - registrationVerifyTime;

        // Start timing the connection test
        long connectionTestTime = System.currentTimeMillis();

        // Test that the socket is actually listening with a shorter connection timeout
        Socket testConnection = null;
        try {
            testConnection = new Socket();
            testConnection.connect(new InetSocketAddress(peerHost, peerPort), TEST_TIMEOUT_MS);
            assertTrue(testConnection.isConnected(), "Should be able to connect to started peer");
        } catch (ConnectException e) {
            fail("Peer is not listening on expected port");
        } finally {
            if (testConnection != null && !testConnection.isClosed()) {
                testConnection.close();
            }
        }

        long connectionTestDuration = System.currentTimeMillis() - connectionTestTime;

        // Start timing the deregistration setup
        long deregSetupTime = System.currentTimeMillis();

        // Set up a mock tracker to accept the deregistration request that will happen in teardown
        CompletableFuture<String> deregistrationFuture = new CompletableFuture<>();
        executor.submit(() -> {
            try {
                // Set a shorter accept timeout to avoid blocking
                mockedTrackerSocket.setSoTimeout(TEST_TIMEOUT_MS);

                Socket clientSocket = mockedTrackerSocket.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

                // Read the deregistration message
                String message = in.readLine();
                deregistrationFuture.complete(message);

                // Send back fake success response
                out.println("DEREGISTERED");

                // Close resources
                out.close();
                in.close();
                clientSocket.close();
            } catch (IOException e) {
                deregistrationFuture.completeExceptionally(e);
            }
        });

        // Set up a mock index server to accept the deregistration request
        ServerSocket mockIndexServer = new ServerSocket(6001);
        mockIndexServer.setSoTimeout(TEST_TIMEOUT_MS);
        CompletableFuture<String> indexDeregistrationFuture = new CompletableFuture<>();
        executor.submit(() -> {
            try {
                Socket clientSocket = mockIndexServer.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

                // Read the deregistration message
                String message = in.readLine();
                indexDeregistrationFuture.complete(message);

                // Send back fake success response
                out.println("PEER_DEREGISTERED");

                // Close resources
                out.close();
                in.close();
                clientSocket.close();
            } catch (IOException e) {
                // This is fine, might happen if there are no shared files
                indexDeregistrationFuture.completeExceptionally(e);
            } finally {
                try {
                    mockIndexServer.close();
                } catch (IOException ex) {
                    // Ignore
                }
            }
        });

        long deregSetupDuration = System.currentTimeMillis() - deregSetupTime;

        // Record and log the test execution time
        long testDuration = System.currentTimeMillis() - startTime;

        // Log all timing metrics
        Logger logger = Logger.getLogger(PeerTest.class.getName());
        logger.info("[PERFORMANCE] Setup time: " + setupDuration + "ms");
        logger.info("[PERFORMANCE] Peer.start() execution time: " + peerStartDuration + "ms");
        logger.info("[PERFORMANCE] Registration verification time: " + registrationVerifyDuration + "ms");
        logger.info("[PERFORMANCE] Connection test time: " + connectionTestDuration + "ms");
        logger.info("[PERFORMANCE] Deregistration setup time: " + deregSetupDuration + "ms");
        logger.info("[PERFORMANCE] testPeerStart total execution time: " + testDuration + "ms");

        // Assert that the test execution time is within acceptable limits
        // The test should complete in under 5 seconds (5000ms)
        assertTrue(testDuration < 5000, 
                "Test execution time (" + testDuration + "ms) exceeds acceptable limit (5000ms)");
    }

    @Test
    public void testPeerStop() throws Exception {
        // Set up a mock tracker to accept the registration request
        CompletableFuture<String> registrationFuture = new CompletableFuture<>();
        executor.submit(() -> {
            try (Socket clientSocket = mockedTrackerSocket.accept();
                 BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

                // Read the registration message
                String message = in.readLine();
                registrationFuture.complete(message);

                // Send back fake success response
                out.println("REGISTERED");
            } catch (IOException e) {
                registrationFuture.completeExceptionally(e);
            }
        });

        // Start the peer
        executor.submit(() -> {
            try {
                peer.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Wait for registration to complete
        String registrationMessage = registrationFuture.get(5, TimeUnit.SECONDS);
        assertTrue(registrationMessage.contains("REGISTER " + peerId), 
                "Registration message should contain peer ID");

        // Verify it's running by making a connection
        try (Socket testConnection = new Socket(peerHost, peerPort)) {
            assertTrue(testConnection.isConnected(), "Should be able to connect to started peer");
        }

        // Set up a mock tracker to accept the deregistration request
        CompletableFuture<String> deregistrationFuture = new CompletableFuture<>();
        executor.submit(() -> {
            try (Socket clientSocket = mockedTrackerSocket.accept();
                 BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

                // Read the deregistration message
                String message = in.readLine();
                deregistrationFuture.complete(message);

                // Send back fake success response
                out.println("DEREGISTERED");
            } catch (IOException e) {
                deregistrationFuture.completeExceptionally(e);
            }
        });

        // Set up a mock index server to accept the deregistration request
        ServerSocket mockIndexServer = new ServerSocket(6001);
        CompletableFuture<String> indexDeregistrationFuture = new CompletableFuture<>();
        executor.submit(() -> {
            try (Socket clientSocket = mockIndexServer.accept();
                 BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

                // Read the deregistration message
                String message = in.readLine();
                indexDeregistrationFuture.complete(message);

                // Send back fake success response
                out.println("PEER_DEREGISTERED");
            } catch (IOException e) {
                indexDeregistrationFuture.completeExceptionally(e);
                // Close the server socket if there's an error
                try {
                    mockIndexServer.close();
                } catch (IOException ex) {
                    // Ignore
                }
            }
        });

        // Stop the peer
        peer.stop();

        try {
            // Wait for deregistration to complete (with a timeout)
            String deregistrationMessage = deregistrationFuture.get(5, TimeUnit.SECONDS);
            assertTrue(deregistrationMessage.contains("DEREGISTER " + peerId), 
                    "Deregistration message should contain peer ID");

            // Wait for index server deregistration to complete (with a timeout)
            // This might time out if there are no shared files, which is fine
            try {
                String indexDeregistrationMessage = indexDeregistrationFuture.get(5, TimeUnit.SECONDS);
                assertTrue(indexDeregistrationMessage.contains("DEREGISTER_PEER " + peerId), 
                        "Index deregistration message should contain peer ID");
            } catch (TimeoutException e) {
                // This is fine, might happen if there are no shared files
            }
        } finally {
            // Close the mock index server
            mockIndexServer.close();
        }

        // Verify it's stopped by trying to connect (should fail)
        assertThrows(ConnectException.class, () -> {
            new Socket(peerHost, peerPort).close();
        }, "Should not be able to connect after peer is stopped");
    }
}
