package dev.mars.p2pjava.health;

import dev.mars.p2pjava.config.ConfigurationManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the HealthCheckServer class.
 */
public class HealthCheckServerTest {

    private HealthCheckServer healthCheckServer;
    private static final int TEST_PORT = 8090;
    private static final String BASE_URL = "http://localhost:" + TEST_PORT;

    @BeforeEach
    void setUp() throws IOException {
        // Set the port in the ConfigurationManager
        ConfigurationManager.getInstance().set("healthcheck.port", String.valueOf(TEST_PORT));

        // Create a new HealthCheckServer instance for each test
        healthCheckServer = new HealthCheckServer();
        healthCheckServer.start();

        // Give the server a moment to start
        try {
            TimeUnit.MILLISECONDS.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @AfterEach
    void tearDown() {
        // Stop the server after each test
        if (healthCheckServer != null) {
            healthCheckServer.stop();
        }
    }

    @Test
    void testHealthEndpoint() throws IOException {
        // Test the /health endpoint
        HttpURLConnection connection = createConnection(BASE_URL + "/health");

        // Verify the response
        assertEquals(200, connection.getResponseCode(), "Health endpoint should return 200 OK");
        String response = readResponse(connection);
        assertTrue(response.contains("status"), "Response should contain status field");
    }

    @Test
    void testHealthDetailedEndpoint() throws IOException {
        // Test the /health/detailed endpoint
        HttpURLConnection connection = createConnection(BASE_URL + "/health/detailed");

        // Verify the response
        assertEquals(200, connection.getResponseCode(), "Health detailed endpoint should return 200 OK");
        String response = readResponse(connection);
        assertTrue(response.contains("status"), "Response should contain status field");
        assertTrue(response.contains("details"), "Response should contain details field");
    }

    @Test
    void testServiceHealthEndpoint() throws IOException {
        // Register a test service
        String serviceName = "test-service";
        dev.mars.p2pjava.util.HealthCheck.registerService(serviceName);

        // Test the /health/service endpoint
        HttpURLConnection connection = createConnection(BASE_URL + "/health/service?name=" + serviceName);

        // Verify the response
        assertEquals(200, connection.getResponseCode(), "Service health endpoint should return 200 OK");
        String response = readResponse(connection);
        assertTrue(response.contains(serviceName), "Response should contain the service name");
        assertTrue(response.contains("healthy"), "Response should contain healthy field");

        // Clean up
        dev.mars.p2pjava.util.HealthCheck.deregisterService(serviceName);
    }

    @Test
    void testServiceHealthEndpointWithNonexistentService() throws IOException {
        // Test the /health/service endpoint with a nonexistent service
        HttpURLConnection connection = createConnection(BASE_URL + "/health/service?name=nonexistent-service");

        // Verify the response
        assertEquals(404, connection.getResponseCode(), "Nonexistent service should return 404 Not Found");
    }

    @Test
    void testServiceHealthEndpointWithoutName() throws IOException {
        // Test the /health/service endpoint without a name parameter
        HttpURLConnection connection = createConnection(BASE_URL + "/health/service");

        // Verify the response
        assertEquals(400, connection.getResponseCode(), "Missing name parameter should return 400 Bad Request");
    }

    /**
     * Creates an HTTP connection to the specified URL.
     *
     * @param urlString The URL to connect to
     * @return The HttpURLConnection
     * @throws IOException If an I/O error occurs
     */
    private HttpURLConnection createConnection(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        return connection;
    }

    /**
     * Reads the response from an HTTP connection.
     *
     * @param connection The HttpURLConnection
     * @return The response as a string
     * @throws IOException If an I/O error occurs
     */
    private String readResponse(HttpURLConnection connection) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }
}
