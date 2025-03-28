package dev.mars.p2pjava;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

class TrackerHandler implements Runnable {
    private static final Map<String, PeerInfo> peers = new ConcurrentHashMap<>();
    private final Socket clientSocket;

    public TrackerHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try (PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {

            String inputLine;

            while ((inputLine = in.readLine()) != null) {
                String[] parts = inputLine.split(" ");
                String command = parts[0];

                switch (command) {
                    case "REGISTER":
                        String peerId = parts[1];
                        int port = Integer.parseInt(parts[2]);
                        peers.put(peerId, new PeerInfo(peerId, clientSocket.getInetAddress().getHostAddress(), port));
                        out.println("REGISTERED " + peerId);
                        break;
                    case "DISCOVER":
                        out.println("PEERS " + peers.values());
                        break;
                    default:
                        out.println("UNKNOWN_COMMAND");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

