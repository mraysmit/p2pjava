package dev.mars.p2pjava.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
    private long cacheHits = 0;
    private long cacheMisses = 0;
    private long cacheEvictions = 0;
    private long cacheRefreshes = 0;

    /**
     * Creates a new cache manager with the specified parameters.
     *
     * @param defaultTtlMs The default time-to-live for cache entries in milliseconds
     * @param defaultRefreshMs The default refresh interval for cache entries in milliseconds
     * @param loadFunction The function to load data into the cache
     */
    public CacheManager(long defaultTtlMs, long defaultRefreshMs, Function<K, V> loadFunction) {
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

        if (entry != null && !entry.isExpired()) {
            // Cache hit
            cacheHits++;
            return entry.getValue();
        }

        // Cache miss or expired entry
        cacheMisses++;

        // If entry exists but is expired, increment eviction count
        if (entry != null && entry.isExpired()) {
            cacheEvictions++;
        }

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

        return value;
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
            cacheEvictions++;
            return entry.getValue();
        }
        return null;
    }

    /**
     * Clears the cache.
     */
    public void clear() {
        cache.clear();
        cacheEvictions += cache.size();
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
        return cacheHits;
    }

    /**
     * Gets the cache miss count.
     *
     * @return The cache miss count
     */
    public long getCacheMisses() {
        return cacheMisses;
    }

    /**
     * Gets the cache hit ratio.
     *
     * @return The cache hit ratio (0-1)
     */
    public double getCacheHitRatio() {
        long total = cacheHits + cacheMisses;
        return total > 0 ? (double) cacheHits / total : 0;
    }

    /**
     * Gets the cache eviction count.
     *
     * @return The cache eviction count
     */
    public long getCacheEvictions() {
        return cacheEvictions;
    }

    /**
     * Gets the cache refresh count.
     *
     * @return The cache refresh count
     */
    public long getCacheRefreshes() {
        return cacheRefreshes;
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
                ", hits=" + cacheHits +
                ", misses=" + cacheMisses +
                ", hit ratio=" + String.format("%.2f", getCacheHitRatio() * 100) + "%" +
                ", evictions=" + cacheEvictions +
                ", refreshes=" + cacheRefreshes +
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
                        cacheEvictions++;
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
     * Schedules a refresh for a cache entry.
     *
     * @param key The cache key
     * @param refreshMs The refresh interval in milliseconds
     */
    private void scheduleRefresh(K key, long refreshMs) {
        executor.schedule(() -> {
            try {
                // Check if the entry still exists and is not expired
                CacheEntry<V> entry = cache.get(key);
                if (entry != null && !entry.isExpired()) {
                    // Refresh the entry
                    V value = loadFunction.apply(key);
                    if (value != null) {
                        // Update the entry
                        long expirationTime = System.currentTimeMillis() + defaultTtlMs;
                        entry.update(value, expirationTime);
                        cacheRefreshes++;

                        // Schedule next refresh
                        scheduleRefresh(key, refreshMs);
                    } else {
                        // Remove the entry if the load function returned null
                        cache.remove(key);
                        cacheEvictions++;
                    }
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error refreshing cache entry", e);
            }
        }, refreshMs, TimeUnit.MILLISECONDS);
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
    }
}
