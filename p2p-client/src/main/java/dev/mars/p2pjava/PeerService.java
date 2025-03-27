package dev.mars.p2pjava;

public class PeerService {
    public static void main(String[] args) {
        String peerId = System.getenv("PEER_ID");
        int peerPort = Integer.parseInt(System.getenv("PEER_PORT"));
        String peerHost = System.getenv("PEER_HOST");
        String sharedDir = System.getenv("SHARED_DIR");

        P2PTestHarness.startPeer(peerId, peerHost, peerPort,  sharedDir);

        // Keep the service running
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}