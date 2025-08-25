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


import dev.mars.p2pjava.common.exception.ClientException;
import dev.mars.p2pjava.common.exception.NetworkException;
import dev.mars.p2pjava.common.exception.ServiceException;
import dev.mars.p2pjava.common.protocol.ErrorMessage;
import dev.mars.p2pjava.util.ChecksumUtil;
import dev.mars.p2pjava.util.RetryHelper;
import dev.mars.p2pjava.util.ServiceMonitor;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PeerHandler implements Runnable {
    private static final Logger logger = Logger.getLogger(PeerHandler.class.getName());
    private static final int SOCKET_TIMEOUT_MS = 30000; // 30 seconds
    private static final int MAX_RETRIES = 3;

    private final Socket clientSocket;
    private final Peer peer;
    private final ServiceMonitor.ServiceMetrics metrics;

    public PeerHandler(Socket clientSocket, Peer peer) {
        this.clientSocket = clientSocket;
        this.peer = peer;
        this.metrics = ServiceMonitor.registerService("PeerHandler-" + clientSocket.getRemoteSocketAddress());

        // Set socket timeout for better error handling
        try {
            clientSocket.setSoTimeout(SOCKET_TIMEOUT_MS);
        } catch (SocketException e) {
            logger.warning("Failed to set socket timeout: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        long startTime = System.currentTimeMillis();
        boolean isError = false;

        try {
            handleClientConnection();
        } catch (ClientException e) {
            logger.log(Level.WARNING, "Client error: {0}", e.getMessage());
            isError = true;
            sendErrorResponse(e);
        } catch (NetworkException e) {
            logger.log(Level.WARNING, "Network error: {0}", e.getMessage());
            isError = true;
            // Network errors might be transient, log but don't send response as connection might be broken
        } catch (ServiceException e) {
            logger.log(Level.SEVERE, "Service error: {0}", e.getMessage());
            isError = true;
            sendErrorResponse(e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error handling client connection", e);
            isError = true;
            sendErrorResponse(new ServiceException.InternalServerException(e.getMessage(), e));
        } finally {
            // Record metrics
            long responseTime = System.currentTimeMillis() - startTime;
            metrics.recordRequest(responseTime, isError);
            closeSocket();
        }
    }

    private void handleClientConnection() throws Exception {
        String clientAddress = clientSocket.getInetAddress().toString();
        logger.info("Handling connection from " + clientAddress);

        try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

            String protocolVersion;
            String command;

            try {
                protocolVersion = in.readUTF();
                command = in.readUTF();
            } catch (SocketTimeoutException e) {
                throw new NetworkException.TimeoutException("Timeout reading client request", e);
            } catch (IOException e) {
                throw new NetworkException("Failed to read client request", e);
            }

            logger.info("Received command: " + command + " (protocol: " + protocolVersion + ")");

            switch (command) {
                case "GET_FILE":
                    handleGetFile(in, out);
                    break;
                default:
                    logger.warning("Unknown command: " + command);
                    throw new ClientException.UnknownCommandException(command);
            }

        } catch (SocketTimeoutException e) {
            throw new NetworkException.TimeoutException("Socket timeout during client handling", e);
        } catch (IOException e) {
            throw new NetworkException("I/O error while handling client", e);
        }
    }

    private void handleGetFile(DataInputStream in, DataOutputStream out) throws Exception {
        String fileName;

        try {
            fileName = in.readUTF();
        } catch (IOException e) {
            throw new NetworkException("Failed to read file name from request", e);
        }

        logger.info("Received file request for: " + fileName);

        String filePath = peer.findSharedFilePath(fileName);

        if (filePath == null) {
            logger.info("File not found: " + fileName);
            throw new ClientException.FileNotFoundException(fileName);
        }

        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            logger.warning("File does not exist or is not a file: " + filePath);
            throw new ClientException.FileNotFoundException(fileName);
        }

        if (!file.canRead()) {
            logger.warning("Cannot read file: " + filePath);
            throw new ServiceException("File access denied: " + fileName,
                                     ErrorMessage.ErrorCodes.FILE_ACCESS_ERROR, true);
        }

        // Calculate checksum with retry logic for transient I/O issues
        String checksum;
        try {
            checksum = RetryHelper.executeWithRetry(() -> ChecksumUtil.calculateChecksum(filePath),
                                                   MAX_RETRIES, 1000, 5000,
                                                   RetryHelper.createSmartRetryPredicate());
            if (checksum == null) {
                throw new ServiceException.InternalServerException("Checksum calculation returned null");
            }
        } catch (Exception e) {
            logger.severe("Error calculating checksum after retries: " + e.getMessage());
            throw new ServiceException.InternalServerException("Failed to calculate file checksum", e);
        }

        logger.info("Calculated checksum for file " + fileName + ": " + checksum);

        // Send file with retry logic for network issues
        try {
            RetryHelper.executeWithRetry(() -> {
                sendFileToClient(out, file, fileName, checksum);
                return null;
            }, MAX_RETRIES, 500, 2000, RetryHelper.createSmartRetryPredicate());

            logger.info("File sent successfully: " + fileName + " (" + file.length() + " bytes)");
        } catch (Exception e) {
            logger.severe("Error sending file after retries: " + e.getMessage());
            throw new NetworkException("Failed to send file: " + fileName, e);
        }
    }

    private void sendFileToClient(DataOutputStream out, File file, String fileName, String checksum) throws IOException {
        try (FileInputStream fileIn = new FileInputStream(file)) {
            // Tell client we're sending the file
            out.writeUTF("SENDING_FILE");
            out.writeLong(file.length());
            out.writeUTF(checksum);
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
        }
    }

    private void sendErrorResponse(Exception e) {
        try (DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {
            if (e instanceof ClientException || e instanceof ServiceException) {
                out.writeUTF("ERROR");
                out.writeUTF(e.getMessage());
            } else {
                out.writeUTF("ERROR");
                out.writeUTF("Internal server error");
            }
            out.flush();
        } catch (IOException ioException) {
            logger.warning("Failed to send error response: " + ioException.getMessage());
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
