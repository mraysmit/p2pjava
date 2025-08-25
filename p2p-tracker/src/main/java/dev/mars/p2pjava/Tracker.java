package dev.mars.p2pjava;

/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import dev.mars.p2pjava.config.ConfigurationLoader;
import dev.mars.p2pjava.config.TrackerConfiguration;
import dev.mars.p2pjava.discovery.ServiceInstance;

import java.io.IOException;
import java.util.List;
import java.util.logging.*;

/**
 * Main class for the Tracker application.
 * This class has been refactored to use TrackerService for better testability and lifecycle management.
 *
 * @deprecated Use TrackerService directly for better control and testability.
 */
@Deprecated
public class Tracker {
    private static final Logger logger = Logger.getLogger(Tracker.class.getName());

    // Static instance for backward compatibility
    private static TrackerService trackerService;

    public static void main(String[] args) {
        configureLogging();
        startTracker();
    }

    private static void configureLogging() {
        try {
            Logger rootLogger = Logger.getLogger("");
            Handler[] handlers = rootLogger.getHandlers();
            for (Handler handler : handlers) {
                rootLogger.removeHandler(handler);
            }

            ConsoleHandler handler = new ConsoleHandler();
            handler.setFormatter(new SimpleFormatter());
            handler.setLevel(Level.INFO);

            rootLogger.addHandler(handler);
            rootLogger.setLevel(Level.INFO);
        } catch (Exception e) {
            System.err.println("Error setting up logger: " + e.getMessage());
        }
    }

    /**
     * Starts the tracker service.
     * @deprecated Use TrackerService directly
     */
    @Deprecated
    public static void startTracker() {
        try {
            // Load configuration from all available sources
            TrackerConfiguration config = ConfigurationLoader.loadConfiguration();
            trackerService = new TrackerService(config);

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown hook triggered, stopping tracker...");
                stopTracker();
            }));

            trackerService.start();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to start tracker", e);
            System.exit(1);
        }
    }

    /**
     * Stops the tracker service.
     * @deprecated Use TrackerService directly
     */
    @Deprecated
    public static void stopTracker() {
        if (trackerService != null) {
            trackerService.stop();
        }
    }

    /**
     * Updates peer last seen time.
     * @deprecated Use TrackerService directly
     */
    @Deprecated
    public static void updatePeerLastSeen(String peerId) {
        if (trackerService != null) {
            trackerService.updatePeerLastSeen(peerId);
        }
    }

    /**
     * Checks if peer is alive.
     * @deprecated Use TrackerService directly
     */
    @Deprecated
    public static boolean isPeerAlive(String peerId) {
        if (trackerService != null) {
            return trackerService.isPeerAlive(peerId);
        }
        return false;
    }

    /**
     * Gets active peers.
     * @deprecated Use TrackerService directly
     */
    @Deprecated
    public static List<String> getActivePeers() {
        if (trackerService != null) {
            return trackerService.getActivePeers();
        }
        return List.of();
    }

    /**
     * Discovers other tracker instances.
     * @deprecated Use TrackerService directly
     */
    @Deprecated
    public static List<ServiceInstance> discoverOtherTrackers() {
        if (trackerService != null) {
            return trackerService.discoverOtherTrackers();
        }
        return List.of();
    }

    /**
     * Deregisters a peer.
     * @deprecated Use TrackerService directly
     */
    @Deprecated
    public static boolean deregisterPeer(String peerId) {
        if (trackerService != null) {
            return trackerService.deregisterPeer(peerId);
        }
        return false;
    }
}
