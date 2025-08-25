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


import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ThreadMonitor provides comprehensive monitoring of thread pools and system threads.
 * It tracks performance metrics, detects potential issues, and provides alerts.
 */
public class ThreadMonitor {
    private static final Logger logger = Logger.getLogger(ThreadMonitor.class.getName());
    
    private static final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    private static final Map<String, ThreadPoolMetrics> poolMetrics = new ConcurrentHashMap<>();
    private static final AtomicLong totalTasksExecuted = new AtomicLong(0);
    private static final AtomicLong totalTasksFailed = new AtomicLong(0);
    
    private static ScheduledExecutorService monitoringExecutor;
    private static volatile boolean monitoring = false;
    
    // Monitoring configuration
    private static final long MONITORING_INTERVAL_SECONDS = 30;
    private static final double HIGH_UTILIZATION_THRESHOLD = 80.0;
    private static final int LARGE_QUEUE_THRESHOLD = 100;
    
    /**
     * Starts the thread monitoring service.
     */
    public static synchronized void startMonitoring() {
        if (monitoring) {
            logger.warning("Thread monitoring already started");
            return;
        }
        
        logger.info("Starting thread monitoring service");
        monitoring = true;
        
        monitoringExecutor = ThreadManager.getSingleThreadScheduledExecutor(
            "ThreadMonitor", 
            "ThreadMonitor"
        );
        
        // Schedule periodic monitoring
        monitoringExecutor.scheduleAtFixedRate(
            ThreadMonitor::performMonitoringCheck,
            MONITORING_INTERVAL_SECONDS,
            MONITORING_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
        
        // Schedule periodic cleanup of old metrics
        monitoringExecutor.scheduleAtFixedRate(
            ThreadMonitor::cleanupOldMetrics,
            300, // 5 minutes
            300, // 5 minutes
            TimeUnit.SECONDS
        );
    }
    
    /**
     * Stops the thread monitoring service.
     */
    public static synchronized void stopMonitoring() {
        if (!monitoring) {
            return;
        }
        
        logger.info("Stopping thread monitoring service");
        monitoring = false;
        
        if (monitoringExecutor != null) {
            ThreadManager.shutdownThreadPool("ThreadMonitor");
            monitoringExecutor = null;
        }
    }
    
    /**
     * Records task execution metrics for a thread pool.
     */
    public static void recordTaskExecution(String poolName, long executionTimeMs, boolean failed) {
        ThreadPoolMetrics metrics = poolMetrics.computeIfAbsent(poolName, k -> new ThreadPoolMetrics());
        metrics.recordTask(executionTimeMs, failed);
        
        totalTasksExecuted.incrementAndGet();
        if (failed) {
            totalTasksFailed.incrementAndGet();
        }
    }
    
    /**
     * Gets current system thread information.
     */
    public static SystemThreadInfo getSystemThreadInfo() {
        return new SystemThreadInfo(
            threadMXBean.getThreadCount(),
            threadMXBean.getPeakThreadCount(),
            threadMXBean.getDaemonThreadCount(),
            threadMXBean.getTotalStartedThreadCount()
        );
    }
    
    /**
     * Gets metrics for a specific thread pool.
     */
    public static ThreadPoolMetrics getPoolMetrics(String poolName) {
        return poolMetrics.get(poolName);
    }
    
    /**
     * Gets all thread pool metrics.
     */
    public static Map<String, ThreadPoolMetrics> getAllPoolMetrics() {
        return new ConcurrentHashMap<>(poolMetrics);
    }
    
    /**
     * Performs a comprehensive monitoring check.
     */
    private static void performMonitoringCheck() {
        try {
            logger.fine("Performing thread monitoring check");
            
            // Check system threads
            SystemThreadInfo systemInfo = getSystemThreadInfo();
            if (systemInfo.getCurrentThreadCount() > 200) {
                logger.warning("High system thread count detected: " + systemInfo.getCurrentThreadCount());
            }
            
            // Check thread pool status
            Map<String, ThreadPoolMonitorInfo> poolInfos = ThreadManager.getAllThreadPoolInfo();
            for (Map.Entry<String, ThreadPoolMonitorInfo> entry : poolInfos.entrySet()) {
                ThreadPoolMonitorInfo info = entry.getValue();
                checkPoolHealth(info);
            }
            
            // Log summary if needed
            if (logger.isLoggable(java.util.logging.Level.FINE)) {
                logMonitoringSummary();
            }
            
        } catch (Exception e) {
            logger.warning("Error during monitoring check: " + e.getMessage());
        }
    }
    
    /**
     * Checks the health of a specific thread pool.
     */
    private static void checkPoolHealth(ThreadPoolMonitorInfo info) {
        String poolName = info.getPoolName();
        
        // Check utilization
        if (info.isHighLoad()) {
            logger.warning(String.format(
                "High load detected in pool '%s': utilization=%.1f%%, queue=%d",
                poolName, info.getUtilizationPercentage(), info.getQueueSize()
            ));
        }
        
        // Check queue size
        if (info.getQueueSize() > LARGE_QUEUE_THRESHOLD) {
            logger.warning(String.format(
                "Large queue detected in pool '%s': %d tasks queued",
                poolName, info.getQueueSize()
            ));
        }
        
        // Check if pool is stuck (no completed tasks in a while)
        ThreadPoolMetrics metrics = poolMetrics.get(poolName);
        if (metrics != null && metrics.isStuck()) {
            logger.warning(String.format(
                "Pool '%s' appears to be stuck - no task completions recently",
                poolName
            ));
        }
    }
    
    /**
     * Logs a comprehensive monitoring summary.
     */
    private static void logMonitoringSummary() {
        SystemThreadInfo systemInfo = getSystemThreadInfo();
        
        logger.info("=== Thread Monitoring Summary ===");
        logger.info(String.format(
            "System Threads: current=%d, peak=%d, daemon=%d, total_started=%d",
            systemInfo.getCurrentThreadCount(),
            systemInfo.getPeakThreadCount(),
            systemInfo.getDaemonThreadCount(),
            systemInfo.getTotalStartedThreadCount()
        ));
        
        logger.info(String.format(
            "Global Task Stats: executed=%d, failed=%d, failure_rate=%.2f%%",
            totalTasksExecuted.get(),
            totalTasksFailed.get(),
            totalTasksExecuted.get() > 0 ? 
                (double) totalTasksFailed.get() / totalTasksExecuted.get() * 100.0 : 0.0
        ));
        
        // Log thread pool status
        ThreadManager.logThreadPoolStatus();
        
        logger.info("=== End Thread Monitoring Summary ===");
    }
    
    /**
     * Cleans up old metrics to prevent memory leaks.
     */
    private static void cleanupOldMetrics() {
        // Remove metrics for pools that no longer exist
        poolMetrics.entrySet().removeIf(entry -> {
            String poolName = entry.getKey();
            return ThreadManager.getThreadPool(poolName) == null;
        });
    }
    
    /**
     * Gets the current monitoring status.
     */
    public static boolean isMonitoring() {
        return monitoring;
    }
    
    /**
     * Forces an immediate monitoring check.
     */
    public static void forceMonitoringCheck() {
        if (monitoring) {
            performMonitoringCheck();
        } else {
            logger.warning("Cannot perform monitoring check - monitoring is not started");
        }
    }
}
