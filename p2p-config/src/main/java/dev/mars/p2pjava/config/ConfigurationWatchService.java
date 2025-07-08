package dev.mars.p2pjava.config;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Service for watching configuration files and triggering automatic reloads.
 * Uses efficient file system watching to detect changes in real-time.
 */
public class ConfigurationWatchService {
    private static final Logger logger = Logger.getLogger(ConfigurationWatchService.class.getName());
    
    private static volatile ConfigurationWatchService instance;
    private static final Object lock = new Object();
    
    private final YamlConfigurationManager configManager;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    
    private WatchService watchService;
    private ExecutorService watcherExecutor;
    private ScheduledExecutorService scheduledExecutor;
    private Future<?> watcherFuture;
    
    // Configuration
    private static final long RELOAD_DEBOUNCE_MS = 1000; // Wait 1 second after last change
    private static final long PERIODIC_CHECK_INTERVAL_MS = 30000; // Check every 30 seconds
    
    private volatile long lastChangeTime = 0;
    private final Object reloadLock = new Object();
    
    /**
     * Private constructor for singleton pattern.
     */
    private ConfigurationWatchService() {
        this.configManager = YamlConfigurationManager.getInstance();
    }
    
    /**
     * Gets the singleton instance.
     */
    public static ConfigurationWatchService getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new ConfigurationWatchService();
                }
            }
        }
        return instance;
    }
    
    /**
     * Starts the configuration watch service.
     */
    public synchronized void start() {
        if (running.get()) {
            logger.warning("Configuration watch service is already running");
            return;
        }
        
        logger.info("Starting configuration watch service");
        
        try {
            // Create thread pools
            watcherExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "ConfigWatcher");
                t.setDaemon(true);
                return t;
            });
            
            scheduledExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ConfigReloader");
                t.setDaemon(true);
                return t;
            });
            
            // Start file watching
            startFileWatching();
            
            // Start periodic checking as backup
            startPeriodicChecking();
            
            running.set(true);
            logger.info("Configuration watch service started successfully");
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to start configuration watch service", e);
            stop();
            throw new RuntimeException("Failed to start configuration watch service", e);
        }
    }
    
    /**
     * Stops the configuration watch service.
     */
    public synchronized void stop() {
        if (!running.get()) {
            return;
        }
        
        logger.info("Stopping configuration watch service");
        running.set(false);
        
        // Stop file watcher
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error closing watch service", e);
            }
        }
        
        // Cancel watcher future
        if (watcherFuture != null) {
            watcherFuture.cancel(true);
        }
        
        // Shutdown executors
        shutdownExecutor(watcherExecutor, "watcher");
        shutdownExecutor(scheduledExecutor, "scheduled");
        
        logger.info("Configuration watch service stopped");
    }
    
    /**
     * Starts file system watching for configuration changes.
     */
    private void startFileWatching() throws IOException {
        // Create watch service
        watchService = FileSystems.getDefault().newWatchService();

        // Watch the config directory and current directory
        Path configDir = Paths.get("config");
        Path currentDir = Paths.get(".");

        // Register directories for watching
        currentDir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

        if (Files.exists(configDir)) {
            configDir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
        }

        // Start watching in background
        watcherFuture = watcherExecutor.submit(() -> {
            try {
                logger.info("Starting file system watcher");
                watchForChanges();
            } catch (Exception e) {
                if (running.get()) {
                    logger.log(Level.SEVERE, "File watcher failed", e);
                }
            }
        });
    }

    /**
     * Watches for file system changes.
     */
    private void watchForChanges() {
        while (running.get()) {
            try {
                WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                if (key != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();

                        if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                            Path changedPath = (Path) event.context();
                            handleFileChange(changedPath);
                        }
                    }
                    key.reset();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (running.get()) {
                    logger.log(Level.WARNING, "Error in file watcher", e);
                }
            }
        }
    }
    
    /**
     * Starts periodic configuration checking as a backup mechanism.
     */
    private void startPeriodicChecking() {
        scheduledExecutor.scheduleWithFixedDelay(
            this::performPeriodicCheck,
            PERIODIC_CHECK_INTERVAL_MS,
            PERIODIC_CHECK_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
    }
    
    /**
     * Handles file change events.
     */
    private void handleFileChange(Path changedPath) {
        if (!running.get() || !enabled.get()) {
            return;
        }

        String fileName = changedPath.getFileName().toString();
        
        // Only react to YAML configuration files
        if (isConfigurationFile(fileName)) {
            logger.fine("Configuration file changed: " + changedPath);
            
            synchronized (reloadLock) {
                lastChangeTime = System.currentTimeMillis();
                
                // Schedule debounced reload
                scheduledExecutor.schedule(this::performDebouncedReload, 
                                         RELOAD_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
            }
        }
    }
    
    /**
     * Checks if a file is a configuration file we care about.
     */
    private boolean isConfigurationFile(String fileName) {
        return fileName.equals("application.yml") || 
               fileName.equals("application.yaml") ||
               fileName.equals("config.yml") ||
               fileName.equals("config.yaml") ||
               (fileName.startsWith("application") && (fileName.endsWith(".yml") || fileName.endsWith(".yaml")));
    }
    
    /**
     * Performs debounced configuration reload.
     */
    private void performDebouncedReload() {
        synchronized (reloadLock) {
            long timeSinceLastChange = System.currentTimeMillis() - lastChangeTime;
            
            if (timeSinceLastChange >= RELOAD_DEBOUNCE_MS) {
                // Enough time has passed, perform reload
                performConfigurationReload();
            } else {
                // Still within debounce period, schedule another check
                long remainingTime = RELOAD_DEBOUNCE_MS - timeSinceLastChange;
                scheduledExecutor.schedule(this::performDebouncedReload, 
                                         remainingTime, TimeUnit.MILLISECONDS);
            }
        }
    }
    
    /**
     * Performs periodic configuration check.
     */
    private void performPeriodicCheck() {
        if (!running.get() || !enabled.get()) {
            return;
        }
        
        try {
            if (configManager.checkAndReload()) {
                logger.info("Configuration reloaded during periodic check");
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during periodic configuration check", e);
        }
    }
    
    /**
     * Performs the actual configuration reload.
     */
    private void performConfigurationReload() {
        try {
            logger.info("Reloading configuration due to file changes");
            
            if (configManager.checkAndReload()) {
                logger.info("Configuration successfully reloaded");
            } else {
                logger.fine("No configuration changes detected");
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to reload configuration", e);
        }
    }
    
    /**
     * Enables or disables automatic reloading.
     */
    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
        logger.info("Configuration auto-reload " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Checks if the service is enabled.
     */
    public boolean isEnabled() {
        return enabled.get();
    }
    
    /**
     * Checks if the service is running.
     */
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * Forces an immediate configuration reload.
     */
    public void forceReload() {
        logger.info("Forcing configuration reload");
        performConfigurationReload();
    }
    
    /**
     * Safely shuts down an executor service.
     */
    private void shutdownExecutor(ExecutorService executor, String name) {
        if (executor == null) {
            return;
        }
        
        try {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warning("Forcing shutdown of " + name + " executor");
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warning("Failed to shutdown " + name + " executor");
                }
            }
        } catch (InterruptedException e) {
            logger.warning("Interrupted while shutting down " + name + " executor");
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
