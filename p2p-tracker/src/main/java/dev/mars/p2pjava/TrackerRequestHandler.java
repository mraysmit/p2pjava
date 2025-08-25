package dev.mars.p2pjava;

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


import dev.mars.p2pjava.common.PeerInfo;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.logging.*;

/**
 * Handles individual client requests to the TrackerService.
 * This class replaces the static TrackerHandler with a proper instance-based approach.
 */
class TrackerRequestHandler implements Runnable {
    private static final Logger logger = Logger.getLogger(TrackerRequestHandler.class.getName());
    
    private final Socket clientSocket;
    private final TrackerService trackerService;

    /**
     * Creates a new TrackerRequestHandler.
     *
     * @param clientSocket The client socket
     * @param trackerService The tracker service instance
     */
    public TrackerRequestHandler(Socket clientSocket, TrackerService trackerService) {
        this.clientSocket = clientSocket;
        this.trackerService = trackerService;
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

                try {
                    switch (command) {
                        case "HEARTBEAT":
                            handleHeartbeat(parts, out);
                            return; // Heartbeat connections are short-lived
                            
                        case "REGISTER":
                            handleRegister(parts, out);
                            break;
                            
                        case "DEREGISTER":
                            handleDeregister(parts, out);
                            break;
                            
                        case "DISCOVER":
                            handleDiscover(out);
                            break;
                            
                        case "IS_PEER_ALIVE":
                            handleIsPeerAlive(parts, out);
                            break;
                            
                        default:
                            logger.warning("Unknown command: " + command);
                            out.println("ERROR UNKNOWN_COMMAND Unknown command: " + command);
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error processing command: " + command, e);
                    out.println("ERROR INTERNAL_ERROR Failed to process command: " + command);
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

    /**
     * Handles heartbeat command.
     */
    private void handleHeartbeat(String[] parts, PrintWriter out) {
        if (parts.length > 1) {
            String peerId = parts[1];
            trackerService.updatePeerLastSeen(peerId);
            out.println("HEARTBEAT_ACK");
            logger.fine("Sent heartbeat acknowledgment to peer: " + peerId);
        } else {
            logger.warning("Invalid HEARTBEAT command: missing peer ID");
            out.println("ERROR INVALID_PARAMETERS Missing peer ID for HEARTBEAT");
        }
    }

    /**
     * Handles peer registration command.
     */
    private void handleRegister(String[] parts, PrintWriter out) {
        if (parts.length >= 3) {
            try {
                String peerId = parts[1];
                int port = Integer.parseInt(parts[2]);
                String address = clientSocket.getInetAddress().getHostAddress();
                
                boolean success = trackerService.registerPeer(peerId, address, port);
                if (success) {
                    out.println("REGISTERED " + peerId);
                } else {
                    out.println("ERROR REGISTRATION_FAILED Failed to register peer: " + peerId);
                }
            } catch (NumberFormatException e) {
                logger.warning("Invalid port format in REGISTER command");
                out.println("ERROR INVALID_PARAMETERS Invalid port format");
            }
        } else {
            logger.warning("Invalid REGISTER command: insufficient parameters");
            out.println("ERROR INVALID_PARAMETERS Insufficient parameters for REGISTER");
        }
    }

    /**
     * Handles peer deregistration command.
     */
    private void handleDeregister(String[] parts, PrintWriter out) {
        if (parts.length > 1) {
            String peerId = parts[1];
            boolean success = trackerService.deregisterPeer(peerId);
            if (success) {
                out.println("DEREGISTERED " + peerId);
            } else {
                out.println("ERROR DEREGISTRATION_FAILED Peer not found: " + peerId);
            }
        } else {
            logger.warning("Invalid DEREGISTER command: missing peer ID");
            out.println("ERROR INVALID_PARAMETERS Missing peer ID for DEREGISTER");
        }
    }

    /**
     * Handles peer discovery command.
     */
    private void handleDiscover(PrintWriter out) {
        Collection<PeerInfo> peers = trackerService.getAllPeers();
        logger.info("Processing DISCOVER command, found " + peers.size() + " peers");
        out.println("PEERS " + peers);
    }

    /**
     * Handles peer alive check command.
     */
    private void handleIsPeerAlive(String[] parts, PrintWriter out) {
        if (parts.length > 1) {
            String peerId = parts[1];
            boolean isAlive = trackerService.isPeerAlive(peerId);
            logger.info("Checking if peer is alive: " + peerId + " - " + (isAlive ? "ALIVE" : "NOT_ALIVE"));
            out.println(isAlive ? "ALIVE" : "NOT_ALIVE");
        } else {
            logger.warning("Invalid IS_PEER_ALIVE command: missing peer ID");
            out.println("ERROR INVALID_PARAMETERS Missing peer ID for IS_PEER_ALIVE");
        }
    }
}
