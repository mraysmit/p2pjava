package dev.mars.p2pjava;

import java.io.*;
import java.net.*;
import java.util.*;

public class Tracker {
    private static Map<String, PeerInfo> peers = new HashMap<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(6000)) {
            System.out.println("Tracker is running on port 6000");
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(new TrackerHandler(socket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static synchronized void registerPeer(PeerInfo peerInfo) {
        peers.put(peerInfo.getPeerId(), peerInfo);
    }

    public static synchronized Map<String, PeerInfo> getPeers() {
        return peers;
    }
}