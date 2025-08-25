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