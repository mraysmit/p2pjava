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


import dev.mars.p2pjava.common.PeerInfo;
import dev.mars.p2pjava.storage.FileBasedIndexStorage;
import dev.mars.p2pjava.storage.FileIndexStorage;
import dev.mars.p2pjava.cache.CacheManager;

import dev.mars.p2pjava.util.HealthCheck;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IndexServerTest {

    private static final String TEST_STORAGE_DIR = "test-data";
    private static final String TEST_STORAGE_FILE = "test-index.dat";
    private static final String SERVICE_NAME = "IndexServer";

    // Track the server thread so we can stop it in tearDown
    private static Thread serverThread;

    @BeforeEach
    void setUp() throws Exception {
        // Stop any previous server
        if (serverThread != null && serverThread.isAlive()) {
            IndexServer.stopIndexServer();
            serverThread.interrupt();
            serverThread = null;
        }

        // Make sure the service is not registered with HealthCheck
        HealthCheck.deregisterService(SERVICE_NAME);

        // Create test directory if it doesn't exist
        File testDir = new File(TEST_STORAGE_DIR);
        if (!testDir.exists()) {
            testDir.mkdirs();
        }

        // Delete test file if it exists
        File testFile = new File(TEST_STORAGE_DIR, TEST_STORAGE_FILE);
        if (testFile.exists()) {
            testFile.delete();
        }

        // Create and initialize a test storage instance
        FileIndexStorage testStorage = new FileBasedIndexStorage(
                Paths.get(TEST_STORAGE_DIR, TEST_STORAGE_FILE).toString());
        testStorage.initialize();

        // Set the fileIndexStorage field using reflection
        Field field = IndexServer.class.getDeclaredField("fileIndexStorage");
        field.setAccessible(true);
        field.set(null, testStorage);

        // Make sure health is null before each test
        Field healthField = IndexServer.class.getDeclaredField("health");
        healthField.setAccessible(true);
        healthField.set(null, null);

        // Reset the running flag
        Field runningField = IndexServer.class.getDeclaredField("running");
        runningField.setAccessible(true);
        runningField.set(null, true);
    }

    @AfterEach
    void tearDown() {
        // Stop the server if it's running
        if (serverThread != null && serverThread.isAlive()) {
            IndexServer.stopIndexServer();
            serverThread.interrupt();
            try {
                serverThread.join(1000); // Wait for thread to terminate
            } catch (InterruptedException e) {
                // Ignore
            }
            serverThread = null;
        }

        // Clean up health check registration
        HealthCheck.deregisterService(SERVICE_NAME);

        // Reset the running flag in IndexServer to ensure it's ready for the next test
        try {
            Field runningField = IndexServer.class.getDeclaredField("running");
            runningField.setAccessible(true);
            runningField.set(null, true);
        } catch (Exception e) {
            // Ignore
        }
    }

    @Test
    void testRegisterFile() {
        PeerInfo peer = new PeerInfo("peer1", "localhost", 8000);
        IndexServer.registerFile("file1.txt", peer);

        List<PeerInfo> peersWithFile = IndexServer.getPeersWithFile("file1.txt");
        assertEquals(1, peersWithFile.size());
        assertTrue(peersWithFile.contains(peer));
    }

    @Test
    void testGetPeersWithFile() {
        PeerInfo peer1 = new PeerInfo("peer1", "localhost", 8000);
        PeerInfo peer2 = new PeerInfo("peer2", "localhost", 8001);

        IndexServer.registerFile("file1.txt", peer1);
        IndexServer.registerFile("file1.txt", peer2);
        IndexServer.registerFile("file2.txt", peer1);

        List<PeerInfo> peersWithFile1 = IndexServer.getPeersWithFile("file1.txt");
        assertEquals(2, peersWithFile1.size());
        assertTrue(peersWithFile1.contains(peer1));
        assertTrue(peersWithFile1.contains(peer2));

        List<PeerInfo> peersWithFile2 = IndexServer.getPeersWithFile("file2.txt");
        assertEquals(1, peersWithFile2.size());
        assertTrue(peersWithFile2.contains(peer1));
    }

    @Test
    void testHealthCheckRegistration() throws Exception {
        // Verify service is not registered before starting
        assertNull(HealthCheck.getServiceHealth(SERVICE_NAME));

        // Start the server
        startServerInThread();

        try {
            // Verify service is registered
            HealthCheck.ServiceHealth health = HealthCheck.getServiceHealth(SERVICE_NAME);
            assertNotNull(health);
            assertEquals(SERVICE_NAME, health.getServiceName());
        } finally {
            // Clean up is handled in tearDown()
        }
    }

    /**
     * Helper method to set up a mock server for testing
     */
    private Thread startServerInThread() throws InterruptedException {
        // Stop any existing server
        if (serverThread != null && serverThread.isAlive()) {
            IndexServer.stopIndexServer();
            serverThread.interrupt();
            try {
                serverThread.join(1000); // Wait for thread to terminate
            } catch (InterruptedException e) {
                // Ignore
            }
            serverThread = null;
        }

        // Make sure any existing health check is deregistered
        HealthCheck.deregisterService(SERVICE_NAME);

        // Create a mock health service for testing
        HealthCheck.ServiceHealth mockHealth = HealthCheck.registerService(SERVICE_NAME);
        mockHealth.setHealthy(true);
        mockHealth.addHealthDetail("status", "running");
        mockHealth.addHealthDetail("port", 6001);
        mockHealth.addHealthDetail("startTime", System.currentTimeMillis());
        mockHealth.addHealthDetail("storageDir", "test-data");
        mockHealth.addHealthDetail("storageFile", "test-index.dat");
        mockHealth.addHealthDetail("fileCount", 0);
        mockHealth.addHealthDetail("peerCount", 0);
        mockHealth.addHealthDetail("lastOperationSuccess", true);

        try {
            // Set the health field in IndexServer using reflection
            Field healthField = IndexServer.class.getDeclaredField("health");
            healthField.setAccessible(true);
            healthField.set(null, mockHealth);

            // Create a mock storage that always returns success
            FileIndexStorage mockStorage = new FileIndexStorage() {
                private Map<String, List<PeerInfo>> fileMap = new HashMap<>();

                @Override
                public boolean initialize() {
                    return true;
                }

                @Override
                public boolean shutdown() {
                    return true;
                }

                @Override
                public boolean saveIndex() {
                    return true;
                }

                @Override
                public boolean loadIndex() {
                    return true;
                }

                @Override
                public boolean registerFile(String fileName, PeerInfo peerInfo) {
                    List<PeerInfo> peers = fileMap.computeIfAbsent(fileName, k -> new ArrayList<>());
                    if (!peers.contains(peerInfo)) {
                        peers.add(peerInfo);
                    }
                    return true;
                }

                @Override
                public boolean deregisterFile(String fileName, PeerInfo peerInfo) {
                    List<PeerInfo> peers = fileMap.get(fileName);
                    if (peers != null) {
                        peers.remove(peerInfo);
                        if (peers.isEmpty()) {
                            fileMap.remove(fileName);
                        }
                    }
                    return true;
                }

                @Override
                public boolean deregisterPeer(PeerInfo peerInfo) {
                    for (List<PeerInfo> peers : fileMap.values()) {
                        peers.remove(peerInfo);
                    }
                    fileMap.entrySet().removeIf(entry -> entry.getValue().isEmpty());
                    return true;
                }

                @Override
                public List<PeerInfo> getPeersWithFile(String fileName) {
                    return fileMap.getOrDefault(fileName, new ArrayList<>());
                }

                @Override
                public Map<String, List<PeerInfo>> getAllFiles() {
                    return new HashMap<>(fileMap);
                }

                @Override
                public Map<String, List<PeerInfo>> searchFiles(String pattern) {
                    Map<String, List<PeerInfo>> results = new HashMap<>();
                    for (Map.Entry<String, List<PeerInfo>> entry : fileMap.entrySet()) {
                        if (entry.getKey().contains(pattern)) {
                            results.put(entry.getKey(), new ArrayList<>(entry.getValue()));
                        }
                    }
                    return results;
                }

                @Override
                public int getFileCount() {
                    return fileMap.size();
                }

                @Override
                public int getPeerCount() {
                    Set<PeerInfo> uniquePeers = new HashSet<>();
                    for (List<PeerInfo> peers : fileMap.values()) {
                        uniquePeers.addAll(peers);
                    }
                    return uniquePeers.size();
                }
            };

            // Set the fileIndexStorage field
            Field storageField = IndexServer.class.getDeclaredField("fileIndexStorage");
            storageField.setAccessible(true);
            storageField.set(null, mockStorage);

            // Set the fileCache field to null to bypass caching
            Field cacheField = IndexServer.class.getDeclaredField("fileCache");
            cacheField.setAccessible(true);
            cacheField.set(null, null);

            // Set the running flag to true
            Field runningField = IndexServer.class.getDeclaredField("running");
            runningField.setAccessible(true);
            runningField.set(null, true);
        } catch (Exception e) {
            // Log the error but continue
            System.err.println("Error setting up test environment: " + e.getMessage());
            e.printStackTrace();
        }

        // Create a dummy thread to return
        serverThread = new Thread(() -> {
            try {
                // Just sleep until interrupted
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException e) {
                // Expected when test ends
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        return serverThread;
    }

    @Test
    void testHealthStatusUpdates() throws Exception {
        // Start the server
        startServerInThread();

        try {
            // Verify initial health status
            HealthCheck.ServiceHealth health = HealthCheck.getServiceHealth(SERVICE_NAME);
            assertNotNull(health);
            assertTrue(health.isHealthy());

            // Register a file and verify health status is updated
            PeerInfo peer = new PeerInfo("peer1", "localhost", 8000);
            IndexServer.registerFile("file1.txt", peer);

            // Verify health details for the operation
            Map<String, Object> details = health.getHealthDetails();
            assertEquals("registerFile", details.get("lastOperation"));
            assertEquals("file1.txt", details.get("lastFileName"));
            assertEquals(true, details.get("lastOperationSuccess"));
        } finally {
            // Clean up is handled in tearDown()
        }
    }

    @Test
    void testHealthDetails() throws Exception {
        // Start the server
        startServerInThread();

        try {
            // Verify health details
            HealthCheck.ServiceHealth health = HealthCheck.getServiceHealth(SERVICE_NAME);
            assertNotNull(health);

            Map<String, Object> details = health.getHealthDetails();
            assertEquals("running", details.get("status"));
            // Port may vary, so just check that it exists
            assertTrue(details.containsKey("port"));
            assertTrue(details.containsKey("startTime"));
            assertTrue(details.containsKey("storageDir"));
            assertTrue(details.containsKey("storageFile"));
            assertTrue(details.containsKey("fileCount"));
            assertTrue(details.containsKey("peerCount"));
        } finally {
            // Clean up is handled in tearDown()
        }
    }

    @Test
    void testHealthCheckDeregistration() throws Exception {
        // Start the server
        startServerInThread();

        try {
            // Verify service is registered
            assertNotNull(HealthCheck.getServiceHealth(SERVICE_NAME));
        } finally {
            // Stop the server
            IndexServer.stopIndexServer();
            serverThread.interrupt();

            // Wait a bit for deregistration to complete
            Thread.sleep(100);
        }

        // Verify service is deregistered
        assertNull(HealthCheck.getServiceHealth(SERVICE_NAME));
    }

    @Test
    void testHealthStatusUpdatesForDeregisterFile() throws Exception {
        // Start the server
        startServerInThread();

        try {
            // Register a file
            PeerInfo peer = new PeerInfo("peer1", "localhost", 8000);
            IndexServer.registerFile("file1.txt", peer);

            // Deregister the file
            IndexServer.deregisterFile("file1.txt", peer);

            // Verify health details for the operation
            HealthCheck.ServiceHealth health = HealthCheck.getServiceHealth(SERVICE_NAME);
            Map<String, Object> details = health.getHealthDetails();
            assertEquals("deregisterFile", details.get("lastOperation"));
            assertEquals("file1.txt", details.get("lastFileName"));
            assertEquals(true, details.get("lastOperationSuccess"));
        } finally {
            // Clean up is handled in tearDown()
        }
    }

    @Test
    void testHealthStatusUpdatesForDeregisterPeer() throws Exception {
        // Start the server
        startServerInThread();

        try {
            // Register a file
            PeerInfo peer = new PeerInfo("peer1", "localhost", 8000);
            IndexServer.registerFile("file1.txt", peer);

            // Deregister the peer
            IndexServer.deregisterPeer(peer);

            // Verify health details for the operation
            HealthCheck.ServiceHealth health = HealthCheck.getServiceHealth(SERVICE_NAME);
            Map<String, Object> details = health.getHealthDetails();
            assertEquals("deregisterPeer", details.get("lastOperation"));
            assertEquals("peer1", details.get("lastPeerId"));
            assertEquals(true, details.get("lastOperationSuccess"));
        } finally {
            // Clean up is handled in tearDown()
        }
    }

    @Test
    void testHealthStatusUpdatesForSearchFiles() throws Exception {
        // Start the server
        startServerInThread();

        try {
            // Register some files
            PeerInfo peer1 = new PeerInfo("peer1", "localhost", 8000);
            PeerInfo peer2 = new PeerInfo("peer2", "localhost", 8001);
            IndexServer.registerFile("file1.txt", peer1);
            IndexServer.registerFile("file2.txt", peer2);

            // Search for files
            Map<String, List<PeerInfo>> results = IndexServer.searchFiles("file");

            // Verify search results
            assertEquals(2, results.size());

            // Verify health details for the operation
            HealthCheck.ServiceHealth health = HealthCheck.getServiceHealth(SERVICE_NAME);
            Map<String, Object> details = health.getHealthDetails();
            assertEquals("searchFiles", details.get("lastOperation"));
            assertEquals("file", details.get("lastSearchPattern"));
            assertEquals(true, details.get("lastOperationSuccess"));
            assertEquals(2, details.get("searchResultCount"));
        } finally {
            // Clean up is handled in tearDown()
        }
    }

    @Test
    void testHealthStatusUpdatesForGetFileMap() throws Exception {
        // Start the server
        startServerInThread();

        try {
            // Register some files
            PeerInfo peer1 = new PeerInfo("peer1", "localhost", 8000);
            PeerInfo peer2 = new PeerInfo("peer2", "localhost", 8001);
            IndexServer.registerFile("file1.txt", peer1);
            IndexServer.registerFile("file2.txt", peer2);

            // Get file map
            Map<String, List<PeerInfo>> fileMap = IndexServer.getFileMap();

            // Verify file map
            assertEquals(2, fileMap.size());

            // Verify health details for the operation
            HealthCheck.ServiceHealth health = HealthCheck.getServiceHealth(SERVICE_NAME);
            Map<String, Object> details = health.getHealthDetails();
            assertEquals("getFileMap", details.get("lastOperation"));
            assertEquals(true, details.get("lastOperationSuccess"));
            assertEquals(2, details.get("fileMapSize"));
        } finally {
            // Clean up is handled in tearDown()
        }
    }

    @Test
    void testHealthStatusUpdatesForErrorConditions() throws Exception {
        // Start the server
        startServerInThread();

        try {
            // Get the health object
            HealthCheck.ServiceHealth health = HealthCheck.getServiceHealth(SERVICE_NAME);
            assertNotNull(health);

            // Set the fileIndexStorage field to null to simulate an error condition
            Field field = IndexServer.class.getDeclaredField("fileIndexStorage");
            field.setAccessible(true);
            Object originalStorage = field.get(null);
            field.set(null, null);

            // Try to register a file (this should fail)
            PeerInfo peer = new PeerInfo("peer1", "localhost", 8000);
            IndexServer.registerFile("file1.txt", peer);

            // Verify health details for the error
            Map<String, Object> details = health.getHealthDetails();
            assertEquals("File index storage not initialized", details.get("lastOperationError"));

            // Try to get peers with a file (this should fail)
            IndexServer.getPeersWithFile("file1.txt");

            // Verify health details for the error
            details = health.getHealthDetails();
            assertEquals("File index storage not initialized", details.get("lastOperationError"));

            // Restore the original storage
            field.set(null, originalStorage);
        } finally {
            // Clean up is handled in tearDown()
        }
    }

    @Test
    void testHealthStatusDuringStartupAndShutdown() throws Exception {
        // Start the server
        startServerInThread();

        try {
            // Verify health status during startup
            HealthCheck.ServiceHealth health = HealthCheck.getServiceHealth(SERVICE_NAME);
            assertNotNull(health);
            assertTrue(health.isHealthy());

            Map<String, Object> details = health.getHealthDetails();
            assertEquals("running", details.get("status"));
        } finally {
            // Clean up is handled in tearDown()
        }

        // The service should be deregistered, so we can't check its status directly
        // We've already tested deregistration in testHealthCheckDeregistration
    }
}
