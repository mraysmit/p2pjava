package dev.mars.p2pjava.storage;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the FileBasedIndexStorage class.
 */
public class FileBasedIndexStorageTest {

    private FileBasedIndexStorage storage;
    
    @TempDir
    Path tempDir;
    
    private static final String TEST_FILE_NAME = "test-file.txt";
    private static final String TEST_PEER_ID = "test-peer-1";
    private static final String TEST_HOST = "localhost";
    private static final int TEST_PORT = 8080;
    
    @BeforeEach
    void setUp() {
        // Create a new storage instance for each test
        storage = new FileBasedIndexStorage(tempDir.toString());
        assertTrue(storage.initialize(), "Storage initialization should succeed");
    }
    
    @AfterEach
    void tearDown() {
        // Shutdown the storage after each test
        if (storage != null) {
            storage.shutdown();
        }
    }
    
    @Test
    void testRegisterFile() {
        // Create a peer info
        PeerInfo peerInfo = new PeerInfo(TEST_PEER_ID, TEST_HOST, TEST_PORT);
        
        // Register a file
        boolean registered = storage.registerFile(TEST_FILE_NAME, peerInfo);
        
        // Verify that the file was registered
        assertTrue(registered, "File registration should succeed");
        
        // Get peers with the file
        List<PeerInfo> peers = storage.getPeersWithFile(TEST_FILE_NAME);
        
        // Verify that the peer is in the list
        assertNotNull(peers, "Peers list should not be null");
        assertEquals(1, peers.size(), "Peers list should have one entry");
        assertEquals(TEST_PEER_ID, peers.get(0).getPeerId(), "Peer ID should match");
    }
    
    @Test
    void testRegisterFileDuplicate() {
        // Create a peer info
        PeerInfo peerInfo = new PeerInfo(TEST_PEER_ID, TEST_HOST, TEST_PORT);
        
        // Register a file
        boolean registered1 = storage.registerFile(TEST_FILE_NAME, peerInfo);
        
        // Register the same file again
        boolean registered2 = storage.registerFile(TEST_FILE_NAME, peerInfo);
        
        // Verify that both registrations succeeded
        assertTrue(registered1, "First file registration should succeed");
        assertTrue(registered2, "Second file registration should succeed (idempotent)");
        
        // Get peers with the file
        List<PeerInfo> peers = storage.getPeersWithFile(TEST_FILE_NAME);
        
        // Verify that the peer is in the list only once
        assertEquals(1, peers.size(), "Peers list should have one entry");
    }
    
    @Test
    void testDeregisterFile() {
        // Create a peer info
        PeerInfo peerInfo = new PeerInfo(TEST_PEER_ID, TEST_HOST, TEST_PORT);
        
        // Register a file
        storage.registerFile(TEST_FILE_NAME, peerInfo);
        
        // Deregister the file
        boolean deregistered = storage.deregisterFile(TEST_FILE_NAME, peerInfo);
        
        // Verify that the file was deregistered
        assertTrue(deregistered, "File deregistration should succeed");
        
        // Get peers with the file
        List<PeerInfo> peers = storage.getPeersWithFile(TEST_FILE_NAME);
        
        // Verify that the list is empty
        assertTrue(peers.isEmpty(), "Peers list should be empty after deregistration");
    }
    
    @Test
    void testDeregisterNonexistentFile() {
        // Create a peer info
        PeerInfo peerInfo = new PeerInfo(TEST_PEER_ID, TEST_HOST, TEST_PORT);
        
        // Deregister a nonexistent file
        boolean deregistered = storage.deregisterFile("nonexistent-file.txt", peerInfo);
        
        // Verify that the deregistration failed
        assertFalse(deregistered, "Deregistering nonexistent file should fail");
    }
    
    @Test
    void testDeregisterPeer() {
        // Create a peer info
        PeerInfo peerInfo = new PeerInfo(TEST_PEER_ID, TEST_HOST, TEST_PORT);
        
        // Register multiple files
        storage.registerFile(TEST_FILE_NAME, peerInfo);
        storage.registerFile("another-file.txt", peerInfo);
        
        // Deregister the peer
        boolean deregistered = storage.deregisterPeer(peerInfo);
        
        // Verify that the peer was deregistered
        assertTrue(deregistered, "Peer deregistration should succeed");
        
        // Get peers with the files
        List<PeerInfo> peers1 = storage.getPeersWithFile(TEST_FILE_NAME);
        List<PeerInfo> peers2 = storage.getPeersWithFile("another-file.txt");
        
        // Verify that the lists are empty
        assertTrue(peers1.isEmpty(), "First file's peers list should be empty after peer deregistration");
        assertTrue(peers2.isEmpty(), "Second file's peers list should be empty after peer deregistration");
    }
    
    @Test
    void testGetAllFiles() {
        // Create peer infos
        PeerInfo peer1 = new PeerInfo("peer-1", TEST_HOST, TEST_PORT);
        PeerInfo peer2 = new PeerInfo("peer-2", TEST_HOST, TEST_PORT + 1);
        
        // Register files
        storage.registerFile("file1.txt", peer1);
        storage.registerFile("file2.txt", peer2);
        storage.registerFile("file1.txt", peer2);
        
        // Get all files
        Map<String, List<PeerInfo>> allFiles = storage.getAllFiles();
        
        // Verify the map
        assertNotNull(allFiles, "All files map should not be null");
        assertEquals(2, allFiles.size(), "All files map should have two entries");
        assertTrue(allFiles.containsKey("file1.txt"), "All files map should contain file1.txt");
        assertTrue(allFiles.containsKey("file2.txt"), "All files map should contain file2.txt");
        assertEquals(2, allFiles.get("file1.txt").size(), "file1.txt should have two peers");
        assertEquals(1, allFiles.get("file2.txt").size(), "file2.txt should have one peer");
    }
    
    @Test
    void testSearchFiles() {
        // Create peer infos
        PeerInfo peer1 = new PeerInfo("peer-1", TEST_HOST, TEST_PORT);
        PeerInfo peer2 = new PeerInfo("peer-2", TEST_HOST, TEST_PORT + 1);
        
        // Register files
        storage.registerFile("document1.pdf", peer1);
        storage.registerFile("document2.pdf", peer2);
        storage.registerFile("image.jpg", peer1);
        
        // Search for PDF files
        Map<String, List<PeerInfo>> pdfFiles = storage.searchFiles("pdf");
        
        // Verify the map
        assertNotNull(pdfFiles, "PDF files map should not be null");
        assertEquals(2, pdfFiles.size(), "PDF files map should have two entries");
        assertTrue(pdfFiles.containsKey("document1.pdf"), "PDF files map should contain document1.pdf");
        assertTrue(pdfFiles.containsKey("document2.pdf"), "PDF files map should contain document2.pdf");
        
        // Search for document1
        Map<String, List<PeerInfo>> doc1Files = storage.searchFiles("document1");
        
        // Verify the map
        assertNotNull(doc1Files, "Document1 files map should not be null");
        assertEquals(1, doc1Files.size(), "Document1 files map should have one entry");
        assertTrue(doc1Files.containsKey("document1.pdf"), "Document1 files map should contain document1.pdf");
    }
    
    @Test
    void testGetFileCount() {
        // Create a peer info
        PeerInfo peerInfo = new PeerInfo(TEST_PEER_ID, TEST_HOST, TEST_PORT);
        
        // Register files
        storage.registerFile("file1.txt", peerInfo);
        storage.registerFile("file2.txt", peerInfo);
        
        // Get file count
        int fileCount = storage.getFileCount();
        
        // Verify the count
        assertEquals(2, fileCount, "File count should be 2");
    }
    
    @Test
    void testGetPeerCount() {
        // Create peer infos
        PeerInfo peer1 = new PeerInfo("peer-1", TEST_HOST, TEST_PORT);
        PeerInfo peer2 = new PeerInfo("peer-2", TEST_HOST, TEST_PORT + 1);
        
        // Register files
        storage.registerFile("file1.txt", peer1);
        storage.registerFile("file2.txt", peer2);
        
        // Get peer count
        int peerCount = storage.getPeerCount();
        
        // Verify the count
        assertEquals(2, peerCount, "Peer count should be 2");
    }
    
    @Test
    void testSaveAndLoadIndex() {
        // Create a peer info
        PeerInfo peerInfo = new PeerInfo(TEST_PEER_ID, TEST_HOST, TEST_PORT);
        
        // Register a file
        storage.registerFile(TEST_FILE_NAME, peerInfo);
        
        // Save the index
        boolean saved = storage.saveIndex();
        assertTrue(saved, "Index save should succeed");
        
        // Create a new storage instance
        FileBasedIndexStorage newStorage = new FileBasedIndexStorage(tempDir.toString());
        newStorage.initialize();
        
        // Load the index
        boolean loaded = newStorage.loadIndex();
        assertTrue(loaded, "Index load should succeed");
        
        // Verify that the file is still registered
        List<PeerInfo> peers = newStorage.getPeersWithFile(TEST_FILE_NAME);
        assertNotNull(peers, "Peers list should not be null after loading index");
        assertEquals(1, peers.size(), "Peers list should have one entry after loading index");
        assertEquals(TEST_PEER_ID, peers.get(0).getPeerId(), "Peer ID should match after loading index");
        
        // Clean up
        newStorage.shutdown();
    }
}