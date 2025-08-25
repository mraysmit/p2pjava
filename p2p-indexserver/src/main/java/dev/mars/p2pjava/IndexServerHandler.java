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
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.logging.*;

public class IndexServerHandler implements Runnable {
    private static final Logger logger = Logger.getLogger(IndexServerHandler.class.getName());
    private final Socket clientSocket;

    public IndexServerHandler(Socket socket) {
        this.clientSocket = socket;
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

                if ("REGISTER_FILE".equals(command)) {
                    if (parts.length >= 4) {
                        String fileName = parts[1];
                        String peerId = parts[2];
                        int port = Integer.parseInt(parts[3]);
                        String address = clientSocket.getInetAddress().getHostAddress();

                        PeerInfo peerInfo = new PeerInfo(peerId, address, port);
                        IndexServer.registerFile(fileName, peerInfo);
                        // Logging moved to IndexServer.registerFile method

                        out.println("FILE_REGISTERED " + fileName);
                    } else {
                        logger.warning("Invalid REGISTER_FILE command: " + inputLine);
                        out.println("ERROR Invalid REGISTER_FILE command");
                    }
                }
                else if ("DEREGISTER_FILE".equals(command)) {
                    if (parts.length >= 4) {
                        String fileName = parts[1];
                        String peerId = parts[2];
                        int port = Integer.parseInt(parts[3]);
                        String address = clientSocket.getInetAddress().getHostAddress();

                        PeerInfo peerInfo = new PeerInfo(peerId, address, port);
                        boolean success = IndexServer.deregisterFile(fileName, peerInfo);

                        if (success) {
                            out.println("FILE_DEREGISTERED " + fileName);
                        } else {
                            out.println("ERROR Failed to deregister file");
                        }
                    } else {
                        logger.warning("Invalid DEREGISTER_FILE command: " + inputLine);
                        out.println("ERROR Invalid DEREGISTER_FILE command");
                    }
                }
                else if ("DEREGISTER_PEER".equals(command)) {
                    if (parts.length >= 3) {
                        String peerId = parts[1];
                        int port = Integer.parseInt(parts[2]);
                        String address = clientSocket.getInetAddress().getHostAddress();

                        PeerInfo peerInfo = new PeerInfo(peerId, address, port);
                        boolean success = IndexServer.deregisterPeer(peerInfo);

                        if (success) {
                            out.println("PEER_DEREGISTERED " + peerId);
                        } else {
                            out.println("ERROR Failed to deregister peer");
                        }
                    } else {
                        logger.warning("Invalid DEREGISTER_PEER command: " + inputLine);
                        out.println("ERROR Invalid DEREGISTER_PEER command");
                    }
                }
                else if ("GET_PEERS_WITH_FILE".equals(command)) {
                    if (parts.length >= 2) {
                        String fileName = parts[1];
                        List<PeerInfo> peers = IndexServer.getPeersWithFile(fileName);

                        if (peers.isEmpty()) {
                            logger.info("No peers found with file: " + fileName);
                            out.println("NO_PEERS_FOUND");
                        } else {
                            logger.info("Found " + peers.size() + " peers with file: " + fileName);
                            out.println(peers.toString());
                        }
                    } else {
                        logger.warning("Invalid GET_PEERS_WITH_FILE command: " + inputLine);
                        out.println("ERROR Invalid GET_PEERS_WITH_FILE command");
                    }
                }
                else if ("SEARCH_FILES".equals(command)) {
                    if (parts.length >= 2) {
                        String pattern = parts[1];
                        Map<String, List<PeerInfo>> results = IndexServer.searchFiles(pattern);

                        if (results.isEmpty()) {
                            logger.info("No files found matching pattern: " + pattern);
                            out.println("NO_FILES_FOUND");
                        } else {
                            logger.info("Found " + results.size() + " files matching pattern: " + pattern);
                            out.println(results.toString());
                        }
                    } else {
                        logger.warning("Invalid SEARCH_FILES command: " + inputLine);
                        out.println("ERROR Invalid SEARCH_FILES command");
                    }
                }
                else {
                    logger.warning("Unknown command: " + command);
                    out.println("UNKNOWN_COMMAND");
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error handling client", e);
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
