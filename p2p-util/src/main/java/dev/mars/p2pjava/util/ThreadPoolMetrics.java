package dev.mars.p2pjava.util;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe metrics collection for individual thread pools.
 */
public class ThreadPoolMetrics {
    private final AtomicLong totalTasks = new AtomicLong(0);
    private final AtomicLong failedTasks = new AtomicLong(0);
    private final AtomicLong totalExecutionTime = new AtomicLong(0);
    private final AtomicLong minExecutionTime = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxExecutionTime = new AtomicLong(0);
    private final AtomicReference<Long> lastTaskCompletionTime = new AtomicReference<>(System.currentTimeMillis());
    
    // Time window for calculating recent metrics (5 minutes)
    private static final long RECENT_WINDOW_MS = 5 * 60 * 1000;
    
    /**
     * Records the execution of a task.
     */
    public void recordTask(long executionTimeMs, boolean failed) {
        totalTasks.incrementAndGet();
        if (failed) {
            failedTasks.incrementAndGet();
        }
        
        totalExecutionTime.addAndGet(executionTimeMs);
        lastTaskCompletionTime.set(System.currentTimeMillis());
        
        // Update min/max execution times
        updateMinExecutionTime(executionTimeMs);
        updateMaxExecutionTime(executionTimeMs);
    }
    
    private void updateMinExecutionTime(long executionTimeMs) {
        long current = minExecutionTime.get();
        while (executionTimeMs < current && !minExecutionTime.compareAndSet(current, executionTimeMs)) {
            current = minExecutionTime.get();
        }
    }
    
    private void updateMaxExecutionTime(long executionTimeMs) {
        long current = maxExecutionTime.get();
        while (executionTimeMs > current && !maxExecutionTime.compareAndSet(current, executionTimeMs)) {
            current = maxExecutionTime.get();
        }
    }
    
    /**
     * Gets the total number of tasks executed.
     */
    public long getTotalTasks() {
        return totalTasks.get();
    }
    
    /**
     * Gets the number of failed tasks.
     */
    public long getFailedTasks() {
        return failedTasks.get();
    }
    
    /**
     * Gets the failure rate as a percentage.
     */
    public double getFailureRate() {
        long total = totalTasks.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) failedTasks.get() / total * 100.0;
    }
    
    /**
     * Gets the average execution time in milliseconds.
     */
    public double getAverageExecutionTime() {
        long total = totalTasks.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) totalExecutionTime.get() / total;
    }
    
    /**
     * Gets the minimum execution time in milliseconds.
     */
    public long getMinExecutionTime() {
        long min = minExecutionTime.get();
        return min == Long.MAX_VALUE ? 0 : min;
    }
    
    /**
     * Gets the maximum execution time in milliseconds.
     */
    public long getMaxExecutionTime() {
        return maxExecutionTime.get();
    }
    
    /**
     * Gets the time of the last task completion.
     */
    public long getLastTaskCompletionTime() {
        return lastTaskCompletionTime.get();
    }
    
    /**
     * Checks if the thread pool appears to be stuck (no recent task completions).
     */
    public boolean isStuck() {
        long lastCompletion = lastTaskCompletionTime.get();
        long timeSinceLastTask = System.currentTimeMillis() - lastCompletion;
        
        // Consider stuck if no tasks completed in the last 5 minutes and we have tasks
        return timeSinceLastTask > RECENT_WINDOW_MS && totalTasks.get() > 0;
    }
    
    /**
     * Resets all metrics (useful for testing or periodic resets).
     */
    public void reset() {
        totalTasks.set(0);
        failedTasks.set(0);
        totalExecutionTime.set(0);
        minExecutionTime.set(Long.MAX_VALUE);
        maxExecutionTime.set(0);
        lastTaskCompletionTime.set(System.currentTimeMillis());
    }
    
    @Override
    public String toString() {
        return String.format(
            "ThreadPoolMetrics{total=%d, failed=%d, failureRate=%.2f%%, " +
            "avgTime=%.2fms, minTime=%dms, maxTime=%dms, lastCompletion=%d}",
            getTotalTasks(),
            getFailedTasks(),
            getFailureRate(),
            getAverageExecutionTime(),
            getMinExecutionTime(),
            getMaxExecutionTime(),
            getLastTaskCompletionTime()
        );
    }
}
