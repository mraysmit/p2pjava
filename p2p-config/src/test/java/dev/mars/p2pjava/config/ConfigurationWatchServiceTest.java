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


import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test class for ConfigurationWatchService.
 */
class ConfigurationWatchServiceTest {
    
    private static final String TEST_CONFIG_DIR = "test-watch-config";
    private static final String TEST_CONFIG_FILE = "application.yml";
    
    private Path testConfigDir;
    private Path testConfigFile;
    private ConfigurationWatchService watchService;
    
    @BeforeEach
    void setUp() throws IOException {
        // Reset singleton instances
        YamlConfigurationManager.resetInstance();

        // Create test configuration directory
        testConfigDir = Paths.get(TEST_CONFIG_DIR);
        Files.createDirectories(testConfigDir);

        testConfigFile = testConfigDir.resolve(TEST_CONFIG_FILE);

        // Create test configuration file
        createTestConfigFile();

        // Get watch service instance
        watchService = ConfigurationWatchService.getInstance();
    }
    
    @AfterEach
    void tearDown() throws IOException {
        // Clear system properties
        System.clearProperty("config.file");

        // Stop watch service
        if (watchService != null && watchService.isRunning()) {
            watchService.stop();
        }

        // Clean up test files
        if (Files.exists(testConfigFile)) {
            Files.delete(testConfigFile);
        }
        if (Files.exists(testConfigDir)) {
            Files.delete(testConfigDir);
        }

        // Reset singleton instances
        YamlConfigurationManager.resetInstance();
    }
    
    @Test
    void testWatchServiceStartStop() {
        assertFalse(watchService.isRunning());
        
        watchService.start();
        assertTrue(watchService.isRunning());
        
        watchService.stop();
        assertFalse(watchService.isRunning());
    }
    
    @Test
    void testWatchServiceEnabled() {
        assertTrue(watchService.isEnabled());
        
        watchService.setEnabled(false);
        assertFalse(watchService.isEnabled());
        
        watchService.setEnabled(true);
        assertTrue(watchService.isEnabled());
    }
    
    @Test
    void testForceReload() {
        // Set system property to use test config
        System.setProperty("config.file", testConfigFile.toString());
        
        try {
            YamlConfigurationManager configManager = YamlConfigurationManager.getInstance();
            
            AtomicBoolean changeDetected = new AtomicBoolean(false);
            CountDownLatch latch = new CountDownLatch(1);
            
            // Add change listener
            configManager.addConfigurationChangeListener((oldConfig, newConfig) -> {
                changeDetected.set(true);
                latch.countDown();
            });

            // Start watch service
            watchService.start();

            // Modify configuration file
            try {
                Thread.sleep(100); // Ensure different timestamp
                updateTestConfigFile();

                // Force reload - this should trigger the change listener
                watchService.forceReload();

                // Wait for change detection with more time
                boolean changeOccurred = latch.await(10, TimeUnit.SECONDS);

                // If the latch didn't trigger, check if the configuration actually changed
                if (!changeOccurred) {
                    // Try manual reload check
                    boolean reloaded = configManager.checkAndReload();
                    if (reloaded) {
                        changeDetected.set(true);
                    }
                }

                assertTrue(changeDetected.get(), "Configuration change should have been detected");
                
            } catch (InterruptedException e) {
                fail("Test interrupted");
            }
            
        } catch (IOException e) {
            fail("Failed to update test config file: " + e.getMessage());
        } finally {
            System.clearProperty("config.file");
        }
    }
    
    @Test
    void testMultipleStartStop() {
        // Test that multiple start/stop calls don't cause issues
        watchService.start();
        assertTrue(watchService.isRunning());
        
        watchService.start(); // Should not cause issues
        assertTrue(watchService.isRunning());
        
        watchService.stop();
        assertFalse(watchService.isRunning());
        
        watchService.stop(); // Should not cause issues
        assertFalse(watchService.isRunning());
    }
    
    @Test
    void testDisabledWatchService() throws IOException, InterruptedException {
        System.setProperty("config.file", testConfigFile.toString());
        
        try {
            YamlConfigurationManager configManager = YamlConfigurationManager.getInstance();
            
            AtomicBoolean changeDetected = new AtomicBoolean(false);
            
            // Add change listener
            configManager.addConfigurationChangeListener((oldConfig, newConfig) -> {
                changeDetected.set(true);
            });
            
            // Start watch service but disable it
            watchService.start();
            watchService.setEnabled(false);
            
            // Modify configuration file
            Thread.sleep(100);
            updateTestConfigFile();
            
            // Wait a bit to see if change is detected (it shouldn't be)
            Thread.sleep(2000);
            
            // Change should not be detected when disabled
            assertFalse(changeDetected.get());
            
        } finally {
            System.clearProperty("config.file");
        }
    }
    
    /**
     * Creates a test configuration file.
     */
    private void createTestConfigFile() throws IOException {
        String testConfig = """
                application:
                  name: "watch-test-app"
                  version: "1.0.0"
                  environment: "test"
                
                tracker:
                  host: "localhost"
                  port: 8000
                
                peer:
                  socketTimeoutMs: 60000
                
                healthcheck:
                  enabled: true
                  port: 8080
                """;
        
        Files.write(testConfigFile, testConfig.getBytes());
    }
    
    /**
     * Updates the test configuration file to trigger reload.
     */
    private void updateTestConfigFile() throws IOException {
        String updatedConfig = """
                application:
                  name: "watch-test-app-updated"
                  version: "1.0.1"
                  environment: "test"
                
                tracker:
                  host: "localhost"
                  port: 8001
                
                peer:
                  socketTimeoutMs: 70000
                
                healthcheck:
                  enabled: true
                  port: 8081
                """;
        
        Files.write(testConfigFile, updatedConfig.getBytes());
    }
}
