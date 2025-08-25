package dev.mars.p2pjava.common.util;

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


import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for calculating and verifying checksums.
 * This class provides methods for calculating checksums for files and byte arrays,
 * and for verifying that a file or byte array matches a given checksum.
 */
public class ChecksumUtil {
    private static final Logger logger = Logger.getLogger(ChecksumUtil.class.getName());
    private static final String DEFAULT_ALGORITHM = "SHA-256";
    private static final int BUFFER_SIZE = 8192;

    /**
     * Calculates a checksum for a file.
     *
     * @param filePath The path to the file
     * @return The checksum as a hexadecimal string, or null if an error occurs
     */
    public static String calculateChecksum(String filePath) {
        return calculateChecksum(filePath, DEFAULT_ALGORITHM);
    }

    /**
     * Calculates a checksum for a file using the specified algorithm.
     *
     * @param filePath The path to the file
     * @param algorithm The algorithm to use (e.g., "MD5", "SHA-1", "SHA-256")
     * @return The checksum as a hexadecimal string, or null if an error occurs
     */
    public static String calculateChecksum(String filePath, String algorithm) {
        try {
            Path path = Paths.get(filePath);
            return calculateChecksum(path, algorithm);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error calculating checksum for file: " + filePath, e);
            return null;
        }
    }

    /**
     * Calculates a checksum for a file.
     *
     * @param path The path to the file
     * @return The checksum as a hexadecimal string, or null if an error occurs
     */
    public static String calculateChecksum(Path path) {
        return calculateChecksum(path, DEFAULT_ALGORITHM);
    }

    /**
     * Calculates a checksum for a file using the specified algorithm.
     *
     * @param path The path to the file
     * @param algorithm The algorithm to use (e.g., "MD5", "SHA-1", "SHA-256")
     * @return The checksum as a hexadecimal string, or null if an error occurs
     */
    public static String calculateChecksum(Path path, String algorithm) {
        try (InputStream in = Files.newInputStream(path)) {
            return calculateChecksum(in, algorithm);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error calculating checksum for file: " + path, e);
            return null;
        }
    }

    /**
     * Calculates a checksum for an input stream.
     *
     * @param in The input stream
     * @return The checksum as a hexadecimal string, or null if an error occurs
     */
    public static String calculateChecksum(InputStream in) {
        return calculateChecksum(in, DEFAULT_ALGORITHM);
    }

    /**
     * Calculates a checksum for an input stream using the specified algorithm.
     *
     * @param in The input stream
     * @param algorithm The algorithm to use (e.g., "MD5", "SHA-1", "SHA-256")
     * @return The checksum as a hexadecimal string, or null if an error occurs
     */
    public static String calculateChecksum(InputStream in, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            
            while ((bytesRead = in.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            
            byte[] checksumBytes = digest.digest();
            return bytesToHex(checksumBytes);
        } catch (NoSuchAlgorithmException | IOException e) {
            logger.log(Level.WARNING, "Error calculating checksum for input stream", e);
            return null;
        }
    }

    /**
     * Calculates a checksum for a byte array.
     *
     * @param data The byte array
     * @return The checksum as a hexadecimal string, or null if an error occurs
     */
    public static String calculateChecksum(byte[] data) {
        return calculateChecksum(data, DEFAULT_ALGORITHM);
    }

    /**
     * Calculates a checksum for a byte array using the specified algorithm.
     *
     * @param data The byte array
     * @param algorithm The algorithm to use (e.g., "MD5", "SHA-1", "SHA-256")
     * @return The checksum as a hexadecimal string, or null if an error occurs
     */
    public static String calculateChecksum(byte[] data, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] checksumBytes = digest.digest(data);
            return bytesToHex(checksumBytes);
        } catch (NoSuchAlgorithmException e) {
            logger.log(Level.WARNING, "Error calculating checksum for byte array", e);
            return null;
        }
    }

    /**
     * Verifies that a file matches a given checksum.
     *
     * @param filePath The path to the file
     * @param expectedChecksum The expected checksum
     * @return true if the file matches the checksum, false otherwise
     */
    public static boolean verifyChecksum(String filePath, String expectedChecksum) {
        String actualChecksum = calculateChecksum(filePath);
        return actualChecksum != null && actualChecksum.equalsIgnoreCase(expectedChecksum);
    }

    /**
     * Verifies that a file matches a given checksum using the specified algorithm.
     *
     * @param filePath The path to the file
     * @param expectedChecksum The expected checksum
     * @param algorithm The algorithm to use (e.g., "MD5", "SHA-1", "SHA-256")
     * @return true if the file matches the checksum, false otherwise
     */
    public static boolean verifyChecksum(String filePath, String expectedChecksum, String algorithm) {
        String actualChecksum = calculateChecksum(filePath, algorithm);
        return actualChecksum != null && actualChecksum.equalsIgnoreCase(expectedChecksum);
    }

    /**
     * Verifies that a byte array matches a given checksum.
     *
     * @param data The byte array
     * @param expectedChecksum The expected checksum
     * @return true if the byte array matches the checksum, false otherwise
     */
    public static boolean verifyChecksum(byte[] data, String expectedChecksum) {
        String actualChecksum = calculateChecksum(data);
        return actualChecksum != null && actualChecksum.equalsIgnoreCase(expectedChecksum);
    }

    /**
     * Verifies that a byte array matches a given checksum using the specified algorithm.
     *
     * @param data The byte array
     * @param expectedChecksum The expected checksum
     * @param algorithm The algorithm to use (e.g., "MD5", "SHA-1", "SHA-256")
     * @return true if the byte array matches the checksum, false otherwise
     */
    public static boolean verifyChecksum(byte[] data, String expectedChecksum, String algorithm) {
        String actualChecksum = calculateChecksum(data, algorithm);
        return actualChecksum != null && actualChecksum.equalsIgnoreCase(expectedChecksum);
    }

    /**
     * Converts a byte array to a hexadecimal string.
     *
     * @param bytes The byte array
     * @return The hexadecimal string
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
