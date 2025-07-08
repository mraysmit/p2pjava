package dev.mars.p2pjava.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.Map;

/**
 * Comprehensive demonstration of improved thread management capabilities
 * including CompletableFuture usage, monitoring, and proper shutdown procedures.
 */
public class ThreadManagementDemo {
    private static final Logger logger = Logger.getLogger(ThreadManagementDemo.class.getName());
    
    public static void main(String[] args) {
        ThreadManagementDemo demo = new ThreadManagementDemo();
        
        try {
            demo.runDemo();
        } catch (Exception e) {
            logger.severe("Demo failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void runDemo() throws Exception {
        logger.info("=== Thread Management Improvements Demo ===");
        
        // 1. Start thread monitoring
        demonstrateThreadMonitoring();
        
        // 2. Demonstrate CompletableFuture chains
        demonstrateAsyncChains();
        
        // 3. Demonstrate parallel execution
        demonstrateParallelExecution();
        
        // 4. Demonstrate server with async patterns
        demonstrateAsyncServer();
        
        // 5. Demonstrate proper shutdown
        demonstrateGracefulShutdown();
        
        logger.info("=== Demo completed successfully ===");
    }
    
    /**
     * Demonstrates thread monitoring capabilities.
     */
    private void demonstrateThreadMonitoring() {
        logger.info("\n--- Thread Monitoring Demo ---");
        
        // Start monitoring
        ThreadMonitor.startMonitoring();
        
        // Create some thread pools
        ThreadManager.getCachedThreadPool("DemoPool1", "Demo1");
        ThreadManager.getFixedThreadPool("DemoPool2", "Demo2", 5);
        ThreadManager.getScheduledThreadPool("DemoPool3", "Demo3", 2);
        
        // Log current status
        ThreadManager.logThreadPoolStatus();
        
        // Get system thread info
        SystemThreadInfo systemInfo = ThreadMonitor.getSystemThreadInfo();
        logger.info("System threads: " + systemInfo);
        
        // Force a monitoring check
        ThreadMonitor.forceMonitoringCheck();
    }
    
    /**
     * Demonstrates CompletableFuture sequential chains.
     */
    private void demonstrateAsyncChains() throws Exception {
        logger.info("\n--- Async Chain Demo ---");
        
        // Example: Peer startup sequence
        CompletableFuture<String> peerStartup = AsyncOperationManager.executeSequentialChain(
            "DemoPool1",
            // First: Initialize
            () -> {
                logger.info("Step 1: Initializing peer...");
                simulateWork(1000);
                return "initialized";
            },
            // Second: Register with tracker
            (initResult) -> {
                logger.info("Step 2: Registering with tracker...");
                simulateWork(2000);
                return "registered";
            },
            // Third: Start heartbeat
            (regResult) -> {
                logger.info("Step 3: Starting heartbeat...");
                simulateWork(500);
                return "heartbeat_started";
            },
            "PeerStartupDemo"
        );
        
        String result = peerStartup.get();
        logger.info("Peer startup completed: " + result);
    }
    
    /**
     * Demonstrates parallel execution.
     */
    private void demonstrateParallelExecution() throws Exception {
        logger.info("\n--- Parallel Execution Demo ---");
        
        // Execute multiple operations in parallel
        CompletableFuture<java.util.List<String>> parallelOps = AsyncOperationManager.executeParallel(
            "DemoPool2",
            "ParallelDemo",
            () -> {
                logger.info("Parallel task 1 starting");
                simulateWork(1500);
                return "Task1 completed";
            },
            () -> {
                logger.info("Parallel task 2 starting");
                simulateWork(1000);
                return "Task2 completed";
            },
            () -> {
                logger.info("Parallel task 3 starting");
                simulateWork(2000);
                return "Task3 completed";
            }
        );

        java.util.List<String> results = parallelOps.get();
        logger.info("Parallel execution completed:");
        for (String res : results) {
            logger.info("  - " + res);
        }
    }
    
    /**
     * Demonstrates async server with monitoring.
     */
    private void demonstrateAsyncServer() throws Exception {
        logger.info("\n--- Async Server Demo ---");
        
        AsyncServerExample server = new AsyncServerExample(9999, "DemoServer");
        
        // Start server asynchronously
        CompletableFuture<Void> serverStart = server.startAsync();
        
        // Wait a bit for server to start
        Thread.sleep(2000);
        
        // Check server status
        logger.info("Server running: " + server.isRunning());
        
        // Get monitoring info
        ThreadPoolMonitorInfo monitorInfo = server.getMonitoringInfo();
        if (monitorInfo != null) {
            logger.info("Server pool info: " + monitorInfo);
        }
        
        // Stop server
        server.stopAsync().get();
        logger.info("Server stopped");
    }
    
    /**
     * Demonstrates proper shutdown procedures.
     */
    private void demonstrateGracefulShutdown() {
        logger.info("\n--- Graceful Shutdown Demo ---");
        
        // Show current thread pool status
        Map<String, ThreadPoolMonitorInfo> allPools = ThreadManager.getAllThreadPoolInfo();
        logger.info("Active thread pools before shutdown: " + allPools.size());
        
        for (Map.Entry<String, ThreadPoolMonitorInfo> entry : allPools.entrySet()) {
            ThreadPoolMonitorInfo info = entry.getValue();
            logger.info("  " + info.getPoolName() + ": " + 
                       info.getActiveCount() + " active, " + 
                       info.getQueueSize() + " queued");
        }
        
        // Shutdown specific pools
        logger.info("Shutting down demo pools...");
        ThreadManager.shutdownThreadPool("DemoPool1");
        ThreadManager.shutdownThreadPool("DemoPool2");
        ThreadManager.shutdownThreadPool("DemoPool3");
        
        // Stop monitoring
        ThreadMonitor.stopMonitoring();
        
        // Show final status
        int remainingPools = ThreadManager.getActiveThreadPoolCount();
        logger.info("Remaining active thread pools: " + remainingPools);
        
        // Final monitoring summary
        ThreadManager.logThreadPoolStatus();
    }
    
    /**
     * Simulates work by sleeping for the specified duration.
     */
    private void simulateWork(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Work simulation interrupted", e);
        }
    }
    
    /**
     * Demonstrates retry logic with exponential backoff.
     */
    private void demonstrateRetryLogic() throws Exception {
        logger.info("\n--- Retry Logic Demo ---");
        
        // Simulate an operation that fails a few times then succeeds
        CompletableFuture<String> retryOperation = AsyncOperationManager.executeWithRetry(
            "DemoPool1",
            () -> {
                // Simulate random failure
                if (Math.random() < 0.7) { // 70% chance of failure
                    throw new RuntimeException("Simulated failure");
                }
                return "Operation succeeded";
            },
            3, // max retries
            1000, // initial delay
            "RetryDemo"
        );
        
        try {
            String result = retryOperation.get();
            logger.info("Retry operation result: " + result);
        } catch (Exception e) {
            logger.warning("Retry operation failed: " + e.getMessage());
        }
    }
    
    /**
     * Demonstrates timeout handling.
     */
    private void demonstrateTimeoutHandling() throws Exception {
        logger.info("\n--- Timeout Handling Demo ---");
        
        CompletableFuture<String> timeoutOperation = AsyncOperationManager.executeWithTimeout(
            "DemoPool1",
            () -> {
                // Simulate long-running operation
                simulateWork(5000);
                return "Long operation completed";
            },
            2000, // 2 second timeout
            "TimeoutDemo"
        );
        
        try {
            String result = timeoutOperation.get();
            logger.info("Timeout operation result: " + result);
        } catch (Exception e) {
            logger.warning("Timeout operation failed: " + e.getMessage());
        }
    }
}
