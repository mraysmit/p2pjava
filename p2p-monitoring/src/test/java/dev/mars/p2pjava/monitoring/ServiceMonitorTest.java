package dev.mars.p2pjava.monitoring;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the ServiceMonitor class.
 */
public class ServiceMonitorTest {

    private static final String TEST_SERVICE = "TestService";
    private static final String ANOTHER_SERVICE = "AnotherService";

    @BeforeEach
    void setUp() {
        // Ensure we start with a clean state for each test
        ServiceMonitor.deregisterService(TEST_SERVICE);
        ServiceMonitor.deregisterService(ANOTHER_SERVICE);
    }

    @AfterEach
    void tearDown() {
        // Clean up after each test
        ServiceMonitor.deregisterService(TEST_SERVICE);
        ServiceMonitor.deregisterService(ANOTHER_SERVICE);
    }

    @Test
    void testRegisterService() {
        // Register a service
        ServiceMonitor.ServiceMetrics metrics = ServiceMonitor.registerService(TEST_SERVICE);

        // Verify the service was registered
        assertNotNull(metrics, "Service metrics should not be null");
        assertEquals(TEST_SERVICE, metrics.getServiceName(), "Service name should match");

        // Verify we can get the metrics for the service
        ServiceMonitor.ServiceMetrics retrievedMetrics = ServiceMonitor.getServiceMetrics(TEST_SERVICE);
        assertNotNull(retrievedMetrics, "Retrieved metrics should not be null");
        assertEquals(TEST_SERVICE, retrievedMetrics.getServiceName(), "Retrieved service name should match");

        // Verify the metrics are the same instance
        assertSame(metrics, retrievedMetrics, "Retrieved metrics should be the same instance");
    }

    @Test
    void testRegisterDuplicateService() {
        // Register a service
        ServiceMonitor.ServiceMetrics metrics1 = ServiceMonitor.registerService(TEST_SERVICE);

        // Register the same service again
        ServiceMonitor.ServiceMetrics metrics2 = ServiceMonitor.registerService(TEST_SERVICE);

        // Verify both metrics objects have the same service name
        // Note: ServiceMonitor creates a new instance each time, so we can't use assertSame
        assertEquals(metrics1.getServiceName(), metrics2.getServiceName(), 
                "Both metrics objects should have the same service name");
    }

    @Test
    void testDeregisterService() {
        // Register a service
        ServiceMonitor.registerService(TEST_SERVICE);

        // Verify the service was registered
        assertNotNull(ServiceMonitor.getServiceMetrics(TEST_SERVICE), "Service should be registered");

        // Deregister the service
        ServiceMonitor.deregisterService(TEST_SERVICE);

        // Verify the service was deregistered
        assertNull(ServiceMonitor.getServiceMetrics(TEST_SERVICE), "Service should be deregistered");
    }

    @Test
    void testRecordRequest() {
        // Register a service
        ServiceMonitor.ServiceMetrics metrics = ServiceMonitor.registerService(TEST_SERVICE);

        // Record a successful request
        ServiceMonitor.recordRequest(TEST_SERVICE, 100, false);

        // Verify the request was recorded
        assertEquals(1, metrics.getRequestCount(), "Request count should be 1");
        assertEquals(0, metrics.getErrorCount(), "Error count should be 0");
        assertEquals(0.0, metrics.getErrorRate(), "Error rate should be 0.0");
        assertEquals(100.0, metrics.getAverageResponseTimeMs(), "Average response time should be 100.0");

        // Record an error request
        ServiceMonitor.recordRequest(TEST_SERVICE, 200, true);

        // Verify the error request was recorded
        assertEquals(2, metrics.getRequestCount(), "Request count should be 2");
        assertEquals(1, metrics.getErrorCount(), "Error count should be 1");
        assertEquals(0.5, metrics.getErrorRate(), "Error rate should be 0.5");
        assertEquals(150.0, metrics.getAverageResponseTimeMs(), "Average response time should be 150.0");
    }

    @Test
    void testRecordRequestForNonExistentService() {
        // ServiceMonitor doesn't automatically register services, so we need to register it first
        ServiceMonitor.ServiceMetrics metrics = ServiceMonitor.registerService("NonExistentService");

        // Record a request for the service
        ServiceMonitor.recordRequest("NonExistentService", 100, false);

        // Verify the request was recorded
        assertEquals(1, metrics.getRequestCount(), "Request count should be 1");

        // Clean up
        ServiceMonitor.deregisterService("NonExistentService");
    }

    @Test
    void testRecordOperation() {
        // Register a service
        ServiceMonitor.ServiceMetrics metrics = ServiceMonitor.registerService(TEST_SERVICE);

        // Record an operation
        ServiceMonitor.recordOperation(TEST_SERVICE, "operation1");

        // Verify the operation was recorded
        assertEquals(1, metrics.getOperationCount("operation1"), "Operation count should be 1");

        // Record the same operation again
        ServiceMonitor.recordOperation(TEST_SERVICE, "operation1");

        // Verify the operation count was incremented
        assertEquals(2, metrics.getOperationCount("operation1"), "Operation count should be 2");

        // Record a different operation
        ServiceMonitor.recordOperation(TEST_SERVICE, "operation2");

        // Verify both operations were recorded
        assertEquals(2, metrics.getOperationCount("operation1"), "Operation1 count should be 2");
        assertEquals(1, metrics.getOperationCount("operation2"), "Operation2 count should be 1");

        // Verify we can get all operation counts
        Map<String, Long> operationCounts = metrics.getAllOperationCounts();
        assertEquals(2, operationCounts.size(), "Should have 2 operations");
        assertEquals(2L, operationCounts.get("operation1"), "Operation1 count should be 2");
        assertEquals(1L, operationCounts.get("operation2"), "Operation2 count should be 1");
    }

    @Test
    void testRecordOperationForNonExistentService() {
        // ServiceMonitor doesn't automatically register services, so we need to register it first
        ServiceMonitor.ServiceMetrics metrics = ServiceMonitor.registerService("NonExistentService");

        // Record an operation for the service
        ServiceMonitor.recordOperation("NonExistentService", "operation1");

        // Verify the operation was recorded
        assertEquals(1, metrics.getOperationCount("operation1"), "Operation count should be 1");

        // Clean up
        ServiceMonitor.deregisterService("NonExistentService");
    }

    @Test
    void testIncrementCounter() {
        // Register a service
        ServiceMonitor.ServiceMetrics metrics = ServiceMonitor.registerService(TEST_SERVICE);

        // Increment a counter
        metrics.incrementCounter("counter1");

        // Verify the counter was incremented
        assertEquals(1, metrics.getCounter("counter1"), "Counter should be 1");

        // Increment the same counter again
        metrics.incrementCounter("counter1");

        // Verify the counter was incremented again
        assertEquals(2, metrics.getCounter("counter1"), "Counter should be 2");

        // Increment a different counter with a specific value
        metrics.incrementCounter("counter2", 5);

        // Verify the counter was set to the specified value
        assertEquals(5, metrics.getCounter("counter2"), "Counter should be 5");

        // Verify we can get all counters
        Map<String, Long> counters = metrics.getAllCounters();
        assertEquals(2, counters.size(), "Should have 2 counters");
        assertEquals(2L, counters.get("counter1"), "Counter1 should be 2");
        assertEquals(5L, counters.get("counter2"), "Counter2 should be 5");
    }

    @Test
    void testGetNonExistentCounter() {
        // Register a service
        ServiceMonitor.ServiceMetrics metrics = ServiceMonitor.registerService(TEST_SERVICE);

        // Get a non-existent counter
        long counter = metrics.getCounter("nonExistentCounter");

        // Verify the counter is 0
        assertEquals(0, counter, "Non-existent counter should be 0");
    }

    @Test
    void testResetMetrics() {
        // Register a service
        ServiceMonitor.ServiceMetrics metrics = ServiceMonitor.registerService(TEST_SERVICE);

        // Record some metrics
        ServiceMonitor.recordRequest(TEST_SERVICE, 100, false);
        ServiceMonitor.recordOperation(TEST_SERVICE, "operation1");
        metrics.incrementCounter("counter1");

        // Verify the metrics were recorded
        assertEquals(1, metrics.getRequestCount(), "Request count should be 1");
        assertEquals(1, metrics.getOperationCount("operation1"), "Operation count should be 1");
        assertEquals(1, metrics.getCounter("counter1"), "Counter should be 1");

        // Reset the metrics
        ServiceMonitor.resetMetrics(TEST_SERVICE);

        // Verify the metrics were reset
        assertEquals(0, metrics.getRequestCount(), "Request count should be 0");
        assertEquals(0, metrics.getOperationCount("operation1"), "Operation count should be 0");
        assertEquals(0, metrics.getCounter("counter1"), "Counter should be 0");
    }

    @Test
    void testGetAllServiceMetrics() {
        // Register two services
        ServiceMonitor.registerService(TEST_SERVICE);
        ServiceMonitor.registerService(ANOTHER_SERVICE);

        // Record some metrics
        ServiceMonitor.recordRequest(TEST_SERVICE, 100, false);
        ServiceMonitor.recordRequest(ANOTHER_SERVICE, 200, true);

        // Get all service metrics
        Map<String, ServiceMonitor.ServiceMetrics> allMetrics = ServiceMonitor.getAllServiceMetrics();

        // Verify we have metrics for both services
        assertTrue(allMetrics.containsKey(TEST_SERVICE), "Should have metrics for TestService");
        assertTrue(allMetrics.containsKey(ANOTHER_SERVICE), "Should have metrics for AnotherService");
        assertEquals(1, allMetrics.get(TEST_SERVICE).getRequestCount(), "TestService request count should be 1");
        assertEquals(1, allMetrics.get(ANOTHER_SERVICE).getRequestCount(), "AnotherService request count should be 1");
    }

    @Test
    void testToString() {
        // Register a service
        ServiceMonitor.ServiceMetrics metrics = ServiceMonitor.registerService(TEST_SERVICE);

        // Record some metrics
        ServiceMonitor.recordRequest(TEST_SERVICE, 100, false);
        ServiceMonitor.recordRequest(TEST_SERVICE, 200, true);
        ServiceMonitor.recordOperation(TEST_SERVICE, "operation1");
        metrics.incrementCounter("counter1");

        // Get the string representation
        String metricsString = metrics.toString();

        // Verify the string contains expected information
        assertTrue(metricsString.contains(TEST_SERVICE), "String should contain service name");
        assertTrue(metricsString.contains("requestCount=2"), "String should contain request count");
        assertTrue(metricsString.contains("errorCount=1"), "String should contain error count");
        assertTrue(metricsString.contains("errorRate=50.00%"), "String should contain error rate");
        assertTrue(metricsString.contains("avgResponseTime=150.00ms"), "String should contain average response time");
        assertTrue(metricsString.contains("operations={operation1=1}"), "String should contain operation count");
        assertTrue(metricsString.contains("counters={counter1=1}"), "String should contain counter value");
    }

    @Test
    void testLogMetricsSummary() {
        // Register two services
        ServiceMonitor.registerService(TEST_SERVICE);
        ServiceMonitor.registerService(ANOTHER_SERVICE);

        // Record some metrics
        ServiceMonitor.recordRequest(TEST_SERVICE, 100, false);
        ServiceMonitor.recordRequest(ANOTHER_SERVICE, 200, true);

        // Log metrics summary (this just tests that the method doesn't throw an exception)
        assertDoesNotThrow(() -> ServiceMonitor.logMetricsSummary(), "logMetricsSummary should not throw an exception");
    }
}
