package dev.mars.p2pjava.storage;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics for tracking index server performance and reliability.
 */
public class IndexServerMetrics {
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong totalResponseTime = new AtomicLong(0);
    private final AtomicLong lastRequestTime = new AtomicLong(0);
    private final AtomicLong consecutiveFailures = new AtomicLong(0);
    
    // Moving averages
    private volatile double successRateAverage = 1.0;
    private volatile double responseTimeAverage = 0.0;
    private final double alpha = 0.1; // Smoothing factor
    
    /**
     * Records the result of a request to this index server.
     */
    public void recordRequest(boolean success, long responseTime) {
        long requests = totalRequests.incrementAndGet();
        lastRequestTime.set(System.currentTimeMillis());
        
        if (success) {
            long successful = successfulRequests.incrementAndGet();
            totalResponseTime.addAndGet(responseTime);
            consecutiveFailures.set(0);
            
            // Update moving averages
            double currentSuccessRate = (double) successful / requests;
            successRateAverage = alpha * currentSuccessRate + (1 - alpha) * successRateAverage;
            
            double currentResponseTime = (double) totalResponseTime.get() / successful;
            responseTimeAverage = alpha * currentResponseTime + (1 - alpha) * responseTimeAverage;
            
        } else {
            consecutiveFailures.incrementAndGet();
            
            // Decrease success rate average on failure
            double currentSuccessRate = (double) successfulRequests.get() / requests;
            successRateAverage = alpha * currentSuccessRate + (1 - alpha) * successRateAverage;
        }
    }
    
    /**
     * Gets the success rate (0.0 to 1.0).
     */
    public double getSuccessRate() {
        long total = totalRequests.get();
        if (total == 0) return 1.0;
        return successRateAverage;
    }
    
    /**
     * Gets the average response time in milliseconds.
     */
    public long getAverageResponseTime() {
        long successful = successfulRequests.get();
        if (successful == 0) return Long.MAX_VALUE;
        return (long) responseTimeAverage;
    }
    
    /**
     * Gets the number of consecutive failures.
     */
    public long getConsecutiveFailures() {
        return consecutiveFailures.get();
    }
    
    /**
     * Gets the total number of requests.
     */
    public long getTotalRequests() {
        return totalRequests.get();
    }
    
    /**
     * Gets the timestamp of the last request.
     */
    public long getLastRequestTime() {
        return lastRequestTime.get();
    }
    
    /**
     * Checks if this server is considered healthy.
     */
    public boolean isHealthy() {
        return consecutiveFailures.get() <= 3 && getSuccessRate() >= 0.5;
    }
    
    /**
     * Calculates a reliability score (0.0 to 1.0).
     */
    public double getReliabilityScore() {
        double successRate = getSuccessRate();
        long avgResponseTime = getAverageResponseTime();
        long consecutiveFails = getConsecutiveFailures();
        
        // Base score from success rate
        double score = successRate;
        
        // Penalize high response times
        if (avgResponseTime != Long.MAX_VALUE) {
            double responseTimePenalty = Math.min(1.0, avgResponseTime / 5000.0); // 5 second baseline
            score *= (1.0 - responseTimePenalty * 0.3);
        }
        
        // Penalize consecutive failures
        double failurePenalty = Math.min(1.0, consecutiveFails / 5.0);
        score *= (1.0 - failurePenalty * 0.5);
        
        return Math.max(0.0, Math.min(1.0, score));
    }
    
    @Override
    public String toString() {
        return String.format("IndexServerMetrics{successRate=%.2f, avgResponseTime=%dms, consecutiveFailures=%d, totalRequests=%d}",
                getSuccessRate(), getAverageResponseTime(), getConsecutiveFailures(), getTotalRequests());
    }
}
