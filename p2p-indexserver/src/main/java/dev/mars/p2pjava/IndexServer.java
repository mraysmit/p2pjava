package dev.mars.p2pjava;

import java.io.*;
import java.net.*;
import java.util.*;

public class IndexServer {
    private static Map<String, List<PeerInfo>> fileIndex = new HashMap<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(6001)) {
            System.out.println("IndexServer is running on port 6001");
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(new IndexServerHandler(socket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static synchronized void registerFile(String fileName, PeerInfo peerInfo) {
        fileIndex.computeIfAbsent(fileName, k -> new ArrayList<>()).add(peerInfo);
    }

    public static synchronized List<PeerInfo> getPeersWithFile(String fileName) {
        return fileIndex.getOrDefault(fileName, Collections.emptyList());
    }
}

