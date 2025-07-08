package dev.mars.p2pjava.util;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * Example server implementation demonstrating improved thread management
 * with CompletableFuture, proper shutdown procedures, and monitoring.
 */
public class AsyncServerExample {
    private static final Logger logger = Logger.getLogger(AsyncServerExample.class.getName());
    
    private final int port;
    private final String serverName;
    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private CompletableFuture<Void> serverFuture;
    
    public AsyncServerExample(int port, String serverName) {
        this.port = port;
        this.serverName = serverName;
    }
    
    /**
     * Starts the server asynchronously using CompletableFuture.
     */
    public CompletableFuture<Void> startAsync() {
        if (running) {
            return CompletableFuture.completedFuture(null);
        }
        
        logger.info("Starting " + serverName + " on port " + port);
        
        // Start thread monitoring
        ThreadMonitor.startMonitoring();

        // Create thread pool for this server
        ThreadManager.getCachedThreadPool("ServerPool-" + serverName, "Server-" + serverName);

        // Create server startup chain
        serverFuture = AsyncOperationManager.executeSequentialChain(
            "ServerPool-" + serverName,
            // First: Initialize server socket
            this::initializeServerSocket,
            // Second: Start accept loop
            (socketResult) -> {
                startAcceptLoop();
                return "acceptLoopStarted";
            },
            // Third: Mark as running
            (acceptResult) -> {
                running = true;
                logger.info(serverName + " started successfully on port " + port);
                return "serverStarted";
            },
            "ServerStartup-" + serverName
        ).thenApply(result -> null); // Convert to Void
        
        return serverFuture.exceptionally(ex -> {
            logger.severe("Failed to start " + serverName + ": " + ex.getMessage());
            cleanup();
            throw new RuntimeException(ex);
        });
    }
    
    /**
     * Initializes the server socket.
     */
    private String initializeServerSocket() {
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setSoTimeout(5000); // 5 second timeout for accept
            logger.info("Server socket initialized on port " + port);
            return "socketInitialized";
        } catch (IOException e) {
            throw new RuntimeException("Failed to create server socket", e);
        }
    }
    
    /**
     * Starts the connection accept loop asynchronously.
     */
    private void startAcceptLoop() {
        String poolName = "ServerPool-" + serverName;
        
        // Submit accept loop to run continuously
        ThreadManager.executeAsync(poolName, () -> {
            logger.info("Starting accept loop for " + serverName);
            
            while (running && !serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    logger.info("Accepted connection from " + clientSocket.getInetAddress());
                    
                    // Handle client asynchronously with timeout and monitoring
                    handleClientAsync(clientSocket, poolName);
                    
                } catch (SocketTimeoutException e) {
                    // Normal timeout, continue loop
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
            
            logger.info("Accept loop terminated for " + serverName);
            return null;
        });
    }
    
    /**
     * Handles a client connection asynchronously with proper monitoring.
     */
    private void handleClientAsync(Socket clientSocket, String poolName) {
        long startTime = System.currentTimeMillis();
        
        AsyncOperationManager.executeWithTimeout(
            poolName,
            () -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                     PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)) {
                    
                    // Simulate some processing
                    String request = reader.readLine();
                    logger.info("Processing request: " + request);
                    
                    // Send response
                    String response = "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: text/plain\r\n" +
                                    "Content-Length: " + serverName.length() + "\r\n" +
                                    "\r\n" +
                                    serverName;
                    writer.println(response);
                    
                    return "requestProcessed";
                    
                } catch (IOException e) {
                    throw new RuntimeException("Error processing client request", e);
                } finally {
                    try {
                        clientSocket.close();
                    } catch (IOException e) {
                        logger.warning("Error closing client socket: " + e.getMessage());
                    }
                }
            },
            30000, // 30 second timeout
            "ClientRequest-" + serverName
        ).whenComplete((result, throwable) -> {
            long duration = System.currentTimeMillis() - startTime;
            boolean failed = throwable != null;
            
            // Record metrics
            ThreadMonitor.recordTaskExecution(poolName, duration, failed);
            
            if (failed) {
                logger.warning("Client request failed: " + throwable.getMessage());
            } else {
                logger.fine("Client request completed in " + duration + "ms");
            }
        });
    }
    
    /**
     * Stops the server gracefully with proper shutdown sequence.
     */
    public CompletableFuture<Void> stopAsync() {
        if (!running) {
            return CompletableFuture.completedFuture(null);
        }
        
        logger.info("Stopping " + serverName + "...");
        running = false;
        
        return AsyncOperationManager.executeWithTimeout(
            "ServerPool-" + serverName,
            () -> {
                // Close server socket
                if (serverSocket != null && !serverSocket.isClosed()) {
                    try {
                        serverSocket.close();
                        logger.info("Server socket closed");
                    } catch (IOException e) {
                        logger.warning("Error closing server socket: " + e.getMessage());
                    }
                }
                
                // Wait for server future to complete
                if (serverFuture != null) {
                    try {
                        serverFuture.get(5, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        logger.warning("Error waiting for server future: " + e.getMessage());
                    }
                }
                
                // Shutdown thread pools
                ThreadManager.shutdownThreadPool("ServerPool-" + serverName);
                
                // Stop monitoring
                ThreadMonitor.stopMonitoring();
                
                logger.info(serverName + " stopped successfully");
                return null;
            },
            10000, // 10 second timeout for shutdown
            "ServerShutdown-" + serverName
        );
    }
    
    /**
     * Synchronous wrapper for starting the server.
     */
    public void start() {
        try {
            startAsync().get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to start server", e);
        }
    }
    
    /**
     * Synchronous wrapper for stopping the server.
     */
    public void stop() {
        try {
            stopAsync().get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to stop server", e);
        }
    }
    
    /**
     * Cleanup resources in case of failure.
     */
    private void cleanup() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logger.warning("Error during cleanup: " + e.getMessage());
            }
        }
    }
    
    /**
     * Gets the current server status.
     */
    public boolean isRunning() {
        return running;
    }
    
    /**
     * Gets monitoring information for this server.
     */
    public ThreadPoolMonitorInfo getMonitoringInfo() {
        return ThreadManager.getThreadPoolInfo("ServerPool-" + serverName);
    }
}
