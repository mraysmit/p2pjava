package dev.mars.p2pjava.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the CacheManager class.
 */
public class CacheManagerTest {

    private CacheManager<String, String> cacheManager;
    private static final long DEFAULT_TTL_MS = 1000; // 1 second
    private static final long DEFAULT_REFRESH_MS = 500; // 0.5 seconds
    private static final Function<String, String> LOAD_FUNCTION = key -> "value-" + key;
    
    @BeforeEach
    void setUp() {
        // Create a new cache manager for each test
        cacheManager = new CacheManager<>(DEFAULT_TTL_MS, DEFAULT_REFRESH_MS, LOAD_FUNCTION);
    }
    
    @AfterEach
    void tearDown() {
        // Shutdown the cache manager after each test
        if (cacheManager != null) {
            cacheManager.shutdown();
        }
    }
    
    @Test
    void testGetWithLoadFunction() {
        // Get a value that's not in the cache
        String value = cacheManager.get("key1");
        
        // Verify the value was loaded using the load function
        assertEquals("value-key1", value, "Value should be loaded using the load function");
        
        // Verify cache statistics
        assertEquals(0, cacheManager.getCacheHits(), "Cache hits should be 0");
        assertEquals(1, cacheManager.getCacheMisses(), "Cache misses should be 1");
        assertEquals(0.0, cacheManager.getCacheHitRatio(), "Cache hit ratio should be 0.0");
    }
    
    @Test
    void testGetWithCacheHit() {
        // Put a value in the cache
        cacheManager.put("key1", "value1");
        
        // Get the value from the cache
        String value = cacheManager.get("key1");
        
        // Verify the value was retrieved from the cache
        assertEquals("value1", value, "Value should be retrieved from the cache");
        
        // Verify cache statistics
        assertEquals(1, cacheManager.getCacheHits(), "Cache hits should be 1");
        assertEquals(0, cacheManager.getCacheMisses(), "Cache misses should be 0");
        assertEquals(1.0, cacheManager.getCacheHitRatio(), "Cache hit ratio should be 1.0");
    }
    
    @Test
    void testPutAndGet() {
        // Put a value in the cache
        cacheManager.put("key1", "value1");
        
        // Get the value from the cache
        String value = cacheManager.get("key1");
        
        // Verify the value was retrieved from the cache
        assertEquals("value1", value, "Value should be retrieved from the cache");
    }
    
    @Test
    void testPutWithTtlAndGet() {
        // Put a value in the cache with a short TTL
        cacheManager.put("key1", "value1", 100, 0); // 100ms TTL
        
        // Get the value from the cache immediately
        String value1 = cacheManager.get("key1");
        
        // Verify the value was retrieved from the cache
        assertEquals("value1", value1, "Value should be retrieved from the cache");
        
        // Wait for the TTL to expire
        try {
            TimeUnit.MILLISECONDS.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Get the value from the cache after TTL expiration
        String value2 = cacheManager.get("key1");
        
        // Verify the value was loaded using the load function
        assertEquals("value-key1", value2, "Value should be loaded using the load function after TTL expiration");
    }
    
    @Test
    void testRemove() {
        // Put a value in the cache
        cacheManager.put("key1", "value1");
        
        // Remove the value from the cache
        String removedValue = cacheManager.remove("key1");
        
        // Verify the removed value
        assertEquals("value1", removedValue, "Removed value should match the original value");
        
        // Get the value from the cache after removal
        String value = cacheManager.get("key1");
        
        // Verify the value was loaded using the load function
        assertEquals("value-key1", value, "Value should be loaded using the load function after removal");
    }
    
    @Test
    void testClear() {
        // Put multiple values in the cache
        cacheManager.put("key1", "value1");
        cacheManager.put("key2", "value2");
        
        // Verify the cache size
        assertEquals(2, cacheManager.size(), "Cache size should be 2");
        
        // Clear the cache
        cacheManager.clear();
        
        // Verify the cache size after clearing
        assertEquals(0, cacheManager.size(), "Cache size should be 0 after clearing");
    }
    
    @Test
    void testSize() {
        // Verify initial cache size
        assertEquals(0, cacheManager.size(), "Initial cache size should be 0");
        
        // Put values in the cache
        cacheManager.put("key1", "value1");
        assertEquals(1, cacheManager.size(), "Cache size should be 1 after adding one entry");
        
        cacheManager.put("key2", "value2");
        assertEquals(2, cacheManager.size(), "Cache size should be 2 after adding two entries");
        
        // Remove a value
        cacheManager.remove("key1");
        assertEquals(1, cacheManager.size(), "Cache size should be 1 after removing one entry");
    }
    
    @Test
    void testGetStatistics() {
        // Put a value in the cache
        cacheManager.put("key1", "value1");
        
        // Get the value from the cache
        cacheManager.get("key1");
        
        // Get a value that's not in the cache
        cacheManager.get("key2");
        
        // Get the statistics
        String statistics = cacheManager.getStatistics();
        
        // Verify the statistics contains expected information
        assertNotNull(statistics, "Statistics should not be null");
        assertTrue(statistics.contains("hits"), "Statistics should contain hits information");
        assertTrue(statistics.contains("misses"), "Statistics should contain misses information");
        assertTrue(statistics.contains("hit ratio"), "Statistics should contain hit ratio information");
    }
    
    @Test
    void testCacheEviction() throws InterruptedException {
        // Create a cache manager with a very short TTL
        CacheManager<String, String> shortTtlCache = new CacheManager<>(100, 0, LOAD_FUNCTION);
        
        // Put a value in the cache
        shortTtlCache.put("key1", "value1");
        
        // Wait for the TTL to expire
        TimeUnit.MILLISECONDS.sleep(200);
        
        // Get the value from the cache after TTL expiration
        String value = shortTtlCache.get("key1");
        
        // Verify the value was loaded using the load function
        assertEquals("value-key1", value, "Value should be loaded using the load function after TTL expiration");
        
        // Verify cache evictions
        assertTrue(shortTtlCache.getCacheEvictions() > 0, "Cache evictions should be greater than 0");
        
        // Clean up
        shortTtlCache.shutdown();
    }
    
    @Test
    void testCacheRefresh() throws InterruptedException {
        // Create a counter to track load function calls
        final int[] loadCount = {0};
        
        // Create a cache manager with a refresh function that increments the counter
        Function<String, String> countingLoadFunction = key -> {
            loadCount[0]++;
            return "value-" + key + "-" + loadCount[0];
        };
        
        CacheManager<String, String> refreshCache = new CacheManager<>(1000, 100, countingLoadFunction);
        
        // Get a value to trigger the initial load
        String initialValue = refreshCache.get("key1");
        assertEquals("value-key1-1", initialValue, "Initial value should be loaded");
        assertEquals(1, loadCount[0], "Load function should be called once");
        
        // Wait for the refresh to occur
        TimeUnit.MILLISECONDS.sleep(200);
        
        // Get the value again, which should now be the refreshed value
        String refreshedValue = refreshCache.get("key1");
        assertEquals("value-key1-2", refreshedValue, "Value should be refreshed");
        assertEquals(2, loadCount[0], "Load function should be called twice");
        
        // Verify cache refreshes
        assertTrue(refreshCache.getCacheRefreshes() > 0, "Cache refreshes should be greater than 0");
        
        // Clean up
        refreshCache.shutdown();
    }
}