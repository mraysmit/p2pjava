package dev.mars.p2pjava.monitoring;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for monitoring service metrics and performance.
 * Provides methods to track request counts, error rates, response times, etc.
 */
public class ServiceMonitor {
    private static final Logger logger = Logger.getLogger(ServiceMonitor.class.getName());
    private static final Map<String, ServiceMetrics> serviceMetricsMap = new ConcurrentHashMap<>();
    
    /**
     * Represents metrics for a service.
     */
    public static class ServiceMetrics {
        private final String serviceName;
        private final AtomicLong requestCount = new AtomicLong(0);
        private final AtomicLong errorCount = new AtomicLong(0);
        private final AtomicLong totalResponseTimeMs = new AtomicLong(0);
        private final Map<String, AtomicLong> operationCounts = new ConcurrentHashMap<>();
        private final Map<String, AtomicLong> customCounters = new ConcurrentHashMap<>();
        
        public ServiceMetrics(String serviceName) {
            this.serviceName = serviceName;
        }
        
        public String getServiceName() {
            return serviceName;
        }
        
        public long getRequestCount() {
            return requestCount.get();
        }
        
        public long getErrorCount() {
            return errorCount.get();
        }
        
        public double getErrorRate() {
            long requests = requestCount.get();
            return requests > 0 ? (double) errorCount.get() / requests : 0;
        }
        
        public double getAverageResponseTimeMs() {
            long requests = requestCount.get();
            return requests > 0 ? (double) totalResponseTimeMs.get() / requests : 0;
        }
        
        public void recordRequest(long responseTimeMs, boolean isError) {
            requestCount.incrementAndGet();
            totalResponseTimeMs.addAndGet(responseTimeMs);
            if (isError) {
                errorCount.incrementAndGet();
            }
        }
        
        public void recordOperation(String operation) {
            operationCounts.computeIfAbsent(operation, k -> new AtomicLong(0)).incrementAndGet();
        }
        
        public long getOperationCount(String operation) {
            AtomicLong count = operationCounts.get(operation);
            return count != null ? count.get() : 0;
        }
        
        public void incrementCounter(String counterName) {
            customCounters.computeIfAbsent(counterName, k -> new AtomicLong(0)).incrementAndGet();
        }
        
        public void incrementCounter(String counterName, long value) {
            customCounters.computeIfAbsent(counterName, k -> new AtomicLong(0)).addAndGet(value);
        }
        
        public long getCounter(String counterName) {
            AtomicLong count = customCounters.get(counterName);
            return count != null ? count.get() : 0;
        }
        
        public Map<String, Long> getAllOperationCounts() {
            Map<String, Long> result = new ConcurrentHashMap<>();
            operationCounts.forEach((key, value) -> result.put(key, value.get()));
            return result;
        }
        
        public Map<String, Long> getAllCounters() {
            Map<String, Long> result = new ConcurrentHashMap<>();
            customCounters.forEach((key, value) -> result.put(key, value.get()));
            return result;
        }
        
        public void reset() {
            requestCount.set(0);
            errorCount.set(0);
            totalResponseTimeMs.set(0);
            operationCounts.clear();
            customCounters.clear();
        }
        
        @Override
        public String toString() {
            return "ServiceMetrics{" +
                    "serviceName='" + serviceName + '\'' +
                    ", requestCount=" + requestCount +
                    ", errorCount=" + errorCount +
                    ", errorRate=" + String.format("%.2f%%", getErrorRate() * 100) +
                    ", avgResponseTime=" + String.format("%.2fms", getAverageResponseTimeMs()) +
                    ", operations=" + getAllOperationCounts() +
                    ", counters=" + getAllCounters() +
                    '}';
        }
    }
    
    /**
     * Registers a service with the monitoring system.
     *
     * @param serviceName The name of the service
     * @return The ServiceMetrics object for the service
     */
    public static ServiceMetrics registerService(String serviceName) {
        ServiceMetrics metrics = new ServiceMetrics(serviceName);
        serviceMetricsMap.put(serviceName, metrics);
        logger.log(Level.INFO, "Registered monitoring for service: {0}", serviceName);
        return metrics;
    }
    
    /**
     * Gets the metrics for a service.
     *
     * @param serviceName The name of the service
     * @return The ServiceMetrics object for the service, or null if not registered
     */
    public static ServiceMetrics getServiceMetrics(String serviceName) {
        return serviceMetricsMap.get(serviceName);
    }
    
    /**
     * Records a request to a service.
     *
     * @param serviceName The name of the service
     * @param responseTimeMs The response time in milliseconds
     * @param isError Whether the request resulted in an error
     */
    public static void recordRequest(String serviceName, long responseTimeMs, boolean isError) {
        ServiceMetrics metrics = serviceMetricsMap.get(serviceName);
        if (metrics != null) {
            metrics.recordRequest(responseTimeMs, isError);
        }
    }
    
    /**
     * Records an operation performed by a service.
     *
     * @param serviceName The name of the service
     * @param operation The name of the operation
     */
    public static void recordOperation(String serviceName, String operation) {
        ServiceMetrics metrics = serviceMetricsMap.get(serviceName);
        if (metrics != null) {
            metrics.recordOperation(operation);
        }
    }
    
    /**
     * Gets a map of all registered services and their metrics.
     *
     * @return A map of service names to ServiceMetrics objects
     */
    public static Map<String, ServiceMetrics> getAllServiceMetrics() {
        return new ConcurrentHashMap<>(serviceMetricsMap);
    }
    
    /**
     * Removes a service from the monitoring system.
     *
     * @param serviceName The name of the service to remove
     */
    public static void deregisterService(String serviceName) {
        serviceMetricsMap.remove(serviceName);
        logger.log(Level.INFO, "Deregistered monitoring for service: {0}", serviceName);
    }
    
    /**
     * Resets all metrics for a service.
     *
     * @param serviceName The name of the service
     */
    public static void resetMetrics(String serviceName) {
        ServiceMetrics metrics = serviceMetricsMap.get(serviceName);
        if (metrics != null) {
            metrics.reset();
            logger.log(Level.INFO, "Reset metrics for service: {0}", serviceName);
        }
    }
    
    /**
     * Logs a summary of all service metrics.
     */
    public static void logMetricsSummary() {
        logger.info("=== Service Metrics Summary ===");
        serviceMetricsMap.forEach((name, metrics) -> 
                logger.info(metrics.toString()));
        logger.info("==============================");
    }
}
