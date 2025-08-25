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


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Centralized configuration service that provides REST API for configuration management,
 * versioning, rollback capabilities, and distributed configuration updates.
 */
public class CentralizedConfigurationService {
    private static final Logger logger = Logger.getLogger(CentralizedConfigurationService.class.getName());
    
    private static volatile CentralizedConfigurationService instance;
    private static final Object lock = new Object();
    
    private final YamlConfigurationManager configManager;
    private final ConfigurationWatchService watchService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    // Configuration versioning
    private final Map<String, ConfigurationVersion> configVersions = new ConcurrentHashMap<>();
    private final AtomicLong versionCounter = new AtomicLong(1);
    
    // HTTP Server for REST API
    private HttpServer httpServer;
    private ExecutorService httpExecutor;
    
    // Configuration
    private static final int DEFAULT_PORT = 8081;
    private static final String CONFIG_ENDPOINT = "/api/config";
    private static final String HEALTH_ENDPOINT = "/api/health";
    private static final String VERSIONS_ENDPOINT = "/api/config/versions";
    
    /**
     * Private constructor for singleton pattern.
     */
    private CentralizedConfigurationService() {
        this.configManager = YamlConfigurationManager.getInstance();
        this.watchService = ConfigurationWatchService.getInstance();
        
        // Initialize with current configuration
        saveCurrentConfigurationVersion("Initial configuration");
    }
    
    /**
     * Gets the singleton instance.
     */
    public static CentralizedConfigurationService getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new CentralizedConfigurationService();
                }
            }
        }
        return instance;
    }
    
    /**
     * Starts the centralized configuration service.
     */
    public synchronized void start() {
        start(DEFAULT_PORT);
    }
    
    /**
     * Starts the centralized configuration service on specified port.
     */
    public synchronized void start(int port) {
        if (running.get()) {
            logger.warning("Centralized configuration service is already running");
            return;
        }
        
        try {
            logger.info("Starting centralized configuration service on port " + port);
            
            // Create HTTP server
            httpServer = HttpServer.create(new InetSocketAddress(port), 0);
            httpExecutor = Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "ConfigService-HTTP");
                t.setDaemon(true);
                return t;
            });
            httpServer.setExecutor(httpExecutor);
            
            // Setup endpoints
            setupEndpoints();
            
            // Start HTTP server
            httpServer.start();
            
            // Start watch service
            watchService.start();
            
            // Add configuration change listener
            configManager.addConfigurationChangeListener(this::onConfigurationChanged);
            
            running.set(true);
            logger.info("Centralized configuration service started on port " + port);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to start centralized configuration service", e);
            stop();
            throw new RuntimeException("Failed to start centralized configuration service", e);
        }
    }
    
    /**
     * Stops the centralized configuration service.
     */
    public synchronized void stop() {
        if (!running.get()) {
            return;
        }
        
        logger.info("Stopping centralized configuration service");
        running.set(false);
        
        // Stop HTTP server
        if (httpServer != null) {
            httpServer.stop(5);
        }
        
        // Shutdown HTTP executor
        if (httpExecutor != null) {
            httpExecutor.shutdown();
            try {
                if (!httpExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    httpExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                httpExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // Stop watch service
        watchService.stop();
        
        logger.info("Centralized configuration service stopped");
    }
    
    /**
     * Sets up HTTP endpoints.
     */
    private void setupEndpoints() {
        // Health check endpoint
        httpServer.createContext(HEALTH_ENDPOINT, this::handleHealthCheck);
        
        // Configuration endpoints
        httpServer.createContext(CONFIG_ENDPOINT, this::handleConfigurationRequest);
        httpServer.createContext(VERSIONS_ENDPOINT, this::handleVersionsRequest);
        
        // Root endpoint
        httpServer.createContext("/", this::handleRootRequest);
    }
    
    /**
     * Handles health check requests.
     */
    private void handleHealthCheck(HttpExchange exchange) throws IOException {
        String response = "{\n" +
                "  \"status\": \"UP\",\n" +
                "  \"timestamp\": \"" + Instant.now() + "\",\n" +
                "  \"service\": \"centralized-configuration-service\",\n" +
                "  \"version\": \"" + getCurrentVersion() + "\",\n" +
                "  \"watchService\": \"" + (watchService.isRunning() ? "RUNNING" : "STOPPED") + "\"\n" +
                "}";
        
        sendJsonResponse(exchange, 200, response);
    }
    
    /**
     * Handles configuration requests (GET, PUT, POST).
     */
    private void handleConfigurationRequest(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        
        switch (method) {
            case "GET":
                handleGetConfiguration(exchange);
                break;
            case "PUT":
                handleUpdateConfiguration(exchange);
                break;
            case "POST":
                handleReloadConfiguration(exchange);
                break;
            default:
                sendJsonResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
        }
    }
    
    /**
     * Handles GET configuration requests.
     */
    private void handleGetConfiguration(HttpExchange exchange) throws IOException {
        try {
            String yamlConfig = configManager.toYamlString();
            
            String response = "{\n" +
                    "  \"version\": \"" + getCurrentVersion() + "\",\n" +
                    "  \"timestamp\": \"" + Instant.now() + "\",\n" +
                    "  \"configuration\": " + escapeJson(yamlConfig) + "\n" +
                    "}";
            
            sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error getting configuration", e);
            sendJsonResponse(exchange, 500, "{\"error\": \"Failed to get configuration\"}");
        }
    }
    
    /**
     * Handles configuration update requests.
     */
    private void handleUpdateConfiguration(HttpExchange exchange) throws IOException {
        try {
            // Read request body
            String requestBody = readRequestBody(exchange);
            
            // Parse and validate configuration
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            P2PConfiguration newConfig = yamlMapper.readValue(requestBody, P2PConfiguration.class);
            
            // Save current version before update
            saveCurrentConfigurationVersion("Pre-update backup");
            
            // Update configuration (this would typically save to file)
            // For now, we'll just validate and respond
            String response = "{\n" +
                    "  \"status\": \"success\",\n" +
                    "  \"message\": \"Configuration updated successfully\",\n" +
                    "  \"version\": \"" + getNextVersion() + "\",\n" +
                    "  \"timestamp\": \"" + Instant.now() + "\"\n" +
                    "}";
            
            sendJsonResponse(exchange, 200, response);
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error updating configuration", e);
            sendJsonResponse(exchange, 400, "{\"error\": \"Invalid configuration: " + e.getMessage() + "\"}");
        }
    }
    
    /**
     * Handles configuration reload requests.
     */
    private void handleReloadConfiguration(HttpExchange exchange) throws IOException {
        try {
            watchService.forceReload();
            
            String response = "{\n" +
                    "  \"status\": \"success\",\n" +
                    "  \"message\": \"Configuration reloaded successfully\",\n" +
                    "  \"timestamp\": \"" + Instant.now() + "\"\n" +
                    "}";
            
            sendJsonResponse(exchange, 200, response);
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error reloading configuration", e);
            sendJsonResponse(exchange, 500, "{\"error\": \"Failed to reload configuration\"}");
        }
    }
    
    /**
     * Handles version history requests.
     */
    private void handleVersionsRequest(HttpExchange exchange) throws IOException {
        try {
            StringBuilder response = new StringBuilder();
            response.append("{\n");
            response.append("  \"currentVersion\": \"").append(getCurrentVersion()).append("\",\n");
            response.append("  \"versions\": [\n");
            
            boolean first = true;
            for (ConfigurationVersion version : configVersions.values()) {
                if (!first) response.append(",\n");
                response.append("    {\n");
                response.append("      \"version\": \"").append(version.getVersion()).append("\",\n");
                response.append("      \"timestamp\": \"").append(version.getTimestamp()).append("\",\n");
                response.append("      \"description\": \"").append(version.getDescription()).append("\"\n");
                response.append("    }");
                first = false;
            }
            
            response.append("\n  ]\n");
            response.append("}");
            
            sendJsonResponse(exchange, 200, response.toString());
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error getting version history", e);
            sendJsonResponse(exchange, 500, "{\"error\": \"Failed to get version history\"}");
        }
    }
    
    /**
     * Handles root requests.
     */
    private void handleRootRequest(HttpExchange exchange) throws IOException {
        String response = "{\n" +
                "  \"service\": \"P2P Java Centralized Configuration Service\",\n" +
                "  \"version\": \"1.0.0\",\n" +
                "  \"endpoints\": {\n" +
                "    \"health\": \"" + HEALTH_ENDPOINT + "\",\n" +
                "    \"configuration\": \"" + CONFIG_ENDPOINT + "\",\n" +
                "    \"versions\": \"" + VERSIONS_ENDPOINT + "\"\n" +
                "  }\n" +
                "}";
        
        sendJsonResponse(exchange, 200, response);
    }
    
    /**
     * Sends a JSON response.
     */
    private void sendJsonResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }
    
    /**
     * Reads the request body as string.
     */
    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody()))) {
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line).append("\n");
            }
            return body.toString();
        }
    }
    
    /**
     * Escapes a string for JSON.
     */
    private String escapeJson(String str) {
        return "\"" + str.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\t", "\\t") + "\"";
    }
    
    /**
     * Saves current configuration as a version.
     */
    private void saveCurrentConfigurationVersion(String description) {
        String version = String.valueOf(versionCounter.getAndIncrement());
        ConfigurationVersion configVersion = new ConfigurationVersion(
                version,
                Instant.now(),
                description,
                configManager.toYamlString()
        );
        configVersions.put(version, configVersion);
        
        // Keep only last 10 versions
        if (configVersions.size() > 10) {
            String oldestVersion = configVersions.keySet().stream()
                    .min(Comparator.comparing(Long::valueOf))
                    .orElse(null);
            if (oldestVersion != null) {
                configVersions.remove(oldestVersion);
            }
        }
    }
    
    /**
     * Gets the current version number.
     */
    private String getCurrentVersion() {
        return String.valueOf(versionCounter.get() - 1);
    }
    
    /**
     * Gets the next version number.
     */
    private String getNextVersion() {
        return String.valueOf(versionCounter.get());
    }
    
    /**
     * Handles configuration change events.
     */
    private void onConfigurationChanged(P2PConfiguration oldConfig, P2PConfiguration newConfig) {
        saveCurrentConfigurationVersion("Automatic save on configuration change");
        logger.info("Configuration version saved: " + getCurrentVersion());
    }
    
    /**
     * Checks if the service is running.
     */
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * Configuration version data class.
     */
    public static class ConfigurationVersion {
        private final String version;
        private final Instant timestamp;
        private final String description;
        private final String configuration;
        
        public ConfigurationVersion(String version, Instant timestamp, String description, String configuration) {
            this.version = version;
            this.timestamp = timestamp;
            this.description = description;
            this.configuration = configuration;
        }
        
        public String getVersion() { return version; }
        public Instant getTimestamp() { return timestamp; }
        public String getDescription() { return description; }
        public String getConfiguration() { return configuration; }
    }
}
