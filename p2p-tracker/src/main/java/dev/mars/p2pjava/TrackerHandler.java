package dev.mars.p2pjava;

import java.io.*;
import java.net.*;

public class TrackerHandler implements Runnable {
    private Socket socket;

    public TrackerHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String request = in.readLine();
            if (request.startsWith("REGISTER")) {
                String[] parts = request.split(" ");
                String peerId = parts[1];
                String address = socket.getInetAddress().getHostAddress();
                int port = Integer.parseInt(parts[2]);
                PeerInfo peerInfo = new PeerInfo(peerId, address, port);
                Tracker.registerPeer(peerInfo);
                out.println("Peer registered: " + peerInfo);
            } else if (request.equals("GET_PEERS")) {
                out.println(Tracker.getPeers());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}