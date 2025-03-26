package dev.mars.p2pjava;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

public class TrackerTest {

    @BeforeEach
    void setUp() {
        // Clear the peers map before each test
        Tracker.getPeers().clear();
    }

    @Test
    void testRegisterPeer() {
        PeerInfo peer = new PeerInfo("peer1", "localhost", 8000);
        Tracker.registerPeer(peer);

        Map<String, PeerInfo> peers = Tracker.getPeers();
        assertEquals(1, peers.size());
        assertTrue(peers.containsKey("peer1"));
        assertEquals(peer, peers.get("peer1"));
    }

    @Test
    void testGetPeers() {
        PeerInfo peer1 = new PeerInfo("peer1", "localhost", 8000);
        PeerInfo peer2 = new PeerInfo("peer2", "localhost", 8001);

        Tracker.registerPeer(peer1);
        Tracker.registerPeer(peer2);

        Map<String, PeerInfo> peers = Tracker.getPeers();
        assertEquals(2, peers.size());
        assertEquals(peer1, peers.get("peer1"));
        assertEquals(peer2, peers.get("peer2"));
    }
}