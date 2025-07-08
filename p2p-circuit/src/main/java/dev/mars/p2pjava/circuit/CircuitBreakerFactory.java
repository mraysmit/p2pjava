package dev.mars.p2pjava.circuit;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * Factory for creating and managing circuit breakers with consistent configurations.
 */
public class CircuitBreakerFactory {
    private static final Logger logger = Logger.getLogger(CircuitBreakerFactory.class.getName());
    
    private static final ConcurrentHashMap<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    
    // Default configurations for different service types
    public static class Config {
        public final int failureThreshold;
        public final long resetTimeoutMs;
        public final int successThreshold;
        public final Predicate<Exception> failureFilter;
        
        public Config(int failureThreshold, long resetTimeoutMs, int successThreshold, 
                     Predicate<Exception> failureFilter) {
            this.failureThreshold = failureThreshold;
            this.resetTimeoutMs = resetTimeoutMs;
            this.successThreshold = successThreshold;
            this.failureFilter = failureFilter;
        }
    }
    
    // Predefined configurations
    public static final Config NETWORK_SERVICE_CONFIG = new Config(
        5,      // 5 failures
        30000,  // 30 second reset timeout
        3,      // 3 successes to close
        e -> e instanceof IOException || e instanceof SocketException || 
             e instanceof SocketTimeoutException || e instanceof TimeoutException
    );
    
    public static final Config CRITICAL_SERVICE_CONFIG = new Config(
        3,      // 3 failures (more sensitive)
        60000,  // 60 second reset timeout (longer recovery time)
        2,      // 2 successes to close
        e -> true // All exceptions trigger circuit breaker
    );
    
    public static final Config FAST_RECOVERY_CONFIG = new Config(
        10,     // 10 failures (more tolerant)
        10000,  // 10 second reset timeout (quick recovery)
        5,      // 5 successes to close
        e -> e instanceof IOException || e instanceof SocketException
    );
    
    public static final Config TRACKER_SERVICE_CONFIG = new Config(
        5,      // 5 failures
        30000,  // 30 second reset timeout
        3,      // 3 successes to close
        e -> e instanceof IOException || e instanceof SocketException || 
             e instanceof SocketTimeoutException
    );
    
    public static final Config INDEX_SERVER_CONFIG = new Config(
        3,      // 3 failures (index server is critical)
        45000,  // 45 second reset timeout
        2,      // 2 successes to close
        e -> e instanceof IOException || e instanceof SocketException || 
             e instanceof SocketTimeoutException
    );
    
    /**
     * Gets or creates a circuit breaker with the specified configuration.
     *
     * @param name The name of the circuit breaker
     * @param config The configuration to use
     * @return The circuit breaker instance
     */
    public static CircuitBreaker getOrCreate(String name, Config config) {
        return circuitBreakers.computeIfAbsent(name, n -> {
            logger.info("Creating new circuit breaker: " + n);
            return new CircuitBreaker(n, config.failureThreshold, config.resetTimeoutMs, 
                                    config.successThreshold, config.failureFilter);
        });
    }
    
    /**
     * Gets or creates a circuit breaker for network services.
     *
     * @param serviceName The name of the service
     * @return The circuit breaker instance
     */
    public static CircuitBreaker forNetworkService(String serviceName) {
        return getOrCreate(serviceName, NETWORK_SERVICE_CONFIG);
    }
    
    /**
     * Gets or creates a circuit breaker for critical services.
     *
     * @param serviceName The name of the service
     * @return The circuit breaker instance
     */
    public static CircuitBreaker forCriticalService(String serviceName) {
        return getOrCreate(serviceName, CRITICAL_SERVICE_CONFIG);
    }
    
    /**
     * Gets or creates a circuit breaker for services that need fast recovery.
     *
     * @param serviceName The name of the service
     * @return The circuit breaker instance
     */
    public static CircuitBreaker forFastRecovery(String serviceName) {
        return getOrCreate(serviceName, FAST_RECOVERY_CONFIG);
    }
    
    /**
     * Gets or creates a circuit breaker for tracker service.
     *
     * @param serviceName The name of the service
     * @return The circuit breaker instance
     */
    public static CircuitBreaker forTrackerService(String serviceName) {
        return getOrCreate(serviceName, TRACKER_SERVICE_CONFIG);
    }
    
    /**
     * Gets or creates a circuit breaker for index server.
     *
     * @param serviceName The name of the service
     * @return The circuit breaker instance
     */
    public static CircuitBreaker forIndexServer(String serviceName) {
        return getOrCreate(serviceName, INDEX_SERVER_CONFIG);
    }
    
    /**
     * Gets an existing circuit breaker by name.
     *
     * @param name The name of the circuit breaker
     * @return The circuit breaker instance, or null if not found
     */
    public static CircuitBreaker get(String name) {
        return circuitBreakers.get(name);
    }
    
    /**
     * Removes a circuit breaker from the factory.
     *
     * @param name The name of the circuit breaker to remove
     * @return The removed circuit breaker, or null if not found
     */
    public static CircuitBreaker remove(String name) {
        CircuitBreaker removed = circuitBreakers.remove(name);
        if (removed != null) {
            logger.info("Removed circuit breaker: " + name);
        }
        return removed;
    }
    
    /**
     * Resets all circuit breakers managed by this factory.
     */
    public static void resetAll() {
        logger.info("Resetting all circuit breakers");
        circuitBreakers.values().forEach(CircuitBreaker::reset);
    }
    
    /**
     * Gets the number of circuit breakers managed by this factory.
     *
     * @return The number of circuit breakers
     */
    public static int size() {
        return circuitBreakers.size();
    }
    
    /**
     * Clears all circuit breakers from the factory.
     */
    public static void clear() {
        logger.info("Clearing all circuit breakers");
        circuitBreakers.clear();
    }
}
