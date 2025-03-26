package dev.mars.p2pjava;

import java.io.*;
import java.net.*;
import java.util.*;

public class Peer {
    private String peerId;
    private int port;
    private List<String> sharedFiles;

    public Peer(String peerId, int port) {
        this.peerId = peerId;
        this.port = port;
        this.sharedFiles = new ArrayList<>();
    }

    public void registerWithTracker() {
        try (Socket socket = new Socket("localhost", 6000);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println("REGISTER " + peerId + " " + port);
            for (String file : sharedFiles) {
                out.println("REGISTER_FILE " + file);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addSharedFile(String filePath) {
        sharedFiles.add(filePath);
    }

    public void start() {
        registerWithTracker();
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Peer " + peerId + " is running on port " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(new PeerHandler(socket, sharedFiles)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Peer peer = new Peer("Peer1", 5001);
        peer.addSharedFile("file1.txt");
        peer.addSharedFile("file2.txt");
        peer.start();
    }
}