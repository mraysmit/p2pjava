module p2p.util {
    requires java.logging;
    requires java.base;
    requires java.management;
    requires p2p.common.api;
    requires p2p.monitoring;
    requires p2p.circuit;

    exports dev.mars.p2pjava.util;
}