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
