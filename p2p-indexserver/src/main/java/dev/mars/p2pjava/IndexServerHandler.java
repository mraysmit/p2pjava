package dev.mars.p2pjava;

        import java.io.*;
        import java.net.Socket;
        import java.util.*;
        import java.util.concurrent.ConcurrentHashMap;

        public class IndexServerHandler implements Runnable {
            // Map filenames to a list of peers that have the file
            private static final Map<String, List<PeerInfo>> fileMap = new ConcurrentHashMap<>();
            private final Socket clientSocket;

            public IndexServerHandler(Socket socket) {
                this.clientSocket = socket;
            }

            @Override
            public void run() {
                try (
                    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
                ) {
                    String line = in.readLine();
                    if (line != null) {
                        processCommand(line, out);
                    }
                } catch (IOException e) {
                    System.err.println("Error in IndexServerHandler: " + e.getMessage());
                } finally {
                    try {
                        clientSocket.close();
                    } catch (IOException e) {
                        // Ignore closing errors
                    }
                }
            }

            private void processCommand(String command, PrintWriter out) {
                System.out.println("IndexServer received: " + command);

                if (command.startsWith("REGISTER_FILE")) {
                    // Format: REGISTER_FILE filename peerId port
                    String[] parts = command.split(" ", 4);
                    if (parts.length == 4) {
                        String fileName = parts[1];
                        String peerId = parts[2];
                        int port = Integer.parseInt(parts[3]);

                        registerFile(fileName, peerId, "localhost", port);
                        out.println("FILE_REGISTERED " + fileName);
                    } else {
                        out.println("ERROR Invalid REGISTER_FILE format");
                    }
                } else if (command.startsWith("GET_PEERS_WITH_FILE")) {
                    // Format: GET_PEERS_WITH_FILE filename
                    String[] parts = command.split(" ", 2);
                    if (parts.length == 2) {
                        String fileName = parts[1];
                        List<PeerInfo> peers = getPeersWithFile(fileName);
                        out.println(peers.toString());
                    } else {
                        out.println("ERROR Invalid GET_PEERS_WITH_FILE format");
                    }
                } else {
                    out.println("ERROR Unknown command");
                }
            }

            private synchronized void registerFile(String fileName, String peerId, String address, int port) {
                PeerInfo peerInfo = new PeerInfo(peerId, address, port);
                fileMap.computeIfAbsent(fileName, k -> new ArrayList<>()).add(peerInfo);
                System.out.println("Registered file " + fileName + " with peer " + peerInfo);
                System.out.println("Current file map: " + fileMap);
            }

            private synchronized List<PeerInfo> getPeersWithFile(String fileName) {
                return fileMap.getOrDefault(fileName, Collections.emptyList());
            }
        }