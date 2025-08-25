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


import java.io.IOException;
import java.nio.file.*;
import java.util.Scanner;
import java.util.logging.Logger;

/**
 * Comprehensive demonstration of the enhanced configuration management system.
 * Shows YAML configuration, dynamic reloading, centralized service, and migration capabilities.
 */
public class ConfigurationManagementDemo {
    private static final Logger logger = Logger.getLogger(ConfigurationManagementDemo.class.getName());
    
    private final YamlConfigurationManager configManager;
    private final ConfigurationWatchService watchService;
    private final CentralizedConfigurationService centralService;
    private final Scanner scanner;
    
    public ConfigurationManagementDemo() {
        this.configManager = YamlConfigurationManager.getInstance();
        this.watchService = ConfigurationWatchService.getInstance();
        this.centralService = CentralizedConfigurationService.getInstance();
        this.scanner = new Scanner(System.in);
    }
    
    public static void main(String[] args) {
        ConfigurationManagementDemo demo = new ConfigurationManagementDemo();
        
        try {
            demo.runDemo();
        } catch (Exception e) {
            logger.severe("Demo failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void runDemo() throws Exception {
        logger.info("=== Configuration Management Improvements Demo ===");
        
        // 1. Demonstrate YAML configuration loading
        demonstrateYamlConfiguration();
        
        // 2. Demonstrate dynamic configuration reloading
        demonstrateDynamicReloading();
        
        // 3. Demonstrate centralized configuration service
        demonstrateCentralizedService();
        
        // 4. Demonstrate configuration migration
        demonstrateConfigurationMigration();
        
        // 5. Interactive configuration management
        interactiveConfigurationManagement();
        
        logger.info("=== Demo completed successfully ===");
    }
    
    /**
     * Demonstrates YAML configuration loading and structured access.
     */
    private void demonstrateYamlConfiguration() {
        logger.info("\n--- YAML Configuration Demo ---");
        
        // Get configuration object
        P2PConfiguration config = configManager.getConfiguration();
        
        // Display structured configuration
        logger.info("Application Configuration:");
        logger.info("  Name: " + config.getApplication().getName());
        logger.info("  Version: " + config.getApplication().getVersion());
        logger.info("  Environment: " + config.getApplication().getEnvironment());
        
        logger.info("Tracker Configuration:");
        logger.info("  Host: " + config.getTracker().getHost());
        logger.info("  Port: " + config.getTracker().getPort());
        logger.info("  Thread Pool Size: " + config.getTracker().getThreadPool().getSize());
        
        logger.info("Peer Configuration:");
        logger.info("  Socket Timeout: " + config.getPeer().getSocketTimeoutMs() + "ms");
        logger.info("  Heartbeat Interval: " + config.getPeer().getHeartbeat().getIntervalSeconds() + "s");
        logger.info("  File Sharing Enabled: " + config.getPeer().getFileSharing().isEnabled());
        
        // Demonstrate flat property access
        logger.info("\nFlat Property Access:");
        logger.info("  tracker.host = " + configManager.getString("tracker.host"));
        logger.info("  tracker.port = " + configManager.getInt("tracker.port", 0));
        logger.info("  peer.heartbeat.enabled = " + configManager.getBoolean("peer.heartbeat.enabled", false));
        
        // Show YAML representation
        logger.info("\nYAML Representation (first 500 chars):");
        String yamlString = configManager.toYamlString();
        logger.info(yamlString.substring(0, Math.min(500, yamlString.length())) + "...");
    }
    
    /**
     * Demonstrates dynamic configuration reloading.
     */
    private void demonstrateDynamicReloading() throws IOException, InterruptedException {
        logger.info("\n--- Dynamic Configuration Reloading Demo ---");
        
        // Add configuration change listener
        configManager.addConfigurationChangeListener((oldConfig, newConfig) -> {
            logger.info("Configuration changed detected!");
            logger.info("  Old tracker port: " + oldConfig.getTracker().getPort());
            logger.info("  New tracker port: " + newConfig.getTracker().getPort());
        });
        
        // Start watch service
        logger.info("Starting configuration watch service...");
        watchService.start();
        
        logger.info("Watch service status: " + (watchService.isRunning() ? "RUNNING" : "STOPPED"));
        logger.info("Auto-reload enabled: " + watchService.isEnabled());
        
        // Create a temporary config file for demonstration
        Path tempConfigFile = createTemporaryConfigFile();
        System.setProperty("config.file", tempConfigFile.toString());
        
        logger.info("Created temporary config file: " + tempConfigFile);
        logger.info("Current tracker port: " + configManager.getInt("tracker.port", 0));
        
        // Simulate configuration change
        logger.info("Simulating configuration change in 3 seconds...");
        Thread.sleep(3000);
        
        updateTemporaryConfigFile(tempConfigFile);
        logger.info("Configuration file updated");
        
        // Force reload to demonstrate
        watchService.forceReload();
        
        Thread.sleep(1000); // Give time for reload
        logger.info("New tracker port: " + configManager.getInt("tracker.port", 0));
        
        // Cleanup
        Files.deleteIfExists(tempConfigFile);
        System.clearProperty("config.file");
    }
    
    /**
     * Demonstrates centralized configuration service.
     */
    private void demonstrateCentralizedService() throws InterruptedException {
        logger.info("\n--- Centralized Configuration Service Demo ---");
        
        // Start centralized service
        logger.info("Starting centralized configuration service on port 8081...");
        centralService.start(8081);
        
        logger.info("Service status: " + (centralService.isRunning() ? "RUNNING" : "STOPPED"));
        
        logger.info("Available REST API endpoints:");
        logger.info("  GET  http://localhost:8081/api/health - Health check");
        logger.info("  GET  http://localhost:8081/api/config - Get current configuration");
        logger.info("  POST http://localhost:8081/api/config - Reload configuration");
        logger.info("  GET  http://localhost:8081/api/config/versions - Get version history");
        
        // Demonstrate API calls (would normally use HTTP client)
        logger.info("\nCentralized service is running. You can test the API endpoints:");
        logger.info("Example: curl http://localhost:8081/api/health");
        logger.info("Example: curl http://localhost:8081/api/config");
        
        // Keep service running for a bit
        Thread.sleep(5000);
        
        logger.info("Stopping centralized configuration service...");
        centralService.stop();
    }
    
    /**
     * Demonstrates configuration migration from properties to YAML.
     */
    private void demonstrateConfigurationMigration() throws IOException {
        logger.info("\n--- Configuration Migration Demo ---");
        
        // Create sample properties file
        Path propsFile = createSamplePropertiesFile();
        Path yamlFile = Paths.get("migrated-config.yml");
        Path backupDir = Paths.get("config-backup");
        
        logger.info("Created sample properties file: " + propsFile);
        
        // Perform migration
        ConfigurationMigrationUtility migrator = new ConfigurationMigrationUtility();
        
        logger.info("Performing migration to YAML...");
        System.setProperty("config.file", propsFile.toString());
        
        try {
            // Create backup
            migrator.backupPropertiesFiles(backupDir);
            logger.info("Created backup in: " + backupDir);
            
            // Migrate to YAML
            migrator.migrateToYaml(yamlFile);
            logger.info("Migration completed: " + yamlFile);
            
            // Validate migration
            boolean isValid = migrator.validateMigratedConfiguration(yamlFile);
            logger.info("Migration validation: " + (isValid ? "PASSED" : "FAILED"));
            
            // Show migrated content (first few lines)
            if (Files.exists(yamlFile)) {
                logger.info("Migrated YAML content (first 10 lines):");
                Files.lines(yamlFile).limit(10).forEach(line -> logger.info("  " + line));
            }
            
        } finally {
            System.clearProperty("config.file");
            
            // Cleanup
            Files.deleteIfExists(propsFile);
            Files.deleteIfExists(yamlFile);
            deleteDirectoryRecursively(backupDir);
        }
    }
    
    /**
     * Interactive configuration management demonstration.
     */
    private void interactiveConfigurationManagement() {
        logger.info("\n--- Interactive Configuration Management ---");
        
        boolean running = true;
        while (running) {
            System.out.println("\nConfiguration Management Options:");
            System.out.println("1. Show current configuration");
            System.out.println("2. Get specific property");
            System.out.println("3. Start/stop watch service");
            System.out.println("4. Force configuration reload");
            System.out.println("5. Show configuration as YAML");
            System.out.println("6. Exit");
            System.out.print("Choose option (1-6): ");
            
            String choice = scanner.nextLine().trim();
            
            switch (choice) {
                case "1":
                    showCurrentConfiguration();
                    break;
                case "2":
                    getSpecificProperty();
                    break;
                case "3":
                    toggleWatchService();
                    break;
                case "4":
                    forceConfigurationReload();
                    break;
                case "5":
                    showConfigurationAsYaml();
                    break;
                case "6":
                    running = false;
                    break;
                default:
                    System.out.println("Invalid option. Please choose 1-6.");
            }
        }
    }
    
    private void showCurrentConfiguration() {
        P2PConfiguration config = configManager.getConfiguration();
        System.out.println("\nCurrent Configuration Summary:");
        System.out.println("  Application: " + config.getApplication().getName() + " v" + config.getApplication().getVersion());
        System.out.println("  Environment: " + config.getApplication().getEnvironment());
        System.out.println("  Tracker: " + config.getTracker().getHost() + ":" + config.getTracker().getPort());
        System.out.println("  Health Check: " + (config.getHealthCheck().isEnabled() ? "Enabled" : "Disabled"));
        System.out.println("  Monitoring: " + (config.getMonitoring().isEnabled() ? "Enabled" : "Disabled"));
    }
    
    private void getSpecificProperty() {
        System.out.print("Enter property key (e.g., tracker.host): ");
        String key = scanner.nextLine().trim();
        
        String value = configManager.getString(key);
        if (value != null) {
            System.out.println("  " + key + " = " + value);
        } else {
            System.out.println("  Property not found: " + key);
        }
    }
    
    private void toggleWatchService() {
        if (watchService.isRunning()) {
            watchService.stop();
            System.out.println("  Watch service stopped");
        } else {
            watchService.start();
            System.out.println("  Watch service started");
        }
        System.out.println("  Status: " + (watchService.isRunning() ? "RUNNING" : "STOPPED"));
    }
    
    private void forceConfigurationReload() {
        System.out.println("  Forcing configuration reload...");
        boolean reloaded = configManager.checkAndReload();
        System.out.println("  Reload result: " + (reloaded ? "Configuration reloaded" : "No changes detected"));
    }
    
    private void showConfigurationAsYaml() {
        System.out.println("\nConfiguration as YAML:");
        String yaml = configManager.toYamlString();
        String[] lines = yaml.split("\n");
        for (int i = 0; i < Math.min(20, lines.length); i++) {
            System.out.println("  " + lines[i]);
        }
        if (lines.length > 20) {
            System.out.println("  ... (" + (lines.length - 20) + " more lines)");
        }
    }
    
    /**
     * Creates a temporary configuration file for demonstration.
     */
    private Path createTemporaryConfigFile() throws IOException {
        Path tempFile = Files.createTempFile("demo-config", ".yml");
        
        String config = """
                application:
                  name: "demo-app"
                  version: "1.0.0"
                  environment: "demo"
                
                tracker:
                  host: "localhost"
                  port: 9000
                
                peer:
                  socketTimeoutMs: 30000
                """;
        
        Files.write(tempFile, config.getBytes());
        return tempFile;
    }
    
    /**
     * Updates the temporary configuration file.
     */
    private void updateTemporaryConfigFile(Path configFile) throws IOException {
        String updatedConfig = """
                application:
                  name: "demo-app"
                  version: "1.0.1"
                  environment: "demo"
                
                tracker:
                  host: "localhost"
                  port: 9001
                
                peer:
                  socketTimeoutMs: 35000
                """;
        
        Files.write(configFile, updatedConfig.getBytes());
    }
    
    /**
     * Creates a sample properties file for migration demonstration.
     */
    private Path createSamplePropertiesFile() throws IOException {
        Path propsFile = Files.createTempFile("sample-config", ".properties");
        
        String properties = """
                # Sample properties for migration
                application.name=sample-app
                application.version=2.0.0
                application.environment=production
                
                tracker.host=prod-tracker
                tracker.port=6000
                tracker.thread.pool.size=20
                
                peer.socket.timeout.ms=60000
                peer.heartbeat.interval.seconds=60
                
                healthcheck.enabled=true
                healthcheck.port=8080
                
                monitoring.enabled=true
                monitoring.interval.ms=60000
                """;
        
        Files.write(propsFile, properties.getBytes());
        return propsFile;
    }
    
    /**
     * Recursively deletes a directory.
     */
    private void deleteDirectoryRecursively(Path directory) {
        try {
            if (Files.exists(directory)) {
                Files.walk(directory)
                     .sorted((a, b) -> b.compareTo(a))
                     .forEach(path -> {
                         try {
                             Files.delete(path);
                         } catch (IOException e) {
                             // Ignore deletion errors
                         }
                     });
            }
        } catch (IOException e) {
            // Ignore errors
        }
    }
}
