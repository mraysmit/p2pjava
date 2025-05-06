package dev.mars.p2pjava.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the ChecksumUtil class.
 */
public class ChecksumUtilTest {

    @TempDir
    Path tempDir;

    private static final String TEST_DATA = "This is test data for checksum calculation";
    private static final String DEFAULT_ALGORITHM = "SHA-256";

    @Test
    void testCalculateChecksumFromBytes() {
        // Calculate checksum from bytes
        byte[] data = TEST_DATA.getBytes(StandardCharsets.UTF_8);
        String checksum = ChecksumUtil.calculateChecksum(data);

        // Verify the checksum is not null or empty
        assertNotNull(checksum, "Checksum should not be null");
        assertFalse(checksum.isEmpty(), "Checksum should not be empty");

        // Verify the checksum is consistent
        String checksum2 = ChecksumUtil.calculateChecksum(data);
        assertEquals(checksum, checksum2, "Checksums for the same data should be equal");
    }

    @Test
    void testCalculateChecksumFromBytesWithAlgorithm() {
        // Calculate checksum from bytes with different algorithms
        byte[] data = TEST_DATA.getBytes(StandardCharsets.UTF_8);
        String sha256Checksum = ChecksumUtil.calculateChecksum(data, "SHA-256");
        String md5Checksum = ChecksumUtil.calculateChecksum(data, "MD5");

        // Verify the checksums are not null or empty
        assertNotNull(sha256Checksum, "SHA-256 checksum should not be null");
        assertNotNull(md5Checksum, "MD5 checksum should not be null");

        // Verify the checksums are different for different algorithms
        assertNotEquals(sha256Checksum, md5Checksum, "Checksums for different algorithms should be different");
    }

    @Test
    void testCalculateChecksumFromInputStream() throws IOException {
        // Calculate checksum from input stream
        ByteArrayInputStream inputStream = new ByteArrayInputStream(TEST_DATA.getBytes(StandardCharsets.UTF_8));
        String checksum = ChecksumUtil.calculateChecksum(inputStream);

        // Verify the checksum is not null or empty
        assertNotNull(checksum, "Checksum should not be null");
        assertFalse(checksum.isEmpty(), "Checksum should not be empty");

        // Verify the checksum is the same as calculated from bytes
        String bytesChecksum = ChecksumUtil.calculateChecksum(TEST_DATA.getBytes(StandardCharsets.UTF_8));
        assertEquals(bytesChecksum, checksum, "Checksums from bytes and input stream should be equal");
    }

    @Test
    void testCalculateChecksumFromInputStreamWithAlgorithm() throws IOException {
        // Calculate checksum from input stream with different algorithms
        ByteArrayInputStream inputStream1 = new ByteArrayInputStream(TEST_DATA.getBytes(StandardCharsets.UTF_8));
        ByteArrayInputStream inputStream2 = new ByteArrayInputStream(TEST_DATA.getBytes(StandardCharsets.UTF_8));

        String sha256Checksum = ChecksumUtil.calculateChecksum(inputStream1, "SHA-256");
        String md5Checksum = ChecksumUtil.calculateChecksum(inputStream2, "MD5");

        // Verify the checksums are not null or empty
        assertNotNull(sha256Checksum, "SHA-256 checksum should not be null");
        assertNotNull(md5Checksum, "MD5 checksum should not be null");

        // Verify the checksums are different for different algorithms
        assertNotEquals(sha256Checksum, md5Checksum, "Checksums for different algorithms should be different");
    }

    @Test
    void testCalculateChecksumFromFile() throws IOException {
        // Create a test file
        Path testFile = tempDir.resolve("test-file.txt");
        Files.write(testFile, TEST_DATA.getBytes(StandardCharsets.UTF_8));

        // Calculate checksum from file
        String checksum = ChecksumUtil.calculateChecksum(testFile.toString());

        // Verify the checksum is not null or empty
        assertNotNull(checksum, "Checksum should not be null");
        assertFalse(checksum.isEmpty(), "Checksum should not be empty");

        // Verify the checksum is the same as calculated from bytes
        String bytesChecksum = ChecksumUtil.calculateChecksum(TEST_DATA.getBytes(StandardCharsets.UTF_8));
        assertEquals(bytesChecksum, checksum, "Checksums from file and bytes should be equal");
    }

    @Test
    void testCalculateChecksumFromFileWithAlgorithm() throws IOException {
        // Create a test file
        Path testFile = tempDir.resolve("test-file.txt");
        Files.write(testFile, TEST_DATA.getBytes(StandardCharsets.UTF_8));

        // Calculate checksum from file with different algorithms
        String sha256Checksum = ChecksumUtil.calculateChecksum(testFile.toString(), "SHA-256");
        String md5Checksum = ChecksumUtil.calculateChecksum(testFile.toString(), "MD5");

        // Verify the checksums are not null or empty
        assertNotNull(sha256Checksum, "SHA-256 checksum should not be null");
        assertNotNull(md5Checksum, "MD5 checksum should not be null");

        // Verify the checksums are different for different algorithms
        assertNotEquals(sha256Checksum, md5Checksum, "Checksums for different algorithms should be different");
    }

    @Test
    void testCalculateChecksumFromPath() throws IOException {
        // Create a test file
        Path testFile = tempDir.resolve("test-file.txt");
        Files.write(testFile, TEST_DATA.getBytes(StandardCharsets.UTF_8));

        // Calculate checksum from path
        String checksum = ChecksumUtil.calculateChecksum(testFile);

        // Verify the checksum is not null or empty
        assertNotNull(checksum, "Checksum should not be null");
        assertFalse(checksum.isEmpty(), "Checksum should not be empty");

        // Verify the checksum is the same as calculated from bytes
        String bytesChecksum = ChecksumUtil.calculateChecksum(TEST_DATA.getBytes(StandardCharsets.UTF_8));
        assertEquals(bytesChecksum, checksum, "Checksums from path and bytes should be equal");
    }

    @Test
    void testVerifyChecksum() throws IOException {
        // Create a test file
        Path testFile = tempDir.resolve("test-file.txt");
        Files.write(testFile, TEST_DATA.getBytes(StandardCharsets.UTF_8));

        // Calculate checksum
        String checksum = ChecksumUtil.calculateChecksum(testFile.toString());

        // Verify the checksum
        boolean verified = ChecksumUtil.verifyChecksum(testFile.toString(), checksum);

        // Verify the verification result
        assertTrue(verified, "Checksum verification should succeed");
    }

    @Test
    void testVerifyChecksumWithWrongChecksum() throws IOException {
        // Create a test file
        Path testFile = tempDir.resolve("test-file.txt");
        Files.write(testFile, TEST_DATA.getBytes(StandardCharsets.UTF_8));

        // Verify with wrong checksum
        boolean verified = ChecksumUtil.verifyChecksum(testFile.toString(), "wrong-checksum");

        // Verify the verification result
        assertFalse(verified, "Checksum verification with wrong checksum should fail");
    }

    @Test
    void testVerifyChecksumWithBytes() {
        // Create test data
        byte[] data = TEST_DATA.getBytes(StandardCharsets.UTF_8);

        // Calculate checksum
        String checksum = ChecksumUtil.calculateChecksum(data);

        // Verify the checksum
        boolean verified = ChecksumUtil.verifyChecksum(data, checksum);

        // Verify the verification result
        assertTrue(verified, "Checksum verification should succeed");
    }

    @Test
    void testVerifyChecksumWithBytesAndWrongChecksum() {
        // Create test data
        byte[] data = TEST_DATA.getBytes(StandardCharsets.UTF_8);

        // Verify with wrong checksum
        boolean verified = ChecksumUtil.verifyChecksum(data, "wrong-checksum");

        // Verify the verification result
        assertFalse(verified, "Checksum verification with wrong checksum should fail");
    }

}
