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


import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import dev.mars.p2pjava.common.util.ChecksumUtil;

/**
 * Represents metadata about a file in the P2P network.
 * This class stores information such as file name, size, checksum, creation date,
 * and other metadata that can be used for searching and verification.
 */
public class FileMetadata implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String fileName;
    private final long fileSize;
    private final String checksum;
    private final Instant creationTime;
    private final Instant lastModifiedTime;
    private final Map<String, String> additionalMetadata;
    
    /**
     * Creates a new FileMetadata instance with the specified parameters.
     *
     * @param fileName The name of the file
     * @param fileSize The size of the file in bytes
     * @param checksum The checksum of the file
     * @param creationTime The creation time of the file
     * @param lastModifiedTime The last modified time of the file
     * @param additionalMetadata Additional metadata about the file
     */
    public FileMetadata(String fileName, long fileSize, String checksum, 
                        Instant creationTime, Instant lastModifiedTime,
                        Map<String, String> additionalMetadata) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.checksum = checksum;
        this.creationTime = creationTime;
        this.lastModifiedTime = lastModifiedTime;
        this.additionalMetadata = additionalMetadata != null ? 
                new HashMap<>(additionalMetadata) : new HashMap<>();
    }
    
    /**
     * Creates a new FileMetadata instance from a file path.
     * This method reads the file attributes and calculates the checksum.
     *
     * @param filePath The path to the file
     * @return A new FileMetadata instance, or null if an error occurs
     */
    public static FileMetadata fromFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            String fileName = path.getFileName().toString();
            long fileSize = Files.size(path);
            String checksum = ChecksumUtil.calculateChecksum(path);
            
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            Instant creationTime = attrs.creationTime().toInstant();
            Instant lastModifiedTime = attrs.lastModifiedTime().toInstant();
            
            // Extract additional metadata (e.g., file extension, MIME type)
            Map<String, String> additionalMetadata = new HashMap<>();
            
            // Add file extension
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
                String extension = fileName.substring(dotIndex + 1).toLowerCase();
                additionalMetadata.put("extension", extension);
                
                // Add MIME type based on extension (simplified)
                String mimeType = getMimeTypeFromExtension(extension);
                if (mimeType != null) {
                    additionalMetadata.put("mimeType", mimeType);
                }
            }
            
            return new FileMetadata(fileName, fileSize, checksum, creationTime, lastModifiedTime, additionalMetadata);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Gets a simplified MIME type based on file extension.
     *
     * @param extension The file extension
     * @return The MIME type, or null if unknown
     */
    private static String getMimeTypeFromExtension(String extension) {
        switch (extension.toLowerCase()) {
            case "txt":
                return "text/plain";
            case "html":
            case "htm":
                return "text/html";
            case "pdf":
                return "application/pdf";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "mp3":
                return "audio/mpeg";
            case "mp4":
                return "video/mp4";
            case "json":
                return "application/json";
            case "xml":
                return "application/xml";
            case "zip":
                return "application/zip";
            default:
                return "application/octet-stream";
        }
    }
    
    /**
     * Gets the name of the file.
     *
     * @return The file name
     */
    public String getFileName() {
        return fileName;
    }
    
    /**
     * Gets the size of the file in bytes.
     *
     * @return The file size
     */
    public long getFileSize() {
        return fileSize;
    }
    
    /**
     * Gets the checksum of the file.
     *
     * @return The checksum
     */
    public String getChecksum() {
        return checksum;
    }
    
    /**
     * Gets the creation time of the file.
     *
     * @return The creation time
     */
    public Instant getCreationTime() {
        return creationTime;
    }
    
    /**
     * Gets the last modified time of the file.
     *
     * @return The last modified time
     */
    public Instant getLastModifiedTime() {
        return lastModifiedTime;
    }
    
    /**
     * Gets additional metadata about the file.
     *
     * @return A map of additional metadata
     */
    public Map<String, String> getAdditionalMetadata() {
        return new HashMap<>(additionalMetadata);
    }
    
    /**
     * Gets a specific metadata value.
     *
     * @param key The metadata key
     * @return The metadata value, or null if not found
     */
    public String getMetadata(String key) {
        return additionalMetadata.get(key);
    }
    
    /**
     * Adds or updates a metadata value.
     *
     * @param key The metadata key
     * @param value The metadata value
     */
    public void addMetadata(String key, String value) {
        additionalMetadata.put(key, value);
    }
    
    /**
     * Checks if the file matches a search query.
     * The query can match the file name, extension, or other metadata.
     *
     * @param query The search query
     * @return true if the file matches the query, false otherwise
     */
    public boolean matchesQuery(String query) {
        if (query == null || query.isEmpty()) {
            return true;
        }
        
        String lowerQuery = query.toLowerCase();
        
        // Check file name
        if (fileName.toLowerCase().contains(lowerQuery)) {
            return true;
        }
        
        // Check extension
        String extension = getMetadata("extension");
        if (extension != null && extension.toLowerCase().contains(lowerQuery)) {
            return true;
        }
        
        // Check MIME type
        String mimeType = getMetadata("mimeType");
        if (mimeType != null && mimeType.toLowerCase().contains(lowerQuery)) {
            return true;
        }
        
        // Check other metadata
        for (Map.Entry<String, String> entry : additionalMetadata.entrySet()) {
            if (entry.getValue().toLowerCase().contains(lowerQuery)) {
                return true;
            }
        }
        
        return false;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileMetadata that = (FileMetadata) o;
        return fileSize == that.fileSize &&
                Objects.equals(fileName, that.fileName) &&
                Objects.equals(checksum, that.checksum);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(fileName, fileSize, checksum);
    }
    
    @Override
    public String toString() {
        return "FileMetadata{" +
                "fileName='" + fileName + '\'' +
                ", fileSize=" + fileSize +
                ", checksum='" + checksum + '\'' +
                ", creationTime=" + creationTime +
                ", lastModifiedTime=" + lastModifiedTime +
                ", additionalMetadata=" + additionalMetadata +
                '}';
    }
}