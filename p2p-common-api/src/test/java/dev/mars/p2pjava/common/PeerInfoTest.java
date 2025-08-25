package dev.mars.p2pjava.common;

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


import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the PeerInfo class.
 */
public class PeerInfoTest {

    private static final String TEST_PEER_ID = "test-peer-1";
    private static final String TEST_ADDRESS = "localhost";
    private static final int TEST_PORT = 8080;

    @Test
    void testConstructorAndGetters() {
        // Create a PeerInfo instance
        PeerInfo peerInfo = new PeerInfo(TEST_PEER_ID, TEST_ADDRESS, TEST_PORT);
        
        // Verify that the getters return the expected values
        assertEquals(TEST_PEER_ID, peerInfo.getPeerId(), "PeerId should match the constructor argument");
        assertEquals(TEST_ADDRESS, peerInfo.getAddress(), "Address should match the constructor argument");
        assertEquals(TEST_PORT, peerInfo.getPort(), "Port should match the constructor argument");
    }

    @Test
    void testEqualsAndHashCode() {
        // Create two identical PeerInfo instances
        PeerInfo peerInfo1 = new PeerInfo(TEST_PEER_ID, TEST_ADDRESS, TEST_PORT);
        PeerInfo peerInfo2 = new PeerInfo(TEST_PEER_ID, TEST_ADDRESS, TEST_PORT);
        
        // Create a different PeerInfo instance
        PeerInfo differentPeerInfo = new PeerInfo("different-peer", TEST_ADDRESS, TEST_PORT);
        
        // Test equals method
        assertTrue(peerInfo1.equals(peerInfo2), "Equal PeerInfo instances should be equal");
        assertFalse(peerInfo1.equals(differentPeerInfo), "Different PeerInfo instances should not be equal");
        assertFalse(peerInfo1.equals(null), "PeerInfo should not be equal to null");
        assertFalse(peerInfo1.equals(new Object()), "PeerInfo should not be equal to an object of a different class");
        
        // Test hashCode method
        assertEquals(peerInfo1.hashCode(), peerInfo2.hashCode(), "Equal PeerInfo instances should have the same hash code");
    }

    @Test
    void testToString() {
        // Create a PeerInfo instance
        PeerInfo peerInfo = new PeerInfo(TEST_PEER_ID, TEST_ADDRESS, TEST_PORT);
        
        // Verify that toString contains the expected values
        String toString = peerInfo.toString();
        assertTrue(toString.contains(TEST_PEER_ID), "toString should contain the peer ID");
        assertTrue(toString.contains(TEST_ADDRESS), "toString should contain the address");
        assertTrue(toString.contains(String.valueOf(TEST_PORT)), "toString should contain the port");
    }

    @Test
    void testWithDifferentValues() {
        // Test with different values
        String peerId = "peer-123";
        String address = "192.168.1.1";
        int port = 9000;
        
        PeerInfo peerInfo = new PeerInfo(peerId, address, port);
        
        assertEquals(peerId, peerInfo.getPeerId());
        assertEquals(address, peerInfo.getAddress());
        assertEquals(port, peerInfo.getPort());
    }

    @Test
    void testEqualsWithDifferentFields() {
        // Create a base PeerInfo
        PeerInfo base = new PeerInfo(TEST_PEER_ID, TEST_ADDRESS, TEST_PORT);
        
        // Test with different peer ID
        PeerInfo differentPeerId = new PeerInfo("different-id", TEST_ADDRESS, TEST_PORT);
        assertFalse(base.equals(differentPeerId), "PeerInfo with different peer ID should not be equal");
        
        // Test with different address
        PeerInfo differentAddress = new PeerInfo(TEST_PEER_ID, "different-address", TEST_PORT);
        assertFalse(base.equals(differentAddress), "PeerInfo with different address should not be equal");
        
        // Test with different port
        PeerInfo differentPort = new PeerInfo(TEST_PEER_ID, TEST_ADDRESS, TEST_PORT + 1);
        assertFalse(base.equals(differentPort), "PeerInfo with different port should not be equal");
    }
}