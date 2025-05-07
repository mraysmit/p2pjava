package dev.mars.p2pjava.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralized configuration management for the P2P system.
 * This class provides methods for loading configuration from properties files,
 * environment variables, and command-line arguments.
 */
public class ConfigurationManager {
    private static final Logger logger = Logger.getLogger(ConfigurationManager.class.getName());

    // Singleton instance
    private static ConfigurationManager instance;

    // Configuration storage
    private final Map<String, String> configuration = new ConcurrentHashMap<>();

    // Default configuration file paths
    private static final String[] DEFAULT_CONFIG_PATHS = {
        "config/application.properties",
        "application.properties",
        "config.properties"
    };

    // Default properties file path (in classpath)
    private static final String DEFAULT_PROPERTIES_FILE = "config/config-manager-defaults.properties";

    /**
     * Private constructor to enforce singleton pattern.
     */
    private ConfigurationManager() {
        // Load default configuration
        loadDefaults();
    }

    /**
     * Gets the singleton instance of the configuration manager.
     *
     * @return The singleton instance
     */
    public static synchronized ConfigurationManager getInstance() {
        if (instance == null) {
            instance = new ConfigurationManager();
        }
        return instance;
    }

    /**
     * Loads default configuration values.
     */
    private void loadDefaults() {

        // First try to load defaults from properties file
        boolean loadedFromFile = loadDefaultsFromPropertiesFile();

        if ((!loadedFromFile)) {
            logger.info("No default properties file found at location " + DEFAULT_PROPERTIES_FILE);
            logger.info("Using hardcoded default configuration values");

            // Tracker defaults
            setIfNotExists("tracker.host", "localhost");
            setIfNotExists("tracker.port", "6000");
            setIfNotExists("tracker.thread.pool.size", "10");
            setIfNotExists("tracker.peer.timeout.ms", "90000");

            // IndexServer defaults
            setIfNotExists("indexserver.host", "localhost");
            setIfNotExists("indexserver.port", "6001");
            setIfNotExists("indexserver.thread.pool.size", "10");
            setIfNotExists("indexserver.storage.dir", "data");
            setIfNotExists("indexserver.storage.file", "file_index.dat");
            setIfNotExists("indexserver.cache.ttl.ms", "60000");
            setIfNotExists("indexserver.cache.refresh.ms", "300000");
            setIfNotExists("indexserver.connection.pool.max", "100");
            setIfNotExists("indexserver.connection.timeout.ms", "5000");

            // Peer defaults
            setIfNotExists("peer.socket.timeout.ms", "30000");
            setIfNotExists("peer.heartbeat.interval.seconds", "30");

            // Health check defaults
            setIfNotExists("healthcheck.enabled", "true");
            setIfNotExists("healthcheck.port", "8080");
            setIfNotExists("healthcheck.path", "/health");

            // Bootstrap defaults
            setIfNotExists("bootstrap.auto.start", "true");
            setIfNotExists("bootstrap.startup.timeout.seconds", "30");
            setIfNotExists("bootstrap.dynamic.ports", "false");
        }
    }

    /**
     * Attempts to load default configuration values from a properties file.
     *
     * @return true if defaults were successfully loaded from file, false otherwise
     */
    private boolean loadDefaultsFromPropertiesFile() {
        try {
            // Try to load from classpath resource
            InputStream input = getClass().getClassLoader().getResourceAsStream(DEFAULT_PROPERTIES_FILE);
            if (input != null) {
                Properties props = new Properties();
                props.load(input);

                // Add all properties to configuration using setIfNotExists
                for (String key : props.stringPropertyNames()) {
                    setIfNotExists(key, props.getProperty(key));
                }

                input.close();
                logger.info("Loaded default configuration from " + DEFAULT_PROPERTIES_FILE);
                return true;
            }

            // If not found in classpath, try file system
            Path path = Paths.get("src/main/resources/" + DEFAULT_PROPERTIES_FILE);
            if (Files.exists(path)) {
                try (InputStream fileInput = new FileInputStream(path.toFile())) {
                    Properties props = new Properties();
                    props.load(fileInput);

                    // Add all properties to configuration using setIfNotExists
                    for (String key : props.stringPropertyNames()) {
                        setIfNotExists(key, props.getProperty(key));
                    }

                    logger.info("Loaded default configuration from file: " + path);
                    return true;
                }
            }

            logger.info("Default properties file not found: " + DEFAULT_PROPERTIES_FILE);
            return false;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error loading default properties file", e);
            return false;
        }
    }

    /**
     * Sets a configuration value if it doesn't already exist.
     *
     * @param key The configuration key
     * @param value The configuration value
     */
    private void setIfNotExists(String key, String value) {
        configuration.putIfAbsent(key, value);
    }

    /**
     * Initializes the configuration manager by loading configuration from
     * properties files, environment variables, and command-line arguments.
     *
     * @param args Command-line arguments
     * @return true if initialization was successful, false otherwise
     */
    public boolean initialize(String[] args) {
        try {
            // Load from default properties files
            loadFromPropertiesFiles();

            // Load from environment variables
            loadFromEnvironment();

            // Load from command-line arguments
            loadFromCommandLine(args);

            // Validate configuration
            if (!validateConfiguration()) {
                logger.severe("Configuration validation failed");
                return false;
            }

            logger.info("Configuration initialized successfully");
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error initializing configuration", e);
            return false;
        }
    }

    /**
     * Loads configuration from properties files.
     */
    private void loadFromPropertiesFiles() {
        boolean loaded = false;

        // Try to load from custom config file specified in system property
        String customConfigPath = System.getProperty("config.file");
        if (customConfigPath != null && !customConfigPath.isEmpty()) {
            loaded = loadPropertiesFile(customConfigPath);
        }

        // If custom config file not loaded, try default paths
        if (!loaded) {
            for (String path : DEFAULT_CONFIG_PATHS) {
                if (loadPropertiesFile(path)) {
                    loaded = true;
                    break;
                }
            }
        }

        if (!loaded) {
            logger.info("No properties files found, using default configuration");
        }
    }

    /**
     * Loads configuration from a properties file.
     *
     * @param filePath The path to the properties file
     * @return true if the file was loaded successfully, false otherwise
     */
    private boolean loadPropertiesFile(String filePath) {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            logger.fine("Properties file not found: " + filePath);
            return false;
        }

        try (InputStream input = new FileInputStream(path.toFile())) {
            Properties props = new Properties();
            props.load(input);

            // Add all properties to configuration
            for (String key : props.stringPropertyNames()) {
                configuration.put(key, props.getProperty(key));
            }

            logger.info("Loaded configuration from " + filePath);
            return true;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error loading properties file: " + filePath, e);
            return false;
        }
    }

    /**
     * Loads configuration from environment variables.
     * Environment variables are expected to be in the format P2P_UPPERCASE_WITH_UNDERSCORES
     * and will be converted to lowercase.with.dots format.
     */
    private void loadFromEnvironment() {
        Map<String, String> env = System.getenv();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            // Only process environment variables that start with P2P_
            if (key.startsWith("P2P_")) {
                // Convert P2P_UPPERCASE_WITH_UNDERSCORES to lowercase.with.dots
                String configKey = key.substring(4).toLowerCase().replace('_', '.');
                configuration.put(configKey, value);
            }
        }
    }

    /**
     * Loads configuration from command-line arguments.
     * Command-line arguments are expected to be in the format --key=value.
     * Arguments starting with --config. will have the config. prefix removed.
     *
     * @param args Command-line arguments
     */
    private void loadFromCommandLine(String[] args) {
        if (args == null || args.length == 0) {
            return;
        }

        for (String arg : args) {
            if (arg.startsWith("--") && arg.contains("=")) {
                String[] parts = arg.substring(2).split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0];
                    String value = parts[1];

                    // Handle config.file specially to set the system property
                    if (key.equals("config.file")) {
                        System.setProperty("config.file", value);
                        // Re-load from properties files
                        loadFromPropertiesFiles();
                    }

                    // Remove config. prefix if present
                    if (key.startsWith("config.")) {
                        key = key.substring(7);
                    }

                    configuration.put(key, value);
                }
            }
        }
    }

    /**
     * Validates the configuration.
     *
     * @return true if the configuration is valid, false otherwise
     */
    private boolean validateConfiguration() {
        // Validate required configuration
        String[] requiredKeys = {
            "tracker.host",
            "tracker.port",
            "indexserver.host",
            "indexserver.port"
        };

        for (String key : requiredKeys) {
            if (!configuration.containsKey(key) || configuration.get(key).isEmpty()) {
                logger.severe("Required configuration missing: " + key);
                return false;
            }
        }

        // Validate port numbers
        String[] portKeys = {
            "tracker.port",
            "indexserver.port",
            "healthcheck.port"
        };

        for (String key : portKeys) {
            String value = configuration.get(key);
            if (value != null) {
                try {
                    int port = Integer.parseInt(value);
                    if (port < 0 || port > 65535) {
                        logger.severe("Invalid port number for " + key + ": " + port);
                        return false;
                    }
                } catch (NumberFormatException e) {
                    logger.severe("Invalid port number format for " + key + ": " + value);
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Gets a configuration value.
     *
     * @param key The configuration key
     * @return The configuration value, or null if not found
     */
    public String get(String key) {
        return configuration.get(key);
    }

    /**
     * Gets a configuration value, or a default value if not found.
     *
     * @param key The configuration key
     * @param defaultValue The default value
     * @return The configuration value, or the default value if not found
     */
    public String get(String key, String defaultValue) {
        return configuration.getOrDefault(key, defaultValue);
    }

    /**
     * Gets a configuration value as an integer.
     *
     * @param key The configuration key
     * @param defaultValue The default value
     * @return The configuration value as an integer, or the default value if not found or not a valid integer
     */
    public int getInt(String key, int defaultValue) {
        String value = get(key);
        if (value == null) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warning("Invalid integer format for " + key + ": " + value);
            return defaultValue;
        }
    }

    /**
     * Gets a configuration value as a long.
     *
     * @param key The configuration key
     * @param defaultValue The default value
     * @return The configuration value as a long, or the default value if not found or not a valid long
     */
    public long getLong(String key, long defaultValue) {
        String value = get(key);
        if (value == null) {
            return defaultValue;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            logger.warning("Invalid long format for " + key + ": " + value);
            return defaultValue;
        }
    }

    /**
     * Gets a configuration value as a boolean.
     *
     * @param key The configuration key
     * @param defaultValue The default value
     * @return The configuration value as a boolean, or the default value if not found or not a valid boolean
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key);
        if (value == null) {
            return defaultValue;
        }

        value = value.toLowerCase();
        if (value.equals("true") || value.equals("false")) {
            return Boolean.parseBoolean(value);
        } else {
            logger.warning("Invalid boolean format for " + key + ": " + value);
            return defaultValue;
        }
    }

    /**
     * Sets a configuration value.
     *
     * @param key The configuration key
     * @param value The configuration value
     */
    public void set(String key, String value) {
        configuration.put(key, value);
    }

    /**
     * Gets all configuration values.
     *
     * @return A map of all configuration values
     */
    public Map<String, String> getAll() {
        return new HashMap<>(configuration);
    }

    /**
     * Finds an available port starting from the specified port.
     *
     * @param startPort The port to start searching from
     * @return An available port, or -1 if no port is available
     */
    public int findAvailablePort(int startPort) {
        for (int port = startPort; port < startPort + 1000; port++) {
            try (java.net.ServerSocket socket = new java.net.ServerSocket(port)) {
                return port;
            } catch (IOException e) {
                // Port is in use, try the next one
            }
        }
        return -1;
    }
}