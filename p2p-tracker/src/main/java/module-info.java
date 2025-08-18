module dev.mars.p2ptracker {
    // Export the package containing the Tracker and TrackerHandler classes
    exports dev.mars.p2pjava;

    // Require the java.logging module as specified in the issue description
    requires java.logging;

    // Require other Java modules used by the p2p-tracker module
    requires java.base; // This is implicit, but included for clarity

    requires java.net.http; // For HTTP client functionality

    // Require the p2p-common-api module for PeerInfo
    requires p2p.common.api;

    // Require the p2p-discovery module
    requires p2p.discovery;

    // Require the p2p-util module for ThreadManager
    requires p2p.util;
}
