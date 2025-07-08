package dev.mars.p2pjava.util;

/**
 * Immutable class containing monitoring information for a thread pool.
 * Provides insights into thread pool performance and current state.
 */
public class ThreadPoolMonitorInfo {
    private final String poolName;
    private final int activeCount;
    private final int poolSize;
    private final int maxPoolSize;
    private final long taskCount;
    private final long completedTaskCount;
    private final int queueSize;
    private final boolean running;
    
    public ThreadPoolMonitorInfo(String poolName, int activeCount, int poolSize, 
                                int maxPoolSize, long taskCount, long completedTaskCount, 
                                int queueSize, boolean running) {
        this.poolName = poolName;
        this.activeCount = activeCount;
        this.poolSize = poolSize;
        this.maxPoolSize = maxPoolSize;
        this.taskCount = taskCount;
        this.completedTaskCount = completedTaskCount;
        this.queueSize = queueSize;
        this.running = running;
    }
    
    public String getPoolName() {
        return poolName;
    }
    
    public int getActiveCount() {
        return activeCount;
    }
    
    public int getPoolSize() {
        return poolSize;
    }
    
    public int getMaxPoolSize() {
        return maxPoolSize;
    }
    
    public long getTaskCount() {
        return taskCount;
    }
    
    public long getCompletedTaskCount() {
        return completedTaskCount;
    }
    
    public int getQueueSize() {
        return queueSize;
    }
    
    public boolean isRunning() {
        return running;
    }
    
    /**
     * Calculates the utilization percentage of the thread pool.
     * @return Utilization as a percentage (0-100), or -1 if cannot be calculated
     */
    public double getUtilizationPercentage() {
        if (maxPoolSize <= 0) {
            return -1;
        }
        return (double) activeCount / maxPoolSize * 100.0;
    }
    
    /**
     * Calculates the completion rate of tasks.
     * @return Completion rate as a percentage (0-100), or -1 if cannot be calculated
     */
    public double getCompletionRate() {
        if (taskCount <= 0) {
            return -1;
        }
        return (double) completedTaskCount / taskCount * 100.0;
    }
    
    /**
     * Checks if the thread pool appears to be under high load.
     * @return true if utilization is above 80% or queue size is significant
     */
    public boolean isHighLoad() {
        double utilization = getUtilizationPercentage();
        return (utilization > 80.0) || (queueSize > poolSize * 2);
    }
    
    @Override
    public String toString() {
        return String.format(
            "ThreadPoolMonitorInfo{poolName='%s', active=%d, poolSize=%d, max=%d, " +
            "tasks=%d, completed=%d, queue=%d, running=%s, utilization=%.1f%%, completion=%.1f%%}",
            poolName, activeCount, poolSize, maxPoolSize, taskCount, completedTaskCount, 
            queueSize, running, getUtilizationPercentage(), getCompletionRate()
        );
    }
}
