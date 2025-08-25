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


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * A test class with a Main method to demonstrate loading a custom properties file
 * location from command-line arguments.
 * 
 * Usage: java dev.mars.p2pjava.config.ConfigurationManagerMainTest --config.file=/path/to/custom/properties/file
 */
public class ConfigurationManagerMainTest {

    /**
     * Main method that demonstrates loading a custom properties file location
     * from command-line arguments.
     * 
     * @param args Command-line arguments
     */
    public static void main(String[] args) {
        try {
            // Create a temporary properties file if no file is specified
            if (args.length == 0 || !args[0].startsWith("--config.file=")) {
                System.out.println("No custom properties file specified. Creating a temporary one...");
                createTemporaryPropertiesFile(args);
            }
            
            // Get the ConfigurationManager instance
            ConfigurationManager configManager = ConfigurationManager.getInstance();
            
            // Initialize with the command-line arguments
            boolean initialized = configManager.initialize(args);
            
            if (initialized) {
                System.out.println("Configuration initialized successfully!");
                
                // Print all configuration values
                System.out.println("\nConfiguration values:");
                configManager.getAll().forEach((key, value) -> 
                    System.out.println(key + " = " + value));
                
                // Check for custom properties
                String customProp1 = configManager.get("custom.property1");
                if (customProp1 != null) {
                    System.out.println("\nFound custom property: custom.property1 = " + customProp1);
                }
                
                // Check for overridden defaults
                String trackerHost = configManager.get("tracker.host");
                System.out.println("tracker.host = " + trackerHost);
                if (!"localhost".equals(trackerHost)) {
                    System.out.println("Default tracker.host has been overridden!");
                }
            } else {
                System.err.println("Failed to initialize configuration!");
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Creates a temporary properties file and updates the command-line arguments
     * to use this file.
     * 
     * @param args Command-line arguments to update
     * @throws IOException If an I/O error occurs
     */
    private static void createTemporaryPropertiesFile(String[] args) throws IOException {
        // Create a temporary directory
        Path tempDir = Files.createTempDirectory("config-test");
        
        // Create a properties file
        File propsFile = tempDir.resolve("custom-config.properties").toFile();
        Properties props = new Properties();
        
        // Add some custom properties
        props.setProperty("custom.property1", "value1-from-main");
        props.setProperty("custom.property2", "value2-from-main");
        props.setProperty("tracker.host", "custom-host-from-main");
        
        // Store the properties
        try (FileWriter writer = new FileWriter(propsFile)) {
            props.store(writer, "Test custom properties for Main method test");
        }
        
        System.out.println("Created temporary properties file: " + propsFile.getAbsolutePath());
        
        // Update the command-line arguments
        if (args.length == 0) {
            args = new String[1];
        }
        args[0] = "--config.file=" + propsFile.getAbsolutePath();
        
        System.out.println("Updated command-line arguments: " + args[0]);
    }
}