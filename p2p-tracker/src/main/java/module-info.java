module dev.mars.p2ptracker {
    // Export the package containing the Tracker and TrackerHandler classes
    exports dev.mars.p2pjava;

    // Require the java.logging module as specified in the issue description
    requires java.logging;

    // Require other Java modules used by the p2p-tracker module
    requires java.base; // This is implicit, but included for clarity

    // Require the p2p-common module
    requires dev.mars.p2pcommon;
}
