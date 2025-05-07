package dev.mars.p2pjava.bootstrap;

import dev.mars.p2pjava.config.ConfigurationManager;
import dev.mars.p2pjava.util.HealthCheck;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map.Entry;

/**
 * Main application class for bootstrapping the P2P system.
 * This class serves as the entry point for the P2P system and provides
 * different startup modes (e.g., start all components, start only specific components, etc.).
 * Uses BootstrapService to manage the lifecycle of components.
 */
public class P2PBootstrap {
    private static final Logger logger = Logger.getLogger(P2PBootstrap.class.getName());

    // Available components
    public static final String TRACKER = "tracker";
    public static final String INDEX_SERVER = "indexserver";
    public static final String PEER = "peer";
    public static final String ALL = "all";

    // Available modes
    public static final String MODE_START = "start";
    public static final String MODE_STOP = "stop";
    public static final String MODE_STATUS = "status";

    /**
     * Main method.
     *
     * @param args Command-line arguments
     */
    public static void main(String[] args) {
        try {
            // Parse command-line arguments
            String mode = MODE_START;
            Set<String> components = new HashSet<>();
            components.add(ALL);

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];

                if (arg.equals("--mode") && i + 1 < args.length) {
                    mode = args[++i];
                } else if (arg.equals("--components") && i + 1 < args.length) {
                    components.clear();
                    String[] componentList = args[++i].split(",");
                    components.addAll(Arrays.asList(componentList));
                }
            }

            // Initialize configuration
            ConfigurationManager config = ConfigurationManager.getInstance();
            if (!config.initialize(args)) {
                logger.severe("Failed to initialize configuration");
                System.exit(1);
            }

            // Register with health check
            HealthCheck.ServiceHealth health = HealthCheck.registerService("P2PBootstrap");
            health.addHealthDetail("mode", mode);
            health.addHealthDetail("components", components);

            // Execute the requested mode
            switch (mode) {
                case MODE_START:
                    startComponents(components);
                    break;
                case MODE_STOP:
                    stopComponents(components);
                    break;
                case MODE_STATUS:
                    showStatus(components);
                    break;
                default:
                    logger.severe("Unknown mode: " + mode);
                    System.exit(1);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error bootstrapping P2P system", e);
            System.exit(1);
        }
    }

    /**
     * Starts the specified components.
     *
     * @param components The components to start
     * @throws IOException If an I/O error occurs
     */
    private static void startComponents(Set<String> components) throws IOException {
        logger.info("Starting components: " + components);

        // Create bootstrap service
        BootstrapService bootstrap = new BootstrapService();

        // Register components
        try {
            if (components.contains(ALL) || components.contains(TRACKER)) {
                bootstrap.registerService(TRACKER, Class.forName("dev.mars.p2pjava.Tracker"), "startTracker", "stopTracker");
            }

            if (components.contains(ALL) || components.contains(INDEX_SERVER)) {
                bootstrap.registerService(INDEX_SERVER, Class.forName("dev.mars.p2pjava.IndexServer"), "startIndexServer", "stopIndexServer");
            }
        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, "Failed to load service class", e);
            throw new IOException("Failed to load service class: " + e.getMessage(), e);
        }

        if (components.contains(ALL) || components.contains(PEER)) {
            // For peers, we need to create instances with configuration
            // This will be handled separately
            logger.info("Peer startup will be handled separately");
        }

        // Add dependencies
        if ((components.contains(ALL) || components.contains(INDEX_SERVER)) && 
            (components.contains(ALL) || components.contains(TRACKER))) {
            bootstrap.addDependency(INDEX_SERVER, TRACKER);
        }

        if ((components.contains(ALL) || components.contains(PEER)) && 
            (components.contains(ALL) || components.contains(TRACKER))) {
            // Peers depend on the tracker
            // This will be handled separately
        }

        // Start the bootstrap service
        if (!bootstrap.start()) {
            logger.severe("Failed to start components");
            System.exit(1);
        }

        logger.info("Components started successfully");
    }

    /**
     * Stops the specified components.
     *
     * @param components The components to stop
     */
    private static void stopComponents(Set<String> components) {
        logger.info("Stopping components: " + components);

        try {
            // Create bootstrap service
            BootstrapService bootstrap = new BootstrapService();

            // Register components
            try {
                if (components.contains(ALL) || components.contains(TRACKER)) {
                    bootstrap.registerService(TRACKER, Class.forName("dev.mars.p2pjava.Tracker"), "startTracker", "stopTracker");
                }

                if (components.contains(ALL) || components.contains(INDEX_SERVER)) {
                    bootstrap.registerService(INDEX_SERVER, Class.forName("dev.mars.p2pjava.IndexServer"), "startIndexServer", "stopIndexServer");
                }
            } catch (ClassNotFoundException e) {
                logger.log(Level.SEVERE, "Failed to load service class", e);
                return;
            }

            if (components.contains(ALL) || components.contains(PEER)) {
                // For peers, we need to create instances with configuration
                // This will be handled separately
                logger.info("Peer shutdown will be handled separately");
            }

            // Stop the bootstrap service
            bootstrap.stop();

            // Update health status
            for (String component : components) {
                if (component.equals(ALL)) {
                    // Update all components
                    HealthCheck.updateServiceHealth(TRACKER, false);
                    HealthCheck.updateServiceHealth(INDEX_SERVER, false);
                    HealthCheck.updateServiceHealth(PEER, false);
                } else {
                    // Update specific component
                    HealthCheck.updateServiceHealth(component, false);
                }
            }

            logger.info("Components stopped successfully");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error stopping components", e);
        }
    }

    /**
     * Shows the status of the specified components.
     *
     * @param components The components to show status for
     */
    private static void showStatus(Set<String> components) {
        logger.info("Showing status for components: " + components);

        // Get all service health statuses
        Map<String, HealthCheck.ServiceHealth> allHealth = HealthCheck.getAllServiceHealth();

        // Show status for each component
        for (String component : components) {
            if (component.equals(ALL)) {
                // Show status for all components
                showComponentStatus(TRACKER, allHealth.get(TRACKER));
                showComponentStatus(INDEX_SERVER, allHealth.get(INDEX_SERVER));
                showComponentStatus(PEER, allHealth.get(PEER));
            } else {
                // Show status for specific component
                showComponentStatus(component, allHealth.get(component));
            }
        }
    }

    /**
     * Shows the status of a specific component.
     *
     * @param componentId The component ID
     * @param health The health status of the component
     */
    private static void showComponentStatus(String componentId, HealthCheck.ServiceHealth health) {
        if (health != null) {
            logger.info(componentId + " status: " + (health.isHealthy() ? "RUNNING" : "STOPPED"));

            // Show health details
            Map<String, Object> details = health.getHealthDetails();
            if (!details.isEmpty()) {
                logger.info(componentId + " details:");
                for (Map.Entry<String, Object> entry : details.entrySet()) {
                    logger.info("  " + entry.getKey() + ": " + entry.getValue());
                }
            }
        } else {
            logger.info(componentId + " status: NOT REGISTERED");
        }
    }

    /**
     * Prints usage information.
     */
    private static void printUsage() {
        System.out.println("Usage: java -jar p2p-bootstrap.jar [options]");
        System.out.println("Options:");
        System.out.println("  --mode <mode>           Mode: start, stop, status (default: start)");
        System.out.println("  --components <list>     Components to start: tracker, indexserver, peer, all (default: all)");
        System.out.println("  --config.file <path>    Path to configuration file");
        System.out.println("  --<key>=<value>         Set configuration value");
    }
}
