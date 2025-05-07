package dev.mars.p2pjava;

import dev.mars.p2pjava.util.HealthCheck;
import dev.mars.p2pjava.util.ServiceMonitor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the resilience features of the Peer class, including retry logic and circuit breakers.
 */
public class PeerResilienceTest {
    private static final int TEST_TRACKER_PORT = 6100;
    private static final int TEST_PEER_PORT = 6101;
    private static final String TEST_PEER_ID = "test-peer";

    private ExecutorService executorService;
    private MockTracker mockTracker;
    private Peer peer;

    @BeforeEach
    void setUp() {
        executorService = Executors.newCachedThreadPool();
        mockTracker = new MockTracker(TEST_TRACKER_PORT);
        peer = new Peer(TEST_PEER_ID, "localhost", TEST_PEER_PORT, "localhost", TEST_TRACKER_PORT);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (peer != null) {
            peer.stop();
        }

        if (mockTracker != null) {
            mockTracker.stop();
        }

        if (executorService != null) {
            executorService.shutdownNow();
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void testRetryOnTrackerFailure() throws Exception {
        // Configure mock tracker to fail the first two requests
        mockTracker.setFailureCount(2);
        executorService.submit(mockTracker);

        // Wait for mock tracker to start
        Thread.sleep(500);

        // Start peer - this should trigger registration with tracker
        peer.start();

        // Wait for registration to complete
        Thread.sleep(1000);

        // Verify that the peer is healthy
        HealthCheck.ServiceHealth health = HealthCheck.getServiceHealth("Peer-" + TEST_PEER_ID);
        assertNotNull(health);
        assertTrue(health.isHealthy());

        // Verify that the tracker received the expected number of registration attempts
        assertEquals(3, mockTracker.getTotalRequests());
        assertEquals(2, mockTracker.getFailedRequests());
        assertEquals(1, mockTracker.getSuccessfulRequests());

        // Verify metrics
        ServiceMonitor.ServiceMetrics metrics = ServiceMonitor.getServiceMetrics("Peer-" + TEST_PEER_ID);
        assertNotNull(metrics);
        assertTrue(metrics.getCounter("trackerRegistrationSuccesses") > 0);
    }

    @Test
    void testCircuitBreakerOnTrackerFailure() throws Exception {
        // Configure mock tracker to always fail
        mockTracker.setFailureCount(Integer.MAX_VALUE);
        executorService.submit(mockTracker);

        // Wait for mock tracker to start
        Thread.sleep(500);

        // Note: This test intentionally causes warning messages in the logs from RetryHelper and CircuitBreaker
        // These warnings are expected and are part of testing the circuit breaker functionality
        // when the tracker consistently fails

        // Start peer - this should trigger registration with tracker
        try {
            peer.start();
        } catch (Exception e) {
            // Expected - peer will fail to start due to tracker failure
        }

        // Wait for registration attempts to complete
        Thread.sleep(2000);

        // Verify that the peer is not healthy
        HealthCheck.ServiceHealth health = HealthCheck.getServiceHealth("Peer-" + TEST_PEER_ID);
        assertNotNull(health);
        assertFalse(health.isHealthy());

        // Verify that the tracker received the expected number of registration attempts
        // Should be limited by the circuit breaker
        assertTrue(mockTracker.getTotalRequests() >= 3);
        assertEquals(mockTracker.getTotalRequests(), mockTracker.getFailedRequests());
        assertEquals(0, mockTracker.getSuccessfulRequests());

        // Verify metrics
        ServiceMonitor.ServiceMetrics metrics = ServiceMonitor.getServiceMetrics("Peer-" + TEST_PEER_ID);
        assertNotNull(metrics);
        assertTrue(metrics.getCounter("trackerRegistrationFailures") > 0);
    }

    /**
     * Mock implementation of a tracker server that can be configured to fail a certain number of requests.
     */
    private static class MockTracker implements Runnable {
        private final int port;
        private volatile boolean running = false;
        private volatile int failureCount = 0;
        private volatile int totalRequests = 0;
        private volatile int failedRequests = 0;
        private volatile int successfulRequests = 0;
        private ServerSocket serverSocket;

        public MockTracker(int port) {
            this.port = port;
        }

        public void setFailureCount(int failureCount) {
            this.failureCount = failureCount;
        }

        public int getTotalRequests() {
            return totalRequests;
        }

        public int getFailedRequests() {
            return failedRequests;
        }

        public int getSuccessfulRequests() {
            return successfulRequests;
        }

        @Override
        public void run() {
            running = true;
            try (ServerSocket ss = new ServerSocket(port)) {
                serverSocket = ss;
                ss.setSoTimeout(500);

                while (running) {
                    try {
                        Socket socket = ss.accept();
                        handleClient(socket);
                    } catch (IOException e) {
                        // Timeout or other error, just continue
                    }
                }
            } catch (IOException e) {
                System.err.println("MockTracker error: " + e.getMessage());
            }
        }

        private void handleClient(Socket socket) {
            totalRequests++;

            try {
                // Simulate processing delay
                Thread.sleep(100);

                if (failureCount > 0) {
                    failureCount--;
                    failedRequests++;
                    socket.close();
                    return;
                }

                // Handle the request normally
                java.io.PrintWriter out = new java.io.PrintWriter(socket.getOutputStream(), true);
                java.io.BufferedReader in = new java.io.BufferedReader(
                        new java.io.InputStreamReader(socket.getInputStream()));

                String request = in.readLine();
                if (request != null) {
                    if (request.startsWith("REGISTER")) {
                        out.println("REGISTERED");
                    } else if (request.startsWith("HEARTBEAT")) {
                        out.println("HEARTBEAT_ACK");
                    } else if (request.startsWith("DISCOVER")) {
                        out.println("PEERS []");
                    } else {
                        out.println("UNKNOWN_COMMAND");
                    }
                }

                successfulRequests++;
            } catch (Exception e) {
                failedRequests++;
                System.err.println("Error handling client: " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }

        public void stop() {
            running = false;
            if (serverSocket != null && !serverSocket.isClosed()) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }
}
