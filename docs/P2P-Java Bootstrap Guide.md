
# P2P-Java Bootstrap Guide (Updated)

This guide explains how to use the bootstrap system to configure and start the P2P system.

## Overview

The bootstrap system provides a centralized way to configure and start the P2P system components. It includes:

1. **Configuration Management**: Load configuration from properties files, environment variables, and command-line arguments
2. **Health Checks**: Monitor the health of system components
3. **Dependency Management**: Start components in the correct order based on dependencies
4. **Dynamic Port Allocation**: Automatically find available ports for services
5. **Graceful Shutdown**: Ensure proper shutdown of all components when the application exits

## Configuration

### Configuration Sources

The configuration system loads settings from the following sources, in order of precedence (highest to lowest):

1. Command-line arguments (`--key=value`)
2. Environment variables (`P2P_KEY=value`)
3. Properties files
4. Default values from properties file (`config/config-manager-defaults.properties`)
5. Hardcoded default values (fallback if properties file is not found)

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
--config.file=/path/to/custom/config.properties
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

#### Usage Help

You can view usage information by calling the `printUsage()` method, which displays:

```
Usage: java -jar p2p-bootstrap.jar [options]
Options:
  --mode <mode>           Mode: start, stop, status (default: start)
  --components <list>     Components to start: tracker, indexserver, peer, all (default: all)
  --config.file <path>    Path to configuration file
  --<key>=<value>         Set configuration value
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

## Component Handling

### Standard Components

The tracker and index server components are handled directly by the BootstrapService, which:
1. Creates instances of these components
2. Calls their start/stop methods
3. Manages their dependencies

### Peer Components

Peer components are handled differently from the tracker and index server:

1. P2PBootstrap identifies peer components with the message: "Peer startup will be handled separately"
2. Peers may require specific configuration and multiple instances
3. Peer dependencies on the tracker are managed separately
4. When stopping, P2PBootstrap logs: "Peer shutdown will be handled separately"

This separation allows for more flexible peer management, especially in scenarios where multiple peer instances need to be created with different configurations.

## Health Checks

### Health Check Server

When `healthcheck.enabled` is set to `true` (default), the BootstrapService automatically starts a HealthCheckServer that provides HTTP endpoints for monitoring the health of system components.

The HealthCheckServer:
1. Is initialized during BootstrapService creation
2. Uses the port specified by `healthcheck.port` (default: 8080)
3. Starts when the bootstrap service starts
4. Stops when the bootstrap service stops

### Service Registration

Both P2PBootstrap and BootstrapService register themselves with the health check system:

```java
// In P2PBootstrap
HealthCheck.ServiceHealth health = HealthCheck.registerService("P2PBootstrap");
health.addHealthDetail("mode", mode);
health.addHealthDetail("components", components);

// In BootstrapService
HealthCheck.ServiceHealth health = HealthCheck.registerService("BootstrapService");
health.addHealthDetail("startTime", System.currentTimeMillis());
```

This allows their status to be monitored along with other components.

### Endpoints

- `GET /health`: Returns the overall health status of the system
- `GET /health/details`: Returns detailed health status information for all components
- `GET /health/detailed`: Alternative endpoint for detailed health (for test compatibility)
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
    },
    "P2PBootstrap": {
      "status": "UP",
      "lastChecked": 1621234567890,
      "details": {
        "mode": "start",
        "components": ["all"]
      }
    },
    "BootstrapService": {
      "status": "UP",
      "lastChecked": 1621234567890,
      "details": {
        "startTime": 1621234567890
      }
    }
  }
}
```

## Dynamic Port Allocation

The bootstrap system can automatically find available ports for services if the `bootstrap.dynamic.ports` configuration is set to `true`.

When dynamic port allocation is enabled, the system will start searching for available ports from the configured port numbers and increment until it finds an available port.

For example, if `tracker.port` is set to `6000` and that port is already in use, the system will try `6001`, `6002`, and so on until it finds an available port.

This is particularly useful for:
- Running multiple instances on the same machine
- Automated testing environments
- Environments where port conflicts are common

## Graceful Shutdown

The BootstrapService sets up a shutdown hook to ensure graceful shutdown of all components when the application exits:

```java
// Set up shutdown hook for graceful shutdown
Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
```

This ensures that:
1. All services are stopped in the reverse order of their startup
2. Resources are properly released
3. Health check server is stopped
4. Thread pools are shut down

The shutdown process can also be triggered manually by calling the `stop()` method on the BootstrapService.

## Troubleshooting

### Common Issues

1. **ClassNotFoundException**: Make sure all required classes are in the classpath.
2. **Port already in use**: Set `bootstrap.dynamic.ports` to `true` or use different port numbers.
3. **Configuration not loading**: Check the configuration file path and format.
4. **Circular dependencies**: Ensure there are no circular dependencies between components.
5. **Startup timeout**: Increase the `bootstrap.startup.timeout.seconds` value if components take longer to start.

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

The bootstrap system provides a flexible and powerful way to configure and start the P2P system. By using the configuration management, health checks, dependency management, and graceful shutdown features, you can ensure that your P2P system starts correctly, remains healthy, and shuts down properly when needed.