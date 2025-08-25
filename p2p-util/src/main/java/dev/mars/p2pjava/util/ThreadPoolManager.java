package dev.mars.p2pjava.util;

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


import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Instance-based thread pool manager that replaces the static ThreadManager.
 * Provides proper lifecycle management and dependency injection support.
 */
public class ThreadPoolManager {
    private static final Logger logger = Logger.getLogger(ThreadPoolManager.class.getName());
    
    // Thread pool registry for this instance
    private final Map<String, ExecutorService> threadPools = new ConcurrentHashMap<>();
    private final Map<String, ScheduledExecutorService> scheduledPools = new ConcurrentHashMap<>();
    
    // Configuration
    private final int defaultCorePoolSize;
    private final int defaultMaxPoolSize;
    private final long defaultKeepAliveTime;
    private final TimeUnit defaultTimeUnit;
    
    // Shutdown management
    private volatile boolean shutdown = false;
    
    /**
     * Creates a ThreadPoolManager with default configuration.
     */
    public ThreadPoolManager() {
        this(10, 50, 60L, TimeUnit.SECONDS);
    }
    
    /**
     * Creates a ThreadPoolManager with custom configuration.
     */
    public ThreadPoolManager(int defaultCorePoolSize, int defaultMaxPoolSize, 
                           long defaultKeepAliveTime, TimeUnit defaultTimeUnit) {
        this.defaultCorePoolSize = defaultCorePoolSize;
        this.defaultMaxPoolSize = defaultMaxPoolSize;
        this.defaultKeepAliveTime = defaultKeepAliveTime;
        this.defaultTimeUnit = defaultTimeUnit;
        
        logger.info("ThreadPoolManager created with defaults: core=" + defaultCorePoolSize + 
                   ", max=" + defaultMaxPoolSize + ", keepAlive=" + defaultKeepAliveTime + " " + defaultTimeUnit);
    }
    
    /**
     * Creates or returns a cached thread pool with the given name.
     */
    public ExecutorService getCachedThreadPool(String poolName, String threadNamePrefix) {
        if (shutdown) {
            throw new IllegalStateException("ThreadPoolManager has been shutdown");
        }
        
        return threadPools.computeIfAbsent(poolName, k -> {
            ExecutorService pool = Executors.newCachedThreadPool(createThreadFactory(threadNamePrefix));
            logger.info("Created cached thread pool: " + poolName + " with prefix: " + threadNamePrefix);
            return pool;
        });
    }
    
    /**
     * Creates or returns a fixed thread pool with the given name and size.
     */
    public ExecutorService getFixedThreadPool(String poolName, String threadNamePrefix, int poolSize) {
        if (shutdown) {
            throw new IllegalStateException("ThreadPoolManager has been shutdown");
        }
        
        return threadPools.computeIfAbsent(poolName, k -> {
            ExecutorService pool = Executors.newFixedThreadPool(poolSize, createThreadFactory(threadNamePrefix));
            logger.info("Created fixed thread pool: " + poolName + " with size: " + poolSize + 
                       " and prefix: " + threadNamePrefix);
            return pool;
        });
    }
    
    /**
     * Creates or returns a scheduled thread pool with the given name and size.
     */
    public ScheduledExecutorService getScheduledThreadPool(String poolName, String threadNamePrefix, int poolSize) {
        if (shutdown) {
            throw new IllegalStateException("ThreadPoolManager has been shutdown");
        }
        
        return scheduledPools.computeIfAbsent(poolName, k -> {
            ScheduledExecutorService pool = Executors.newScheduledThreadPool(poolSize, createThreadFactory(threadNamePrefix));
            logger.info("Created scheduled thread pool: " + poolName + " with size: " + poolSize + 
                       " and prefix: " + threadNamePrefix);
            return pool;
        });
    }
    
    /**
     * Creates a custom thread pool executor.
     */
    public ThreadPoolExecutor createCustomThreadPool(String poolName, String threadNamePrefix,
                                                    int corePoolSize, int maximumPoolSize,
                                                    long keepAliveTime, TimeUnit unit,
                                                    BlockingQueue<Runnable> workQueue) {
        if (shutdown) {
            throw new IllegalStateException("ThreadPoolManager has been shutdown");
        }
        
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, 
            createThreadFactory(threadNamePrefix)
        );
        
        threadPools.put(poolName, executor);
        logger.info("Created custom thread pool: " + poolName + " with core=" + corePoolSize + 
                   ", max=" + maximumPoolSize + ", keepAlive=" + keepAliveTime + " " + unit);
        
        return executor;
    }
    
    /**
     * Shuts down a specific thread pool.
     */
    public boolean shutdownThreadPool(String poolName) {
        return shutdownThreadPool(poolName, 30, TimeUnit.SECONDS);
    }
    
    /**
     * Shuts down a specific thread pool with timeout.
     */
    public boolean shutdownThreadPool(String poolName, long timeout, TimeUnit unit) {
        ExecutorService pool = threadPools.remove(poolName);
        ScheduledExecutorService scheduledPool = scheduledPools.remove(poolName);
        
        boolean success = true;
        
        if (pool != null) {
            success &= shutdownExecutor(pool, poolName, timeout, unit);
        }
        
        if (scheduledPool != null) {
            success &= shutdownExecutor(scheduledPool, poolName, timeout, unit);
        }
        
        if (pool == null && scheduledPool == null) {
            logger.warning("Thread pool not found: " + poolName);
            return false;
        }
        
        return success;
    }
    
    /**
     * Shuts down all thread pools managed by this instance.
     */
    public void shutdownAll() {
        shutdownAll(30, TimeUnit.SECONDS);
    }
    
    /**
     * Shuts down all thread pools with timeout.
     */
    public void shutdownAll(long timeout, TimeUnit unit) {
        if (shutdown) {
            return;
        }
        
        shutdown = true;
        logger.info("Shutting down all thread pools...");
        
        // Shutdown all regular thread pools
        for (Map.Entry<String, ExecutorService> entry : threadPools.entrySet()) {
            shutdownExecutor(entry.getValue(), entry.getKey(), timeout, unit);
        }
        
        // Shutdown all scheduled thread pools
        for (Map.Entry<String, ScheduledExecutorService> entry : scheduledPools.entrySet()) {
            shutdownExecutor(entry.getValue(), entry.getKey(), timeout, unit);
        }
        
        threadPools.clear();
        scheduledPools.clear();
        
        logger.info("All thread pools shut down");
    }
    
    /**
     * Gets the status of all thread pools.
     */
    public ThreadPoolStatus getStatus() {
        return new ThreadPoolStatus(threadPools, scheduledPools);
    }
    
    /**
     * Logs the status of all thread pools.
     */
    public void logStatus() {
        ThreadPoolStatus status = getStatus();
        logger.info("Thread Pool Status: " + status);
    }
    
    /**
     * Checks if this manager has been shutdown.
     */
    public boolean isShutdown() {
        return shutdown;
    }
    
    /**
     * Gets the number of active thread pools.
     */
    public int getActivePoolCount() {
        return threadPools.size() + scheduledPools.size();
    }
    
    // Helper methods
    
    private ThreadFactory createThreadFactory(String threadNamePrefix) {
        return new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, threadNamePrefix + "-" + threadNumber.getAndIncrement());
                t.setDaemon(false);
                t.setPriority(Thread.NORM_PRIORITY);
                return t;
            }
        };
    }
    
    private boolean shutdownExecutor(ExecutorService executor, String poolName, long timeout, TimeUnit unit) {
        try {
            logger.info("Shutting down thread pool: " + poolName);
            executor.shutdown();
            
            if (!executor.awaitTermination(timeout, unit)) {
                logger.warning("Thread pool " + poolName + " did not terminate gracefully, forcing shutdown");
                executor.shutdownNow();
                
                if (!executor.awaitTermination(timeout, unit)) {
                    logger.severe("Thread pool " + poolName + " did not terminate after forced shutdown");
                    return false;
                }
            }
            
            logger.info("Thread pool " + poolName + " shut down successfully");
            return true;
            
        } catch (InterruptedException e) {
            logger.warning("Interrupted while shutting down thread pool: " + poolName);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            return false;
        }
    }
    
    /**
     * Thread pool status information.
     */
    public static class ThreadPoolStatus {
        private final int regularPoolCount;
        private final int scheduledPoolCount;
        private final int totalPoolCount;
        
        ThreadPoolStatus(Map<String, ExecutorService> threadPools, 
                        Map<String, ScheduledExecutorService> scheduledPools) {
            this.regularPoolCount = threadPools.size();
            this.scheduledPoolCount = scheduledPools.size();
            this.totalPoolCount = regularPoolCount + scheduledPoolCount;
        }
        
        public int getRegularPoolCount() { return regularPoolCount; }
        public int getScheduledPoolCount() { return scheduledPoolCount; }
        public int getTotalPoolCount() { return totalPoolCount; }
        
        @Override
        public String toString() {
            return "ThreadPoolStatus{regular=" + regularPoolCount + 
                   ", scheduled=" + scheduledPoolCount + 
                   ", total=" + totalPoolCount + "}";
        }
    }
}
