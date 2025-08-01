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

    // Service configuration map
    private static final Map<String, ServiceConfig> SERVICE_CONFIGS = new HashMap<>();

    // Static initializer to populate the service configurations
    static {
        // Tracker service configuration
        SERVICE_CONFIGS.put("tracker", new ServiceConfig(6000, "tracker.port", "tracker"));

        // Index server configuration
        SERVICE_CONFIGS.put("indexserver", new ServiceConfig(6001, "indexserver.port", "index server"));

        // Peer service configuration
        SERVICE_CONFIGS.put("peer", new ServiceConfig(7000, "peer.port", "peer"));

        // Cache service configuration
        SERVICE_CONFIGS.put("cache", new ServiceConfig(0, "", "cache manager"));

        // Connection service configuration
        SERVICE_CONFIGS.put("connection", new ServiceConfig(0, "", "connection pool"));

        // Discovery service configuration
        SERVICE_CONFIGS.put("discovery", new ServiceConfig(0, "", "service registry"));

        // Storage service configuration
        SERVICE_CONFIGS.put("storage", new ServiceConfig(0, "", "file storage"));
    }

    // Service configuration class
    private static class ServiceConfig {
        private final int basePort;
        private final String portProperty;
        private final String serviceType;

        public ServiceConfig(int basePort, String portProperty, String serviceType) {
            this.basePort = basePort;
            this.portProperty = portProperty;
            this.serviceType = serviceType;
        }

        public int getBasePort() {
            return basePort;
        }

        public String getPortProperty() {
            return portProperty;
        }

        public String getServiceType() {
            return serviceType;
        }
    }

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
     * @throws CircularDependencyException if circular dependencies are detected
     */
    public boolean start() throws CircularDependencyException {
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

        // Validate dependencies for circular references
        DependencyAnalyzer analyzer = new DependencyAnalyzer(services.keySet(), dependencies);
        DependencyAnalyzer.AnalysisResult analysisResult = analyzer.analyze();

        if (!analysisResult.isValid()) {
            logger.severe("Circular dependency detected: " + analysisResult.getErrorMessage());
            stop();
            throw new CircularDependencyException(analysisResult.getErrorMessage(), analysisResult.getCircularDependencies());
        }

        logger.info("Dependency validation passed - no circular dependencies detected");

        // Use the topologically sorted order from the analyzer
        List<String> startupOrder = analysisResult.getTopologicalOrder();

        // Start services in the validated order
        boolean success = startServicesInTopologicalOrder(startupOrder);

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
     * Starts services in the provided topological order.
     * This method uses the validated topological order from the dependency analyzer
     * to start services in the correct sequence.
     *
     * @param topologicalOrder The topologically sorted list of service IDs
     * @return true if all services started successfully, false otherwise
     */
    private boolean startServicesInTopologicalOrder(List<String> topologicalOrder) {
        logger.info("Starting services in topological order: " + topologicalOrder);

        for (String serviceId : topologicalOrder) {
            ServiceInstance service = services.get(serviceId);
            if (service != null) {
                if (!startService(service)) {
                    logger.severe("Failed to start service: " + serviceId);
                    return false;
                }

                // Wait for service to be ready before starting the next one
                long timeoutMs = config.getInt("bootstrap.service.startup.timeout.seconds", 10) * 1000L;
                long startTime = System.currentTimeMillis();

                while (running && !service.isReady() && !service.isFailed() &&
                       System.currentTimeMillis() - startTime < timeoutMs) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.warning("Interrupted while waiting for service to be ready: " + serviceId);
                        return false;
                    }
                }

                if (service.isFailed()) {
                    logger.severe("Service failed during startup: " + serviceId);
                    return false;
                }

                if (!service.isReady()) {
                    logger.severe("Service startup timeout: " + serviceId);
                    return false;
                }

                logger.info("Service started successfully: " + serviceId);
            } else {
                logger.warning("Service not found in registry: " + serviceId);
            }
        }

        return true;
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
                    // Get service configuration from P2PComponent
                    P2PComponent.ComponentConfig componentConfig = null;

                    // Check for exact match first
                    componentConfig = P2PComponent.getConfig(serviceId);

                    // For peer services that might have a suffix (e.g., peer1, peer2)
                    if (componentConfig == null && serviceId.startsWith("peer")) {
                        componentConfig = P2PComponent.getConfig(P2PComponent.PEER);
                    }

                    // Configure dynamic port if component configuration exists
                    if (componentConfig != null && componentConfig.getBasePort() > 0 && !componentConfig.getPortProperty().isEmpty()) {
                        int dynamicPort = config.findAvailablePort(componentConfig.getBasePort());
                        config.set(componentConfig.getPortProperty(), String.valueOf(dynamicPort));
                        // Set system property for service to use
                        System.setProperty(componentConfig.getPortProperty(), String.valueOf(dynamicPort));
                        logger.info("Using dynamic port for " + componentConfig.getServiceType() + ": " + dynamicPort);
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
    public static class ServiceDependency {
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
