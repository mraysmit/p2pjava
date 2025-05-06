module dev.mars.p2pcommon {
    // Export the packages in the p2p-common module
    exports dev.mars.p2pjava.bootstrap;
    exports dev.mars.p2pjava.common;
    exports dev.mars.p2pjava.config;
    exports dev.mars.p2pjava.discovery;
    exports dev.mars.p2pjava.health;
    exports dev.mars.p2pjava.storage;
    exports dev.mars.p2pjava.util;

    // Require the Java modules that are used
    requires java.base; // This is implicit, but included for clarity
    requires java.logging;
    requires jdk.httpserver; // For com.sun.net.httpserver
}
