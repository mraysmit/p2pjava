package dev.mars.p2pjava;

import java.io.*;
import java.net.*;
import java.util.List;

class IndexServerHandler implements Runnable {
    private Socket socket;

    public IndexServerHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String request = in.readLine();
            if (request.startsWith("REGISTER_FILE")) {
                String[] parts = request.split(" ");
                String fileName = parts[1];
                String peerId = parts[2];
                int port = Integer.parseInt(parts[3]);
                String address = socket.getInetAddress().getHostAddress();

                PeerInfo peerInfo = new PeerInfo(peerId, address, port);
                IndexServer.registerFile(fileName, peerInfo);
                out.println("File registered: " + fileName + " by " + peerInfo);
            } else if (request.startsWith("GET_PEERS_WITH_FILE")) {
                String[] parts = request.split(" ");
                String fileName = parts[1];
                List<PeerInfo> peers = IndexServer.getPeersWithFile(fileName);
                out.println(peers);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}