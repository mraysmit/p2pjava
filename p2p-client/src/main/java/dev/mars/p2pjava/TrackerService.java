package dev.mars.p2pjava;

public class TrackerService {
    public static void main(String[] args) {
        String trackerHost = System.getProperty("tracker.host", "0.0.0.0");
        int trackerPort = Integer.getInteger("tracker.port", 6000);

        P2PTestHarness.startTracker(trackerHost, trackerPort);
    }
}