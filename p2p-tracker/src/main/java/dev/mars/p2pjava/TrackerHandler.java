package dev.mars.p2pjava;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

import static dev.mars.p2pjava.Tracker.updatePeerLastSeen;

class TrackerHandler implements Runnable {
    private static final Logger logger = Logger.getLogger(TrackerHandler.class.getName());
    private static final Map<String, PeerInfo> peers = new ConcurrentHashMap<>();
    private final Socket clientSocket;

    /**
     * Removes a peer from the peers map.
     *
     * @param peerId The ID of the peer to remove
     * @return true if the peer was removed, false otherwise
     */
    public static boolean removePeer(String peerId) {
        if (peerId == null || peerId.isEmpty()) {
            logger.warning("Invalid peer ID for removal");
            return false;
        }

        PeerInfo removed = peers.remove(peerId);

        if (removed != null) {
            logger.info("Removed peer from peers map: " + peerId);
            return true;
        } else {
            logger.fine("Peer not found in peers map: " + peerId);
            return false;
        }
    }

    public TrackerHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        logger.info("Handling client connection: " + clientSocket);

        try (PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {

            String inputLine;

            while ((inputLine = in.readLine()) != null) {
                logger.info("Received: " + inputLine);
                String[] parts = inputLine.split(" ");
                String command = parts[0];

                // Handle heartbeat command
                if ("HEARTBEAT".equals(command) && parts.length > 1) {
                    String peerId = parts[1];
                    updatePeerLastSeen(peerId);
                    out.println("HEARTBEAT_ACK");
                    logger.fine("Sent heartbeat acknowledgment to peer: " + peerId);
                    return;
                }

                switch (command) {
                    case "REGISTER":
                        if (parts.length < 3) {
                            logger.warning("Invalid REGISTER command: " + inputLine);
                            out.println("ERROR Insufficient parameters for REGISTER");
                            break;
                        }
                        try {
                            String peerId = parts[1];
                            int port = Integer.parseInt(parts[2]);
                            PeerInfo peerInfo = new PeerInfo(peerId, clientSocket.getInetAddress().getHostAddress(), port);
                            peers.put(peerId, peerInfo);
                            logger.info("Registered peer: " + peerInfo);
                            out.println("REGISTERED " + peerId);
                        } catch (NumberFormatException e) {
                            logger.warning("Invalid port format in REGISTER command: " + inputLine);
                            out.println("ERROR Invalid port format");
                        }
                        break;
                    case "DEREGISTER":
                        if (parts.length > 1) {
                            String peerId = parts[1];
                            boolean success = Tracker.deregisterPeer(peerId);
                            if (success) {
                                logger.info("Deregistered peer: " + peerId);
                                out.println("DEREGISTERED " + peerId);
                            } else {
                                logger.warning("Failed to deregister peer: " + peerId);
                                out.println("ERROR Failed to deregister peer");
                            }
                        } else {
                            logger.warning("Invalid DEREGISTER command: " + inputLine);
                            out.println("ERROR Insufficient parameters for DEREGISTER");
                        }
                        break;
                    case "DISCOVER":
                        logger.info("Processing DISCOVER command");
                        out.println("PEERS " + peers.values());
                        break;
                    case "IS_PEER_ALIVE":
                        if (parts.length > 1) {
                            String peerId = parts[1];
                            boolean isAlive = Tracker.isPeerAlive(peerId);
                            logger.info("Checking if peer is alive: " + peerId + " - " + (isAlive ? "ALIVE" : "NOT_ALIVE"));
                            out.println(isAlive ? "ALIVE" : "NOT_ALIVE");
                        } else {
                            logger.warning("Invalid IS_PEER_ALIVE command: " + inputLine);
                            out.println("ERROR Insufficient parameters for IS_PEER_ALIVE");
                        }
                        break;
                    default:
                        logger.warning("Unknown command: " + command);
                        out.println("UNKNOWN_COMMAND");
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error handling client connection", e);
        } finally {
            try {
                logger.info("Closing socket: " + clientSocket);
                if (!clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error closing client socket", e);
            }
        }
    }
}
