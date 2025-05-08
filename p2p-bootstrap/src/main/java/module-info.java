module p2p.bootstrap {
    requires java.logging;
    requires java.base;
    requires p2p.config;
    requires p2p.util;
    requires dev.mars.p2pjava.health;

    exports dev.mars.p2pjava.bootstrap;
}