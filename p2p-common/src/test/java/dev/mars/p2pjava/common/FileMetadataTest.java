package dev.mars.p2pjava.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the FileMetadata class.
 */
public class FileMetadataTest {

    private static final String TEST_FILE_NAME = "test-file.txt";
    private static final long TEST_FILE_SIZE = 1024;
    private static final String TEST_CHECKSUM = "abc123";
    private static final Instant TEST_CREATION_TIME = Instant.now().minusSeconds(3600);
    private static final Instant TEST_MODIFIED_TIME = Instant.now();

    @Test
    void testConstructorAndGetters() {
        // Create a map of additional metadata
        Map<String, String> additionalMetadata = new HashMap<>();
        additionalMetadata.put("author", "test-author");
        additionalMetadata.put("version", "1.0");

        // Create a FileMetadata instance
        FileMetadata metadata = new FileMetadata(
                TEST_FILE_NAME,
                TEST_FILE_SIZE,
                TEST_CHECKSUM,
                TEST_CREATION_TIME,
                TEST_MODIFIED_TIME,
                additionalMetadata
        );

        // Verify that the getters return the expected values
        assertEquals(TEST_FILE_NAME, metadata.getFileName(), "File name should match");
        assertEquals(TEST_FILE_SIZE, metadata.getFileSize(), "File size should match");
        assertEquals(TEST_CHECKSUM, metadata.getChecksum(), "Checksum should match");
        assertEquals(TEST_CREATION_TIME, metadata.getCreationTime(), "Creation time should match");
        assertEquals(TEST_MODIFIED_TIME, metadata.getLastModifiedTime(), "Last modified time should match");
        
        // Verify additional metadata
        Map<String, String> retrievedMetadata = metadata.getAdditionalMetadata();
        assertNotNull(retrievedMetadata, "Additional metadata should not be null");
        assertEquals(additionalMetadata.size(), retrievedMetadata.size(), "Additional metadata size should match");
        assertEquals("test-author", retrievedMetadata.get("author"), "Author metadata should match");
        assertEquals("1.0", retrievedMetadata.get("version"), "Version metadata should match");
    }

    @Test
    void testConstructorWithNullMetadata() {
        // Create a FileMetadata instance with null additional metadata
        FileMetadata metadata = new FileMetadata(
                TEST_FILE_NAME,
                TEST_FILE_SIZE,
                TEST_CHECKSUM,
                TEST_CREATION_TIME,
                TEST_MODIFIED_TIME,
                null
        );

        // Verify that the additional metadata is not null but empty
        Map<String, String> retrievedMetadata = metadata.getAdditionalMetadata();
        assertNotNull(retrievedMetadata, "Additional metadata should not be null even when constructed with null");
        assertTrue(retrievedMetadata.isEmpty(), "Additional metadata should be empty when constructed with null");
    }

    @Test
    void testGetMetadataAndAddMetadata() {
        // Create a FileMetadata instance with empty additional metadata
        FileMetadata metadata = new FileMetadata(
                TEST_FILE_NAME,
                TEST_FILE_SIZE,
                TEST_CHECKSUM,
                TEST_CREATION_TIME,
                TEST_MODIFIED_TIME,
                new HashMap<>()
        );

        // Add metadata
        metadata.addMetadata("key1", "value1");
        metadata.addMetadata("key2", "value2");

        // Verify that the metadata was added
        assertEquals("value1", metadata.getMetadata("key1"), "Metadata key1 should match");
        assertEquals("value2", metadata.getMetadata("key2"), "Metadata key2 should match");
        assertNull(metadata.getMetadata("nonexistent"), "Nonexistent metadata should return null");
    }

    @Test
    void testMatchesQuery() {
        // Create a FileMetadata instance with additional metadata
        Map<String, String> additionalMetadata = new HashMap<>();
        additionalMetadata.put("author", "test-author");
        additionalMetadata.put("version", "1.0");

        FileMetadata metadata = new FileMetadata(
                "document.pdf",
                TEST_FILE_SIZE,
                TEST_CHECKSUM,
                TEST_CREATION_TIME,
                TEST_MODIFIED_TIME,
                additionalMetadata
        );

        // Test matching queries
        assertTrue(metadata.matchesQuery("document"), "Should match partial file name");
        assertTrue(metadata.matchesQuery("pdf"), "Should match file extension");
        assertTrue(metadata.matchesQuery("test-author"), "Should match metadata value");
        assertTrue(metadata.matchesQuery("1.0"), "Should match metadata value");

        // Test non-matching queries
        assertFalse(metadata.matchesQuery("nonexistent"), "Should not match nonexistent term");
        assertFalse(metadata.matchesQuery("txt"), "Should not match wrong extension");
    }

    @Test
    void testEqualsAndHashCode() {
        // Create two identical FileMetadata instances
        Map<String, String> metadata1 = new HashMap<>();
        metadata1.put("key", "value");

        Map<String, String> metadata2 = new HashMap<>();
        metadata2.put("key", "value");

        FileMetadata file1 = new FileMetadata(
                TEST_FILE_NAME,
                TEST_FILE_SIZE,
                TEST_CHECKSUM,
                TEST_CREATION_TIME,
                TEST_MODIFIED_TIME,
                metadata1
        );

        FileMetadata file2 = new FileMetadata(
                TEST_FILE_NAME,
                TEST_FILE_SIZE,
                TEST_CHECKSUM,
                TEST_CREATION_TIME,
                TEST_MODIFIED_TIME,
                metadata2
        );

        // Create a different FileMetadata instance
        FileMetadata differentFile = new FileMetadata(
                "different-file.txt",
                TEST_FILE_SIZE,
                TEST_CHECKSUM,
                TEST_CREATION_TIME,
                TEST_MODIFIED_TIME,
                metadata1
        );

        // Test equals method
        assertTrue(file1.equals(file2), "Equal FileMetadata instances should be equal");
        assertFalse(file1.equals(differentFile), "Different FileMetadata instances should not be equal");
        assertFalse(file1.equals(null), "FileMetadata should not be equal to null");
        assertFalse(file1.equals(new Object()), "FileMetadata should not be equal to an object of a different class");

        // Test hashCode method
        assertEquals(file1.hashCode(), file2.hashCode(), "Equal FileMetadata instances should have the same hash code");
    }

    @Test
    void testToString() {
        // Create a FileMetadata instance
        FileMetadata metadata = new FileMetadata(
                TEST_FILE_NAME,
                TEST_FILE_SIZE,
                TEST_CHECKSUM,
                TEST_CREATION_TIME,
                TEST_MODIFIED_TIME,
                new HashMap<>()
        );

        // Verify that toString contains the expected values
        String toString = metadata.toString();
        assertTrue(toString.contains(TEST_FILE_NAME), "toString should contain the file name");
        assertTrue(toString.contains(String.valueOf(TEST_FILE_SIZE)), "toString should contain the file size");
        assertTrue(toString.contains(TEST_CHECKSUM), "toString should contain the checksum");
    }

    @Test
    void testFromFile(@TempDir Path tempDir) throws IOException {
        // Create a temporary file
        Path testFile = tempDir.resolve("test-file.txt");
        String content = "This is a test file content";
        Files.write(testFile, content.getBytes());

        // Create FileMetadata from the file
        FileMetadata metadata = FileMetadata.fromFile(testFile.toString());

        // Verify the metadata
        assertEquals("test-file.txt", metadata.getFileName(), "File name should match");
        assertEquals(content.getBytes().length, metadata.getFileSize(), "File size should match");
        assertNotNull(metadata.getChecksum(), "Checksum should not be null");
        assertNotNull(metadata.getCreationTime(), "Creation time should not be null");
        assertNotNull(metadata.getLastModifiedTime(), "Last modified time should not be null");
        
        // Verify additional metadata
        Map<String, String> additionalMetadata = metadata.getAdditionalMetadata();
        assertNotNull(additionalMetadata, "Additional metadata should not be null");
        assertTrue(additionalMetadata.containsKey("mimeType"), "Additional metadata should contain mimeType");
        assertEquals("text/plain", additionalMetadata.get("mimeType"), "MimeType should be text/plain");
    }
}