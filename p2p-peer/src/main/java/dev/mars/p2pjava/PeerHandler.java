package dev.mars.p2pjava;

import dev.mars.p2pjava.util.ChecksumUtil;

import java.io.*;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PeerHandler implements Runnable {
    private static final Logger logger = Logger.getLogger(PeerHandler.class.getName());

    private final Socket clientSocket;
    private final Peer peer;

    public PeerHandler(Socket clientSocket, Peer peer) {
        this.clientSocket = clientSocket;
        this.peer = peer;
    }

    @Override
    public void run() {
        try {
            handleClientConnection();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error handling client connection", e);
        } finally {
            closeSocket();
        }
    }

    private void handleClientConnection() {
        String clientAddress = clientSocket.getInetAddress().toString();
        logger.info("Handling connection from " + clientAddress);

        try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

            String protocolVersion = in.readUTF();
            String command = in.readUTF();

            logger.info("Received command: " + command + " (protocol: " + protocolVersion + ")");

            switch (command) {
                case "GET_FILE":
                    handleGetFile(in, out);
                    break;
                default:
                    logger.warning("Unknown command: " + command);
                    out.writeUTF("UNKNOWN_COMMAND");
            }

        } catch (IOException e) {
            logger.warning("I/O error while handling client: " + e.getMessage());
        }
    }

    private void handleGetFile(DataInputStream in, DataOutputStream out) throws IOException {
        String fileName = in.readUTF();
        logger.info("Received file request for: " + fileName);

        String filePath = peer.findSharedFilePath(fileName);

        if (filePath == null) {
            logger.info("File not found: " + fileName);
            out.writeUTF("FILE_NOT_FOUND");
            return;
        }

        File file = new File(filePath);
        if (!file.exists() || !file.isFile() || !file.canRead()) {
            logger.warning("Cannot access file: " + filePath);
            out.writeUTF("FILE_NOT_FOUND");
            return;
        }

        // Calculate checksum before sending file
        String checksum = ChecksumUtil.calculateChecksum(filePath);
        if (checksum == null) {
            logger.warning("Failed to calculate checksum for file: " + filePath);
            out.writeUTF("ERROR_CALCULATING_CHECKSUM");
            return;
        }

        logger.info("Calculated checksum for file " + fileName + ": " + checksum);

        // Send file
        try (FileInputStream fileIn = new FileInputStream(file)) {
            // Tell client we're sending the file
            out.writeUTF("SENDING_FILE");
            out.writeLong(file.length());
            out.writeUTF(checksum); // Send the checksum
            out.flush();

            // Transfer file content
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalSent = 0;

            while ((bytesRead = fileIn.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalSent += bytesRead;
            }
            out.flush();

            logger.info("File sent successfully: " + fileName + " (" + totalSent + " bytes)");
        } catch (IOException e) {
            logger.severe("Error sending file: " + e.getMessage());
            throw e;
        }
    }

    private void closeSocket() {
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            logger.warning("Error closing socket: " + e.getMessage());
        }
    }
}
