# P2P-Java Bootstrap Guide

This guide explains how to use the bootstrap system to configure and start the P2P system.

## Overview

The bootstrap system provides a centralized way to configure and start the P2P system components. It includes:

1. **Configuration Management**: Load configuration from properties files, environment variables, and command-line arguments
2. **Health Checks**: Monitor the health of system components
3. **Dependency Management**: Start components in the correct order based on dependencies
4. **Dynamic Port Allocation**: Automatically find available ports for services

## Configuration

### Configuration Sources

The configuration system loads settings from the following sources, in order of precedence (highest to lowest):

1. Command-line arguments (`--key=value`)
2. Environment variables (`P2P_KEY=value`)
3. Properties files
4. Default values

### Configuration Files

The system looks for configuration files in the following locations:

1. Path specified by the `--config.file` command-line argument
2. `config/application.properties`
3. `application.properties`
4. `config.properties`

### Configuration Format

Configuration files use the standard Java properties format:

```properties
# Tracker configuration
tracker.host=localhost
tracker.port=6000
tracker.thread.pool.size=10
tracker.peer.timeout.ms=90000

# IndexServer configuration
indexserver.host=localhost
indexserver.port=6001
indexserver.thread.pool.size=10
indexserver.storage.dir=data
indexserver.storage.file=file_index.dat
indexserver.cache.ttl.ms=60000
indexserver.cache.refresh.ms=300000
indexserver.connection.pool.max=100
indexserver.connection.timeout.ms=5000

# Peer configuration
peer.socket.timeout.ms=30000
peer.heartbeat.interval.seconds=30

# Health check configuration
healthcheck.enabled=true
healthcheck.port=8080
healthcheck.path=/health

# Bootstrap configuration
bootstrap.auto.start=true
bootstrap.startup.timeout.seconds=30
bootstrap.dynamic.ports=false
```

### Environment Variables

Environment variables must be prefixed with `P2P_` and use uppercase with underscores instead of dots:

```
P2P_TRACKER_PORT=6000
P2P_INDEXSERVER_PORT=6001
P2P_HEALTHCHECK_ENABLED=true
```

### Command-Line Arguments

Command-line arguments use the format `--key=value`:

```
--tracker.port=6000
--indexserver.port=6001
--healthcheck.enabled=true
```

## Starting the System

### Using the P2PBootstrap Class

The `P2PBootstrap` class is the main entry point for starting the P2P system. It can be run with the following command:

```
java -cp <classpath> dev.mars.p2pjava.bootstrap.P2PBootstrap [options]
```

#### Options

- `--mode <mode>`: The mode to run in (start, stop, status)
- `--components <list>`: The components to start (tracker, indexserver, peer, all)
- `--config.file <path>`: Path to a configuration file
- `--<key>=<value>`: Set a configuration value

#### Examples

Start all components:

```
java -cp <classpath> dev.mars.p2pjava.bootstrap.P2PBootstrap
```

Start only the tracker and index server:

```
java -cp <classpath> dev.mars.p2pjava.bootstrap.P2PBootstrap --components tracker,indexserver
```

Start all components with custom ports:

```
java -cp <classpath> dev.mars.p2pjava.bootstrap.P2PBootstrap --tracker.port=7000 --indexserver.port=7001
```

### Using the BootstrapService Programmatically

You can also use the `BootstrapService` class programmatically to start the P2P system:

```java
// Initialize configuration
ConfigurationManager config = ConfigurationManager.getInstance();
config.initialize(args);

// Create bootstrap service
BootstrapService bootstrap = new BootstrapService();

// Register components
bootstrap.registerService("tracker", Tracker.class, "startTracker", "stopTracker");
bootstrap.registerService("indexserver", IndexServer.class, "startIndexServer", "stopIndexServer");

// Add dependencies
bootstrap.addDependency("indexserver", "tracker");

// Start the bootstrap service
bootstrap.start();
```

## Health Checks

The bootstrap system includes a health check server that provides HTTP endpoints for monitoring the health of system components.

### Endpoints

- `GET /health`: Returns the overall health status of the system
- `GET /health/details`: Returns detailed health status information for all components
- `GET /health/service?name=<service>`: Returns health status information for a specific service

### Health Status Format

The health status is returned in JSON format:

```json
{
  "status": "UP",
  "services": {
    "tracker": {
      "status": "UP",
      "lastChecked": 1621234567890,
      "details": {
        "host": "localhost",
        "port": 6000
      }
    },
    "indexserver": {
      "status": "UP",
      "lastChecked": 1621234567890,
      "details": {
        "host": "localhost",
        "port": 6001
      }
    }
  }
}
```

## Dynamic Port Allocation

The bootstrap system can automatically find available ports for services if the `bootstrap.dynamic.ports` configuration is set to `true`.

When dynamic port allocation is enabled, the system will start searching for available ports from the configured port numbers and increment until it finds an available port.

For example, if `tracker.port` is set to `6000` and that port is already in use, the system will try `6001`, `6002`, and so on until it finds an available port.

## Troubleshooting

### Common Issues

1. **ClassNotFoundException**: Make sure all required classes are in the classpath.
2. **Port already in use**: Set `bootstrap.dynamic.ports` to `true` or use different port numbers.
3. **Configuration not loading**: Check the configuration file path and format.

### Logging

The bootstrap system uses Java's built-in logging system. You can configure logging by setting the following system properties:

```
-Djava.util.logging.config.file=logging.properties
```

A sample logging.properties file:

```properties
handlers=java.util.logging.ConsoleHandler
.level=INFO
java.util.logging.ConsoleHandler.level=INFO
java.util.logging.ConsoleHandler.formatter=java.util.logging.SimpleFormatter
dev.mars.p2pjava.level=FINE
```

## Conclusion

The bootstrap system provides a flexible and powerful way to configure and start the P2P system. By using the configuration management, health checks, and dependency management features, you can ensure that your P2P system starts correctly and remains healthy.