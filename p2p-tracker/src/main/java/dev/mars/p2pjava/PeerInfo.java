package dev.mars.p2pjava;

class PeerInfo {
    private final String peerId;
    private final String address;
    private final int port;

    public PeerInfo(String peerId, String address, int port) {
        this.peerId = peerId;
        this.address = address;
        this.port = port;
    }

    public String getPeerId() {
        return peerId;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return peerId + " " + address + " " + port;
    }
}