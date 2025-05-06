package dev.mars.p2pjava.common;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents information about a peer in the P2P network.
 * This class is used across multiple modules and is serializable for persistence.
 */
public class PeerInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String peerId;
    private final String address;
    private final int port;

    /**
     * Creates a new PeerInfo instance.
     *
     * @param peerId The unique identifier of the peer
     * @param address The network address of the peer
     * @param port The port number the peer is listening on
     */
    public PeerInfo(String peerId, String address, int port) {
        this.peerId = peerId;
        this.address = address;
        this.port = port;
    }

    /**
     * Gets the peer's unique identifier.
     *
     * @return The peer ID
     */
    public String getPeerId() {
        return peerId;
    }

    /**
     * Gets the peer's network address.
     *
     * @return The address
     */
    public String getAddress() {
        return address;
    }

    /**
     * Gets the port number the peer is listening on.
     *
     * @return The port number
     */
    public int getPort() {
        return port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PeerInfo peerInfo = (PeerInfo) o;
        return port == peerInfo.port &&
                Objects.equals(peerId, peerInfo.peerId) &&
                Objects.equals(address, peerInfo.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(peerId, address, port);
    }

    @Override
    public String toString() {
        return "PeerInfo{" +
                "peerId='" + peerId + '\'' +
                ", address='" + address + '\'' +
                ", port=" + port +
                '}';
    }
}