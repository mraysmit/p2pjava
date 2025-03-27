package dev.mars.p2pjava;

public class IndexService {
    public static void main(String[] args) {
        String indexHost = System.getProperty("index.host", "0.0.0.0");
        int indexPort = Integer.getInteger("index.port", 6001);

        P2PTestHarness.startIndexServer(indexHost, indexPort);
    }
}