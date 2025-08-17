module p2p.discovery {
    requires java.logging;
    requires java.base;
    requires p2p.common.api;
    requires p2p.util;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.annotation;

    exports dev.mars.p2pjava.discovery;
}
