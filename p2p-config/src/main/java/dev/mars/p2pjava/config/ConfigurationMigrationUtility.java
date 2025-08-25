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

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Utility for migrating from properties-based configuration to YAML-based configuration.
 * Provides backward compatibility and smooth transition path.
 */
public class ConfigurationMigrationUtility {
    private static final Logger logger = Logger.getLogger(ConfigurationMigrationUtility.class.getName());
    
    private final ObjectMapper yamlMapper;
    private final ConfigurationManager legacyConfigManager;
    
    public ConfigurationMigrationUtility() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.legacyConfigManager = ConfigurationManager.getInstance();
    }
    
    /**
     * Migrates properties configuration to YAML format.
     */
    public void migrateToYaml(Path outputPath) throws IOException {
        logger.info("Starting migration from properties to YAML configuration");
        
        // Create new configuration object
        P2PConfiguration config = new P2PConfiguration();
        
        // Migrate application settings
        migrateApplicationConfig(config);
        
        // Migrate tracker settings
        migrateTrackerConfig(config);
        
        // Migrate index server settings
        migrateIndexServerConfig(config);
        
        // Migrate peer settings
        migratePeerConfig(config);
        
        // Migrate health check settings
        migrateHealthCheckConfig(config);
        
        // Migrate bootstrap settings
        migrateBootstrapConfig(config);
        
        // Migrate logging settings
        migrateLoggingConfig(config);
        
        // Migrate monitoring settings
        migrateMonitoringConfig(config);
        
        // Migrate security settings
        migrateSecurityConfig(config);
        
        // Save to YAML file
        yamlMapper.writeValue(outputPath.toFile(), config);
        
        logger.info("Migration completed successfully. YAML configuration saved to: " + outputPath);
    }
    
    /**
     * Migrates application configuration.
     */
    private void migrateApplicationConfig(P2PConfiguration config) {
        P2PConfiguration.ApplicationConfig appConfig = config.getApplication();
        
        appConfig.setName(legacyConfigManager.get("application.name", "p2p-java-app"));
        appConfig.setVersion(legacyConfigManager.get("application.version", "1.0.0"));
        appConfig.setEnvironment(legacyConfigManager.get("application.environment", "development"));
        appConfig.setProfile(legacyConfigManager.get("application.profile", "default"));
    }
    
    /**
     * Migrates tracker configuration.
     */
    private void migrateTrackerConfig(P2PConfiguration config) {
        P2PConfiguration.TrackerConfig trackerConfig = config.getTracker();
        
        trackerConfig.setHost(legacyConfigManager.get("tracker.host", "localhost"));
        trackerConfig.setPort(legacyConfigManager.getInt("tracker.port", 6000));
        trackerConfig.setPeerTimeoutMs(legacyConfigManager.getLong("tracker.peer.timeout.ms", 90000));
        trackerConfig.setMaxPeers(legacyConfigManager.getInt("tracker.max.peers", 1000));
        trackerConfig.setCleanupIntervalMs(legacyConfigManager.getLong("tracker.cleanup.interval.ms", 60000));
        
        // Thread pool configuration
        P2PConfiguration.ThreadPoolConfig threadPool = trackerConfig.getThreadPool();
        threadPool.setSize(legacyConfigManager.getInt("tracker.thread.pool.size", 10));
        threadPool.setMaxSize(legacyConfigManager.getInt("tracker.thread.pool.max.size", 20));
        threadPool.setQueueSize(legacyConfigManager.getInt("tracker.thread.pool.queue.size", 1000));
        threadPool.setKeepAliveMs(legacyConfigManager.getLong("tracker.thread.pool.keep.alive.ms", 60000));
    }
    
    /**
     * Migrates index server configuration.
     */
    private void migrateIndexServerConfig(P2PConfiguration config) {
        P2PConfiguration.IndexServerConfig indexConfig = config.getIndexServer();
        
        indexConfig.setHost(legacyConfigManager.get("indexserver.host", "localhost"));
        indexConfig.setPort(legacyConfigManager.getInt("indexserver.port", 6001));

        // Thread pool configuration
        P2PConfiguration.ThreadPoolConfig threadPool = indexConfig.getThreadPool();
        threadPool.setSize(legacyConfigManager.getInt("indexserver.thread.pool.size", 10));
        threadPool.setMaxSize(legacyConfigManager.getInt("indexserver.thread.pool.max.size", 20));

        // Storage configuration
        P2PConfiguration.StorageConfig storage = indexConfig.getStorage();
        storage.setDirectory(legacyConfigManager.get("indexserver.storage.dir", "data"));
        storage.setFilename(legacyConfigManager.get("indexserver.storage.file", "file_index.dat"));
        storage.setBackupEnabled(legacyConfigManager.getBoolean("indexserver.storage.backup.enabled", true));
        storage.setBackupIntervalMs(legacyConfigManager.getLong("indexserver.storage.backup.interval.ms", 3600000));
        
        // Cache configuration
        P2PConfiguration.CacheConfig cache = indexConfig.getCache();
        cache.setTtlMs(legacyConfigManager.getLong("indexserver.cache.ttl.ms", 60000));
        cache.setRefreshMs(legacyConfigManager.getLong("indexserver.cache.refresh.ms", 300000));
        cache.setMaxSize(legacyConfigManager.getInt("indexserver.cache.max.size", 10000));
        
        // Connection configuration
        P2PConfiguration.ConnectionConfig connection = indexConfig.getConnection();
        connection.setPoolMax(legacyConfigManager.getInt("indexserver.connection.pool.max", 100));
        connection.setTimeoutMs(legacyConfigManager.getLong("indexserver.connection.timeout.ms", 5000));
        connection.setRetryAttempts(legacyConfigManager.getInt("indexserver.connection.retry.attempts", 3));
        connection.setRetryDelayMs(legacyConfigManager.getLong("indexserver.connection.retry.delay.ms", 1000));
    }
    
    /**
     * Migrates peer configuration.
     */
    private void migratePeerConfig(P2PConfiguration config) {
        PeerConfig peerConfig = config.getPeer();
        
        peerConfig.setSocketTimeoutMs(legacyConfigManager.getLong("peer.socket.timeout.ms", 30000));
        
        // Heartbeat configuration
        PeerConfig.HeartbeatConfig heartbeat = peerConfig.getHeartbeat();
        heartbeat.setIntervalSeconds(legacyConfigManager.getInt("peer.heartbeat.interval.seconds", 30));
        heartbeat.setTimeoutMs(legacyConfigManager.getLong("peer.heartbeat.timeout.ms", 10000));
        heartbeat.setMaxMissed(legacyConfigManager.getInt("peer.heartbeat.max.missed", 3));
        heartbeat.setEnabled(legacyConfigManager.getBoolean("peer.heartbeat.enabled", true));
        
        // File sharing configuration
        PeerConfig.FileSharingConfig fileSharing = peerConfig.getFileSharing();
        fileSharing.setEnabled(legacyConfigManager.getBoolean("peer.file.sharing.enabled", true));
        fileSharing.setShareDirectory(legacyConfigManager.get("peer.file.sharing.directory", "shared"));
        fileSharing.setMaxFileSize(legacyConfigManager.getLong("peer.file.sharing.max.file.size", 104857600));
        fileSharing.setUploadRateLimit(legacyConfigManager.getLong("peer.file.sharing.upload.rate.limit", 1048576));
        fileSharing.setDownloadRateLimit(legacyConfigManager.getLong("peer.file.sharing.download.rate.limit", 2097152));
        fileSharing.setMaxConcurrentTransfers(legacyConfigManager.getInt("peer.file.sharing.max.concurrent.transfers", 5));
        
        // Discovery configuration
        PeerConfig.DiscoveryConfig discovery = peerConfig.getDiscovery();
        discovery.setEnabled(legacyConfigManager.getBoolean("peer.discovery.enabled", true));
        discovery.setBroadcastPort(legacyConfigManager.getInt("peer.discovery.broadcast.port", 6002));
        discovery.setDiscoveryIntervalMs(legacyConfigManager.getLong("peer.discovery.interval.ms", 60000));
        discovery.setMaxPeers(legacyConfigManager.getInt("peer.discovery.max.peers", 50));
    }
    
    /**
     * Migrates health check configuration.
     */
    private void migrateHealthCheckConfig(P2PConfiguration config) {
        HealthCheckConfig healthConfig = config.getHealthCheck();
        
        healthConfig.setEnabled(legacyConfigManager.getBoolean("healthcheck.enabled", true));
        healthConfig.setPort(legacyConfigManager.getInt("healthcheck.port", 8080));
        healthConfig.setPath(legacyConfigManager.get("healthcheck.path", "/health"));
        healthConfig.setIntervalMs(legacyConfigManager.getLong("healthcheck.interval.ms", 30000));
        healthConfig.setTimeoutMs(legacyConfigManager.getLong("healthcheck.timeout.ms", 5000));
        
        // Individual checks
        HealthCheckConfig.ChecksConfig checks = healthConfig.getChecks();
        checks.setDatabase(legacyConfigManager.getBoolean("healthcheck.checks.database", true));
        checks.setTracker(legacyConfigManager.getBoolean("healthcheck.checks.tracker", true));
        checks.setIndexServer(legacyConfigManager.getBoolean("healthcheck.checks.indexserver", true));
        checks.setDiskSpace(legacyConfigManager.getBoolean("healthcheck.checks.diskspace", true));
        checks.setMemory(legacyConfigManager.getBoolean("healthcheck.checks.memory", true));
        checks.setThreadPools(legacyConfigManager.getBoolean("healthcheck.checks.threadpools", true));
    }
    
    /**
     * Migrates bootstrap configuration.
     */
    private void migrateBootstrapConfig(P2PConfiguration config) {
        BootstrapConfig bootstrapConfig = config.getBootstrap();
        
        bootstrapConfig.setAutoStart(legacyConfigManager.getBoolean("bootstrap.auto.start", true));
        bootstrapConfig.setStartupTimeoutSeconds(legacyConfigManager.getInt("bootstrap.startup.timeout.seconds", 30));
        bootstrapConfig.setDynamicPorts(legacyConfigManager.getBoolean("bootstrap.dynamic.ports", false));
        bootstrapConfig.setRetryAttempts(legacyConfigManager.getInt("bootstrap.retry.attempts", 3));
        bootstrapConfig.setRetryDelayMs(legacyConfigManager.getLong("bootstrap.retry.delay.ms", 5000));
    }
    
    /**
     * Migrates logging configuration.
     */
    private void migrateLoggingConfig(P2PConfiguration config) {
        LoggingConfig loggingConfig = config.getLogging();
        
        loggingConfig.setLevel(legacyConfigManager.get("logging.level", "INFO"));
        loggingConfig.setPattern(legacyConfigManager.get("logging.pattern",
                "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"));

        // File logging
        LoggingConfig.FileLoggingConfig file = loggingConfig.getFile();
        file.setEnabled(legacyConfigManager.getBoolean("logging.file.enabled", true));
        file.setPath(legacyConfigManager.get("logging.file.path", "logs/p2p-java.log"));
        file.setMaxSize(legacyConfigManager.get("logging.file.max.size", "10MB"));
        file.setMaxFiles(legacyConfigManager.getInt("logging.file.max.files", 10));
        file.setCompress(legacyConfigManager.getBoolean("logging.file.compress", true));
        
        // Console logging
        LoggingConfig.ConsoleLoggingConfig console = loggingConfig.getConsole();
        console.setEnabled(legacyConfigManager.getBoolean("logging.console.enabled", true));
        console.setColorEnabled(legacyConfigManager.getBoolean("logging.console.color.enabled", true));
        console.setLevel(legacyConfigManager.get("logging.console.level", "INFO"));
    }
    
    /**
     * Migrates monitoring configuration.
     */
    private void migrateMonitoringConfig(P2PConfiguration config) {
        MonitoringConfig monitoringConfig = config.getMonitoring();
        
        monitoringConfig.setEnabled(legacyConfigManager.getBoolean("monitoring.enabled", true));
        monitoringConfig.setIntervalMs(legacyConfigManager.getLong("monitoring.interval.ms", 30000));
        
        // Metrics configuration
        MonitoringConfig.MetricsConfig metrics = monitoringConfig.getMetrics();
        metrics.setThreadPools(legacyConfigManager.getBoolean("monitoring.metrics.threadpools", true));
        metrics.setMemory(legacyConfigManager.getBoolean("monitoring.metrics.memory", true));
        metrics.setNetwork(legacyConfigManager.getBoolean("monitoring.metrics.network", true));
        metrics.setFileTransfers(legacyConfigManager.getBoolean("monitoring.metrics.filetransfers", true));
        metrics.setPerformance(legacyConfigManager.getBoolean("monitoring.metrics.performance", true));
        metrics.setRetentionDays(legacyConfigManager.getInt("monitoring.metrics.retention.days", 7));
    }
    
    /**
     * Migrates security configuration.
     */
    private void migrateSecurityConfig(P2PConfiguration config) {
        SecurityConfig securityConfig = config.getSecurity();
        
        securityConfig.setEnabled(legacyConfigManager.getBoolean("security.enabled", false));
        
        // Encryption configuration
        SecurityConfig.EncryptionConfig encryption = securityConfig.getEncryption();
        encryption.setEnabled(legacyConfigManager.getBoolean("security.encryption.enabled", false));
        encryption.setAlgorithm(legacyConfigManager.get("security.encryption.algorithm", "AES-256-GCM"));
        encryption.setKeySize(legacyConfigManager.getInt("security.encryption.key.size", 256));
        encryption.setKeyRotationDays(legacyConfigManager.getInt("security.encryption.key.rotation.days", 30));

        // Authentication configuration
        SecurityConfig.AuthenticationConfig auth = securityConfig.getAuthentication();
        auth.setEnabled(legacyConfigManager.getBoolean("security.authentication.enabled", false));
        auth.setMethod(legacyConfigManager.get("security.authentication.method", "token"));
        auth.setTokenExpiryHours(legacyConfigManager.getInt("security.authentication.token.expiry.hours", 24));
        auth.setAllowAnonymous(legacyConfigManager.getBoolean("security.authentication.allow.anonymous", true));
    }
    
    /**
     * Creates a backup of existing properties files.
     */
    public void backupPropertiesFiles(Path backupDirectory) throws IOException {
        logger.info("Creating backup of existing properties files");
        
        Files.createDirectories(backupDirectory);
        
        String[] propertyFiles = {
                "config/application.properties",
                "application.properties",
                "config.properties"
        };
        
        for (String propertyFile : propertyFiles) {
            Path sourcePath = Paths.get(propertyFile);
            if (Files.exists(sourcePath)) {
                Path backupPath = backupDirectory.resolve(sourcePath.getFileName());
                Files.copy(sourcePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Backed up: " + propertyFile + " to " + backupPath);
            }
        }
    }
    
    /**
     * Validates the migrated YAML configuration.
     */
    public boolean validateMigratedConfiguration(Path yamlPath) {
        try {
            P2PConfiguration config = yamlMapper.readValue(yamlPath.toFile(), P2PConfiguration.class);
            
            // Basic validation
            if (config.getTracker() == null || config.getPeer() == null || config.getIndexServer() == null) {
                logger.severe("Migration validation failed: Missing required configuration sections");
                return false;
            }
            
            logger.info("Migration validation passed");
            return true;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Migration validation failed", e);
            return false;
        }
    }
    
    /**
     * Main method for running migration from command line.
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java ConfigurationMigrationUtility <output-yaml-file> [backup-directory]");
            System.exit(1);
        }
        
        try {
            ConfigurationMigrationUtility migrator = new ConfigurationMigrationUtility();
            Path outputPath = Paths.get(args[0]);
            
            // Create backup if specified
            if (args.length > 1) {
                Path backupDir = Paths.get(args[1]);
                migrator.backupPropertiesFiles(backupDir);
            }
            
            // Perform migration
            migrator.migrateToYaml(outputPath);
            
            // Validate migration
            if (migrator.validateMigratedConfiguration(outputPath)) {
                System.out.println("Migration completed successfully!");
            } else {
                System.out.println("Migration completed but validation failed. Please check the output file.");
            }
            
        } catch (Exception e) {
            System.err.println("Migration failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
