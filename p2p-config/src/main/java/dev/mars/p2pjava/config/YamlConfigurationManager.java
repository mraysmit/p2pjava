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
import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.List;
import java.util.Map;

/**
 * Enhanced YAML-based configuration manager with dynamic reloading capabilities.
 * Supports structured configuration, file watching, and change notifications.
 */
public class YamlConfigurationManager {
    private static final Logger logger = Logger.getLogger(YamlConfigurationManager.class.getName());
    
    private static volatile YamlConfigurationManager instance;
    private static final Object lock = new Object();
    
    private final ObjectMapper yamlMapper;
    private volatile P2PConfiguration configuration;
    private final Map<String, Object> flattenedConfig = new ConcurrentHashMap<>();
    private final List<ConfigurationChangeListener> listeners = new CopyOnWriteArrayList<>();
    
    // Configuration file paths
    private static final String[] DEFAULT_CONFIG_PATHS = {
        "config/application.yml",
        "config/application.yaml", 
        "application.yml",
        "application.yaml"
    };
    
    private Path currentConfigPath;
    private long lastModified = 0;
    
    /**
     * Private constructor for singleton.
     */
    private YamlConfigurationManager() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.configuration = new P2PConfiguration(); // Start with defaults
        loadConfiguration();
        flattenConfiguration();
    }
    
    /**
     * Gets the singleton instance.
     */
    public static YamlConfigurationManager getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new YamlConfigurationManager();
                }
            }
        }
        return instance;
    }

    /**
     * Resets the singleton instance (for testing purposes).
     */
    public static synchronized void resetInstance() {
        instance = null;
    }
    
    /**
     * Loads configuration from YAML files.
     */
    private void loadConfiguration() {
        P2PConfiguration loadedConfig = null;
        
        // Try custom config file from system property
        String customConfigPath = System.getProperty("config.file");
        if (customConfigPath != null && !customConfigPath.isEmpty()) {
            loadedConfig = loadFromFile(Paths.get(customConfigPath));
        }
        
        // Try default paths if custom config not loaded
        if (loadedConfig == null) {
            for (String path : DEFAULT_CONFIG_PATHS) {
                loadedConfig = loadFromFile(Paths.get(path));
                if (loadedConfig != null) {
                    break;
                }
                
                // Try classpath
                loadedConfig = loadFromClasspath(path);
                if (loadedConfig != null) {
                    break;
                }
            }
        }
        
        if (loadedConfig != null) {
            this.configuration = loadedConfig;
            logger.info("Configuration loaded successfully from: " + 
                       (currentConfigPath != null ? currentConfigPath : "classpath"));
        } else {
            logger.info("No configuration file found, using defaults");
        }
    }
    
    /**
     * Loads configuration from a file path.
     */
    private P2PConfiguration loadFromFile(Path path) {
        if (!Files.exists(path)) {
            return null;
        }
        
        try {
            P2PConfiguration config = yamlMapper.readValue(path.toFile(), P2PConfiguration.class);
            currentConfigPath = path;
            lastModified = Files.getLastModifiedTime(path).toMillis();
            logger.info("Loaded configuration from file: " + path);
            return config;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to load configuration from file: " + path, e);
            return null;
        }
    }
    
    /**
     * Loads configuration from classpath.
     */
    private P2PConfiguration loadFromClasspath(String path) {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(path)) {
            if (inputStream == null) {
                return null;
            }
            
            P2PConfiguration config = yamlMapper.readValue(inputStream, P2PConfiguration.class);
            logger.info("Loaded configuration from classpath: " + path);
            return config;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to load configuration from classpath: " + path, e);
            return null;
        }
    }
    
    /**
     * Flattens the configuration for easy property access.
     */
    private void flattenConfiguration() {
        flattenedConfig.clear();
        try {
            Map<String, Object> configMap = yamlMapper.convertValue(configuration, Map.class);
            flattenMap("", configMap, flattenedConfig);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to flatten configuration", e);
        }
    }
    
    /**
     * Recursively flattens a nested map.
     */
    @SuppressWarnings("unchecked")
    private void flattenMap(String prefix, Map<String, Object> map, Map<String, Object> result) {
        if (map == null) {
            return;
        }

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (key == null) {
                continue; // Skip null keys
            }

            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;

            if (value instanceof Map) {
                flattenMap(fullKey, (Map<String, Object>) value, result);
            } else if (value != null) {
                result.put(fullKey, value);
            }
        }
    }
    
    /**
     * Gets the complete configuration object.
     */
    public P2PConfiguration getConfiguration() {
        return configuration;
    }
    
    /**
     * Gets a configuration value by key.
     */
    public String getString(String key) {
        Object value = flattenedConfig.get(key);
        return value != null ? value.toString() : null;
    }
    
    /**
     * Gets a configuration value by key with default.
     */
    public String getString(String key, String defaultValue) {
        String value = getString(key);
        return value != null ? value : defaultValue;
    }
    
    /**
     * Gets an integer configuration value.
     */
    public int getInt(String key, int defaultValue) {
        Object value = flattenedConfig.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                logger.warning("Invalid integer value for key " + key + ": " + value);
            }
        }
        return defaultValue;
    }
    
    /**
     * Gets a long configuration value.
     */
    public long getLong(String key, long defaultValue) {
        Object value = flattenedConfig.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                logger.warning("Invalid long value for key " + key + ": " + value);
            }
        }
        return defaultValue;
    }
    
    /**
     * Gets a boolean configuration value.
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = flattenedConfig.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }
    
    /**
     * Checks if configuration has changed and reloads if necessary.
     */
    public boolean checkAndReload() {
        if (currentConfigPath == null || !Files.exists(currentConfigPath)) {
            return false;
        }
        
        try {
            long currentModified = Files.getLastModifiedTime(currentConfigPath).toMillis();
            if (currentModified > lastModified) {
                logger.info("Configuration file changed, reloading...");
                P2PConfiguration oldConfig = this.configuration;
                loadConfiguration();
                flattenConfiguration();
                
                // Notify listeners
                notifyConfigurationChanged(oldConfig, this.configuration);
                return true;
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to check configuration file modification time", e);
        }
        
        return false;
    }
    
    /**
     * Adds a configuration change listener.
     */
    public void addConfigurationChangeListener(ConfigurationChangeListener listener) {
        listeners.add(listener);
    }
    
    /**
     * Removes a configuration change listener.
     */
    public void removeConfigurationChangeListener(ConfigurationChangeListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Notifies all listeners of configuration changes.
     */
    private void notifyConfigurationChanged(P2PConfiguration oldConfig, P2PConfiguration newConfig) {
        for (ConfigurationChangeListener listener : listeners) {
            try {
                listener.onConfigurationChanged(oldConfig, newConfig);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error notifying configuration change listener", e);
            }
        }
    }
    
    /**
     * Saves the current configuration to a file.
     */
    public void saveConfiguration(Path path) throws IOException {
        yamlMapper.writeValue(path.toFile(), configuration);
        logger.info("Configuration saved to: " + path);
    }
    
    /**
     * Gets the configuration as YAML string.
     */
    public String toYamlString() {
        try {
            return yamlMapper.writeValueAsString(configuration);
        } catch (JsonProcessingException e) {
            logger.log(Level.WARNING, "Failed to convert configuration to YAML string", e);
            return "";
        }
    }
    
    /**
     * Interface for configuration change listeners.
     */
    public interface ConfigurationChangeListener {
        void onConfigurationChanged(P2PConfiguration oldConfig, P2PConfiguration newConfig);
    }
}
