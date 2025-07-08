package dev.mars.p2pjava.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.HashMap;

/**
 * Root configuration class for P2P Java application.
 * This class represents the complete configuration structure in YAML format.
 */
public class P2PConfiguration {
    
    @JsonProperty("application")
    private ApplicationConfig application = new ApplicationConfig();
    
    @JsonProperty("tracker")
    private TrackerConfig tracker = new TrackerConfig();
    
    @JsonProperty("indexserver")
    private IndexServerConfig indexServer = new IndexServerConfig();
    
    @JsonProperty("peer")
    private PeerConfig peer = new PeerConfig();
    
    @JsonProperty("healthcheck")
    private HealthCheckConfig healthCheck = new HealthCheckConfig();
    
    @JsonProperty("bootstrap")
    private BootstrapConfig bootstrap = new BootstrapConfig();
    
    @JsonProperty("logging")
    private LoggingConfig logging = new LoggingConfig();
    
    @JsonProperty("monitoring")
    private MonitoringConfig monitoring = new MonitoringConfig();
    
    @JsonProperty("security")
    private SecurityConfig security = new SecurityConfig();
    
    @JsonProperty("custom")
    private Map<String, Object> customProperties = new HashMap<>();
    
    // Default constructor
    public P2PConfiguration() {}
    
    // Getters and setters
    public ApplicationConfig getApplication() { return application; }
    public void setApplication(ApplicationConfig application) { this.application = application; }
    
    public TrackerConfig getTracker() { return tracker; }
    public void setTracker(TrackerConfig tracker) { this.tracker = tracker; }
    
    public IndexServerConfig getIndexServer() { return indexServer; }
    public void setIndexServer(IndexServerConfig indexServer) { this.indexServer = indexServer; }
    
    public PeerConfig getPeer() { return peer; }
    public void setPeer(PeerConfig peer) { this.peer = peer; }
    
    public HealthCheckConfig getHealthCheck() { return healthCheck; }
    public void setHealthCheck(HealthCheckConfig healthCheck) { this.healthCheck = healthCheck; }
    
    public BootstrapConfig getBootstrap() { return bootstrap; }
    public void setBootstrap(BootstrapConfig bootstrap) { this.bootstrap = bootstrap; }
    
    public LoggingConfig getLogging() { return logging; }
    public void setLogging(LoggingConfig logging) { this.logging = logging; }
    
    public MonitoringConfig getMonitoring() { return monitoring; }
    public void setMonitoring(MonitoringConfig monitoring) { this.monitoring = monitoring; }
    
    public SecurityConfig getSecurity() { return security; }
    public void setSecurity(SecurityConfig security) { this.security = security; }
    
    public Map<String, Object> getCustomProperties() { return customProperties; }
    public void setCustomProperties(Map<String, Object> customProperties) { this.customProperties = customProperties; }
    
    /**
     * Application-level configuration
     */
    public static class ApplicationConfig {
        @JsonProperty("name")
        private String name = "p2p-java-app";
        
        @JsonProperty("version")
        private String version = "1.0.0";
        
        @JsonProperty("environment")
        private String environment = "development";
        
        @JsonProperty("profile")
        private String profile = "default";
        
        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        
        public String getEnvironment() { return environment; }
        public void setEnvironment(String environment) { this.environment = environment; }
        
        public String getProfile() { return profile; }
        public void setProfile(String profile) { this.profile = profile; }
    }
    
    /**
     * Tracker configuration
     */
    public static class TrackerConfig {
        @JsonProperty("host")
        private String host = "localhost";
        
        @JsonProperty("port")
        private int port = 6000;
        
        @JsonProperty("threadPool")
        private ThreadPoolConfig threadPool = new ThreadPoolConfig(10);
        
        @JsonProperty("peerTimeoutMs")
        private long peerTimeoutMs = 90000;
        
        @JsonProperty("maxPeers")
        private int maxPeers = 1000;
        
        @JsonProperty("cleanupIntervalMs")
        private long cleanupIntervalMs = 60000;
        
        // Getters and setters
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        
        public ThreadPoolConfig getThreadPool() { return threadPool; }
        public void setThreadPool(ThreadPoolConfig threadPool) { this.threadPool = threadPool; }
        
        public long getPeerTimeoutMs() { return peerTimeoutMs; }
        public void setPeerTimeoutMs(long peerTimeoutMs) { this.peerTimeoutMs = peerTimeoutMs; }
        
        public int getMaxPeers() { return maxPeers; }
        public void setMaxPeers(int maxPeers) { this.maxPeers = maxPeers; }
        
        public long getCleanupIntervalMs() { return cleanupIntervalMs; }
        public void setCleanupIntervalMs(long cleanupIntervalMs) { this.cleanupIntervalMs = cleanupIntervalMs; }
    }
    
    /**
     * Thread pool configuration
     */
    public static class ThreadPoolConfig {
        @JsonProperty("size")
        private int size;
        
        @JsonProperty("maxSize")
        private int maxSize;
        
        @JsonProperty("queueSize")
        private int queueSize = 1000;
        
        @JsonProperty("keepAliveMs")
        private long keepAliveMs = 60000;
        
        public ThreadPoolConfig() {}
        
        public ThreadPoolConfig(int size) {
            this.size = size;
            this.maxSize = size * 2;
        }
        
        // Getters and setters
        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
        
        public int getMaxSize() { return maxSize; }
        public void setMaxSize(int maxSize) { this.maxSize = maxSize; }
        
        public int getQueueSize() { return queueSize; }
        public void setQueueSize(int queueSize) { this.queueSize = queueSize; }
        
        public long getKeepAliveMs() { return keepAliveMs; }
        public void setKeepAliveMs(long keepAliveMs) { this.keepAliveMs = keepAliveMs; }
    }

    /**
     * Index Server configuration
     */
    public static class IndexServerConfig {
        @JsonProperty("host")
        private String host = "localhost";

        @JsonProperty("port")
        private int port = 6001;

        @JsonProperty("threadPool")
        private ThreadPoolConfig threadPool = new ThreadPoolConfig(10);

        @JsonProperty("storage")
        private StorageConfig storage = new StorageConfig();

        @JsonProperty("cache")
        private CacheConfig cache = new CacheConfig();

        @JsonProperty("connection")
        private ConnectionConfig connection = new ConnectionConfig();

        // Getters and setters
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }

        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }

        public ThreadPoolConfig getThreadPool() { return threadPool; }
        public void setThreadPool(ThreadPoolConfig threadPool) { this.threadPool = threadPool; }

        public StorageConfig getStorage() { return storage; }
        public void setStorage(StorageConfig storage) { this.storage = storage; }

        public CacheConfig getCache() { return cache; }
        public void setCache(CacheConfig cache) { this.cache = cache; }

        public ConnectionConfig getConnection() { return connection; }
        public void setConnection(ConnectionConfig connection) { this.connection = connection; }
    }

    /**
     * Storage configuration
     */
    public static class StorageConfig {
        @JsonProperty("directory")
        private String directory = "data";

        @JsonProperty("filename")
        private String filename = "file_index.dat";

        @JsonProperty("backupEnabled")
        private boolean backupEnabled = true;

        @JsonProperty("backupIntervalMs")
        private long backupIntervalMs = 3600000; // 1 hour

        // Getters and setters
        public String getDirectory() { return directory; }
        public void setDirectory(String directory) { this.directory = directory; }

        public String getFilename() { return filename; }
        public void setFilename(String filename) { this.filename = filename; }

        public boolean isBackupEnabled() { return backupEnabled; }
        public void setBackupEnabled(boolean backupEnabled) { this.backupEnabled = backupEnabled; }

        public long getBackupIntervalMs() { return backupIntervalMs; }
        public void setBackupIntervalMs(long backupIntervalMs) { this.backupIntervalMs = backupIntervalMs; }
    }

    /**
     * Cache configuration
     */
    public static class CacheConfig {
        @JsonProperty("ttlMs")
        private long ttlMs = 60000;

        @JsonProperty("refreshMs")
        private long refreshMs = 300000;

        @JsonProperty("maxSize")
        private int maxSize = 10000;

        // Getters and setters
        public long getTtlMs() { return ttlMs; }
        public void setTtlMs(long ttlMs) { this.ttlMs = ttlMs; }

        public long getRefreshMs() { return refreshMs; }
        public void setRefreshMs(long refreshMs) { this.refreshMs = refreshMs; }

        public int getMaxSize() { return maxSize; }
        public void setMaxSize(int maxSize) { this.maxSize = maxSize; }
    }

    /**
     * Connection configuration
     */
    public static class ConnectionConfig {
        @JsonProperty("poolMax")
        private int poolMax = 100;

        @JsonProperty("timeoutMs")
        private long timeoutMs = 5000;

        @JsonProperty("retryAttempts")
        private int retryAttempts = 3;

        @JsonProperty("retryDelayMs")
        private long retryDelayMs = 1000;

        // Getters and setters
        public int getPoolMax() { return poolMax; }
        public void setPoolMax(int poolMax) { this.poolMax = poolMax; }

        public long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }

        public int getRetryAttempts() { return retryAttempts; }
        public void setRetryAttempts(int retryAttempts) { this.retryAttempts = retryAttempts; }

        public long getRetryDelayMs() { return retryDelayMs; }
        public void setRetryDelayMs(long retryDelayMs) { this.retryDelayMs = retryDelayMs; }
    }
}
