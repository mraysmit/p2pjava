
# Enhancing ConfigurationManager.loadDefaults Method

To enhance the `loadDefaults` method to load default values from a properties file in the standard module properties folder, I'll provide a complete solution with code changes and implementation steps.

## Current Implementation

Currently, the `loadDefaults` method hardcodes all default values:

```java
private void loadDefaults() {
    // Tracker defaults
    setIfNotExists("tracker.host", "localhost");
    setIfNotExists("tracker.port", "6000");
    // ... many more hardcoded defaults
}
```

## Solution

### 1. Create a Default Properties File

Create a file at `p2p-common/src/main/resources/config/defaults.properties` with all the current default values:

```properties
# Tracker defaults
tracker.host=localhost
tracker.port=6000
tracker.thread.pool.size=10
tracker.peer.timeout.ms=90000

# IndexServer defaults
indexserver.host=localhost
indexserver.port=6001
indexserver.thread.pool.size=10
indexserver.storage.dir=data
indexserver.storage.file=file_index.dat
indexserver.cache.ttl.ms=60000
indexserver.cache.refresh.ms=300000
indexserver.connection.pool.max=100
indexserver.connection.timeout.ms=5000

# Peer defaults
peer.socket.timeout.ms=30000
peer.heartbeat.interval.seconds=30

# Health check defaults
healthcheck.enabled=true
healthcheck.port=8080
healthcheck.path=/health

# Bootstrap defaults
bootstrap.auto.start=true
bootstrap.startup.timeout.seconds=30
bootstrap.dynamic.ports=false
```

### 2. Modify the ConfigurationManager Class

Update the `ConfigurationManager` class with the following changes:

```java
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
    private static final String DEFAULT_PROPERTIES_FILE = "config/defaults.properties";
    
    /**
     * Private constructor to enforce singleton pattern.
     */
    private ConfigurationManager() {
        // Load default configuration
        loadDefaults();
    }
    
    // ... other methods ...
    
    /**
     * Loads default configuration values.
     */
    private void loadDefaults() {
        // First try to load defaults from properties file
        boolean loadedFromFile = loadDefaultsFromPropertiesFile();
        
        // If loading from file failed, use hardcoded defaults
        if (!loadedFromFile) {
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
    
    // ... rest of the class ...
}
```

## Implementation Steps

1. Create the resources directory structure:
   ```
   mkdir -p p2p-common/src/main/resources/config
   ```

2. Create the defaults.properties file with all the default values as shown above.

3. Update the ConfigurationManager.java file with the changes shown above.

4. If you're using Maven, make sure your pom.xml includes the resources directory:
   ```xml
   <build>
       <resources>
           <resource>
               <directory>src/main/resources</directory>
           </resource>
       </resources>
   </build>
   ```

5. Rebuild the project to include the new resources file in the classpath.

## Benefits of This Approach

1. **Separation of Configuration from Code**: Default values are now stored in a properties file instead of being hardcoded in the Java code.

2. **Easier Maintenance**: Updating default values no longer requires code changes and recompilation.

3. **Fallback Mechanism**: The system will still work with hardcoded defaults if the properties file is missing.

4. **Flexibility**: The properties file can be easily modified for different environments or deployments.

This implementation maintains backward compatibility while providing a more flexible and maintainable way to manage default configuration values.