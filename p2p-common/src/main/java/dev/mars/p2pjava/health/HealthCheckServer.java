package dev.mars.p2pjava.health;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.mars.p2pjava.config.ConfigurationManager;
import dev.mars.p2pjava.util.HealthCheck;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP server that provides health check endpoints for the P2P system.
 * This server exposes endpoints that return health status information in JSON format.
 */
public class HealthCheckServer {
    private static final Logger logger = Logger.getLogger(HealthCheckServer.class.getName());

    private final HttpServer server;
    private final int port;
    private final String contextPath;
    private final ConfigurationManager config;

    /**
     * Creates a new health check server.
     *
     * @throws IOException If the server cannot be created
     */
    public HealthCheckServer() throws IOException {
        config = ConfigurationManager.getInstance();
        port = config.getInt("healthcheck.port", 8080);
        contextPath = config.get("healthcheck.path", "/health");

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext(contextPath, new HealthHandler());
        server.createContext(contextPath + "/details", new DetailedHealthHandler());
        server.createContext(contextPath + "/service", new ServiceHealthHandler());
        server.setExecutor(Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "HealthCheck-" + java.util.UUID.randomUUID().toString().substring(0, 8));
            t.setDaemon(true);
            return t;
        }));

        logger.info("Health check server created on port " + port + " with context path " + contextPath);
    }

    /**
     * Starts the health check server.
     */
    public void start() {
        server.start();
        logger.info("Health check server started on port " + port);
    }

    /**
     * Stops the health check server.
     */
    public void stop() {
        server.stop(0);
        logger.info("Health check server stopped");
    }

    /**
     * Handler for the basic health check endpoint.
     * Returns a simple JSON response with the overall health status.
     */
    private static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                // Get overall health status
                boolean healthy = HealthCheck.getAllServiceHealth().values().stream()
                        .allMatch(HealthCheck.ServiceHealth::isHealthy);

                // Create JSON response
                String response = "{\"status\":\"" + (healthy ? "UP" : "DOWN") + "\"}";

                // Set response headers
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(healthy ? 200 : 503, response.length());

                // Send response
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error handling health check request", e);
                String errorResponse = "{\"status\":\"ERROR\",\"message\":\"" + e.getMessage() + "\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(500, errorResponse.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errorResponse.getBytes(StandardCharsets.UTF_8));
                }
            }
        }
    }

    /**
     * Handler for the detailed health check endpoint.
     * Returns a JSON response with detailed health status information for all services.
     */
    private static class DetailedHealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                // Get all service health statuses
                Map<String, HealthCheck.ServiceHealth> serviceHealthMap = HealthCheck.getAllServiceHealth();

                // Determine overall health
                boolean healthy = serviceHealthMap.values().stream()
                        .allMatch(HealthCheck.ServiceHealth::isHealthy);

                // Build JSON response
                StringBuilder response = new StringBuilder();
                response.append("{\"status\":\"").append(healthy ? "UP" : "DOWN").append("\",");
                response.append("\"services\":{");

                boolean first = true;
                for (Map.Entry<String, HealthCheck.ServiceHealth> entry : serviceHealthMap.entrySet()) {
                    if (!first) {
                        response.append(",");
                    }
                    first = false;

                    String serviceName = entry.getKey();
                    HealthCheck.ServiceHealth health = entry.getValue();

                    response.append("\"").append(serviceName).append("\":{");
                    response.append("\"status\":\"").append(health.isHealthy() ? "UP" : "DOWN").append("\",");
                    response.append("\"lastChecked\":").append(health.getLastCheckedTimestamp()).append(",");

                    // Add health details
                    response.append("\"details\":{");
                    Map<String, Object> details = health.getHealthDetails();
                    boolean firstDetail = true;
                    for (Map.Entry<String, Object> detail : details.entrySet()) {
                        if (!firstDetail) {
                            response.append(",");
                        }
                        firstDetail = false;

                        response.append("\"").append(detail.getKey()).append("\":");
                        Object value = detail.getValue();
                        if (value instanceof String) {
                            response.append("\"").append(value).append("\"");
                        } else if (value instanceof Number || value instanceof Boolean) {
                            response.append(value);
                        } else {
                            response.append("\"").append(value).append("\"");
                        }
                    }
                    response.append("}");

                    response.append("}");
                }

                response.append("}}");

                // Set response headers
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(healthy ? 200 : 503, response.length());

                // Send response
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.toString().getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error handling detailed health check request", e);
                String errorResponse = "{\"status\":\"ERROR\",\"message\":\"" + e.getMessage() + "\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(500, errorResponse.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errorResponse.getBytes(StandardCharsets.UTF_8));
                }
            }
        }
    }

    /**
     * Handler for the service-specific health check endpoint.
     * Returns a JSON response with health status information for a specific service.
     */
    private static class ServiceHealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                // Get the service name from the query string
                String query = exchange.getRequestURI().getQuery();
                String serviceName = null;
                if (query != null && query.startsWith("name=")) {
                    serviceName = query.substring(5);
                }

                if (serviceName == null || serviceName.isEmpty()) {
                    // Bad request
                    String errorResponse = "{\"status\":\"ERROR\",\"message\":\"Service name not specified\"}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(400, errorResponse.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(errorResponse.getBytes(StandardCharsets.UTF_8));
                    }
                    return;
                }

                // Get the service health
                HealthCheck.ServiceHealth health = HealthCheck.getServiceHealth(serviceName);

                if (health == null) {
                    // Service not found
                    String errorResponse = "{\"status\":\"ERROR\",\"message\":\"Service not found: " + serviceName + "\"}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(404, errorResponse.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(errorResponse.getBytes(StandardCharsets.UTF_8));
                    }
                    return;
                }

                // Build JSON response
                StringBuilder response = new StringBuilder();
                response.append("{\"service\":\"").append(serviceName).append("\",");
                response.append("\"status\":\"").append(health.isHealthy() ? "UP" : "DOWN").append("\",");
                response.append("\"lastChecked\":").append(health.getLastCheckedTimestamp()).append(",");

                // Add health details
                response.append("\"details\":{");
                Map<String, Object> details = health.getHealthDetails();
                boolean firstDetail = true;
                for (Map.Entry<String, Object> detail : details.entrySet()) {
                    if (!firstDetail) {
                        response.append(",");
                    }
                    firstDetail = false;

                    response.append("\"").append(detail.getKey()).append("\":");
                    Object value = detail.getValue();
                    if (value instanceof String) {
                        response.append("\"").append(value).append("\"");
                    } else if (value instanceof Number || value instanceof Boolean) {
                        response.append(value);
                    } else {
                        response.append("\"").append(value).append("\"");
                    }
                }
                response.append("}");

                response.append("}");

                // Set response headers
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(health.isHealthy() ? 200 : 503, response.length());

                // Send response
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.toString().getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error handling service health check request", e);
                String errorResponse = "{\"status\":\"ERROR\",\"message\":\"" + e.getMessage() + "\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(500, errorResponse.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errorResponse.getBytes(StandardCharsets.UTF_8));
                }
            }
        }
    }
}
