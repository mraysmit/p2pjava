package dev.mars.p2pjava.config;

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


import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Health check configuration settings.
 */
public class HealthCheckConfig {
    
    @JsonProperty("enabled")
    private boolean enabled = true;
    
    @JsonProperty("port")
    private int port = 8080;
    
    @JsonProperty("path")
    private String path = "/health";
    
    @JsonProperty("intervalMs")
    private long intervalMs = 30000;
    
    @JsonProperty("timeoutMs")
    private long timeoutMs = 5000;
    
    @JsonProperty("checks")
    private ChecksConfig checks = new ChecksConfig();
    
    // Getters and setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    
    public long getIntervalMs() { return intervalMs; }
    public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }
    
    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
    
    public ChecksConfig getChecks() { return checks; }
    public void setChecks(ChecksConfig checks) { this.checks = checks; }
    
    /**
     * Individual health checks configuration
     */
    public static class ChecksConfig {
        @JsonProperty("database")
        private boolean database = true;
        
        @JsonProperty("tracker")
        private boolean tracker = true;
        
        @JsonProperty("indexServer")
        private boolean indexServer = true;
        
        @JsonProperty("diskSpace")
        private boolean diskSpace = true;
        
        @JsonProperty("memory")
        private boolean memory = true;
        
        @JsonProperty("threadPools")
        private boolean threadPools = true;
        
        // Getters and setters
        public boolean isDatabase() { return database; }
        public void setDatabase(boolean database) { this.database = database; }
        
        public boolean isTracker() { return tracker; }
        public void setTracker(boolean tracker) { this.tracker = tracker; }
        
        public boolean isIndexServer() { return indexServer; }
        public void setIndexServer(boolean indexServer) { this.indexServer = indexServer; }
        
        public boolean isDiskSpace() { return diskSpace; }
        public void setDiskSpace(boolean diskSpace) { this.diskSpace = diskSpace; }
        
        public boolean isMemory() { return memory; }
        public void setMemory(boolean memory) { this.memory = memory; }
        
        public boolean isThreadPools() { return threadPools; }
        public void setThreadPools(boolean threadPools) { this.threadPools = threadPools; }
    }
}
