# P2P Java Bootstrap Guide

#### © Mark Andrew Ray-Smith Cityline Ltd 2025

## Overview

The P2P Java bootstrap system provides automated component lifecycle management with dependency resolution, health monitoring, and graceful shutdown capabilities. This guide covers everything from basic startup to advanced production deployment scenarios.

## Bootstrap System Architecture

### Core Components

**BootstrapService**: Central orchestrator for component lifecycle
- Dependency graph analysis and topological sorting
- Circular dependency detection
- Service registration and startup coordination
- Health monitoring integration

**ConfigurationManager**: Multi-source configuration management
- Properties files, environment variables, command-line arguments
- Configuration validation and type safety
- Dynamic configuration reloading

**HealthCheckServer**: HTTP-based health monitoring
- Component health status endpoints
- Detailed health information reporting
- Integration with monitoring systems

## Basic Bootstrap Usage

### 1. Simple Component Startup

```java
// Create bootstrap service
BootstrapService bootstrap = new BootstrapService();

// Register services
bootstrap.registerService("tracker", TrackerService.class, "start", "stop");
bootstrap.registerService("indexserver", IndexServerService.class, "start", "stop");

// Add dependencies
bootstrap.addDependency("indexserver", "tracker");

// Start all services
boolean success = bootstrap.start();
```

### 2. Using the Main Bootstrap Application

```bash
# Start all components
java -jar p2p-bootstrap.jar start all

# Start specific components
java -jar p2p-bootstrap.jar start tracker,indexserver

# Check status
java -jar p2p-bootstrap.jar status

# Stop components
java -jar p2p-bootstrap.jar stop all
```

### 3. Configuration-Driven Bootstrap

Create `bootstrap.properties`:

```properties
# Component Configuration
bootstrap.components=tracker,indexserver,peer
bootstrap.startup.timeout=30000
bootstrap.shutdown.timeout=15000

# Health Check Configuration
bootstrap.health.enabled=true
bootstrap.health.port=8080
bootstrap.health.path=/health

# Dynamic Port Allocation
bootstrap.dynamic.ports=true
```

## Advanced Bootstrap Configuration

### 1. Dependency Management

The bootstrap system automatically resolves dependencies and starts components in the correct order:

```java
// Complex dependency example
bootstrap.registerService("auth", AuthService.class, "start", "stop");
bootstrap.registerService("config", ConfigService.class, "start", "stop");
bootstrap.registerService("tracker", TrackerService.class, "start", "stop");
bootstrap.registerService("indexserver", IndexServerService.class, "start", "stop");
bootstrap.registerService("peer", PeerService.class, "start", "stop");

// Define dependencies
bootstrap.addDependency("auth", "config");
bootstrap.addDependency("tracker", "auth");
bootstrap.addDependency("indexserver", "auth");
bootstrap.addDependency("peer", "tracker");
bootstrap.addDependency("peer", "indexserver");

// Bootstrap will start in order: config -> auth -> tracker,indexserver -> peer
```

### 2. Circular Dependency Detection

The system prevents deadlocks by detecting circular dependencies:

```java
try {
    bootstrap.start();
} catch (CircularDependencyException e) {
    System.err.println("Circular dependency detected:");
    System.err.println(e.getFormattedCircularDependencies());
    // Output: "Cycle 1: serviceA -> serviceB -> serviceC -> serviceA"
}
```

### 3. Custom Service Registration

```java
// Register with custom configuration
bootstrap.registerService("customService", CustomService.class, 
                         "initialize", "shutdown", 
                         Map.of("timeout", "30000", "retries", "3"));

// Register with factory method
bootstrap.registerServiceWithFactory("complexService", 
                                    () -> new ComplexService(config),
                                    "start", "stop");
```

## Production Deployment

### 1. Environment-Specific Configuration

**Development Environment:**
```properties
# development.properties
bootstrap.components=tracker,indexserver
bootstrap.health.enabled=true
bootstrap.dynamic.ports=true
logging.level=DEBUG
```

**Production Environment:**
```properties
# production.properties
bootstrap.components=tracker,indexserver,peer,auth
bootstrap.health.enabled=true
bootstrap.dynamic.ports=false
logging.level=INFO
monitoring.enabled=true
```

### 2. Docker Container Bootstrap

```dockerfile
FROM openjdk:23-jdk-slim

COPY p2p-bootstrap.jar /app/
COPY config/ /app/config/

WORKDIR /app

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s \
  CMD curl -f http://localhost:8080/health || exit 1

# Start with environment-specific config
CMD ["java", "-jar", "p2p-bootstrap.jar", "start", "all"]
```

### 3. Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: p2p-bootstrap
spec:
  replicas: 3
  selector:
    matchLabels:
      app: p2p-bootstrap
  template:
    metadata:
      labels:
        app: p2p-bootstrap
    spec:
      containers:
      - name: p2p-bootstrap
        image: p2p-java:latest
        env:
        - name: BOOTSTRAP_COMPONENTS
          value: "tracker,indexserver"
        - name: BOOTSTRAP_HEALTH_ENABLED
          value: "true"
        ports:
        - containerPort: 6000
          name: tracker
        - containerPort: 8080
          name: health
        livenessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
```

## Health Monitoring

### 1. Health Check Endpoints

The bootstrap system provides comprehensive health monitoring:

```bash
# Overall system health
curl http://localhost:8080/health

# Detailed health information
curl http://localhost:8080/health/details

# Specific service health
curl http://localhost:8080/health/service?name=tracker
```

**Response Format:**
```json
{
  "status": "UP",
  "timestamp": "2025-01-17T10:30:00Z",
  "services": {
    "tracker": {
      "status": "UP",
      "details": {
        "port": 6000,
        "activePeers": 5,
        "uptime": "PT2H30M"
      }
    },
    "indexserver": {
      "status": "UP",
      "details": {
        "port": 6001,
        "indexedFiles": 1250,
        "uptime": "PT2H30M"
      }
    }
  }
}
```

### 2. Custom Health Checks

```java
// Register custom health check
bootstrap.registerHealthCheck("database", () -> {
    try {
        database.ping();
        return HealthStatus.UP;
    } catch (Exception e) {
        return HealthStatus.DOWN.withDetail("error", e.getMessage());
    }
});
```

## Configuration Management

### 1. Multi-Source Configuration

The bootstrap system loads configuration from multiple sources:

```java
ConfigurationManager config = ConfigurationManager.getInstance();

// Load from multiple sources
config.loadFromFile("application.properties");
config.loadFromEnvironment("P2P_");
config.loadFromSystemProperties();

// Access configuration
String trackerHost = config.getString("tracker.host", "localhost");
int trackerPort = config.getInt("tracker.port", 6000);
```

### 2. Configuration Validation

```java
// Define validation rules
config.addValidator("tracker.port", value -> {
    int port = Integer.parseInt(value);
    return port > 1024 && port < 65536;
});

// Validate configuration
List<String> errors = config.validate();
if (!errors.isEmpty()) {
    throw new ConfigurationException("Invalid configuration: " + errors);
}
```

### 3. Dynamic Configuration Updates

```java
// Enable configuration watching
ConfigurationWatchService watchService = ConfigurationWatchService.getInstance();
watchService.start();

// Add change listener
config.addConfigurationChangeListener((oldConfig, newConfig) -> {
    logger.info("Configuration changed, reloading services...");
    bootstrap.reloadConfiguration(newConfig);
});
```

## Error Handling and Recovery

### 1. Startup Failure Handling

```java
try {
    boolean success = bootstrap.start();
    if (!success) {
        // Handle partial startup
        List<String> failedServices = bootstrap.getFailedServices();
        logger.error("Failed to start services: " + failedServices);
        
        // Attempt recovery
        bootstrap.retryFailedServices();
    }
} catch (CircularDependencyException e) {
    logger.error("Circular dependency detected: " + e.getFormattedCircularDependencies());
    System.exit(1);
} catch (Exception e) {
    logger.error("Bootstrap failed: " + e.getMessage(), e);
    System.exit(1);
}
```

### 2. Graceful Shutdown

```java
// Register shutdown hook
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    logger.info("Shutting down P2P system...");
    bootstrap.stop();
    logger.info("Shutdown complete");
}));

// Manual shutdown with timeout
boolean stopped = bootstrap.stop(30, TimeUnit.SECONDS);
if (!stopped) {
    logger.warning("Forced shutdown due to timeout");
    bootstrap.forceStop();
}
```

### 3. Service Recovery

```java
// Monitor service health and restart if needed
bootstrap.enableServiceRecovery(true);
bootstrap.setRecoveryPolicy(ServiceRecoveryPolicy.RESTART_ON_FAILURE);

// Custom recovery strategy
bootstrap.setRecoveryHandler("tracker", (service, exception) -> {
    logger.warning("Tracker service failed: " + exception.getMessage());
    
    // Wait before restart
    Thread.sleep(5000);
    
    // Restart with new configuration
    TrackerConfiguration newConfig = loadBackupConfiguration();
    return bootstrap.restartService("tracker", newConfig);
});
```

## Monitoring and Observability

### 1. Metrics Collection

```java
// Enable metrics
bootstrap.enableMetrics(true);

// Custom metrics
bootstrap.addMetric("startup.time", startupDuration);
bootstrap.addMetric("active.services", activeServiceCount);

// Export metrics
MetricsExporter.exportToPrometheus(bootstrap.getMetrics());
```

### 2. Logging Configuration

```properties
# Logging configuration
logging.level.root=INFO
logging.level.dev.mars.p2pjava.bootstrap=DEBUG
logging.pattern=%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n
logging.file.name=logs/bootstrap.log
logging.file.max-size=10MB
logging.file.max-history=30
```

### 3. Distributed Tracing

```java
// Enable tracing
bootstrap.enableTracing(true);
bootstrap.setTraceExporter(JaegerTraceExporter.create("http://jaeger:14268/api/traces"));

// Trace service startup
try (Span span = tracer.spanBuilder("service.startup").startSpan()) {
    span.setAttribute("service.name", serviceName);
    bootstrap.startService(serviceName);
}
```

## Troubleshooting

### Common Bootstrap Issues

**Service Startup Timeout:**
```bash
# Increase timeout
java -Dbootstrap.startup.timeout=60000 -jar p2p-bootstrap.jar start all
```

**Port Conflicts:**
```bash
# Enable dynamic port allocation
java -Dbootstrap.dynamic.ports=true -jar p2p-bootstrap.jar start all
```

**Configuration Not Found:**
```bash
# Specify configuration file
java -Dconfig.file=/path/to/config.properties -jar p2p-bootstrap.jar start all
```

**Memory Issues:**
```bash
# Increase heap size
java -Xmx2g -jar p2p-bootstrap.jar start all
```

### Debug Mode

```bash
# Enable debug logging
java -Dlogging.level.dev.mars.p2pjava.bootstrap=DEBUG -jar p2p-bootstrap.jar start all

# Enable JMX monitoring
java -Dcom.sun.management.jmxremote -jar p2p-bootstrap.jar start all
```

This bootstrap guide provides comprehensive coverage of the P2P Java bootstrap system, from basic usage to advanced production deployment scenarios. The system is designed to handle complex distributed system requirements while maintaining simplicity for basic use cases.
