package dev.mars.p2pjava.storage;

import dev.mars.p2pjava.common.PeerInfo;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A file-based implementation of the FileIndexStorage interface.
 * This implementation stores the file index in a JSON file on disk,
 * providing persistence across server restarts.
 */
public class FileBasedIndexStorage implements FileIndexStorage {
    private static final Logger logger = Logger.getLogger(FileBasedIndexStorage.class.getName());
    
    // The in-memory representation of the file index
    private final Map<String, List<PeerInfo>> fileIndex = new ConcurrentHashMap<>();
    
    // Path to the storage file
    private final Path storagePath;
    
    // Lock for synchronizing file operations
    private final ReadWriteLock fileLock = new ReentrantReadWriteLock();
    
    // Scheduled executor for periodic saves
    private ScheduledExecutorService saveExecutor;
    
    // Flag to track if the storage is initialized
    private volatile boolean initialized = false;
    
    /**
     * Creates a new FileBasedIndexStorage with the specified storage file path.
     *
     * @param storagePath The path to the storage file
     */
    public FileBasedIndexStorage(String storagePath) {
        this.storagePath = Paths.get(storagePath);
    }
    
    /**
     * Creates a new FileBasedIndexStorage with the default storage file path.
     */
    public FileBasedIndexStorage() {
        this("index_storage.json");
    }
    
    @Override
    public boolean initialize() {
        if (initialized) {
            logger.info("Storage already initialized");
            return true;
        }
        
        try {
            // Create parent directories if they don't exist
            Files.createDirectories(storagePath.getParent());
            
            // Load existing index if available
            if (Files.exists(storagePath)) {
                if (!loadIndex()) {
                    logger.warning("Failed to load existing index");
                    return false;
                }
            } else {
                // Create an empty file
                Files.createFile(storagePath);
                saveIndex();
            }
            
            // Start periodic save task
            startPeriodicSave();
            
            initialized = true;
            logger.info("Storage initialized at " + storagePath);
            return true;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to initialize storage", e);
            return false;
        }
    }
    
    @Override
    public boolean shutdown() {
        if (!initialized) {
            logger.info("Storage not initialized");
            return true;
        }
        
        try {
            // Stop periodic save task
            if (saveExecutor != null) {
                saveExecutor.shutdown();
                try {
                    if (!saveExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        saveExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    saveExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            
            // Save index one last time
            boolean saved = saveIndex();
            
            initialized = false;
            logger.info("Storage shut down");
            return saved;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to shut down storage", e);
            return false;
        }
    }
    
    @Override
    public boolean registerFile(String fileName, PeerInfo peerInfo) {
        if (!initialized) {
            logger.warning("Storage not initialized");
            return false;
        }
        
        if (fileName == null || peerInfo == null) {
            logger.warning("Invalid parameters for registerFile");
            return false;
        }
        
        try {
            List<PeerInfo> peers = fileIndex.computeIfAbsent(fileName, k -> new CopyOnWriteArrayList<>());
            
            // Check if peer already registered for this file
            for (PeerInfo existingPeer : peers) {
                if (existingPeer.getPeerId().equals(peerInfo.getPeerId())) {
                    logger.fine("Peer " + peerInfo.getPeerId() + " already registered for file " + fileName);
                    return true;
                }
            }
            
            // Add the peer
            peers.add(peerInfo);
            logger.info("Registered file " + fileName + " with peer " + peerInfo.getPeerId());
            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to register file", e);
            return false;
        }
    }
    
    @Override
    public boolean deregisterFile(String fileName, PeerInfo peerInfo) {
        if (!initialized) {
            logger.warning("Storage not initialized");
            return false;
        }
        
        if (fileName == null || peerInfo == null) {
            logger.warning("Invalid parameters for deregisterFile");
            return false;
        }
        
        try {
            List<PeerInfo> peers = fileIndex.get(fileName);
            if (peers == null) {
                logger.fine("File " + fileName + " not found in index");
                return false;
            }
            
            // Remove the peer
            boolean removed = peers.removeIf(p -> p.getPeerId().equals(peerInfo.getPeerId()));
            
            // If no peers left for this file, remove the file entry
            if (peers.isEmpty()) {
                fileIndex.remove(fileName);
            }
            
            if (removed) {
                logger.info("Deregistered file " + fileName + " from peer " + peerInfo.getPeerId());
            } else {
                logger.fine("Peer " + peerInfo.getPeerId() + " not registered for file " + fileName);
            }
            
            return removed;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to deregister file", e);
            return false;
        }
    }
    
    @Override
    public boolean deregisterPeer(PeerInfo peerInfo) {
        if (!initialized) {
            logger.warning("Storage not initialized");
            return false;
        }
        
        if (peerInfo == null) {
            logger.warning("Invalid parameter for deregisterPeer");
            return false;
        }
        
        try {
            boolean anyRemoved = false;
            
            // Iterate through all files and remove the peer
            for (Iterator<Map.Entry<String, List<PeerInfo>>> it = fileIndex.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String, List<PeerInfo>> entry = it.next();
                List<PeerInfo> peers = entry.getValue();
                
                boolean removed = peers.removeIf(p -> p.getPeerId().equals(peerInfo.getPeerId()));
                if (removed) {
                    anyRemoved = true;
                }
                
                // If no peers left for this file, remove the file entry
                if (peers.isEmpty()) {
                    it.remove();
                }
            }
            
            if (anyRemoved) {
                logger.info("Deregistered peer " + peerInfo.getPeerId() + " from all files");
            } else {
                logger.fine("Peer " + peerInfo.getPeerId() + " not found in index");
            }
            
            return anyRemoved;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to deregister peer", e);
            return false;
        }
    }
    
    @Override
    public List<PeerInfo> getPeersWithFile(String fileName) {
        if (!initialized) {
            logger.warning("Storage not initialized");
            return Collections.emptyList();
        }
        
        if (fileName == null) {
            logger.warning("Invalid parameter for getPeersWithFile");
            return Collections.emptyList();
        }
        
        List<PeerInfo> peers = fileIndex.get(fileName);
        if (peers == null) {
            return Collections.emptyList();
        }
        
        // Return a copy to avoid external modification
        return new ArrayList<>(peers);
    }
    
    @Override
    public Map<String, List<PeerInfo>> getAllFiles() {
        if (!initialized) {
            logger.warning("Storage not initialized");
            return Collections.emptyMap();
        }
        
        // Create a deep copy to avoid external modification
        Map<String, List<PeerInfo>> copy = new HashMap<>();
        for (Map.Entry<String, List<PeerInfo>> entry : fileIndex.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        
        return copy;
    }
    
    @Override
    public Map<String, List<PeerInfo>> searchFiles(String pattern) {
        if (!initialized) {
            logger.warning("Storage not initialized");
            return Collections.emptyMap();
        }
        
        if (pattern == null) {
            logger.warning("Invalid parameter for searchFiles");
            return Collections.emptyMap();
        }
        
        // Convert the pattern to a regex pattern
        // Replace * with .* for wildcard matching
        String regex = pattern.replace("*", ".*").toLowerCase();
        Pattern compiledPattern = Pattern.compile(regex);
        
        // Filter files that match the pattern
        Map<String, List<PeerInfo>> results = fileIndex.entrySet().stream()
            .filter(entry -> compiledPattern.matcher(entry.getKey().toLowerCase()).matches())
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> new ArrayList<>(entry.getValue())
            ));
        
        logger.fine("Search for pattern '" + pattern + "' found " + results.size() + " matches");
        return results;
    }
    
    @Override
    public int getFileCount() {
        return fileIndex.size();
    }
    
    @Override
    public int getPeerCount() {
        // Count unique peers across all files
        Set<String> uniquePeerIds = new HashSet<>();
        
        for (List<PeerInfo> peers : fileIndex.values()) {
            for (PeerInfo peer : peers) {
                uniquePeerIds.add(peer.getPeerId());
            }
        }
        
        return uniquePeerIds.size();
    }
    
    @Override
    public boolean saveIndex() {
        if (!initialized) {
            logger.warning("Storage not initialized");
            return false;
        }
        
        fileLock.writeLock().lock();
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(storagePath.toFile()))) {
            // Serialize the file index
            oos.writeObject(fileIndex);
            logger.fine("Index saved to " + storagePath);
            return true;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save index", e);
            return false;
        } finally {
            fileLock.writeLock().unlock();
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public boolean loadIndex() {
        if (Files.exists(storagePath) && Files.isRegularFile(storagePath)) {
            fileLock.readLock().lock();
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(storagePath.toFile()))) {
                // Deserialize the file index
                Map<String, List<PeerInfo>> loadedIndex = (Map<String, List<PeerInfo>>) ois.readObject();
                
                // Clear current index and add all entries from loaded index
                fileIndex.clear();
                fileIndex.putAll(loadedIndex);
                
                logger.info("Index loaded from " + storagePath + " with " + fileIndex.size() + " files");
                return true;
            } catch (IOException | ClassNotFoundException e) {
                logger.log(Level.SEVERE, "Failed to load index", e);
                return false;
            } finally {
                fileLock.readLock().unlock();
            }
        } else {
            logger.warning("Storage file does not exist: " + storagePath);
            return false;
        }
    }
    
    /**
     * Starts a periodic task to save the index to disk.
     */
    private void startPeriodicSave() {
        saveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "IndexStorage-Save");
            t.setDaemon(true);
            return t;
        });
        
        // Save every 5 minutes
        saveExecutor.scheduleAtFixedRate(() -> {
            try {
                saveIndex();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to perform periodic save", e);
            }
        }, 5, 5, TimeUnit.MINUTES);
        
        logger.info("Periodic save task started");
    }
}