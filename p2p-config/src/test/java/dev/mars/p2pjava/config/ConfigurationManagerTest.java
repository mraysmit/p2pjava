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

        assertTrue(configManager.getBoolean("boolean.key.true", false), "getBoolean should return true for 'true'");
        assertFalse(configManager.getBoolean("boolean.key.false", true), "getBoolean should return false for 'false'");

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
    @Test
    void testLoadDefaultsFromPropertiesFile() throws IOException {
        // Create a properties file with custom default values
        File propsFile = tempDir.resolve("custom-defaults.properties").toFile();
        Properties props = new Properties();

        // Add some properties that override the defaults
        props.setProperty("tracker.host", "custom-host");
        props.setProperty("tracker.port", "7000");
        props.setProperty("indexserver.host", "custom-indexserver");

        try (FileWriter writer = new FileWriter(propsFile)) {
            props.store(writer, "Test custom default properties");
        }

        // Set the system property to use our custom file
        System.setProperty("config.file", propsFile.getAbsolutePath());

        try {
            // Reset the singleton instance using reflection 8-)
            java.lang.reflect.Field instanceField = ConfigurationManager.class.getDeclaredField("instance");
            instanceField.setAccessible(true);
            instanceField.set(null, null);

            // Get a new instance that will load our custom properties
            ConfigurationManager newInstance = ConfigurationManager.getInstance();

            // Initialize with empty args (it will use the system property)
            newInstance.initialize(new String[0]);

            // Verify that our custom properties were loaded and override the defaults
            assertEquals("custom-host", newInstance.get("tracker.host"), 
                    "Custom tracker.host should override default");
            assertEquals("7000", newInstance.get("tracker.port"), 
                    "Custom tracker.port should override default");
            assertEquals("custom-indexserver", newInstance.get("indexserver.host"), 
                    "Custom indexserver.host should override default");

            // Verify that other defaults are still present
            assertEquals("6001", newInstance.get("indexserver.port"), 
                    "Default indexserver.port should still be present");
            assertEquals("30000", newInstance.get("peer.socket.timeout.ms"), 
                    "Default peer.socket.timeout.ms should still be present");
        } catch (NoSuchFieldException | IllegalAccessException e) {
            fail("Failed to use reflection to reset singleton instance: " + e.getMessage());
        } finally {
            // Clear the system property
            System.clearProperty("config.file");

            // Reset the singleton instance
            try {
                java.lang.reflect.Field instanceField = ConfigurationManager.class.getDeclaredField("instance");
                instanceField.setAccessible(true);
                instanceField.set(null, null);

                // Get a fresh instance
                ConfigurationManager.getInstance();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    @Test
    void testFallbackToHardcodedDefaults() {
        // Verify that some hard-coded defaults exist
        // These should be present regardless of whether they were loaded from a properties file
        // or set as hard-coded defaults

        // Tracker defaults
        assertEquals("localhost", configManager.get("tracker.host", "not-set"), 
                "tracker.host default should be set");
        assertEquals("6000", configManager.get("tracker.port", "not-set"), 
                "tracker.port default should be set");

        // IndexServer defaults
        assertEquals("localhost", configManager.get("indexserver.host", "not-set"), 
                "indexserver.host default should be set");
        assertEquals("6001", configManager.get("indexserver.port", "not-set"), 
                "indexserver.port default should be set");

        // Peer defaults
        assertEquals("30000", configManager.get("peer.socket.timeout.ms", "not-set"), 
                "peer.socket.timeout.ms default should be set");

        // Health check defaults
        assertEquals("true", configManager.get("healthcheck.enabled", "not-set"), 
                "healthcheck.enabled default should be set");

        // Bootstrap defaults
        assertEquals("true", configManager.get("bootstrap.auto.start", "not-set"), 
                "bootstrap.auto.start default should be set");
    }

    @Test
    void testLoadCustomPropertiesFileFromCommandLineArgs() throws IOException {
        // Create a properties file with custom values
        File propsFile = tempDir.resolve("custom-config.properties").toFile();
        Properties props = new Properties();

        // Add some custom properties
        props.setProperty("custom.property1", "value1");
        props.setProperty("custom.property2", "value2");
        props.setProperty("tracker.host", "custom-host-from-args");

        try (FileWriter writer = new FileWriter(propsFile)) {
            props.store(writer, "Test custom properties for command line args test");
        }

        try {
            // Reset the singleton instance using reflection
            java.lang.reflect.Field instanceField = ConfigurationManager.class.getDeclaredField("instance");
            instanceField.setAccessible(true);
            instanceField.set(null, null);

            // Get a new instance
            ConfigurationManager newInstance = ConfigurationManager.getInstance();

            // Initialize with command line args specifying the custom properties file
            String[] args = {"--config.file=" + propsFile.getAbsolutePath()};
            boolean initialized = newInstance.initialize(args);

            // Verify initialization was successful
            assertTrue(initialized, "Initialization with custom properties file should be successful");

            // Verify that properties from the custom file were loaded
            assertEquals("value1", newInstance.get("custom.property1"), 
                    "Custom property1 should be loaded from command line args");
            assertEquals("value2", newInstance.get("custom.property2"), 
                    "Custom property2 should be loaded from command line args");
            assertEquals("custom-host-from-args", newInstance.get("tracker.host"), 
                    "tracker.host should be overridden from command line args");

            // Verify that other defaults are still present
            assertEquals("6000", newInstance.get("tracker.port", "not-set"), 
                    "Default tracker.port should still be present");
        } catch (NoSuchFieldException | IllegalAccessException e) {
            fail("Failed to use reflection to reset singleton instance: " + e.getMessage());
        } finally {
            // Reset the singleton instance
            try {
                java.lang.reflect.Field instanceField = ConfigurationManager.class.getDeclaredField("instance");
                instanceField.setAccessible(true);
                instanceField.set(null, null);

                // Get a fresh instance
                ConfigurationManager.getInstance();
            } catch (Exception e) {
                // Ignore
            }
        }
    }
}
