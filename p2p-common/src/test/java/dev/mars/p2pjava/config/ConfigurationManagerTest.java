package dev.mars.p2pjava.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the ConfigurationManager class.
 */
public class ConfigurationManagerTest {

    private ConfigurationManager configManager;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Get a fresh instance for each test
        configManager = ConfigurationManager.getInstance();
    }

    @AfterEach
    void tearDown() {
        // Clear any configuration set during the test
        configManager.getAll().keySet().forEach(key -> 
            System.clearProperty("config." + key));
    }

    @Test
    void testGetInstance() {
        // Test that getInstance returns the same instance
        ConfigurationManager instance1 = ConfigurationManager.getInstance();
        ConfigurationManager instance2 = ConfigurationManager.getInstance();

        assertSame(instance1, instance2, "getInstance should return the same instance");
    }

    @Test
    void testGetAndSet() {
        // Test setting and getting a configuration value
        configManager.set("test.key", "test.value");

        assertEquals("test.value", configManager.get("test.key"), 
                "get should return the value that was set");
    }

    @Test
    void testGetWithDefault() {
        // Test getting a value with a default
        String value = configManager.get("nonexistent.key", "default.value");

        assertEquals("default.value", value, 
                "get with default should return the default value for nonexistent keys");
    }

    @Test
    void testGetInt() {
        // Test getting an integer value
        configManager.set("int.key", "123");

        assertEquals(123, configManager.getInt("int.key", 0), 
                "getInt should return the integer value");

        // Test with invalid integer
        configManager.set("invalid.int.key", "not-an-int");

        assertEquals(456, configManager.getInt("invalid.int.key", 456), 
                "getInt should return the default value for invalid integers");

        // Test with nonexistent key
        assertEquals(789, configManager.getInt("nonexistent.key", 789), 
                "getInt should return the default value for nonexistent keys");
    }

    @Test
    void testGetLong() {
        // Test getting a long value
        configManager.set("long.key", "123456789012");

        assertEquals(123456789012L, configManager.getLong("long.key", 0L), 
                "getLong should return the long value");

        // Test with invalid long
        configManager.set("invalid.long.key", "not-a-long");

        assertEquals(456L, configManager.getLong("invalid.long.key", 456L), 
                "getLong should return the default value for invalid longs");

        // Test with nonexistent key
        assertEquals(789L, configManager.getLong("nonexistent.key", 789L), 
                "getLong should return the default value for nonexistent keys");
    }

    @Test
    void testGetBoolean() {
        // Test getting a boolean value
        configManager.set("boolean.key.true", "true");
        configManager.set("boolean.key.false", "false");

        assertTrue(configManager.getBoolean("boolean.key.true", false), 
                "getBoolean should return true for 'true'");
        assertFalse(configManager.getBoolean("boolean.key.false", true), 
                "getBoolean should return false for 'false'");

        // Test with invalid boolean
        configManager.set("invalid.boolean.key", "not-a-boolean");

        assertTrue(configManager.getBoolean("invalid.boolean.key", true), 
                "getBoolean should return the default value for invalid booleans");

        // Test with nonexistent key
        assertFalse(configManager.getBoolean("nonexistent.key", false), 
                "getBoolean should return the default value for nonexistent keys");
    }

    @Test
    void testGetAll() {
        // Set some configuration values
        configManager.set("key1", "value1");
        configManager.set("key2", "value2");

        // Get all configuration values
        Map<String, String> allConfig = configManager.getAll();

        // Verify that the map contains the expected values
        assertTrue(allConfig.containsKey("key1"), "All config should contain key1");
        assertTrue(allConfig.containsKey("key2"), "All config should contain key2");
        assertEquals("value1", allConfig.get("key1"), "Value for key1 should match");
        assertEquals("value2", allConfig.get("key2"), "Value for key2 should match");
    }

    @Test
    void testInitializeWithPropertiesFile() throws IOException {
        // Create a properties file
        File propsFile = tempDir.resolve("test.properties").toFile();
        Properties props = new Properties();
        props.setProperty("test.prop1", "value1");
        props.setProperty("test.prop2", "value2");

        try (FileWriter writer = new FileWriter(propsFile)) {
            props.store(writer, "Test properties");
        }

        // Initialize with the properties file
        String[] args = {"--config.file=" + propsFile.getAbsolutePath()};
        boolean initialized = configManager.initialize(args);

        // Verify that the properties were loaded
        assertTrue(initialized, "Initialization with properties file should be successful");
        assertEquals("value1", configManager.get("test.prop1"), "Property test.prop1 should be loaded");
        assertEquals("value2", configManager.get("test.prop2"), "Property test.prop2 should be loaded");
    }

    @Test
    void testFindAvailablePort() {
        // Test finding an available port
        int port = configManager.findAvailablePort(8000);

        // The port should be >= 8000
        assertTrue(port >= 8000, "Available port should be >= 8000");
    }

    @Test
    void testInitialize() {
        // Test initializing with command-line arguments
        String[] args = {
            "--config.test.arg1=value1",
            "--config.test.arg2=value2"
        };

        boolean initialized = configManager.initialize(args);

        // Verify that initialization was successful
        assertTrue(initialized, "Initialization should be successful");
        assertEquals("value1", configManager.get("test.arg1"), "Argument test.arg1 should be loaded");
        assertEquals("value2", configManager.get("test.arg2"), "Argument test.arg2 should be loaded");
    }
}
