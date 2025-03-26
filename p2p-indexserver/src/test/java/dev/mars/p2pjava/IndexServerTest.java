package dev.mars.p2pjava;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;

public class IndexServerTest {

    @BeforeEach
    void setUp() throws Exception {
        // Reset the fileIndex using reflection
        Field field = IndexServer.class.getDeclaredField("fileIndex");
        field.setAccessible(true);
        field.set(null, new HashMap<>());
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
}