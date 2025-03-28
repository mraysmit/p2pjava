package dev.mars.p2pjava;

import java.io.*;
import java.net.*;
import java.util.*;

public class PeerHandler implements Runnable {
    private Socket socket;
    private List<String> sharedFiles;

    public PeerHandler(Socket socket, List<String> sharedFiles) {
        this.socket = socket;
        this.sharedFiles = sharedFiles;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String request = in.readLine();
            if (request.equals("LIST")) {
                out.println(sharedFiles);
            } else if (request.startsWith("GET")) {
                String fileName = request.split(" ")[1];
                if (sharedFiles.contains(fileName)) {
                    sendFile(fileName, socket);
                } else {
                    out.println("File not found");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendFile(String fileName, Socket socket) {
        try (BufferedInputStream fileIn = new BufferedInputStream(new FileInputStream(fileName));
             BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream())) {

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fileIn.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}