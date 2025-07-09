module p2p.circuit {
    requires java.logging;
    requires java.base;
    requires p2p.common.api;
    requires p2p.monitoring;

    exports dev.mars.p2pjava.circuit;
}