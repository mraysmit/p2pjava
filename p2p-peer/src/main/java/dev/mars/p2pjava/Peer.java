package dev.mars.p2pjava;

    import java.io.*;
    import java.net.*;
    import java.util.ArrayList;
    import java.util.List;

    public class Peer {
        private final String peerId;
        private final String peerHost;
        private final int port;
        private List<String> sharedFiles;

        public Peer(String peerId, String peerHost, int port) {
            this.peerId = peerId;
            this.peerHost = peerHost;
            this.port = port;
            this.sharedFiles = new ArrayList<>();
        }

        public void registerWithTracker() {
            try (Socket socket = new Socket("localhost", 6000);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                out.println("REGISTER " + peerId + " " + port);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void addSharedFile(String filePath) {
            sharedFiles.add(filePath);
        }

        public void discoverPeers() {
            try (Socket socket = new Socket("localhost", 6000);
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
    }