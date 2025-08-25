package dev.mars.p2pjava.discovery;

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


import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks metrics for individual peers in the gossip network.
 * Used for adaptive peer selection and network optimization.
 */
public class PeerMetrics {
    private final AtomicLong totalOperations = new AtomicLong(0);
    private final AtomicLong successfulOperations = new AtomicLong(0);
    private final AtomicLong totalResponseTime = new AtomicLong(0);
    private final AtomicReference<Long> lastOperationTime = new AtomicReference<>(0L);
    private final AtomicLong consecutiveFailures = new AtomicLong(0);
    
    // Moving averages for better metrics
    private volatile double successRateAverage = 1.0;
    private volatile double responseTimeAverage = 0.0;
    private final double alpha = 0.1; // Smoothing factor for exponential moving average
    
    /**
     * Records the result of an operation with this peer.
     *
     * @param success Whether the operation was successful
     * @param responseTime Response time in milliseconds (for successful operations)
     */
    public void recordOperation(boolean success, long responseTime) {
        long operations = totalOperations.incrementAndGet();
        lastOperationTime.set(System.currentTimeMillis());
        
        if (success) {
            long successful = successfulOperations.incrementAndGet();
            totalResponseTime.addAndGet(responseTime);
            consecutiveFailures.set(0);
            
            // Update moving averages
            double currentSuccessRate = (double) successful / operations;
            successRateAverage = alpha * currentSuccessRate + (1 - alpha) * successRateAverage;
            
            double currentResponseTime = (double) totalResponseTime.get() / successful;
            responseTimeAverage = alpha * currentResponseTime + (1 - alpha) * responseTimeAverage;
            
        } else {
            consecutiveFailures.incrementAndGet();
            
            // Decrease success rate average on failure
            double currentSuccessRate = (double) successfulOperations.get() / operations;
            successRateAverage = alpha * currentSuccessRate + (1 - alpha) * successRateAverage;
        }
    }
    
    /**
     * Gets the success rate (0.0 to 1.0).
     */
    public double getSuccessRate() {
        long total = totalOperations.get();
        if (total == 0) return 1.0; // Assume good until proven otherwise
        
        return successRateAverage;
    }
    
    /**
     * Gets the average response time in milliseconds.
     */
    public long getAverageResponseTime() {
        long successful = successfulOperations.get();
        if (successful == 0) return Long.MAX_VALUE; // Penalize peers with no successful operations
        
        return (long) responseTimeAverage;
    }
    
    /**
     * Gets the number of consecutive failures.
     */
    public long getConsecutiveFailures() {
        return consecutiveFailures.get();
    }
    
    /**
     * Gets the total number of operations.
     */
    public long getTotalOperations() {
        return totalOperations.get();
    }
    
    /**
     * Gets the number of successful operations.
     */
    public long getSuccessfulOperations() {
        return successfulOperations.get();
    }
    
    /**
     * Gets the timestamp of the last operation.
     */
    public long getLastOperationTime() {
        return lastOperationTime.get();
    }
    
    /**
     * Checks if this peer is considered healthy based on metrics.
     */
    public boolean isHealthy() {
        // Consider unhealthy if:
        // - More than 5 consecutive failures
        // - Success rate below 50%
        // - No successful operations and more than 3 total operations
        
        if (consecutiveFailures.get() > 5) {
            return false;
        }
        
        if (getSuccessRate() < 0.5) {
            return false;
        }
        
        if (successfulOperations.get() == 0 && totalOperations.get() > 3) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Calculates a reliability score for peer selection (0.0 to 1.0).
     * Higher scores indicate more reliable peers.
     */
    public double getReliabilityScore() {
        double successRate = getSuccessRate();
        long avgResponseTime = getAverageResponseTime();
        long consecutiveFails = getConsecutiveFailures();
        
        // Base score from success rate
        double score = successRate;
        
        // Penalize high response times (normalize to 0-1000ms range)
        if (avgResponseTime != Long.MAX_VALUE) {
            double responseTimePenalty = Math.min(1.0, avgResponseTime / 1000.0);
            score *= (1.0 - responseTimePenalty * 0.3); // Up to 30% penalty
        }
        
        // Penalize consecutive failures
        double failurePenalty = Math.min(1.0, consecutiveFails / 10.0);
        score *= (1.0 - failurePenalty * 0.5); // Up to 50% penalty
        
        return Math.max(0.0, Math.min(1.0, score));
    }
    
    /**
     * Resets all metrics (useful for testing or peer recovery).
     */
    public void reset() {
        totalOperations.set(0);
        successfulOperations.set(0);
        totalResponseTime.set(0);
        lastOperationTime.set(0L);
        consecutiveFailures.set(0);
        successRateAverage = 1.0;
        responseTimeAverage = 0.0;
    }
    
    @Override
    public String toString() {
        return String.format("PeerMetrics{successRate=%.2f, avgResponseTime=%dms, consecutiveFailures=%d, totalOps=%d}",
                getSuccessRate(), getAverageResponseTime(), getConsecutiveFailures(), getTotalOperations());
    }
}
