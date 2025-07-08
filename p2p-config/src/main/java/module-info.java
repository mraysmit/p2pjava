module p2p.config {
    requires java.logging;
    requires java.base;
    requires java.net.http;
    requires jdk.httpserver;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.dataformat.yaml;
    requires p2p.common.api;

    exports dev.mars.p2pjava.config;
}