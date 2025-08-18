package dev.mars.p2pjava;

import dev.mars.p2pjava.common.PeerInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for TrackerService to verify the refactoring from static state management
 * to instance-based dependency injection works correctly.
 */
class TrackerServiceTest {

    private TrackerService trackerService;

    @BeforeEach
    void setUp() {
        // Create a new TrackerService instance for each test
        trackerService = new TrackerService();
    }

    @AfterEach
    void tearDown() {
        // Clean up after each test
        if (trackerService != null && trackerService.isRunning()) {
            trackerService.stop();
        }
    }

    @Test
    void testTrackerServiceCreation() {
        assertNotNull(trackerService);
        assertNotNull(trackerService.getTrackerId());
        assertTrue(trackerService.getTrackerId().startsWith("tracker-"));
        assertEquals(6000, trackerService.getTrackerPort()); // Default port
        assertFalse(trackerService.isRunning());
    }

    @Test
    void testPeerRegistration() {
        // Test peer registration
        boolean registered = trackerService.registerPeer("peer1", "localhost", 8080);
        assertTrue(registered);

        // Verify peer is registered
        PeerInfo peer = trackerService.getPeer("peer1");
        assertNotNull(peer);
        assertEquals("peer1", peer.getPeerId());
        assertEquals("localhost", peer.getAddress());
        assertEquals(8080, peer.getPort());

        // Test getting all peers
        Collection<PeerInfo> allPeers = trackerService.getAllPeers();
        assertEquals(1, allPeers.size());
        assertTrue(allPeers.contains(peer));
    }

    @Test
    void testPeerDeregistration() {
        // Register a peer first
        trackerService.registerPeer("peer1", "localhost", 8080);
        assertNotNull(trackerService.getPeer("peer1"));

        // Deregister the peer
        boolean deregistered = trackerService.deregisterPeer("peer1");
        assertTrue(deregistered);

        // Verify peer is no longer registered
        assertNull(trackerService.getPeer("peer1"));
        assertEquals(0, trackerService.getAllPeers().size());

        // Test deregistering non-existent peer
        boolean notDeregistered = trackerService.deregisterPeer("nonexistent");
        assertFalse(notDeregistered);
    }

    @Test
    void testPeerAliveStatus() {
        // Register a peer
        trackerService.registerPeer("peer1", "localhost", 8080);

        // Peer should be alive immediately after registration
        assertTrue(trackerService.isPeerAlive("peer1"));

        // Update last seen time
        trackerService.updatePeerLastSeen("peer1");
        assertTrue(trackerService.isPeerAlive("peer1"));

        // Test non-existent peer
        assertFalse(trackerService.isPeerAlive("nonexistent"));
    }

    @Test
    void testActivePeers() {
        // Initially no active peers
        List<String> activePeers = trackerService.getActivePeers();
        assertEquals(0, activePeers.size());

        // Register some peers
        trackerService.registerPeer("peer1", "localhost", 8080);
        trackerService.registerPeer("peer2", "localhost", 8081);

        // Both should be active
        activePeers = trackerService.getActivePeers();
        assertEquals(2, activePeers.size());
        assertTrue(activePeers.contains("peer1"));
        assertTrue(activePeers.contains("peer2"));
    }

    @Test
    void testInvalidPeerRegistration() {
        // Test with null peer ID
        boolean registered = trackerService.registerPeer(null, "localhost", 8080);
        assertFalse(registered);

        // Test with empty peer ID
        registered = trackerService.registerPeer("", "localhost", 8080);
        assertFalse(registered);
    }

    @Test
    void testInvalidPeerDeregistration() {
        // Test with null peer ID
        boolean deregistered = trackerService.deregisterPeer(null);
        assertFalse(deregistered);

        // Test with empty peer ID
        deregistered = trackerService.deregisterPeer("");
        assertFalse(deregistered);
    }

    @Test
    void testMultipleTrackerInstances() {
        // Create multiple tracker instances to verify they don't interfere with each other
        TrackerService tracker1 = new TrackerService();
        TrackerService tracker2 = new TrackerService();

        try {
            // Register peers in different trackers
            tracker1.registerPeer("peer1", "localhost", 8080);
            tracker2.registerPeer("peer2", "localhost", 8081);

            // Verify isolation
            assertNotNull(tracker1.getPeer("peer1"));
            assertNull(tracker1.getPeer("peer2"));

            assertNotNull(tracker2.getPeer("peer2"));
            assertNull(tracker2.getPeer("peer1"));

            // Verify different tracker IDs
            assertNotEquals(tracker1.getTrackerId(), tracker2.getTrackerId());

        } finally {
            // Clean up
            if (tracker1.isRunning()) tracker1.stop();
            if (tracker2.isRunning()) tracker2.stop();
        }
    }

    @Test
    void testDiscoverOtherTrackers() {
        // This should return empty list for now since service discovery is not implemented
        List<dev.mars.p2pjava.discovery.ServiceInstance> otherTrackers = trackerService.discoverOtherTrackers();
        assertNotNull(otherTrackers);
        assertEquals(0, otherTrackers.size());
    }
}
