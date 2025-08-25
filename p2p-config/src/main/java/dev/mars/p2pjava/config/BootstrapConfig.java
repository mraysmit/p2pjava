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
 * Bootstrap configuration settings.
 */
public class BootstrapConfig {
    
    @JsonProperty("autoStart")
    private boolean autoStart = true;
    
    @JsonProperty("startupTimeoutSeconds")
    private int startupTimeoutSeconds = 30;
    
    @JsonProperty("dynamicPorts")
    private boolean dynamicPorts = false;
    
    @JsonProperty("retryAttempts")
    private int retryAttempts = 3;
    
    @JsonProperty("retryDelayMs")
    private long retryDelayMs = 5000;
    
    @JsonProperty("services")
    private ServicesConfig services = new ServicesConfig();
    
    // Getters and setters
    public boolean isAutoStart() { return autoStart; }
    public void setAutoStart(boolean autoStart) { this.autoStart = autoStart; }
    
    public int getStartupTimeoutSeconds() { return startupTimeoutSeconds; }
    public void setStartupTimeoutSeconds(int startupTimeoutSeconds) { this.startupTimeoutSeconds = startupTimeoutSeconds; }
    
    public boolean isDynamicPorts() { return dynamicPorts; }
    public void setDynamicPorts(boolean dynamicPorts) { this.dynamicPorts = dynamicPorts; }
    
    public int getRetryAttempts() { return retryAttempts; }
    public void setRetryAttempts(int retryAttempts) { this.retryAttempts = retryAttempts; }
    
    public long getRetryDelayMs() { return retryDelayMs; }
    public void setRetryDelayMs(long retryDelayMs) { this.retryDelayMs = retryDelayMs; }
    
    public ServicesConfig getServices() { return services; }
    public void setServices(ServicesConfig services) { this.services = services; }
    
    /**
     * Services startup configuration
     */
    public static class ServicesConfig {
        @JsonProperty("tracker")
        private ServiceConfig tracker = new ServiceConfig(true, 1);
        
        @JsonProperty("indexServer")
        private ServiceConfig indexServer = new ServiceConfig(true, 2);
        
        @JsonProperty("peer")
        private ServiceConfig peer = new ServiceConfig(true, 3);
        
        @JsonProperty("healthCheck")
        private ServiceConfig healthCheck = new ServiceConfig(true, 4);
        
        // Getters and setters
        public ServiceConfig getTracker() { return tracker; }
        public void setTracker(ServiceConfig tracker) { this.tracker = tracker; }
        
        public ServiceConfig getIndexServer() { return indexServer; }
        public void setIndexServer(ServiceConfig indexServer) { this.indexServer = indexServer; }
        
        public ServiceConfig getPeer() { return peer; }
        public void setPeer(ServiceConfig peer) { this.peer = peer; }
        
        public ServiceConfig getHealthCheck() { return healthCheck; }
        public void setHealthCheck(ServiceConfig healthCheck) { this.healthCheck = healthCheck; }
    }
    
    /**
     * Individual service configuration
     */
    public static class ServiceConfig {
        @JsonProperty("enabled")
        private boolean enabled;
        
        @JsonProperty("startupOrder")
        private int startupOrder;
        
        @JsonProperty("startupDelayMs")
        private long startupDelayMs = 0;
        
        @JsonProperty("dependsOn")
        private String[] dependsOn = {};
        
        public ServiceConfig() {}
        
        public ServiceConfig(boolean enabled, int startupOrder) {
            this.enabled = enabled;
            this.startupOrder = startupOrder;
        }
        
        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public int getStartupOrder() { return startupOrder; }
        public void setStartupOrder(int startupOrder) { this.startupOrder = startupOrder; }
        
        public long getStartupDelayMs() { return startupDelayMs; }
        public void setStartupDelayMs(long startupDelayMs) { this.startupDelayMs = startupDelayMs; }
        
        public String[] getDependsOn() { return dependsOn; }
        public void setDependsOn(String[] dependsOn) { this.dependsOn = dependsOn; }
    }
}
