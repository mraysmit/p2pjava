package dev.mars.p2pjava;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class Tracker {
    private static final int TRACKER_PORT = 6000;
    private static final int THREAD_POOL_SIZE = 10;

    public static void startTracker() {
        ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        try (ServerSocket serverSocket = new ServerSocket(TRACKER_PORT)) {
            System.out.println("Tracker started on port " + TRACKER_PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                threadPool.submit(new TrackerHandler(clientSocket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            threadPool.shutdown();
        }
    }

    public static void main(String[] args) {
        startTracker();
    }
}

