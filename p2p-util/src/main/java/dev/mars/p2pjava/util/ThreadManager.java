package dev.mars.p2pjava.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ThreadManager provides a centralized way to manage thread pools across the P2P-Java application.
 * It offers standardized thread pools for different use cases and ensures proper lifecycle management.
 */
public class ThreadManager {
    private static final Logger logger = Logger.getLogger(ThreadManager.class.getName());
    
    // Default thread pool sizes
    private static final int DEFAULT_FIXED_POOL_SIZE = 10;
    private static final int DEFAULT_SCHEDULED_POOL_SIZE = 2;
    
    // Default shutdown parameters
    private static final long DEFAULT_SHUTDOWN_TIMEOUT = 5;
    private static final TimeUnit DEFAULT_SHUTDOWN_TIMEUNIT = TimeUnit.SECONDS;
    
    // Thread pool registry to keep track of all created thread pools
    private static final Map<String, ExecutorService> threadPools = new ConcurrentHashMap<>();
    
    /**
     * Creates or returns a cached thread pool with the given name.
     * Suitable for handling short-lived tasks with variable concurrency needs.
     *
     * @param poolName The name of the thread pool
     * @param threadNamePrefix The prefix for thread names in this pool
     * @return The cached thread pool
     */
    public static ExecutorService getCachedThreadPool(String poolName, String threadNamePrefix) {
        return threadPools.computeIfAbsent(poolName, k -> 
            Executors.newCachedThreadPool(createThreadFactory(threadNamePrefix))
        );
    }
    
    /**
     * Creates or returns a fixed thread pool with the given name and size.
     * Suitable for handling a fixed number of concurrent tasks.
     *
     * @param poolName The name of the thread pool
     * @param threadNamePrefix The prefix for thread names in this pool
     * @param poolSize The size of the thread pool
     * @return The fixed thread pool
     */
    public static ExecutorService getFixedThreadPool(String poolName, String threadNamePrefix, int poolSize) {
        return threadPools.computeIfAbsent(poolName, k -> 
            Executors.newFixedThreadPool(poolSize, createThreadFactory(threadNamePrefix))
        );
    }
    
    /**
     * Creates or returns a fixed thread pool with the default size.
     *
     * @param poolName The name of the thread pool
     * @param threadNamePrefix The prefix for thread names in this pool
     * @return The fixed thread pool
     */
    public static ExecutorService getFixedThreadPool(String poolName, String threadNamePrefix) {
        return getFixedThreadPool(poolName, threadNamePrefix, DEFAULT_FIXED_POOL_SIZE);
    }
    
    /**
     * Creates or returns a single-threaded executor with the given name.
     * Suitable for tasks that must be executed sequentially.
     *
     * @param poolName The name of the thread pool
     * @param threadNamePrefix The prefix for thread names in this pool
     * @return The single-threaded executor
     */
    public static ExecutorService getSingleThreadExecutor(String poolName, String threadNamePrefix) {
        return threadPools.computeIfAbsent(poolName, k -> 
            Executors.newSingleThreadExecutor(createThreadFactory(threadNamePrefix))
        );
    }
    
    /**
     * Creates or returns a scheduled thread pool with the given name and size.
     * Suitable for tasks that need to be executed periodically or with a delay.
     *
     * @param poolName The name of the thread pool
     * @param threadNamePrefix The prefix for thread names in this pool
     * @param poolSize The size of the thread pool
     * @return The scheduled thread pool
     */
    public static ScheduledExecutorService getScheduledThreadPool(String poolName, String threadNamePrefix, int poolSize) {
        return (ScheduledExecutorService) threadPools.computeIfAbsent(poolName, k -> 
            Executors.newScheduledThreadPool(poolSize, createThreadFactory(threadNamePrefix))
        );
    }
    
    /**
     * Creates or returns a scheduled thread pool with the default size.
     *
     * @param poolName The name of the thread pool
     * @param threadNamePrefix The prefix for thread names in this pool
     * @return The scheduled thread pool
     */
    public static ScheduledExecutorService getScheduledThreadPool(String poolName, String threadNamePrefix) {
        return getScheduledThreadPool(poolName, threadNamePrefix, DEFAULT_SCHEDULED_POOL_SIZE);
    }
    
    /**
     * Creates or returns a single-threaded scheduled executor with the given name.
     * Suitable for tasks that need to be executed periodically or with a delay, in a sequential manner.
     *
     * @param poolName The name of the thread pool
     * @param threadNamePrefix The prefix for thread names in this pool
     * @return The single-threaded scheduled executor
     */
    public static ScheduledExecutorService getSingleThreadScheduledExecutor(String poolName, String threadNamePrefix) {
        return (ScheduledExecutorService) threadPools.computeIfAbsent(poolName, k -> 
            Executors.newSingleThreadScheduledExecutor(createThreadFactory(threadNamePrefix))
        );
    }
    
    /**
     * Creates a thread factory with the given thread name prefix.
     * All threads created by this factory will be daemon threads.
     *
     * @param threadNamePrefix The prefix for thread names
     * @return The thread factory
     */
    private static ThreadFactory createThreadFactory(String threadNamePrefix) {
        return r -> {
            Thread t = new Thread(r, threadNamePrefix + "-" + UUID.randomUUID().toString().substring(0, 8));
            t.setDaemon(true);
            return t;
        };
    }
    
    /**
     * Shuts down a specific thread pool.
     *
     * @param poolName The name of the thread pool to shut down
     * @param timeout The timeout for shutdown
     * @param unit The time unit for the timeout
     * @return true if the pool was shut down successfully, false otherwise
     */
    public static boolean shutdownThreadPool(String poolName, long timeout, TimeUnit unit) {
        ExecutorService pool = threadPools.get(poolName);
        if (pool == null) {
            logger.warning("Thread pool not found: " + poolName);
            return false;
        }
        
        try {
            // Initiate orderly shutdown
            pool.shutdown();
            
            // Wait for tasks to complete
            if (!pool.awaitTermination(timeout, unit)) {
                // Force shutdown if tasks don't terminate
                pool.shutdownNow();
                
                // Wait again for tasks to respond to being cancelled
                if (!pool.awaitTermination(timeout, unit)) {
                    logger.warning("Thread pool did not terminate: " + poolName);
                    return false;
                }
            }
            
            // Remove from registry
            threadPools.remove(poolName);
            logger.info("Thread pool shut down successfully: " + poolName);
            return true;
        } catch (InterruptedException e) {
            // (Re-)Cancel if current thread also interrupted
            pool.shutdownNow();
            Thread.currentThread().interrupt();
            logger.log(Level.WARNING, "Shutdown interrupted for pool: " + poolName, e);
            return false;
        }
    }
    
    /**
     * Shuts down a specific thread pool with default timeout.
     *
     * @param poolName The name of the thread pool to shut down
     * @return true if the pool was shut down successfully, false otherwise
     */
    public static boolean shutdownThreadPool(String poolName) {
        return shutdownThreadPool(poolName, DEFAULT_SHUTDOWN_TIMEOUT, DEFAULT_SHUTDOWN_TIMEUNIT);
    }
    
    /**
     * Shuts down all thread pools managed by this class.
     */
    public static void shutdownAllThreadPools() {
        logger.info("Shutting down all thread pools...");
        
        // Make a copy of the keys to avoid ConcurrentModificationException
        String[] poolNames = threadPools.keySet().toArray(new String[0]);
        
        for (String poolName : poolNames) {
            shutdownThreadPool(poolName);
        }
        
        logger.info("All thread pools shut down");
    }
    
    /**
     * Returns the number of active thread pools.
     *
     * @return The number of active thread pools
     */
    public static int getActiveThreadPoolCount() {
        return threadPools.size();
    }
    
    /**
     * Registers a JVM shutdown hook to ensure all thread pools are properly shut down.
     */
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("JVM shutdown detected, shutting down all thread pools...");
            shutdownAllThreadPools();
        }));
        
        logger.info("ThreadManager initialized with shutdown hook");
    }
}