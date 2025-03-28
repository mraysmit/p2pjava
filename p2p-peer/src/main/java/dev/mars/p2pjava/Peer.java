package dev.mars.p2pjava;

                import java.io.*;
                import java.net.*;
                import java.util.ArrayList;
                import java.util.List;

                public class Peer {
                    private final String peerId;
                    private final String peerHost;
                    private final int peerPort;
                    private final String trackerHost;
                    private final int trackerPort;
                    private List<String> sharedFiles;

                    public Peer(String peerId, String peerHost, int peerPort, String trackerHost, int trackerPort) {
                        this.peerId = peerId;
                        this.peerHost = peerHost;
                        this.peerPort = peerPort;
                        this.trackerHost = trackerHost;
                        this.trackerPort = trackerPort;
                        this.sharedFiles = new ArrayList<>();
                    }

                    public void registerWithTracker() {
                        try (Socket socket = new Socket(trackerHost, trackerPort);
                             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                            out.println("REGISTER " + peerId + " " + peerPort);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    public void addSharedFile(String filePath) {
                        sharedFiles.add(filePath);
                    }

                    public void discoverPeers() {
                        try (Socket socket = new Socket(trackerHost, trackerPort);
                             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                            out.println("DISCOVER");
                            String response = in.readLine();
                            System.out.println("Discovered peers: " + response);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    public void start() {
                        registerWithTracker();
                        discoverPeers();
                        try (ServerSocket serverSocket = new ServerSocket(peerPort)) {
                            System.out.println("Peer " + peerId + " is running on port " + peerPort);
                            while (true) {
                                Socket socket = serverSocket.accept();
                                new Thread(new PeerHandler(socket, sharedFiles)).start();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }