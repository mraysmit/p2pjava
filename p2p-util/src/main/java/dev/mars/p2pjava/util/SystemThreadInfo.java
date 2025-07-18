package dev.mars.p2pjava.util;

/**
 * Immutable class containing system-wide thread information.
 */
public class SystemThreadInfo {
    private final int currentThreadCount;
    private final int peakThreadCount;
    private final int daemonThreadCount;
    private final long totalStartedThreadCount;
    
    public SystemThreadInfo(int currentThreadCount, int peakThreadCount, 
                           int daemonThreadCount, long totalStartedThreadCount) {
        this.currentThreadCount = currentThreadCount;
        this.peakThreadCount = peakThreadCount;
        this.daemonThreadCount = daemonThreadCount;
        this.totalStartedThreadCount = totalStartedThreadCount;
    }
    
    public int getCurrentThreadCount() {
        return currentThreadCount;
    }
    
    public int getPeakThreadCount() {
        return peakThreadCount;
    }
    
    public int getDaemonThreadCount() {
        return daemonThreadCount;
    }
    
    public long getTotalStartedThreadCount() {
        return totalStartedThreadCount;
    }
    
    public int getNonDaemonThreadCount() {
        return currentThreadCount - daemonThreadCount;
    }
    
    @Override
    public String toString() {
        return String.format(
            "SystemThreadInfo{current=%d, peak=%d, daemon=%d, nonDaemon=%d, totalStarted=%d}",
            currentThreadCount, peakThreadCount, daemonThreadCount, 
            getNonDaemonThreadCount(), totalStartedThreadCount
        );
    }
}
