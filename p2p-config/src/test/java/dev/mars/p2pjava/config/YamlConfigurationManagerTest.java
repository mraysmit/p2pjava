package dev.mars.p2pjava.config;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test class for YamlConfigurationManager.
 */
class YamlConfigurationManagerTest {
    
    private static final String TEST_CONFIG_DIR = "test-config";
    private static final String TEST_CONFIG_FILE = "test-application.yml";
    
    private Path testConfigDir;
    private Path testConfigFile;
    
    @BeforeEach
    void setUp() throws IOException {
        // Reset singleton instance
        YamlConfigurationManager.resetInstance();

        // Create test configuration directory
        testConfigDir = Paths.get(TEST_CONFIG_DIR);
        Files.createDirectories(testConfigDir);

        testConfigFile = testConfigDir.resolve(TEST_CONFIG_FILE);

        // Create test configuration file
        createTestConfigFile();
    }
    
    @AfterEach
    void tearDown() throws IOException {
        // Clear system properties
        System.clearProperty("config.file");

        // Clean up test files
        if (Files.exists(testConfigFile)) {
            Files.delete(testConfigFile);
        }
        if (Files.exists(testConfigDir)) {
            Files.delete(testConfigDir);
        }

        // Reset singleton instance
        YamlConfigurationManager.resetInstance();
    }
    
    @Test
    void testConfigurationLoading() {
        // Set system property to use test config
        System.setProperty("config.file", testConfigFile.toString());
        
        try {
            YamlConfigurationManager manager = YamlConfigurationManager.getInstance();
            P2PConfiguration config = manager.getConfiguration();
            
            assertNotNull(config);
            assertNotNull(config.getApplication());
            assertEquals("test-app", config.getApplication().getName());
            assertEquals("test", config.getApplication().getEnvironment());
            
        } finally {
            System.clearProperty("config.file");
        }
    }
    
    @Test
    void testPropertyAccess() {
        System.setProperty("config.file", testConfigFile.toString());
        
        try {
            YamlConfigurationManager manager = YamlConfigurationManager.getInstance();
            
            // Test string properties
            assertEquals("test-app", manager.getString("application.name"));
            assertEquals("test", manager.getString("application.environment"));
            assertEquals("default", manager.getString("nonexistent.property", "default"));
            
            // Test integer properties
            assertEquals(7000, manager.getInt("tracker.port", 6000));
            assertEquals(42, manager.getInt("nonexistent.property", 42));
            
            // Test boolean properties
            assertTrue(manager.getBoolean("healthcheck.enabled", false));
            assertFalse(manager.getBoolean("nonexistent.property", false));
            
            // Test long properties
            assertEquals(45000L, manager.getLong("peer.socketTimeoutMs", 30000L));
            assertEquals(123L, manager.getLong("nonexistent.property", 123L));
            
        } finally {
            System.clearProperty("config.file");
        }
    }
    
    @Test
    void testConfigurationChangeListener() throws IOException, InterruptedException {
        System.setProperty("config.file", testConfigFile.toString());
        
        try {
            YamlConfigurationManager manager = YamlConfigurationManager.getInstance();
            
            AtomicBoolean changeDetected = new AtomicBoolean(false);
            
            // Add change listener
            manager.addConfigurationChangeListener((oldConfig, newConfig) -> {
                changeDetected.set(true);
            });
            
            // Modify configuration file
            Thread.sleep(100); // Ensure different timestamp
            updateTestConfigFile();
            
            // Check for changes
            boolean reloaded = manager.checkAndReload();
            assertTrue(reloaded);
            assertTrue(changeDetected.get());
            
        } finally {
            System.clearProperty("config.file");
        }
    }
    
    @Test
    void testYamlSerialization() {
        System.setProperty("config.file", testConfigFile.toString());
        
        try {
            YamlConfigurationManager manager = YamlConfigurationManager.getInstance();
            String yamlString = manager.toYamlString();
            
            assertNotNull(yamlString);
            assertFalse(yamlString.isEmpty());
            assertTrue(yamlString.contains("application:"));
            assertTrue(yamlString.contains("tracker:"));
            
        } finally {
            System.clearProperty("config.file");
        }
    }
    
    @Test
    void testConfigurationSaving() throws IOException {
        System.setProperty("config.file", testConfigFile.toString());
        
        try {
            YamlConfigurationManager manager = YamlConfigurationManager.getInstance();
            
            Path outputPath = testConfigDir.resolve("output.yml");
            manager.saveConfiguration(outputPath);
            
            assertTrue(Files.exists(outputPath));
            assertTrue(Files.size(outputPath) > 0);
            
            // Clean up
            Files.delete(outputPath);
            
        } finally {
            System.clearProperty("config.file");
        }
    }
    
    @Test
    void testDefaultConfiguration() {
        // Reset to ensure clean state
        YamlConfigurationManager.resetInstance();

        // Don't set config file, should use defaults
        YamlConfigurationManager manager = YamlConfigurationManager.getInstance();
        P2PConfiguration config = manager.getConfiguration();

        assertNotNull(config);
        assertNotNull(config.getApplication());
        assertEquals("p2p-java-app", config.getApplication().getName());
        assertEquals("development", config.getApplication().getEnvironment());
        assertEquals("localhost", config.getTracker().getHost());
        assertEquals(6000, config.getTracker().getPort());
    }
    
    @Test
    void testNestedPropertyAccess() {
        System.setProperty("config.file", testConfigFile.toString());
        
        try {
            YamlConfigurationManager manager = YamlConfigurationManager.getInstance();
            
            // Test nested properties
            assertEquals("localhost", manager.getString("tracker.host"));
            assertEquals(7000, manager.getInt("tracker.port", 6000));
            assertEquals(15, manager.getInt("tracker.threadPool.size", 10));
            assertTrue(manager.getBoolean("healthcheck.enabled", false));
            
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
                  name: "test-app"
                  version: "1.0.0"
                  environment: "test"
                  profile: "test"
                
                tracker:
                  host: "localhost"
                  port: 7000
                  threadPool:
                    size: 15
                    maxSize: 30
                  peerTimeoutMs: 120000
                
                peer:
                  socketTimeoutMs: 45000
                  heartbeat:
                    intervalSeconds: 45
                    enabled: true
                
                healthcheck:
                  enabled: true
                  port: 8081
                  path: "/test-health"
                
                monitoring:
                  enabled: true
                  intervalMs: 45000
                """;
        
        Files.write(testConfigFile, testConfig.getBytes());
    }
    
    /**
     * Updates the test configuration file to trigger reload.
     */
    private void updateTestConfigFile() throws IOException {
        String updatedConfig = """
                application:
                  name: "test-app-updated"
                  version: "1.0.1"
                  environment: "test"
                  profile: "test"
                
                tracker:
                  host: "localhost"
                  port: 7001
                  threadPool:
                    size: 20
                    maxSize: 40
                  peerTimeoutMs: 150000
                
                peer:
                  socketTimeoutMs: 50000
                  heartbeat:
                    intervalSeconds: 60
                    enabled: true
                
                healthcheck:
                  enabled: true
                  port: 8082
                  path: "/test-health-updated"
                
                monitoring:
                  enabled: true
                  intervalMs: 60000
                """;
        
        Files.write(testConfigFile, updatedConfig.getBytes());
    }
}
