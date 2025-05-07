package dev.mars.p2pjava.util;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * A connection pool for managing network connections.
 * This class provides a way to reuse connections instead of creating new ones for each request,
 * which improves performance and resource utilization.
 */
public class ConnectionPool {
    private static final Logger logger = Logger.getLogger(ConnectionPool.class.getName());

    // The executor service for managing the connection pool
    private final ExecutorService executor;

    // The maximum number of connections in the pool
    private final int maxConnections;

    // The maximum time to wait for a connection
    private final long connectionTimeoutMs;

    // The semaphore for limiting the number of connections
    private final Semaphore connectionSemaphore;

    // Statistics
    private final AtomicLong totalConnections = new AtomicLong(0);
    private final AtomicLong activeConnections = new AtomicLong(0);
    private final AtomicLong connectionWaitTime = new AtomicLong(0);
    private final AtomicLong connectionRequests = new AtomicLong(0);
    private final AtomicLong connectionTimeouts = new AtomicLong(0);

    /**
     * Creates a new connection pool with the specified parameters.
     *
     * @param maxConnections The maximum number of connections in the pool
     * @param connectionTimeoutMs The maximum time to wait for a connection in milliseconds
     */
    public ConnectionPool(int maxConnections, long connectionTimeoutMs) {
        this.maxConnections = maxConnections;
        this.connectionTimeoutMs = connectionTimeoutMs;
        this.connectionSemaphore = new Semaphore(maxConnections, true);

        // Create a thread pool with a custom thread factory
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "ConnectionPool-" + 
                    java.util.UUID.randomUUID().toString().substring(0, 8));
            t.setDaemon(true);
            return t;
        });

        logger.info("Created connection pool with max connections: " + maxConnections + 
                ", timeout: " + connectionTimeoutMs + "ms");
    }

    /**
     * Creates a new connection pool with default parameters.
     */
    public ConnectionPool() {
        this(100, 5000);
    }

    /**
     * Executes a task using a connection from the pool.
     *
     * @param task The task to execute
     * @param <T> The return type of the task
     * @return The result of the task
     * @throws InterruptedException If the thread is interrupted while waiting for a connection
     * @throws TimeoutException If the timeout expires before a connection becomes available
     * @throws ExecutionException If the task throws an exception
     */
    public <T> T executeWithConnection(Callable<T> task) 
            throws InterruptedException, TimeoutException, ExecutionException {
        connectionRequests.incrementAndGet();
        long startTime = System.currentTimeMillis();

        // Try to acquire a connection
        if (!connectionSemaphore.tryAcquire(connectionTimeoutMs, TimeUnit.MILLISECONDS)) {
            connectionTimeouts.incrementAndGet();
            throw new TimeoutException("Timeout waiting for connection");
        }

        // Update statistics
        long waitTime = System.currentTimeMillis() - startTime;
        connectionWaitTime.addAndGet(waitTime);
        activeConnections.incrementAndGet();

        // Only increment total connections if we're below the maximum
        if (totalConnections.get() < maxConnections) {
            totalConnections.incrementAndGet();
        }

        try {
            // Execute the task
            return executor.submit(task).get();
        } finally {
            // Release the connection
            activeConnections.decrementAndGet();
            connectionSemaphore.release();
        }
    }

    /**
     * Executes a task using a connection from the pool.
     *
     * @param task The task to execute
     * @throws InterruptedException If the thread is interrupted while waiting for a connection
     * @throws TimeoutException If the timeout expires before a connection becomes available
     * @throws ExecutionException If the task throws an exception
     */
    public void executeWithConnection(Runnable task) 
            throws InterruptedException, TimeoutException, ExecutionException {
        executeWithConnection(() -> {
            task.run();
            return null;
        });
    }

    /**
     * Shuts down the connection pool.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warning("Connection pool did not terminate");
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("Connection pool shut down");
    }

    /**
     * Gets the number of active connections.
     *
     * @return The number of active connections
     */
    public long getActiveConnections() {
        return activeConnections.get();
    }

    /**
     * Gets the total number of connections created.
     *
     * @return The total number of connections
     */
    public long getTotalConnections() {
        return totalConnections.get();
    }

    /**
     * Gets the average wait time for connections.
     *
     * @return The average wait time in milliseconds
     */
    public double getAverageWaitTime() {
        long requests = connectionRequests.get();
        return requests > 0 ? (double) connectionWaitTime.get() / requests : 0;
    }

    /**
     * Gets the number of connection timeouts.
     *
     * @return The number of connection timeouts
     */
    public long getConnectionTimeouts() {
        return connectionTimeouts.get();
    }

    /**
     * Gets the maximum number of connections.
     *
     * @return The maximum number of connections
     */
    public int getMaxConnections() {
        return maxConnections;
    }

    /**
     * Gets the connection timeout.
     *
     * @return The connection timeout in milliseconds
     */
    public long getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    /**
     * Gets a summary of the connection pool statistics.
     *
     * @return A string containing the statistics
     */
    public String getStatistics() {
        return "ConnectionPool{" +
                "maxConnections=" + maxConnections +
                ", activeConnections=" + activeConnections.get() +
                ", totalConnections=" + totalConnections.get() +
                ", wait time=" + String.format("%.2f", getAverageWaitTime()) + "ms" +
                ", connectionTimeouts=" + connectionTimeouts.get() +
                '}';
    }
}
