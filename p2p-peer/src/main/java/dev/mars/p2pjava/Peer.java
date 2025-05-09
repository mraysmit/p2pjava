package dev.mars.p2pjava;

import dev.mars.p2pjava.circuit.CircuitBreaker;
import dev.mars.p2pjava.util.HealthCheck;
import dev.mars.p2pjava.util.RetryHelper;
import dev.mars.p2pjava.util.ServiceMonitor;
import dev.mars.p2pjava.util.ThreadManager;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

public class Peer {
    private static final Logger logger = Logger.getLogger(Peer.class.getName());
    private static final int SOCKET_TIMEOUT_MS = 30000;
    private static final int HEARTBEAT_INTERVAL_SECONDS = 30;

    private final String peerId;
    private final String peerHost;
    private final int peerPort;
    private final String trackerHost;
    private final int trackerPort;

    private final List<String> sharedFiles = Collections.synchronizedList(new ArrayList<>());
    private final List<String> discoveredPeers = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private ExecutorService connectionExecutor;
    private ScheduledExecutorService heartbeatExecutor;

    // For synchronizing startup completion
    private final CountDownLatch startupLatch = new CountDownLatch(1);

    // Circuit breakers for external services
    private CircuitBreaker trackerCircuitBreaker;
    private CircuitBreaker indexServerCircuitBreaker;

    // Health check for this peer
    private HealthCheck.ServiceHealth health;

    // Service metrics for this peer
    private ServiceMonitor.ServiceMetrics metrics;

    public Peer(String peerId, String peerHost, int peerPort, String trackerHost, int trackerPort) {
        this.peerId = peerId;
        this.peerHost = peerHost;

        // Check for dynamic port from system property
        String peerPortStr = System.getProperty("peer.port");
        int finalPeerPort = peerPort;
        if (peerPortStr != null && !peerPortStr.isEmpty()) {
            try {
                int dynamicPort = Integer.parseInt(peerPortStr);
                finalPeerPort = dynamicPort;
                logger.info("Using dynamic port for peer: " + dynamicPort);
            } catch (NumberFormatException e) {
                logger.warning("Invalid peer.port system property: " + peerPortStr + ". Using provided port.");
            }
        }

        this.peerPort = finalPeerPort;
        this.trackerHost = trackerHost;
        this.trackerPort = trackerPort;

        configureLogging();

        // Initialize circuit breakers
        this.trackerCircuitBreaker = new CircuitBreaker("TrackerService-" + peerId, 3, 30000);
        this.indexServerCircuitBreaker = new CircuitBreaker("IndexService-" + peerId, 3, 30000);

        // Initialize health check
        this.health = HealthCheck.registerService("Peer-" + peerId);
        this.health.addHealthDetail("host", peerHost);
        this.health.addHealthDetail("port", this.peerPort);

        // Initialize metrics
        this.metrics = ServiceMonitor.registerService("Peer-" + peerId);

        logger.info("Created peer " + peerId + " at " + peerHost + ":" + this.peerPort);
    }

    private void configureLogging() {
        try {
            Logger rootLogger = Logger.getLogger("");
            Handler[] handlers = rootLogger.getHandlers();
            for (Handler handler : handlers) {
                rootLogger.removeHandler(handler);
            }

            ConsoleHandler handler = new ConsoleHandler();
            handler.setFormatter(new SimpleFormatter());
            handler.setLevel(Level.INFO);

            logger.addHandler(handler);
            logger.setLevel(Level.INFO);
        } catch (Exception e) {
            System.err.println("Error setting up logger: " + e.getMessage());
        }
    }



    public void start() throws IOException {
        if (running) {
            logger.warning("Peer already running");
            return;
        }

        running = true;
        logger.info("Starting peer " + peerId + " on port " + peerPort);

        // Initialize thread pools using ThreadManager for standardized thread management
        connectionExecutor = ThreadManager.getCachedThreadPool(
            "PeerConnectionPool-" + peerId, 
            "PeerConnection-" + peerId
        );

        // Create server socket
        try {
            serverSocket = new ServerSocket(peerPort);
            serverSocket.setSoTimeout(SOCKET_TIMEOUT_MS);

            // Start accept loop in a separate thread
            connectionExecutor.submit(this::acceptLoop);

            // Register with tracker
            registerWithTracker();

            // Start heartbeat mechanism
            startHeartbeat();

            // Signal startup completion
            startupLatch.countDown();

        } catch (IOException e) {
            stop();
            throw new IOException("Failed to start peer: " + e.getMessage(), e);
        }
    }

    private void acceptLoop() {
        logger.info("Starting connection accept loop");

        while (running && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                logger.info("Accepted connection from " + clientSocket.getInetAddress());

                // Handle client in thread pool
                connectionExecutor.submit(new PeerHandler(clientSocket, this));

            } catch (SocketTimeoutException e) {
                // This is normal, just continue the loop
            } catch (SocketException e) {
                if (running) {
                    logger.warning("Socket exception in accept loop: " + e.getMessage());
                }
            } catch (IOException e) {
                if (running) {
                    logger.severe("I/O error in accept loop: " + e.getMessage());
                }
            }
        }

        logger.info("Connection accept loop terminated");
    }

    public void stop() {
        if (!running) {
            return;
        }

        logger.info("Stopping peer " + peerId);
        running = false;

        // Record operation in metrics
        metrics.recordOperation("stop");

        // Update health status
        health.setHealthy(false);
        health.addHealthDetail("status", "stopping");

        // Stop heartbeat first
        stopHeartbeat();

        // Deregister from tracker and index server
        deregisterFromTracker();
        deregisterFilesFromIndexServer();

        // Close server socket
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logger.warning("Error closing server socket: " + e.getMessage());
            }
        }

        // Shutdown thread pools using ThreadManager
        try {
            logger.info("Shutting down connection thread pool");
            ThreadManager.shutdownThreadPool("PeerConnectionPool-" + peerId, 5, TimeUnit.SECONDS);
            logger.info("Connection thread pool shut down successfully");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error shutting down connection thread pool", e);
        }

        // Log final metrics
        ServiceMonitor.logMetricsSummary();

        // Deregister from health check and monitoring systems
        HealthCheck.deregisterService("Peer-" + peerId);
        ServiceMonitor.deregisterService("Peer-" + peerId);

        logger.info("Peer " + peerId + " stopped");
    }

    /**
     * Deregisters this peer from the tracker.
     */
    private void deregisterFromTracker() {
        logger.info("Deregistering from tracker at " + trackerHost + ":" + trackerPort);

        // Record operation in metrics
        metrics.recordOperation("deregisterFromTracker");
        long startTime = System.currentTimeMillis();
        boolean isError = false;

        try {
            // Use circuit breaker to prevent repeated calls to failing tracker
            trackerCircuitBreaker.executeWithFallback(() -> {
                // Use retry helper for transient network issues
                try {
                    RetryHelper.executeWithRetry(() -> {
                        try (Socket socket = new Socket(trackerHost, trackerPort);
                             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                            // Set socket timeout
                            socket.setSoTimeout(SOCKET_TIMEOUT_MS);

                            // Send deregistration request
                            out.println("DEREGISTER " + peerId);

                            // Read response
                            String response = in.readLine();
                            if (response != null && response.startsWith("DEREGISTERED")) {
                                logger.info("Successfully deregistered from tracker");
                                // Reset circuit breaker on success
                                trackerCircuitBreaker.reset();
                            } else {
                                logger.warning("Unexpected deregistration response: " + response);
                            }
                        }
                        return null;
                    }, 2, 500, 2000, e -> e instanceof IOException);
                } catch (Exception e) {
                    logger.warning("Failed to deregister from tracker after retries: " + e.getMessage());
                    throw new RuntimeException(e);
                }
                return null;
            }, () -> {
                logger.warning("Circuit breaker open, tracker service appears to be down");
                return null;
            });
        } catch (Exception e) {
            logger.warning("Failed to deregister from tracker: " + e.getMessage());
            isError = true;
        } finally {
            // Record metrics
            long responseTime = System.currentTimeMillis() - startTime;
            metrics.recordRequest(responseTime, isError);
            if (isError) {
                metrics.incrementCounter("trackerDeregistrationFailures");
            } else {
                metrics.incrementCounter("trackerDeregistrationSuccesses");
            }
        }
    }

    /**
     * Deregisters all files from the index server.
     */
    private void deregisterFilesFromIndexServer() {
        // Synchronize access to sharedFiles
        boolean isEmpty;
        synchronized (sharedFiles) {
            isEmpty = sharedFiles.isEmpty();
        }

        if (isEmpty) {
            logger.info("No files to deregister from index server");
            return;
        }

        logger.info("Deregistering files from index server");

        // Record operation in metrics
        metrics.recordOperation("deregisterFilesFromIndexServer");
        long startTime = System.currentTimeMillis();
        boolean isError = false;

        try {
            // Use circuit breaker to prevent repeated calls to failing index server
            indexServerCircuitBreaker.executeWithFallback(() -> {
                // Use retry helper for transient network issues
                try {
                    RetryHelper.executeWithRetry(() -> {
                        try (Socket socket = new Socket("localhost", 6001);
                             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                            // Set socket timeout
                            socket.setSoTimeout(SOCKET_TIMEOUT_MS);

                            // Send deregistration request for all files
                            out.println("DEREGISTER_PEER " + peerId + " " + peerPort);

                            // Read response
                            String response = in.readLine();
                            if (response != null && response.startsWith("PEER_DEREGISTERED")) {
                                logger.info("Successfully deregistered all files from index server");
                                // Reset circuit breaker on success
                                indexServerCircuitBreaker.reset();
                            } else {
                                logger.warning("Unexpected deregistration response: " + response);
                            }
                        }
                        return null;
                    }, 2, 500, 2000, e -> e instanceof IOException);
                } catch (Exception e) {
                    logger.warning("Failed to deregister files from index server after retries: " + e.getMessage());
                    throw new RuntimeException(e);
                }
                return null;
            }, () -> {
                logger.warning("Circuit breaker open, index server appears to be down");
                return null;
            });
        } catch (Exception e) {
            logger.warning("Failed to deregister files from index server: " + e.getMessage());
            isError = true;
        } finally {
            // Record metrics
            long responseTime = System.currentTimeMillis() - startTime;
            metrics.recordRequest(responseTime, isError);
            if (isError) {
                metrics.incrementCounter("indexServerDeregistrationFailures");
            } else {
                metrics.incrementCounter("indexServerDeregistrationSuccesses");
            }
        }
    }

    public void registerWithTracker() {
        logger.info("Registering with tracker at " + trackerHost + ":" + trackerPort);

        // Record operation in metrics
        metrics.recordOperation("registerWithTracker");
        long startTime = System.currentTimeMillis();
        boolean isError = false;

        try {
            // Use circuit breaker to prevent repeated calls to failing tracker
            trackerCircuitBreaker.executeWithFallback(() -> {
                // Use retry helper for transient network issues
                try {
                    RetryHelper.executeWithRetry(() -> {
                        try (Socket socket = new Socket(trackerHost, trackerPort);
                             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                            // Set socket timeout
                            socket.setSoTimeout(SOCKET_TIMEOUT_MS);

                            // Send registration request
                            out.println("REGISTER " + peerId + " " + peerHost + " " + peerPort);

                            // Read response
                            String response = in.readLine();
                            if ("REGISTERED".equals(response)) {
                                logger.info("Successfully registered with tracker");
                                // Update health status
                                health.setHealthy(true);
                                health.addHealthDetail("trackerRegistered", true);
                                // Reset circuit breaker on success
                                trackerCircuitBreaker.reset();
                            } else {
                                logger.warning("Unexpected registration response: " + response);
                                throw new IOException("Unexpected registration response: " + response);
                            }
                        }
                        return null;
                    }, 3, 1000, 10000, e -> e instanceof IOException);
                } catch (Exception e) {
                    logger.severe("Failed to register with tracker after retries: " + e.getMessage());
                    // Update health status
                    health.setHealthy(false);
                    health.addHealthDetail("trackerRegistered", false);
                    health.addHealthDetail("lastTrackerError", e.getMessage());
                    throw new RuntimeException(e);
                }
                return null;
            }, () -> {
                logger.severe("Circuit breaker open, tracker service appears to be down");
                // Update health status
                health.setHealthy(false);
                health.addHealthDetail("trackerRegistered", false);
                health.addHealthDetail("lastTrackerError", "Circuit breaker open");
                return null;
            });
        } catch (Exception e) {
            logger.severe("Failed to register with tracker: " + e.getMessage());
            isError = true;
        } finally {
            // Record metrics
            long responseTime = System.currentTimeMillis() - startTime;
            metrics.recordRequest(responseTime, isError);
            if (isError) {
                metrics.incrementCounter("trackerRegistrationFailures");
            } else {
                metrics.incrementCounter("trackerRegistrationSuccesses");
            }
        }
    }

    private void startHeartbeat() {
        logger.info("Starting heartbeat service");

        heartbeatExecutor = ThreadManager.getSingleThreadScheduledExecutor(
            "HeartbeatPool-" + peerId, 
            "Heartbeat-" + peerId
        );

        heartbeatExecutor.scheduleAtFixedRate(
                this::sendHeartbeat,
                HEARTBEAT_INTERVAL_SECONDS / 2,
                HEARTBEAT_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    private void stopHeartbeat() {
        logger.info("Stopping heartbeat service");
        try {
            ThreadManager.shutdownThreadPool("HeartbeatPool-" + peerId, 5, TimeUnit.SECONDS);
            logger.info("Heartbeat service shut down successfully");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error shutting down heartbeat service", e);
        }
    }

    private void sendHeartbeat() {
        // Record operation in metrics
        metrics.recordOperation("sendHeartbeat");
        long startTime = System.currentTimeMillis();
        boolean isError = false;

        try {
            // Use circuit breaker with fallback
            trackerCircuitBreaker.executeWithFallback(() -> {
                // Use retry helper for transient network issues
                try {
                    RetryHelper.executeWithRetry(() -> {
                        try (Socket socket = new Socket(trackerHost, trackerPort);
                             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                            // Set socket timeout
                            socket.setSoTimeout(5000); // Shorter timeout for heartbeats

                            // Send heartbeat
                            out.println("HEARTBEAT " + peerId);

                            // Read response
                            String response = in.readLine();
                            if ("HEARTBEAT_ACK".equals(response)) {
                                logger.fine("Received heartbeat acknowledgment");
                                // Update health status
                                health.setHealthy(true);
                                health.addHealthDetail("lastHeartbeat", System.currentTimeMillis());
                                // Reset circuit breaker on success
                                trackerCircuitBreaker.reset();
                            } else {
                                logger.warning("Unexpected heartbeat response: " + response);
                                throw new IOException("Unexpected heartbeat response: " + response);
                            }
                        }
                        return null;
                    }, 2, 500, 2000, e -> e instanceof IOException); // Shorter retries for heartbeats
                } catch (Exception e) {
                    logger.warning("Failed to send heartbeat after retries: " + e.getMessage());
                    // Update health status
                    health.addHealthDetail("lastHeartbeatError", e.getMessage());
                    health.addHealthDetail("lastHeartbeatErrorTime", System.currentTimeMillis());
                    throw new RuntimeException(e);
                }
                return null;
            }, () -> {
                logger.warning("Circuit breaker open, tracker service appears to be down");
                // Update health status
                health.addHealthDetail("trackerStatus", "Circuit breaker open");
                return null;
            });
        } catch (Exception e) {
            logger.warning("Failed to send heartbeat: " + e.getMessage());
            isError = true;
        } finally {
            // Record metrics
            long responseTime = System.currentTimeMillis() - startTime;
            metrics.recordRequest(responseTime, isError);
            if (isError) {
                metrics.incrementCounter("heartbeatFailures");
            } else {
                metrics.incrementCounter("heartbeatSuccesses");
            }
        }
    }

    public void addSharedFile(String filePath) {
        sharedFiles.add(filePath);
        logger.info("Added shared file: " + filePath);
    }

    public String findSharedFilePath(String fileName) {
        // Synchronize on sharedFiles to ensure thread-safe iteration
        synchronized (sharedFiles) {
            for (String path : sharedFiles) {
                File file = new File(path);
                if (file.getName().equals(fileName)) {
                    return path;
                }
            }
            return null;
        }
    }

    public void discoverPeers() {
        logger.info("Discovering peers from tracker");

        // Record operation in metrics
        metrics.recordOperation("discoverPeers");
        long startTime = System.currentTimeMillis();
        boolean isError = false;

        try {
            // Use circuit breaker with fallback
            List<String> result = trackerCircuitBreaker.executeWithFallback(() -> {
                // Use retry helper for transient network issues
                try {
                    return RetryHelper.executeWithRetry(() -> {
                        try (Socket socket = new Socket(trackerHost, trackerPort);
                             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                            // Set socket timeout
                            socket.setSoTimeout(SOCKET_TIMEOUT_MS);

                            // Send discovery request
                            out.println("DISCOVER");

                            // Read response
                            String response = in.readLine();
                            if (response != null && !response.isEmpty()) {
                                // Process the response
                                if (response.startsWith("PEERS")) {
                                    // Extract peer info from response
                                    String peerListStr = response.substring(6).trim();
                                    List<String> peerList = new ArrayList<>();

                                    // Parse the peer list - this is a simplified version
                                    // In a real implementation, you would parse the PeerInfo objects properly
                                    if (!peerListStr.isEmpty()) {
                                        String[] peers = peerListStr.split(",");
                                        peerList.addAll(Arrays.asList(peers));
                                    }

                                    logger.info("Discovered " + peerList.size() + " peers");

                                    // Update health status
                                    health.setHealthy(true);
                                    health.addHealthDetail("lastPeerDiscovery", System.currentTimeMillis());
                                    health.addHealthDetail("discoveredPeerCount", peerList.size());

                                    // Reset circuit breaker on success
                                    trackerCircuitBreaker.reset();

                                    return peerList;
                                } else {
                                    logger.warning("Unexpected discovery response: " + response);
                                    throw new IOException("Unexpected discovery response: " + response);
                                }
                            } else {
                                logger.warning("Empty response from tracker");
                                throw new IOException("Empty response from tracker");
                            }
                        }
                    }, 3, 1000, 10000, e -> e instanceof IOException);
                } catch (Exception e) {
                    logger.severe("Failed to discover peers after retries: " + e.getMessage());
                    // Update health status
                    health.addHealthDetail("lastPeerDiscoveryError", e.getMessage());
                    health.addHealthDetail("lastPeerDiscoveryErrorTime", System.currentTimeMillis());
                    throw new RuntimeException(e);
                }
            }, () -> {
                logger.warning("Circuit breaker open, tracker service appears to be down");
                // Update health status
                health.addHealthDetail("trackerStatus", "Circuit breaker open");
                return new ArrayList<>(); // Return empty list as fallback
            });

            // Update discovered peers list - synchronize to ensure atomic operation
            synchronized (discoveredPeers) {
                discoveredPeers.clear();
                if (result != null) {
                    discoveredPeers.addAll(result);
                    metrics.incrementCounter("discoveredPeers", result.size());
                }
            }

        } catch (Exception e) {
            logger.severe("Failed to discover peers: " + e.getMessage());
            isError = true;
        } finally {
            // Record metrics
            long responseTime = System.currentTimeMillis() - startTime;
            metrics.recordRequest(responseTime, isError);
            if (isError) {
                metrics.incrementCounter("peerDiscoveryFailures");
            } else {
                metrics.incrementCounter("peerDiscoverySuccesses");
            }
        }
    }

    public List<String> getDiscoveredPeers() {
        return new ArrayList<>(discoveredPeers);
    }

    // Wait for peer to start up completely
    public boolean waitForStartup(long timeoutMs) {
        try {
            return startupLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public String getPeerId() {
        return peerId;
    }
}
