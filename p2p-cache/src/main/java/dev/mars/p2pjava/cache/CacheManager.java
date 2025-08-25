package dev.mars.p2pjava.cache;

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


import dev.mars.p2pjava.util.HealthCheck;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A generic cache manager for caching frequently accessed data.
 * This class provides a way to cache data with automatic expiration and refresh.
 *
 * @param <K> The type of the cache key
 * @param <V> The type of the cached value
 */
public class CacheManager<K, V> {
    private static final Logger logger = Logger.getLogger(CacheManager.class.getName());

    // The cache storage
    private final Map<K, CacheEntry<V>> cache = new ConcurrentHashMap<>();

    // The executor service for cache maintenance
    private final ScheduledExecutorService executor;

    // The default time-to-live for cache entries in milliseconds
    private final long defaultTtlMs;

    // The default refresh interval for cache entries in milliseconds
    private final long defaultRefreshMs;

    // The function to load data into the cache
    private final Function<K, V> loadFunction;

    // Statistics
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong cacheEvictions = new AtomicLong(0);
    private final AtomicLong cacheRefreshes = new AtomicLong(0);

    // Health check
    private final HealthCheck.ServiceHealth health;

    /**
     * Creates a new cache manager with the specified parameters.
     *
     * @param defaultTtlMs The default time-to-live for cache entries in milliseconds
     * @param defaultRefreshMs The default refresh interval for cache entries in milliseconds
     * @param loadFunction The function to load data into the cache
     */
    public CacheManager(long defaultTtlMs, long defaultRefreshMs, Function<K, V> loadFunction) {
        this(defaultTtlMs, defaultRefreshMs, loadFunction, null);
    }

    /**
     * Creates a new cache manager with the specified parameters and registers it with the health check system.
     *
     * @param defaultTtlMs The default time-to-live for cache entries in milliseconds
     * @param defaultRefreshMs The default refresh interval for cache entries in milliseconds
     * @param loadFunction The function to load data into the cache
     * @param serviceName The name to register with the health check system, or null to skip registration
     */
    public CacheManager(long defaultTtlMs, long defaultRefreshMs, Function<K, V> loadFunction, String serviceName) {
        this.defaultTtlMs = defaultTtlMs;
        this.defaultRefreshMs = defaultRefreshMs;
        this.loadFunction = loadFunction;

        // Create a thread pool with a custom thread factory
        this.executor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "CacheManager-" + 
                    java.util.UUID.randomUUID().toString().substring(0, 8));
            t.setDaemon(true);
            return t;
        });

        // Register with health check system if a service name is provided
        if (serviceName != null) {
            this.health = HealthCheck.registerService(serviceName);
            this.health.addHealthDetail("status", "initialized");
            this.health.addHealthDetail("defaultTtlMs", defaultTtlMs);
            this.health.addHealthDetail("defaultRefreshMs", defaultRefreshMs);
            this.health.setHealthy(true);
            logger.info("Registered cache manager with health check system as: " + serviceName);
        } else {
            this.health = null;
        }

        // Start cache maintenance task
        startCacheMaintenance();

        logger.info("Created cache manager with TTL: " + defaultTtlMs + "ms, refresh: " + defaultRefreshMs + "ms");
    }

    /**
     * Gets a value from the cache, loading it if necessary.
     *
     * @param key The cache key
     * @return The cached value
     */
    public V get(K key) {
        CacheEntry<V> entry = cache.get(key);
        boolean cacheHit = false;
        boolean expired = false;

        if (entry != null && !entry.isExpired()) {
            // Cache hit
            cacheHits.incrementAndGet();
            cacheHit = true;

            // Update health details if registered
            updateHealthDetails("get", true, cacheHit, null);

            return entry.getValue();
        }

        // Cache miss or expired entry
        cacheMisses.incrementAndGet();

        // If entry exists but is expired, increment eviction count
        if (entry != null && entry.isExpired()) {
            cacheEvictions.incrementAndGet();
            expired = true;
        }

        try {
            // Load the value
            V value = loadFunction.apply(key);

            if (value != null) {
                // Put the value in the cache
                put(key, value);
            } else if (entry != null) {
                // Remove expired entry if the load function returned null
                cache.remove(key);
                // Don't increment evictions again, we already did it above
            }

            // Update health details if registered
            updateHealthDetails("get", true, cacheHit, null);

            return value;
        } catch (Exception e) {
            // Update health details if registered
            updateHealthDetails("get", false, cacheHit, e);
            throw e;
        }
    }

    /**
     * Puts a value in the cache with the default TTL and refresh interval.
     *
     * @param key The cache key
     * @param value The value to cache
     */
    public void put(K key, V value) {
        put(key, value, defaultTtlMs, defaultRefreshMs);
    }

    /**
     * Puts a value in the cache with the specified TTL and refresh interval.
     *
     * @param key The cache key
     * @param value The value to cache
     * @param ttlMs The time-to-live for the cache entry in milliseconds
     * @param refreshMs The refresh interval for the cache entry in milliseconds
     */
    public void put(K key, V value, long ttlMs, long refreshMs) {
        long expirationTime = System.currentTimeMillis() + ttlMs;
        CacheEntry<V> entry = new CacheEntry<>(value, expirationTime, refreshMs);
        cache.put(key, entry);

        // Schedule refresh if needed
        if (refreshMs > 0) {
            scheduleRefresh(key, refreshMs);
        }
    }

    /**
     * Removes a value from the cache.
     *
     * @param key The cache key
     * @return The removed value, or null if the key was not in the cache
     */
    public V remove(K key) {
        CacheEntry<V> entry = cache.remove(key);
        if (entry != null) {
            // Cancel any scheduled refresh task
            if (entry.getRefreshFuture() != null && !entry.getRefreshFuture().isDone()) {
                entry.getRefreshFuture().cancel(false);
            }
            cacheEvictions.incrementAndGet();
            return entry.getValue();
        }
        return null;
    }

    /**
     * Clears the cache.
     */
    public void clear() {
        int size = cache.size();

        // Cancel all refresh tasks before clearing
        for (CacheEntry<V> entry : cache.values()) {
            if (entry.getRefreshFuture() != null && !entry.getRefreshFuture().isDone()) {
                entry.getRefreshFuture().cancel(false);
            }
        }

        cache.clear();
        cacheEvictions.addAndGet(size);
        logger.info("Cache cleared");
    }

    /**
     * Gets the number of entries in the cache.
     *
     * @return The number of entries
     */
    public int size() {
        return cache.size();
    }

    /**
     * Gets the cache hit count.
     *
     * @return The cache hit count
     */
    public long getCacheHits() {
        return cacheHits.get();
    }

    /**
     * Gets the cache miss count.
     *
     * @return The cache miss count
     */
    public long getCacheMisses() {
        return cacheMisses.get();
    }

    /**
     * Gets the cache hit ratio.
     *
     * @return The cache hit ratio (0-1)
     */
    public double getCacheHitRatio() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        return total > 0 ? (double) hits / total : 0;
    }

    /**
     * Gets the cache eviction count.
     *
     * @return The cache eviction count
     */
    public long getCacheEvictions() {
        return cacheEvictions.get();
    }

    /**
     * Gets the cache refresh count.
     *
     * @return The cache refresh count
     */
    public long getCacheRefreshes() {
        return cacheRefreshes.get();
    }

    /**
     * Shuts down the cache manager.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warning("Cache manager executor did not terminate");
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("Cache manager shut down");
    }

    /**
     * Gets a summary of the cache statistics.
     *
     * @return A string containing the statistics
     */
    public String getStatistics() {
        return "CacheManager{" +
                "size=" + cache.size() +
                ", hits=" + cacheHits.get() +
                ", misses=" + cacheMisses.get() +
                ", hit ratio=" + String.format("%.2f", getCacheHitRatio() * 100) + "%" +
                ", evictions=" + cacheEvictions.get() +
                ", refreshes=" + cacheRefreshes.get() +
                '}';
    }

    /**
     * Starts the cache maintenance task.
     */
    private void startCacheMaintenance() {
        executor.scheduleAtFixedRate(() -> {
            try {
                // Remove expired entries
                cache.entrySet().removeIf(entry -> {
                    if (entry.getValue().isExpired()) {
                        // Cancel any scheduled refresh task
                        if (entry.getValue().getRefreshFuture() != null && !entry.getValue().getRefreshFuture().isDone()) {
                            entry.getValue().getRefreshFuture().cancel(false);
                        }
                        cacheEvictions.incrementAndGet();
                        return true;
                    }
                    return false;
                });
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error during cache maintenance", e);
            }
        }, 60, 60, TimeUnit.SECONDS);

        logger.info("Cache maintenance task started");
    }

    /**
     * Updates health details if health check is registered.
     *
     * @param operation The operation being performed
     * @param success Whether the operation was successful
     * @param cacheHit Whether the operation resulted in a cache hit
     * @param exception The exception that occurred, or null if no exception
     */
    private void updateHealthDetails(String operation, boolean success, boolean cacheHit, Exception exception) {
        if (health != null) {
            health.addHealthDetail("lastOperation", operation);
            health.addHealthDetail("lastOperationSuccess", success);
            health.addHealthDetail("lastOperationCacheHit", cacheHit);
            health.addHealthDetail("cacheSize", cache.size());
            health.addHealthDetail("cacheHits", cacheHits.get());
            health.addHealthDetail("cacheMisses", cacheMisses.get());
            health.addHealthDetail("cacheHitRatio", String.format("%.2f", getCacheHitRatio() * 100) + "%");
            health.addHealthDetail("cacheEvictions", cacheEvictions.get());
            health.addHealthDetail("cacheRefreshes", cacheRefreshes.get());

            if (exception != null) {
                health.addHealthDetail("lastError", exception.getMessage());
                health.addHealthDetail("lastErrorTime", System.currentTimeMillis());
                health.setHealthy(false);
            } else {
                health.setHealthy(true);
            }
        }
    }

    /**
     * Schedules a refresh for a cache entry.
     *
     * @param key The cache key
     * @param refreshMs The refresh interval in milliseconds
     */
    private void scheduleRefresh(K key, long refreshMs) {
        // Store the future in the cache entry to allow cancellation
        CacheEntry<V> entry = cache.get(key);
        if (entry != null) {
            // If there's already a refresh task scheduled and it's not done, don't schedule another one
            if (entry.getRefreshFuture() != null && !entry.getRefreshFuture().isDone()) {
                logger.fine("Refresh already scheduled for key: " + key);
                return;
            }

            logger.fine("Scheduling refresh for key: " + key + " in " + refreshMs + "ms");

            // Schedule a new refresh task
            entry.setRefreshFuture(executor.schedule(() -> {
                try {
                    logger.fine("Executing refresh for key: " + key);

                    // Check if the entry still exists and is not expired
                    CacheEntry<V> currentEntry = cache.get(key);
                    if (currentEntry != null && !currentEntry.isExpired()) {
                        // Refresh the entry
                        V value = loadFunction.apply(key);
                        if (value != null) {
                            // Update the entry
                            long expirationTime = System.currentTimeMillis() + defaultTtlMs;
                            currentEntry.update(value, expirationTime);
                            cacheRefreshes.incrementAndGet();
                            logger.fine("Refreshed value for key: " + key);

                            // Schedule next refresh
                            scheduleRefresh(key, refreshMs);
                        } else {
                            // Remove the entry if the load function returned null
                            cache.remove(key);
                            cacheEvictions.incrementAndGet();
                            logger.fine("Removed entry for key: " + key + " after refresh returned null");
                        }
                    } else {
                        logger.fine("Entry for key: " + key + " no longer exists or is expired, not refreshing");
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error refreshing cache entry for key: " + key, e);
                }
            }, refreshMs, TimeUnit.MILLISECONDS));
        } else {
            logger.fine("Cannot schedule refresh for key: " + key + ", entry not found in cache");
        }
    }

    /**
     * A cache entry with expiration and refresh information.
     *
     * @param <V> The type of the cached value
     */
    private static class CacheEntry<V> {
        private V value;
        private long expirationTime;
        private final long refreshMs;
        private java.util.concurrent.ScheduledFuture<?> refreshFuture;

        /**
         * Creates a new cache entry.
         *
         * @param value The cached value
         * @param expirationTime The expiration time in milliseconds since the epoch
         * @param refreshMs The refresh interval in milliseconds
         */
        public CacheEntry(V value, long expirationTime, long refreshMs) {
            this.value = value;
            this.expirationTime = expirationTime;
            this.refreshMs = refreshMs;
            this.refreshFuture = null;
        }

        /**
         * Gets the cached value.
         *
         * @return The cached value
         */
        public V getValue() {
            return value;
        }

        /**
         * Checks if the entry is expired.
         *
         * @return true if the entry is expired, false otherwise
         */
        public boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }

        /**
         * Updates the entry with a new value and expiration time.
         *
         * @param value The new value
         * @param expirationTime The new expiration time
         */
        public void update(V value, long expirationTime) {
            this.value = value;
            this.expirationTime = expirationTime;
        }

        /**
         * Gets the refresh future.
         *
         * @return The refresh future
         */
        public java.util.concurrent.ScheduledFuture<?> getRefreshFuture() {
            return refreshFuture;
        }

        /**
         * Sets the refresh future.
         *
         * @param refreshFuture The refresh future
         */
        public void setRefreshFuture(java.util.concurrent.ScheduledFuture<?> refreshFuture) {
            this.refreshFuture = refreshFuture;
        }
    }
}