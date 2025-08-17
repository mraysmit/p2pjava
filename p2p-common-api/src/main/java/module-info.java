module p2p.common.api {
    requires java.logging;
    requires java.base;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;

    exports dev.mars.p2pjava.common.protocol;
    exports dev.mars.p2pjava.common.exception;
    exports dev.mars.p2pjava.common.util;
    exports dev.mars.p2pjava.common;
}
