package dev.mars.p2pjava.storage;

import dev.mars.p2pjava.common.PeerInfo;

import java.util.List;
import java.util.Map;

/**
 * Interface for file index storage implementations.
 * This interface defines the operations needed for storing and retrieving
 * file index information in a persistent manner.
 */
public interface FileIndexStorage {

    /**
     * Initializes the storage system.
     * This method should be called before any other methods.
     *
     * @return true if initialization was successful, false otherwise
     */
    boolean initialize();

    /**
     * Shuts down the storage system.
     * This method should be called when the storage is no longer needed.
     *
     * @return true if shutdown was successful, false otherwise
     */
    boolean shutdown();

    /**
     * Registers a file with a peer in the index.
     *
     * @param fileName The name of the file
     * @param peerInfo The peer that has the file
     * @return true if registration was successful, false otherwise
     */
    boolean registerFile(String fileName, PeerInfo peerInfo);

    /**
     * Deregisters a file from a peer in the index.
     *
     * @param fileName The name of the file
     * @param peerInfo The peer that no longer has the file
     * @return true if deregistration was successful, false otherwise
     */
    boolean deregisterFile(String fileName, PeerInfo peerInfo);

    /**
     * Deregisters all files associated with a peer.
     *
     * @param peerInfo The peer to deregister
     * @return true if deregistration was successful, false otherwise
     */
    boolean deregisterPeer(PeerInfo peerInfo);

    /**
     * Gets all peers that have a specific file.
     *
     * @param fileName The name of the file
     * @return A list of peers that have the file
     */
    List<PeerInfo> getPeersWithFile(String fileName);

    /**
     * Gets all files registered in the index.
     *
     * @return A map of file names to lists of peers that have those files
     */
    Map<String, List<PeerInfo>> getAllFiles();

    /**
     * Searches for files matching a pattern.
     *
     * @param pattern The search pattern (can be a partial file name)
     * @return A map of matching file names to lists of peers that have those files
     */
    Map<String, List<PeerInfo>> searchFiles(String pattern);

    /**
     * Gets the number of files in the index.
     *
     * @return The number of files
     */
    int getFileCount();

    /**
     * Gets the number of peers in the index.
     *
     * @return The number of peers
     */
    int getPeerCount();

    /**
     * Saves the current state of the index to persistent storage.
     *
     * @return true if the save was successful, false otherwise
     */
    boolean saveIndex();

    /**
     * Loads the index from persistent storage.
     *
     * @return true if the load was successful, false otherwise
     */
    boolean loadIndex();
}