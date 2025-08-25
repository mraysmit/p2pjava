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
 * Logging configuration settings.
 */
public class LoggingConfig {
    
    @JsonProperty("level")
    private String level = "INFO";
    
    @JsonProperty("pattern")
    private String pattern = "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n";
    
    @JsonProperty("file")
    private FileLoggingConfig file = new FileLoggingConfig();
    
    @JsonProperty("console")
    private ConsoleLoggingConfig console = new ConsoleLoggingConfig();
    
    @JsonProperty("loggers")
    private LoggersConfig loggers = new LoggersConfig();
    
    // Getters and setters
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    
    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }
    
    public FileLoggingConfig getFile() { return file; }
    public void setFile(FileLoggingConfig file) { this.file = file; }
    
    public ConsoleLoggingConfig getConsole() { return console; }
    public void setConsole(ConsoleLoggingConfig console) { this.console = console; }
    
    public LoggersConfig getLoggers() { return loggers; }
    public void setLoggers(LoggersConfig loggers) { this.loggers = loggers; }
    
    /**
     * File logging configuration
     */
    public static class FileLoggingConfig {
        @JsonProperty("enabled")
        private boolean enabled = true;
        
        @JsonProperty("path")
        private String path = "logs/p2p-java.log";
        
        @JsonProperty("maxSize")
        private String maxSize = "10MB";
        
        @JsonProperty("maxFiles")
        private int maxFiles = 10;
        
        @JsonProperty("compress")
        private boolean compress = true;
        
        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        
        public String getMaxSize() { return maxSize; }
        public void setMaxSize(String maxSize) { this.maxSize = maxSize; }
        
        public int getMaxFiles() { return maxFiles; }
        public void setMaxFiles(int maxFiles) { this.maxFiles = maxFiles; }
        
        public boolean isCompress() { return compress; }
        public void setCompress(boolean compress) { this.compress = compress; }
    }
    
    /**
     * Console logging configuration
     */
    public static class ConsoleLoggingConfig {
        @JsonProperty("enabled")
        private boolean enabled = true;
        
        @JsonProperty("colorEnabled")
        private boolean colorEnabled = true;
        
        @JsonProperty("level")
        private String level = "INFO";
        
        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public boolean isColorEnabled() { return colorEnabled; }
        public void setColorEnabled(boolean colorEnabled) { this.colorEnabled = colorEnabled; }
        
        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
    }
    
    /**
     * Individual logger configurations
     */
    public static class LoggersConfig {
        @JsonProperty("tracker")
        private String tracker = "INFO";
        
        @JsonProperty("indexServer")
        private String indexServer = "INFO";
        
        @JsonProperty("peer")
        private String peer = "INFO";
        
        @JsonProperty("threadManager")
        private String threadManager = "INFO";
        
        @JsonProperty("configManager")
        private String configManager = "INFO";
        
        @JsonProperty("healthCheck")
        private String healthCheck = "WARN";
        
        // Getters and setters
        public String getTracker() { return tracker; }
        public void setTracker(String tracker) { this.tracker = tracker; }
        
        public String getIndexServer() { return indexServer; }
        public void setIndexServer(String indexServer) { this.indexServer = indexServer; }
        
        public String getPeer() { return peer; }
        public void setPeer(String peer) { this.peer = peer; }
        
        public String getThreadManager() { return threadManager; }
        public void setThreadManager(String threadManager) { this.threadManager = threadManager; }
        
        public String getConfigManager() { return configManager; }
        public void setConfigManager(String configManager) { this.configManager = configManager; }
        
        public String getHealthCheck() { return healthCheck; }
        public void setHealthCheck(String healthCheck) { this.healthCheck = healthCheck; }
    }
}
