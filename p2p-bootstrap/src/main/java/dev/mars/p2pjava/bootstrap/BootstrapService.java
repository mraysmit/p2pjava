package dev.mars.p2pjava.bootstrap;

import dev.mars.p2pjava.config.ConfigurationManager;
import dev.mars.p2pjava.health.HealthCheckServer;
import dev.mars.p2pjava.util.HealthCheck;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for bootstrapping the P2P system.
 * This class is responsible for starting the Tracker, IndexServer, and any Peer instances
 * in the correct order, with proper dependency management and graceful shutdown.
 * is used by the P2PBootstrap class to manage the lifecycle of components.
 */
public class BootstrapService {
    private static final Logger logger = Logger.getLogger(BootstrapService.class.getName());

    private final ConfigurationManager config;
    private final ExecutorService executorService;
    private final Map<String, ServiceInstance> services = new HashMap<>();
    private final List<ServiceDependency> dependencies = new ArrayList<>();
    private final HealthCheckServer healthCheckServer;
    private volatile boolean running = false;

    /**
     * Creates a new bootstrap service.
     *
     * @throws IOException If the health check server cannot be created
     */
    public BootstrapService() throws IOException {
        config = ConfigurationManager.getInstance();

        // Create thread pool for service startup
        executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "Bootstrap-" + java.util.UUID.randomUUID().toString().substring(0, 8));
            t.setDaemon(true);
            return t;
        });

        // Create health check server
        // Dynamic port for health check server is enabled by default
        if (config.getBoolean("healthcheck.enabled", true)) {
            // Use dynamic port if configured (enabled by default)
            if (config.getBoolean("bootstrap.dynamic.ports", true)) {
                int dynamicPort = config.findAvailablePort(8000);
                config.set("healthcheck.port", String.valueOf(dynamicPort));
                logger.info("Using dynamic port for health check server: " + dynamicPort);
            }
            healthCheckServer = new HealthCheckServer();
        } else {
            healthCheckServer = null;
        }

        // Register this service with health check
        HealthCheck.ServiceHealth health = HealthCheck.registerService("BootstrapService");
        health.addHealthDetail("startTime", System.currentTimeMillis());

        logger.info("Bootstrap service created");
    }

    /**
     * Registers a service with the bootstrap service.
     *
     * @param serviceId The unique identifier for the service
     * @param serviceClass The class of the service
     * @param startMethodName The name of the method to call to start the service
     * @param stopMethodName The name of the method to call to stop the service
     * @return This bootstrap service for method chaining
     */
    public BootstrapService registerService(String serviceId, Class<?> serviceClass, String startMethodName, String stopMethodName) {
        services.put(serviceId, new ServiceInstance(serviceId, serviceClass, startMethodName, stopMethodName));
        logger.info("Registered service: " + serviceId);
        return this;
    }

    /**
     * Adds a dependency between services.
     *
     * @param dependentServiceId The service that depends on another service
     * @param dependencyServiceId The service that is depended upon
     * @return This bootstrap service for method chaining
     */
    public BootstrapService addDependency(String dependentServiceId, String dependencyServiceId) {
        dependencies.add(new ServiceDependency(dependentServiceId, dependencyServiceId));
        logger.info("Added dependency: " + dependentServiceId + " depends on " + dependencyServiceId);
        return this;
    }

    /**
     * Starts all registered services in the correct order based on dependencies.
     *
     * @return true if all services started successfully, false otherwise
     */
    public boolean start() {
        if (running) {
            logger.warning("Bootstrap service already running");
            return true;
        }

        running = true;
        logger.info("Starting bootstrap service");

        // Start health check server if enabled
        if (healthCheckServer != null) {
            healthCheckServer.start();
        }

        // Build dependency graph
        Map<String, List<String>> dependencyGraph = buildDependencyGraph();

        // Find services with no dependencies (roots)
        List<String> rootServices = findRootServices(dependencyGraph);

        // Start services in dependency order
        boolean success = startServicesInOrder(rootServices, dependencyGraph);

        if (success) {
            logger.info("All services started successfully");

            // Set up shutdown hook for graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
        } else {
            logger.severe("Failed to start all services");
            stop();
        }

        return success;
    }

    /**
     * Stops all registered services in the reverse order of startup.
     */
    public void stop() {
        if (!running) {
            return;
        }

        logger.info("Stopping bootstrap service");
        running = false;

        // Stop services in reverse dependency order
        List<String> serviceIds = new ArrayList<>(services.keySet());
        for (int i = serviceIds.size() - 1; i >= 0; i--) {
            String serviceId = serviceIds.get(i);
            ServiceInstance service = services.get(serviceId);

            try {
                service.stop();
                logger.info("Stopped service: " + serviceId);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error stopping service: " + serviceId, e);
            }
        }

        // Stop health check server
        if (healthCheckServer != null) {
            healthCheckServer.stop();
        }

        // Shutdown executor service
        executorService.shutdownNow();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warning("Executor service did not terminate");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warning("Interrupted while waiting for executor service to terminate");
        }

        logger.info("Bootstrap service stopped");
    }

    /**
     * Builds a dependency graph for the registered services.
     *
     * @return A map of service IDs to lists of dependent service IDs
     */
    private Map<String, List<String>> buildDependencyGraph() {
        Map<String, List<String>> graph = new HashMap<>();

        // Initialize graph with empty dependency lists
        for (String serviceId : services.keySet()) {
            graph.put(serviceId, new ArrayList<>());
        }

        // Add dependencies to graph
        for (ServiceDependency dependency : dependencies) {
            String dependencyServiceId = dependency.getDependencyServiceId();
            String dependentServiceId = dependency.getDependentServiceId();

            List<String> dependents = graph.get(dependencyServiceId);
            if (dependents != null) {
                dependents.add(dependentServiceId);
            }
        }

        return graph;
    }

    /**
     * Finds services with no dependencies (root services).
     *
     * @param dependencyGraph The dependency graph
     * @return A list of service IDs with no dependencies
     */
    private List<String> findRootServices(Map<String, List<String>> dependencyGraph) {
        List<String> rootServices = new ArrayList<>();

        // Find services that are not dependent on any other service
        for (String serviceId : services.keySet()) {
            boolean hasDependency = false;
            for (ServiceDependency dependency : dependencies) {
                if (dependency.getDependentServiceId().equals(serviceId)) {
                    hasDependency = true;
                    break;
                }
            }

            if (!hasDependency) {
                rootServices.add(serviceId);
            }
        }

        return rootServices;
    }

    /**
     * Starts services in dependency order.
     *
     * @param rootServices The root services (with no dependencies)
     * @param dependencyGraph The dependency graph
     * @return true if all services started successfully, false otherwise
     */
    private boolean startServicesInOrder(List<String> rootServices, Map<String, List<String>> dependencyGraph) {
        // Start root services first
        for (String serviceId : rootServices) {
            ServiceInstance service = services.get(serviceId);
            if (!startService(service)) {
                return false;
            }
        }

        // Start dependent services when their dependencies are ready
        Map<String, CountDownLatch> serviceLatchMap = new HashMap<>();
        for (String serviceId : services.keySet()) {
            if (!rootServices.contains(serviceId)) {
                // Count dependencies
                int dependencyCount = 0;
                for (ServiceDependency dependency : dependencies) {
                    if (dependency.getDependentServiceId().equals(serviceId)) {
                        dependencyCount++;
                    }
                }

                // Create latch for this service
                serviceLatchMap.put(serviceId, new CountDownLatch(dependencyCount));
            }
        }

        // Set up dependency countdown
        for (ServiceDependency dependency : dependencies) {
            String dependencyServiceId = dependency.getDependencyServiceId();
            String dependentServiceId = dependency.getDependentServiceId();

            // When a dependency is ready, count down the latch for its dependents
            ServiceInstance dependencyService = services.get(dependencyServiceId);
            CountDownLatch dependentLatch = serviceLatchMap.get(dependentServiceId);

            if (dependencyService != null && dependentLatch != null) {
                executorService.submit(() -> {
                    try {
                        // Wait for dependency to be ready
                        while (running && !dependencyService.isReady()) {
                            Thread.sleep(100);
                        }

                        // Count down the latch for the dependent service
                        dependentLatch.countDown();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
        }

        // Start dependent services when their dependencies are ready
        for (Map.Entry<String, CountDownLatch> entry : serviceLatchMap.entrySet()) {
            String serviceId = entry.getKey();
            CountDownLatch latch = entry.getValue();

            executorService.submit(() -> {
                try {
                    // Wait for dependencies to be ready
                    if (latch.await(config.getInt("bootstrap.startup.timeout.seconds", 30), TimeUnit.SECONDS)) {
                        // Start the service
                        ServiceInstance service = services.get(serviceId);
                        if (service != null) {
                            startService(service);
                        }
                    } else {
                        logger.severe("Timeout waiting for dependencies of service: " + serviceId);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // Wait for all services to be ready or failed
        long timeoutMs = config.getInt("bootstrap.startup.timeout.seconds", 30) * 1000L;
        long startTime = System.currentTimeMillis();

        while (running && System.currentTimeMillis() - startTime < timeoutMs) {
            boolean allReady = true;
            boolean anyFailed = false;

            for (ServiceInstance service : services.values()) {
                if (service.isFailed()) {
                    anyFailed = true;
                    break;
                }

                if (!service.isReady()) {
                    allReady = false;
                }
            }

            if (allReady || anyFailed) {
                break;
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Check if all services are ready
        boolean allReady = true;
        for (ServiceInstance service : services.values()) {
            if (!service.isReady()) {
                allReady = false;
                logger.severe("Service not ready: " + service.getServiceId());
            }
        }

        return allReady;
    }

    /**
     * Starts a service.
     *
     * @param service The service to start
     * @return true if the service started successfully, false otherwise
     */
    private boolean startService(ServiceInstance service) {
        try {
            service.start();
            logger.info("Started service: " + service.getServiceId());
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error starting service: " + service.getServiceId(), e);
            return false;
        }
    }

    /**
     * Represents a service instance.
     */
    private static class ServiceInstance {
        private final String serviceId;
        private final Class<?> serviceClass;
        private final String startMethodName;
        private final String stopMethodName;
        private Object serviceInstance;
        private volatile boolean ready = false;
        private volatile boolean failed = false;

        public ServiceInstance(String serviceId, Class<?> serviceClass, String startMethodName, String stopMethodName) {
            this.serviceId = serviceId;
            this.serviceClass = serviceClass;
            this.startMethodName = startMethodName;
            this.stopMethodName = stopMethodName;
        }

        public String getServiceId() {
            return serviceId;
        }

        public boolean isReady() {
            return ready;
        }

        public boolean isFailed() {
            return failed;
        }

        public void start() throws Exception {
            try {
                // Create service instance if it doesn't exist
                if (serviceInstance == null) {
                    serviceInstance = serviceClass.getDeclaredConstructor().newInstance();
                }

                // Configure dynamic ports if enabled
                ConfigurationManager config = ConfigurationManager.getInstance();
                if (config.getBoolean("bootstrap.dynamic.ports", true)) {
                    // Configure dynamic ports for known services
                    int basePort;
                    String portProperty;
                    String serviceType;

                    if (serviceId.equals("tracker")) {
                        basePort = 6000;
                        portProperty = "tracker.port";
                        serviceType = "tracker";
                    } else if (serviceId.equals("indexserver")) {
                        basePort = 6001;
                        portProperty = "indexserver.port";
                        serviceType = "index server";
                    } else if (serviceId.startsWith("peer")) {
                        basePort = 7000;
                        portProperty = "peer.port";
                        serviceType = "peer";
                    } else {
                        // Skip dynamic port allocation for unknown services
                        basePort = 0;
                        portProperty = null;
                        serviceType = null;
                    }

                    if (portProperty != null) {
                        int dynamicPort = config.findAvailablePort(basePort);
                        config.set(portProperty, String.valueOf(dynamicPort));
                        // Set system property for service to use
                        System.setProperty(portProperty, String.valueOf(dynamicPort));
                        logger.info("Using dynamic port for " + serviceType + ": " + dynamicPort);
                    }
                }

                // Call start method
                Method startMethod = serviceClass.getMethod(startMethodName);
                startMethod.invoke(serviceInstance);

                // Mark as ready
                ready = true;
            } catch (Exception e) {
                failed = true;
                throw e;
            }
        }

        public void stop() throws Exception {
            if (serviceInstance != null) {
                // Call stop method
                Method stopMethod = serviceClass.getMethod(stopMethodName);
                stopMethod.invoke(serviceInstance);

                // Mark as not ready
                ready = false;
            }
        }
    }

    /**
     * Represents a dependency between services.
     */
    private static class ServiceDependency {
        private final String dependentServiceId;
        private final String dependencyServiceId;

        public ServiceDependency(String dependentServiceId, String dependencyServiceId) {
            this.dependentServiceId = dependentServiceId;
            this.dependencyServiceId = dependencyServiceId;
        }

        public String getDependentServiceId() {
            return dependentServiceId;
        }

        public String getDependencyServiceId() {
            return dependencyServiceId;
        }
    }
}
