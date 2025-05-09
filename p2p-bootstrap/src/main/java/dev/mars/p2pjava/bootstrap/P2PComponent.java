package dev.mars.p2pjava.bootstrap;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized definition of all P2P system components.
 * This class serves as a single source of truth for component definitions,
 * eliminating duplication between BootstrapService and P2PBootstrap.
 */
public class P2PComponent {
    // Component IDs
    public static final String TRACKER = "tracker";
    public static final String INDEX_SERVER = "indexserver";
    public static final String PEER = "peer";
    public static final String CACHE = "cache";
    public static final String CONNECTION = "connection";
    public static final String DISCOVERY = "discovery";
    public static final String STORAGE = "storage";
    public static final String AUTH = "auth";
    public static final String ALL = "all";

    // Component configuration
    private static final Map<String, ComponentConfig> COMPONENT_CONFIGS = new ConcurrentHashMap<>();

    // Dependencies between components (dependent -> dependencies)
    private static final Map<String, Set<String>> COMPONENT_DEPENDENCIES = new ConcurrentHashMap<>();

    // Static initializer to populate component configurations and dependencies
    static {
        // Initialize component configurations

        // Tracker service configuration
        COMPONENT_CONFIGS.put(TRACKER, new ComponentConfig(
                6000, 
                "tracker.port", 
                "tracker",
                "dev.mars.p2pjava.Tracker",
                "startTracker",
                "stopTracker"));

        // Index server configuration
        COMPONENT_CONFIGS.put(INDEX_SERVER, new ComponentConfig(
                6001, 
                "indexserver.port", 
                "index server",
                "dev.mars.p2pjava.IndexServer",
                "startIndexServer",
                "stopIndexServer"));

        // Peer service configuration
        COMPONENT_CONFIGS.put(PEER, new ComponentConfig(
                7000, 
                "peer.port", 
                "peer",
                null, // Peer startup is handled separately
                null,
                null));

        // Cache service configuration
        COMPONENT_CONFIGS.put(CACHE, new ComponentConfig(
                0, 
                "", 
                "cache manager",
                "dev.mars.p2pjava.cache.CacheManager",
                "startCacheMaintenance",
                "shutdown"));

        // Connection service configuration
        COMPONENT_CONFIGS.put(CONNECTION, new ComponentConfig(
                0, 
                "", 
                "connection pool",
                "dev.mars.p2pjava.connection.ConnectionPool",
                "executeWithConnection",
                "shutdown"));

        // Discovery service configuration
        COMPONENT_CONFIGS.put(DISCOVERY, new ComponentConfig(
                0, 
                "", 
                "service registry",
                "dev.mars.p2pjava.discovery.InMemoryServiceRegistry",
                "start",
                "stop"));

        // Storage service configuration
        COMPONENT_CONFIGS.put(STORAGE, new ComponentConfig(
                0, 
                "", 
                "file storage",
                "dev.mars.p2pjava.storage.FileBasedIndexStorage",
                "initialize",
                "shutdown"));

        // Auth service configuration
        COMPONENT_CONFIGS.put(AUTH, new ComponentConfig(
                0, 
                "", 
                "authentication service",
                "dev.mars.p2pjava.auth.AuthService",
                "start",
                "stop"));

        // Initialize component dependencies

        // Index server depends on tracker
        addDependency(INDEX_SERVER, TRACKER);

        // Storage depends on discovery
        addDependency(STORAGE, DISCOVERY);

        // Index server depends on storage
        addDependency(INDEX_SERVER, STORAGE);

        // Index server depends on cache
        addDependency(INDEX_SERVER, CACHE);

        // Index server depends on connection
        addDependency(INDEX_SERVER, CONNECTION);

        // Index server depends on auth
        addDependency(INDEX_SERVER, AUTH);

        // Peer depends on tracker (handled separately in P2PBootstrap)

        // Peer depends on auth
        addDependency(PEER, AUTH);
    }

    /**
     * Adds a dependency between components.
     * 
     * @param dependent The component that depends on another
     * @param dependency The component that is depended upon
     */
    private static void addDependency(String dependent, String dependency) {
        COMPONENT_DEPENDENCIES.computeIfAbsent(dependent, k -> ConcurrentHashMap.newKeySet()).add(dependency);
    }

    /**
     * Gets the configuration for a specific component.
     * 
     * @param componentId The component ID
     * @return The component configuration, or null if not found
     */
    public static ComponentConfig getConfig(String componentId) {
        return COMPONENT_CONFIGS.get(componentId);
    }

    /**
     * Gets all component configurations.
     * 
     * @return An unmodifiable map of component configurations
     */
    public static Map<String, ComponentConfig> getAllConfigs() {
        return Collections.unmodifiableMap(COMPONENT_CONFIGS);
    }

    /**
     * Gets the dependencies for a specific component.
     * 
     * @param componentId The component ID
     * @return The set of dependencies, or an empty set if none
     */
    public static Set<String> getDependencies(String componentId) {
        Set<String> dependencies = COMPONENT_DEPENDENCIES.get(componentId);
        return dependencies != null ? Collections.unmodifiableSet(dependencies) : Collections.emptySet();
    }

    /**
     * Gets all component dependencies.
     * 
     * @return An unmodifiable map of component dependencies
     */
    public static Map<String, Set<String>> getAllDependencies() {
        return Collections.unmodifiableMap(COMPONENT_DEPENDENCIES);
    }

    /**
     * Checks if a component ID is valid.
     * 
     * @param componentId The component ID to check
     * @return true if the component ID is valid, false otherwise
     */
    public static boolean isValidComponent(String componentId) {
        return COMPONENT_CONFIGS.containsKey(componentId) || componentId.equals(ALL);
    }

    /**
     * Component configuration class.
     */
    public static class ComponentConfig {
        private final int basePort;
        private final String portProperty;
        private final String serviceType;
        private final String className;
        private final String startMethodName;
        private final String stopMethodName;

        /**
         * Creates a new component configuration.
         * 
         * @param basePort The base port for the component
         * @param portProperty The property name for the port
         * @param serviceType The service type description
         * @param className The fully qualified class name
         * @param startMethodName The name of the start method
         * @param stopMethodName The name of the stop method
         */
        public ComponentConfig(int basePort, String portProperty, String serviceType, 
                              String className, String startMethodName, String stopMethodName) {
            this.basePort = basePort;
            this.portProperty = portProperty;
            this.serviceType = serviceType;
            this.className = className;
            this.startMethodName = startMethodName;
            this.stopMethodName = stopMethodName;
        }

        public int getBasePort() {
            return basePort;
        }

        public String getPortProperty() {
            return portProperty;
        }

        public String getServiceType() {
            return serviceType;
        }

        public String getClassName() {
            return className;
        }

        public String getStartMethodName() {
            return startMethodName;
        }

        public String getStopMethodName() {
            return stopMethodName;
        }
    }
}
