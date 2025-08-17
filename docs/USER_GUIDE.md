# P2P Java User Guide

## Introduction

This comprehensive user guide covers all aspects of using P2P Java, from basic peer-to-peer operations to advanced distributed system features. The guide is organized to take you from fundamental concepts through to expert-level usage.

## Getting Started with P2P Operations

### Understanding P2P Java Components

P2P Java consists of several key components that work together:

**Tracker Service**: Central registry for peer discovery and coordination
**Index Server**: File indexing and search capabilities  
**Peer Nodes**: Individual participants in the P2P network
**Discovery Service**: Both centralized and distributed peer discovery
**Bootstrap System**: Automated component lifecycle management

### Basic Peer Operations

#### 1. Starting Your First Peer

```java
// Create configuration
TrackerConfiguration config = TrackerConfiguration.builder()
    .trackerPort(6000)
    .threadPoolSize(10)
    .peerTimeoutMs(90000)
    .build();

// Create and start tracker service
TrackerService tracker = new TrackerService(config);
tracker.start();

// Register a peer
boolean success = tracker.registerPeer("peer1", "192.168.1.100", 8080);
if (success) {
    System.out.println("Peer registered successfully");
}
```

#### 2. Discovering Other Peers

```java
// Get all active peers
List<String> activePeers = tracker.getActivePeers();
System.out.println("Active peers: " + activePeers);

// Check if specific peer is alive
boolean isAlive = tracker.isPeerAlive("peer1");

// Update peer heartbeat
tracker.updatePeerLastSeen("peer1");
```

#### 3. Peer Lifecycle Management

```java
// Deregister a peer
boolean deregistered = tracker.deregisterPeer("peer1");

// Get peer information
PeerInfo peerInfo = tracker.getPeer("peer1");
if (peerInfo != null) {
    System.out.println("Peer: " + peerInfo.getPeerId() + 
                      " at " + peerInfo.getAddress() + ":" + peerInfo.getPort());
}
```

## Configuration Management

### Configuration Sources and Precedence

P2P Java loads configuration from multiple sources in this order of precedence:

1. **System Properties** (highest priority)
2. **Environment Variables**
3. **Configuration Files**
4. **Default Values** (lowest priority)

### Basic Configuration

#### Using Properties Files

Create `tracker.properties`:

```properties
# Core Tracker Settings
tracker.port=6000
tracker.threadpool.size=10
tracker.peer.timeout.ms=90000

# Service Discovery Settings
discovery.distributed.enabled=false
discovery.gossip.port=6003
discovery.gossip.interval.ms=5000
discovery.gossip.fanout=3
discovery.gossip.bootstrap.peers=

# Health Check Settings
tracker.health.enabled=true
tracker.health.interval.ms=30000

# Logging and Monitoring
tracker.log.level=INFO
tracker.metrics.enabled=true
```

#### Using Environment Variables

```bash
export TRACKER_PORT=6000
export TRACKER_THREADPOOL_SIZE=10
export DISCOVERY_DISTRIBUTED_ENABLED=true
export DISCOVERY_GOSSIP_BOOTSTRAP_PEERS="peer1:6003,peer2:6003"
```

#### Using System Properties

```bash
java -Dtracker.port=6000 \
     -Ddiscovery.distributed.enabled=true \
     -jar p2p-tracker.jar
```

### Advanced Configuration

#### Builder Pattern Configuration

```java
TrackerConfiguration config = TrackerConfiguration.builder()
    .trackerPort(6000)
    .threadPoolSize(20)
    .peerTimeoutMs(120000)
    .distributedDiscoveryEnabled(true)
    .gossipPort(6003)
    .gossipIntervalMs(3000)
    .bootstrapPeers("peer1:6003,peer2:6003,peer3:6003")
    .healthCheckEnabled(true)
    .healthCheckIntervalMs(30000)
    .logLevel("DEBUG")
    .metricsEnabled(true)
    .build();
```

#### Loading Configuration from Files

```java
// Load from specific file
TrackerConfiguration config = ConfigurationLoader.loadFromFile("custom-config.properties");

// Load from multiple sources
TrackerConfiguration config = ConfigurationLoader.loadConfiguration();
```

## Service Discovery

### Centralized Discovery

In centralized mode, peers register with a central tracker:

```java
// Enable centralized discovery (default)
TrackerConfiguration config = TrackerConfiguration.builder()
    .distributedDiscoveryEnabled(false)
    .build();

TrackerService tracker = new TrackerService(config);
tracker.start();

// Discover other tracker instances
List<ServiceInstance> otherTrackers = tracker.discoverOtherTrackers();
```

### Distributed Discovery

Distributed discovery uses gossip protocol for decentralized peer discovery:

#### Enabling Distributed Discovery

```java
TrackerConfiguration config = TrackerConfiguration.builder()
    .distributedDiscoveryEnabled(true)
    .gossipPort(6003)
    .gossipIntervalMs(5000)
    .gossipFanout(3)
    .bootstrapPeers("peer1:6003,peer2:6003")
    .build();
```

#### Running Distributed Peers

```bash
# Terminal 1 - Peer 1
java -Ddiscovery.gossip.port=6003 \
     -Ddiscovery.gossip.bootstrap.peers="localhost:6004,localhost:6005" \
     -jar p2p-peer.jar

# Terminal 2 - Peer 2
java -Ddiscovery.gossip.port=6004 \
     -Ddiscovery.gossip.bootstrap.peers="localhost:6003,localhost:6005" \
     -jar p2p-peer.jar

# Terminal 3 - Peer 3
java -Ddiscovery.gossip.port=6005 \
     -Ddiscovery.gossip.bootstrap.peers="localhost:6003,localhost:6004" \
     -jar p2p-peer.jar
```

#### Using the Distributed P2P Client

```java
// Create distributed P2P client
ConfigurationManager config = ConfigurationManager.getInstance();
config.set("serviceRegistry.distributed.enabled", "true");
DistributedP2PClient client = new DistributedP2PClient(config);

// Register a file-sharing service
Map<String, String> metadata = new HashMap<>();
metadata.put("description", "Shared document");
metadata.put("size", "2048");
metadata.put("type", "pdf");

client.registerFileService("doc-service-1", "localhost", 8080, "document.pdf", metadata);

// Discover peers with specific files
List<PeerInfo> peersWithFile = client.discoverPeersWithFile("document.pdf");

// Discover all file-sharing services
List<ServiceInstance> fileServices = client.discoverServices("file-sharing");
```

## Error Handling and Resilience

### Understanding Error Handling Components

P2P Java implements comprehensive error handling with three main patterns:

**Circuit Breakers**: Prevent cascading failures by failing fast when services are down
**Retry Mechanisms**: Automatically retry failed operations with exponential backoff
**Fallback Strategies**: Provide alternative responses when operations fail

### Using Circuit Breakers

#### Basic Circuit Breaker Usage

```java
// Create circuit breaker
CircuitBreaker circuitBreaker = CircuitBreaker.builder()
    .name("tracker-service")
    .failureThreshold(5)
    .retryTimeout(Duration.ofSeconds(30))
    .build();

// Execute operation with circuit breaker protection
String result = circuitBreaker.execute(() -> {
    return callTrackerService();
});
```

#### Circuit Breaker with Fallback

```java
String result = circuitBreaker.executeWithFallback(
    () -> callPrimaryService(),
    () -> "Service temporarily unavailable"
);
```

### Retry Mechanisms

#### Basic Retry Usage

```java
RetryManager retryManager = RetryManager.builder()
    .maxRetries(3)
    .initialBackoff(1000)
    .maxBackoff(10000)
    .build();

String result = retryManager.executeWithRetry(() -> {
    return performNetworkOperation();
});
```

#### Advanced Retry Configuration

```java
String result = retryManager.policy()
    .maxRetries(5)
    .initialBackoff(500)
    .maxBackoff(30000)
    .retryOn(IOException.class)
    .execute(() -> performOperation());
```

### Comprehensive Error Handling

#### Using ErrorHandlingManager

```java
// Create error handling manager
ErrorHandlingManager errorManager = new ErrorHandlingManager();

// Register circuit breakers and fallbacks
errorManager.registerCircuitBreaker("critical-operation");
errorManager.registerFallback("critical-operation", "Fallback response");

// Execute with full protection
String result = errorManager.operation("critical-operation")
    .withAll() // Circuit breaker + retry + fallback
    .execute(() -> performCriticalOperation());
```

## Authentication and Authorization

### User Management

#### Creating and Managing Users

```java
// Get auth service
AuthService authService = AuthService.getInstance(configManager);

// Create a new user
User user = new User("john_doe", "securePassword123", Set.of(Role.USER));
boolean created = authService.createUser(user);

// Update user roles
boolean updated = authService.updateUserRoles("john_doe", Set.of(Role.USER, Role.PEER));

// Delete user
boolean deleted = authService.deleteUser("john_doe");
```

#### User Authentication

```java
// Authenticate user (login)
Optional<String> tokenOpt = authService.login("john_doe", "securePassword123");
if (tokenOpt.isPresent()) {
    String tokenId = tokenOpt.get();
    System.out.println("Login successful, token: " + tokenId);
    
    // Use token for subsequent operations
    performAuthorizedOperation(tokenId);
} else {
    System.out.println("Login failed");
}

// Logout
boolean loggedOut = authService.logout(tokenId);
```

### Authorization

#### Role-Based Access Control

```java
// Check if user has required role
if (authService.authorize(tokenId, Role.ADMIN)) {
    // Perform admin operation
    performAdminOperation();
} else {
    // Return authorization error
    throw new UnauthorizedException("Admin role required");
}

// Check multiple roles
if (authService.authorize(tokenId, Role.USER) || authService.authorize(tokenId, Role.PEER)) {
    // Perform operation available to users or peers
    performUserOperation();
}
```

#### Protecting Operations

```java
public class SecureTrackerService {
    private final AuthService authService;
    
    public boolean registerPeer(String tokenId, String peerId, String host, int port) {
        // Verify authorization
        if (!authService.authorize(tokenId, Role.PEER)) {
            throw new UnauthorizedException("Peer role required for registration");
        }
        
        // Perform operation
        return tracker.registerPeer(peerId, host, port);
    }
}
```

## Monitoring and Health Checks

### Health Check System

#### Basic Health Monitoring

```bash
# Check overall system health
curl http://localhost:8080/health

# Get detailed health information
curl http://localhost:8080/health/details

# Check specific service
curl http://localhost:8080/health/service?name=tracker
```

#### Health Check Response Format

```json
{
  "status": "UP",
  "timestamp": "2025-01-17T10:30:00Z",
  "services": {
    "tracker": {
      "status": "UP",
      "details": {
        "port": 6000,
        "activePeers": 15,
        "uptime": "PT4H15M30S",
        "threadPoolUtilization": 0.65
      }
    }
  }
}
```

### Custom Health Checks

```java
// Register custom health check
HealthCheck.registerCustomCheck("database", () -> {
    try {
        database.ping();
        return HealthStatus.UP.withDetail("responseTime", "25ms");
    } catch (Exception e) {
        return HealthStatus.DOWN.withDetail("error", e.getMessage());
    }
});
```

### Performance Monitoring

#### Thread Pool Monitoring

```java
// Get thread pool statistics
ThreadPoolManager.ThreadPoolStatus status = threadPoolManager.getStatus();
System.out.println("Active pools: " + status.getTotalPoolCount());

// Log thread pool status
threadPoolManager.logStatus();
```

#### Circuit Breaker Monitoring

```java
// Get circuit breaker statistics
Map<String, CircuitBreaker.CircuitBreakerStats> stats = 
    errorHandlingManager.getCircuitBreakerStats();

for (CircuitBreaker.CircuitBreakerStats stat : stats.values()) {
    System.out.println("Circuit Breaker: " + stat.getName());
    System.out.println("State: " + stat.getState());
    System.out.println("Failure Rate: " + stat.getFailureRate());
}
```

## Advanced Features

### File Sharing and Indexing

#### Using the Index Server

```java
// Start index server
IndexServerService indexServer = new IndexServerService();
indexServer.start();

// Index a file
FileInfo fileInfo = new FileInfo("document.pdf", 2048, "application/pdf");
indexServer.indexFile("peer1", fileInfo);

// Search for files
List<FileInfo> results = indexServer.searchFiles("*.pdf");
```

#### Distributed File Discovery

```java
// Discover peers with specific files
List<PeerInfo> peersWithFile = client.discoverPeersWithFile("document.pdf");

// Get file from peer
if (!peersWithFile.isEmpty()) {
    PeerInfo peer = peersWithFile.get(0);
    byte[] fileData = client.downloadFile(peer, "document.pdf");
}
```

### Caching

#### Using the Distributed Cache

```java
// Get cache instance
DistributedCache cache = DistributedCache.getInstance();

// Store data
cache.put("user:123", userData, Duration.ofMinutes(30));

// Retrieve data
Optional<UserData> cachedData = cache.get("user:123", UserData.class);

// Remove data
cache.remove("user:123");
```

### Performance Tuning

#### Thread Pool Optimization

```properties
# Optimize thread pools for high load
tracker.threadpool.size=50
tracker.threadpool.max.size=100
tracker.threadpool.keepalive.seconds=300
```

#### Memory Management

```bash
# JVM tuning for large deployments
java -Xmx4g -Xms2g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -jar p2p-tracker.jar
```

## Troubleshooting

### Common Issues

**Peer Registration Failures:**
- Check network connectivity
- Verify tracker is running
- Confirm port availability

**Service Discovery Issues:**
- Validate gossip port configuration
- Check bootstrap peer connectivity
- Verify firewall settings

**Performance Problems:**
- Monitor thread pool utilization
- Check circuit breaker states
- Review garbage collection logs

### Debug Mode

```bash
# Enable debug logging
java -Dlogging.level.dev.mars.p2pjava=DEBUG -jar p2p-tracker.jar

# Enable JMX monitoring
java -Dcom.sun.management.jmxremote \
     -Dcom.sun.management.jmxremote.port=9999 \
     -jar p2p-tracker.jar
```

### Log Analysis

```bash
# Monitor logs in real-time
tail -f logs/p2p.log

# Search for errors
grep ERROR logs/p2p.log

# Analyze performance
grep "Circuit Breaker" logs/p2p.log | tail -20
```

This user guide provides comprehensive coverage of P2P Java functionality, from basic operations to advanced distributed system features. Each section builds upon previous concepts while providing practical examples for real-world usage.
