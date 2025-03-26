package dev.mars.p2pjava;

import java.io.*;
import java.net.*;
import java.util.Properties;

public class Client {
    private String serverAddress;
    private int serverPort;

    public Client() {
        loadConfig();
    }

    private void loadConfig() {
        Properties properties = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.out.println("Sorry, unable to find config.properties");
                return;
            }
            properties.load(input);
            serverAddress = properties.getProperty("server.address");
            serverPort = Integer.parseInt(properties.getProperty("server.port"));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void listFiles() {
        try (Socket socket = new Socket(serverAddress, serverPort);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            out.println("LIST");
            String response = in.readLine();
            System.out.println("Available files: " + response);
        } catch (IOException e) {
            System.err.println("Error listing files: " + e.getMessage());
        }
    }

    public void downloadFile(String fileName) {
        try (Socket socket = new Socket(serverAddress, serverPort);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedOutputStream fileOut = new BufferedOutputStream(new FileOutputStream(fileName))) {

            out.println("GET " + fileName);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = socket.getInputStream().read(buffer)) != -1) {
                fileOut.write(buffer, 0, bytesRead);
            }
            fileOut.flush();
            System.out.println("File " + fileName + " downloaded successfully.");
        } catch (FileNotFoundException e) {
            System.err.println("File not found: " + fileName);
        } catch (IOException e) {
            System.err.println("Error downloading file: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        Client client = new Client();
        client.listFiles();
        client.downloadFile("file1.txt");
    }
}