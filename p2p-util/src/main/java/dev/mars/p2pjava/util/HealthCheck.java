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


import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for implementing health checks for services.
 * Provides methods to check if a service is healthy and to report health status.
 */
public class HealthCheck {
    private static final Logger logger = Logger.getLogger(HealthCheck.class.getName());
    private static final Map<String, ServiceHealth> serviceHealthMap = new ConcurrentHashMap<>();
    
    /**
     * Represents the health status of a service.
     */
    public static class ServiceHealth {
        private final String serviceName;
        private final AtomicBoolean healthy = new AtomicBoolean(true);
        private final Map<String, Object> healthDetails = new ConcurrentHashMap<>();
        private long lastCheckedTimestamp;
        
        public ServiceHealth(String serviceName) {
            this.serviceName = serviceName;
            this.lastCheckedTimestamp = System.currentTimeMillis();
        }
        
        public String getServiceName() {
            return serviceName;
        }
        
        public boolean isHealthy() {
            return healthy.get();
        }
        
        public void setHealthy(boolean healthy) {
            this.healthy.set(healthy);
            this.lastCheckedTimestamp = System.currentTimeMillis();
        }
        
        public long getLastCheckedTimestamp() {
            return lastCheckedTimestamp;
        }
        
        public void addHealthDetail(String key, Object value) {
            healthDetails.put(key, value);
        }
        
        public Map<String, Object> getHealthDetails() {
            return new HashMap<>(healthDetails);
        }
        
        @Override
        public String toString() {
            return "ServiceHealth{" +
                    "serviceName='" + serviceName + '\'' +
                    ", healthy=" + healthy +
                    ", lastChecked=" + lastCheckedTimestamp +
                    ", details=" + healthDetails +
                    '}';
        }
    }
    
    /**
     * Registers a service with the health check system.
     *
     * @param serviceName The name of the service
     * @return The ServiceHealth object for the service
     */
    public static ServiceHealth registerService(String serviceName) {
        ServiceHealth health = new ServiceHealth(serviceName);
        serviceHealthMap.put(serviceName, health);
        logger.log(Level.INFO, "Registered health check for service: {0}", serviceName);
        return health;
    }
    
    /**
     * Gets the health status of a service.
     *
     * @param serviceName The name of the service
     * @return The ServiceHealth object for the service, or null if not registered
     */
    public static ServiceHealth getServiceHealth(String serviceName) {
        return serviceHealthMap.get(serviceName);
    }
    
    /**
     * Updates the health status of a service.
     *
     * @param serviceName The name of the service
     * @param healthy Whether the service is healthy
     * @return The updated ServiceHealth object, or null if the service is not registered
     */
    public static ServiceHealth updateServiceHealth(String serviceName, boolean healthy) {
        ServiceHealth health = serviceHealthMap.get(serviceName);
        if (health != null) {
            health.setHealthy(healthy);
            logger.log(healthy ? Level.INFO : Level.WARNING, 
                    "Service {0} health updated to {1}", new Object[]{serviceName, healthy});
        }
        return health;
    }
    
    /**
     * Checks if a service is available at the specified host and port.
     *
     * @param host The host of the service
     * @param port The port of the service
     * @param timeoutMs Connection timeout in milliseconds
     * @return true if the service is available, false otherwise
     */
    public static boolean isServiceAvailable(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (IOException e) {
            logger.log(Level.FINE, "Service at {0}:{1} is not available: {2}", 
                    new Object[]{host, port, e.getMessage()});
            return false;
        }
    }
    
    /**
     * Gets a map of all registered services and their health status.
     *
     * @return A map of service names to ServiceHealth objects
     */
    public static Map<String, ServiceHealth> getAllServiceHealth() {
        return new HashMap<>(serviceHealthMap);
    }
    
    /**
     * Removes a service from the health check system.
     *
     * @param serviceName The name of the service to remove
     */
    public static void deregisterService(String serviceName) {
        serviceHealthMap.remove(serviceName);
        logger.log(Level.INFO, "Deregistered health check for service: {0}", serviceName);
    }
}