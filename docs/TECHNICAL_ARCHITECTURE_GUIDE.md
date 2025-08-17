# P2P Java Technical Architecture Guide

## System Overview

P2P Java is a modern, enterprise-grade peer-to-peer distributed system built with Java 23. The architecture follows dependency injection principles, implements comprehensive error handling, and supports both centralized and distributed service discovery patterns.

## Architectural Principles

### 1. Modular Design

The system is organized into 16 Maven modules across four architectural layers:

**Foundation Layer:**
- `p2p-common-api`: Shared contracts, data models, and messaging protocols
- `p2p-config`: Configuration management with multi-source support
- `p2p-util`: Utility functions, thread management, and error handling

**Infrastructure Layer:**
- `p2p-bootstrap`: Component lifecycle and dependency management
- `p2p-health`: Health monitoring and status reporting
- `p2p-monitoring`: System metrics and performance monitoring

**Core Services Layer:**
- `p2p-discovery`: Service discovery (centralized and distributed)
- `p2p-storage`: File storage and indexing capabilities
- `p2p-connection`: Connection management and networking
- `p2p-cache`: Distributed caching layer
- `p2p-circuit`: Circuit breaker pattern implementation
- `p2p-auth`: Authentication and authorization services

**Application Layer:**
- `p2p-tracker`: Peer registry and coordination service
- `p2p-indexserver`: File index and search service
- `p2p-peer`: Peer node implementation
- `p2p-client`: Client applications and examples

### 2. Dependency Injection Architecture

The system has been modernized from static, tightly-coupled components to a dependency injection-based architecture:

**Before (Static Dependencies):**
```java
public class TrackerService {
    private static Map<String, PeerInfo> peers = new ConcurrentHashMap<>();
    
    public void start() {
        ExecutorService pool = ThreadManager.getFixedThreadPool("tracker", 10);
        ServiceRegistry registry = ServiceRegistryFactory.getInstance().getRegistry();
    }
}
```

**After (Dependency Injection):**
```java
public class TrackerService {
    private final TrackerConfiguration config;
    private final ThreadPoolManager threadPoolManager;
    private final ServiceRegistryManager serviceRegistryManager;
    private final ErrorHandlingManager errorHandlingManager;
    
    public TrackerService(TrackerConfiguration config, 
                         ThreadPoolManager threadPoolManager,
                         ServiceRegistryManager serviceRegistryManager,
                         ErrorHandlingManager errorHandlingManager) {
        // Constructor injection
    }
}
```

### 3. Configuration Management

The configuration system supports multiple sources with proper precedence:

1. **System Properties** (highest priority)
2. **Environment Variables**
3. **Configuration Files** (YAML/Properties)
4. **Default Values** (lowest priority)

**Configuration Architecture:**
```java
public class TrackerConfiguration {
    private final int trackerPort;
    private final int threadPoolSize;
    private final boolean distributedDiscoveryEnabled;
    
    // Builder pattern for fluent configuration
    public static Builder builder() {
        return new Builder();
    }
}
```

## Core Components

### 1. Service Discovery

The system supports dual discovery modes:

**Centralized Discovery:**
- Tracker-based peer registry
- Index server for file discovery
- Simple request-response pattern

**Distributed Discovery:**
- Gossip protocol implementation
- Eventual consistency model
- Conflict resolution strategies
- Anti-entropy mechanisms

**Discovery Architecture:**
```java
public interface ServiceRegistry {
    boolean registerService(String serviceType, String serviceId, 
                           String host, int port, Map<String, String> metadata);
    List<ServiceInstance> discoverServices(String serviceType);
    boolean updateServiceHealth(String serviceType, String serviceId, boolean healthy);
}
```

### 2. Error Handling and Resilience

Comprehensive error handling with multiple patterns:

**Circuit Breaker Pattern:**
```java
public class CircuitBreaker {
    public enum State { CLOSED, OPEN, HALF_OPEN }
    
    public <T> T execute(Callable<T> operation) throws Exception {
        if (!canExecute()) {
            throw new CircuitBreakerOpenException("Circuit breaker is OPEN");
        }
        // Execute with failure tracking
    }
}
```

**Retry Mechanisms:**
```java
public class RetryManager {
    public <T> T executeWithRetry(Callable<T> operation, int maxRetries, 
                                 long initialBackoffMs, long maxBackoffMs,
                                 Predicate<Exception> retryCondition) {
        // Exponential backoff with jitter
    }
}
```

**Error Handling Orchestration:**
```java
public class ErrorHandlingManager {
    public <T> T executeWithFullProtection(String operationName, Callable<T> operation) {
        // Combines circuit breaker + retry + fallback
    }
}
```

### 3. Thread Management

Instance-based thread management replacing static utilities:

**ThreadPoolManager:**
```java
public class ThreadPoolManager {
    private final Map<String, ExecutorService> threadPools = new ConcurrentHashMap<>();
    
    public ExecutorService getFixedThreadPool(String poolName, String threadNamePrefix, int poolSize) {
        return threadPools.computeIfAbsent(poolName, k -> {
            return Executors.newFixedThreadPool(poolSize, createThreadFactory(threadNamePrefix));
        });
    }
    
    public void shutdownAll() {
        // Graceful shutdown of all pools
    }
}
```

### 4. Messaging Protocol

JSON-based messaging with type safety:

**Message Hierarchy:**
```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TrackerMessage.class, name = "tracker"),
    @JsonSubTypes.Type(value = IndexServerMessage.class, name = "indexserver"),
    @JsonSubTypes.Type(value = PeerMessage.class, name = "peer")
})
public abstract class P2PMessage {
    protected String messageId;
    protected long timestamp;
    protected String senderId;
    
    public abstract boolean isValid();
}
```

## Data Flow Architecture

### 1. Peer Registration Flow

```
Peer -> TrackerService -> ServiceRegistry -> DistributedRegistry (if enabled)
  |                                               |
  v                                               v
Health Check                                 Gossip Protocol
  |                                               |
  v                                               v
Monitoring                                   Other Peers
```

### 2. Service Discovery Flow

```
Client Request -> ErrorHandlingManager -> CircuitBreaker -> RetryManager
                                                |
                                                v
                                         ServiceRegistry
                                                |
                                                v
                                    [Centralized | Distributed]
                                                |
                                                v
                                         Service Instances
```

### 3. Configuration Loading Flow

```
ConfigurationLoader -> [System Properties | Environment | Files | Defaults]
                                                |
                                                v
                                    TrackerConfiguration
                                                |
                                                v
                                         Service Components
```

## Performance Characteristics

### 1. Scalability

- **Centralized Mode**: Supports hundreds of peers per tracker
- **Distributed Mode**: Scales to thousands of peers with gossip protocol
- **Thread Management**: Configurable thread pools with monitoring
- **Connection Pooling**: Efficient resource utilization

### 2. Reliability

- **Circuit Breakers**: Prevent cascading failures
- **Retry Mechanisms**: Handle transient failures
- **Health Monitoring**: Continuous service health checks
- **Graceful Degradation**: Fallback strategies for service failures

### 3. Monitoring

- **Thread Pool Metrics**: Utilization, queue size, active threads
- **Circuit Breaker Stats**: Failure rates, state transitions
- **Service Health**: Response times, availability metrics
- **System Resources**: Memory, CPU, network utilization

## Security Architecture

### 1. Authentication

Token-based authentication with role-based access control:

```java
public class AuthenticationService {
    public Optional<String> authenticate(String username, String password) {
        // Password hashing with salt
        // Token generation with expiration
    }
}
```

### 2. Authorization

Role-based access control:

```java
public enum Role {
    ADMIN, USER, PEER, GUEST
}

public class AuthorizationService {
    public boolean authorize(String tokenId, Role requiredRole) {
        // Token validation and role checking
    }
}
```

## Deployment Architecture

### 1. Single Node Deployment

```
[Tracker + IndexServer + Peer] -> Local Storage
                |
                v
        Health Check Endpoint
```

### 2. Distributed Deployment

```
Tracker Cluster <-> Gossip Network <-> Index Server Cluster
       |                                        |
       v                                        v
   Peer Nodes                              File Storage
       |                                        |
       v                                        v
Load Balancer                            Distributed Cache
```

### 3. Container Deployment

```yaml
# Docker Compose example
services:
  tracker:
    image: p2p-java:latest
    environment:
      - TRACKER_PORT=6000
      - DISCOVERY_DISTRIBUTED_ENABLED=true
    ports:
      - "6000:6000"
      - "8080:8080"
```

## Integration Points

### 1. External Systems

- **Service Registries**: ZooKeeper, Consul, etcd integration
- **Monitoring**: Prometheus, Grafana integration
- **Logging**: ELK stack integration
- **Databases**: Persistent storage for configuration and state

### 2. API Interfaces

- **REST APIs**: Health checks, configuration management
- **JSON Messaging**: Inter-service communication
- **JMX**: Management and monitoring interfaces

This technical architecture provides a solid foundation for building scalable, reliable peer-to-peer distributed systems with modern Java practices and enterprise-grade features.
