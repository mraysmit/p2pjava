# Distributed Service Registry Implementation

This document describes the distributed service registry improvements implemented for the P2P Java project, providing resilient and efficient service discovery across a peer-to-peer network.

## Overview

The distributed service registry extends the basic service registry with the following key features:

1. **Gossip Protocol** - Efficient service information propagation
2. **Conflict Resolution** - Handles inconsistencies between registry entries
3. **Versioned Service Registrations** - Tracks changes and facilitates conflict resolution
4. **Registry Synchronization** - Synchronizes registry state between peers
5. **Anti-Entropy Mechanism** - Ensures eventual consistency

## Architecture

### Core Components

#### 1. DistributedServiceRegistry
The main distributed registry implementation that:
- Extends the basic ServiceRegistry interface
- Uses gossip protocol for service discovery
- Maintains eventual consistency across peers
- Handles conflict resolution automatically

#### 2. GossipProtocol
Implements epidemic-style communication for:
- Efficient message propagation with configurable fanout
- Message deduplication and TTL management
- Peer failure detection and removal
- Configurable hop limits to prevent message loops

#### 3. ConflictResolver
Provides multiple strategies for resolving conflicts:
- **LAST_WRITE_WINS**: Uses the service instance with the highest version
- **ORIGIN_PRIORITY**: Prefers instances from specific priority peers
- **HEALTH_PRIORITY**: Prefers healthy instances over unhealthy ones
- **COMPOSITE**: Combines multiple strategies for robust resolution

#### 4. RegistryMessage
Protocol messages for gossip communication:
- Service registration/deregistration/updates
- Synchronization requests and responses
- Heartbeat messages for health monitoring
- Anti-entropy messages for reconciliation

#### 5. AntiEntropyService
Periodic reconciliation mechanism that:
- Selects random peers for reconciliation
- Exchanges registry snapshots
- Resolves conflicts discovered during reconciliation
- Provides configurable reconciliation intervals

## Key Features

### Gossip Protocol Implementation

The gossip protocol ensures efficient and resilient service information propagation:

```java
// Create a distributed registry with gossip protocol
DistributedServiceRegistry registry = new DistributedServiceRegistry(
    "peer1",           // Unique peer ID
    6003,              // Gossip port
    Set.of("peer2:6004") // Bootstrap peers
);

registry.start();

// Register a service - automatically propagated via gossip
registry.registerService("web-service", "web-1", "localhost", 8080, metadata);
```

**Features:**
- Configurable fanout (default: 3 peers per gossip round)
- Message deduplication using unique message IDs
- TTL-based message expiration (default: 30 seconds)
- Hop count limiting to prevent infinite propagation
- Automatic peer failure detection and removal

### Conflict Resolution Strategy

Multiple conflict resolution strategies handle registry inconsistencies:

```java
// Create resolver with composite strategy
ConflictResolver resolver = new ConflictResolver(
    ConflictResolver.ResolutionStrategy.COMPOSITE,
    Set.of("trusted-peer-1", "trusted-peer-2") // Priority peers
);

// Resolve conflicts between multiple service instances
List<ServiceInstance> conflicting = Arrays.asList(instance1, instance2, instance3);
ServiceInstance resolved = resolver.resolveConflict(conflicting);
```

**Strategies:**
- **Last Write Wins**: Selects instance with highest version timestamp
- **Origin Priority**: Prefers instances from designated priority peers
- **Health Priority**: Favors healthy instances over unhealthy ones
- **Composite**: Combines health, origin priority, and version checks

### Versioned Service Registrations

All service instances include version information for conflict resolution:

```java
// Service instances automatically include version and origin information
ServiceInstance instance = new ServiceInstance(
    "web-service", "web-1", "localhost", 8080, metadata,
    System.currentTimeMillis(), // Version timestamp
    "peer1"                     // Origin peer ID
);

// Check if one instance is newer than another
boolean isNewer = instance1.isNewerThan(instance2);

// Create instance with updated version
ServiceInstance updated = instance.withVersion(newVersion, newOriginPeer);
```

### Registry Synchronization

Peers can request and exchange registry snapshots:

```java
// Request synchronization for specific service types
Set<String> requestedTypes = Set.of("web-service", "database");
RegistryMessage syncRequest = RegistryMessage.createSyncRequest(peerId, requestedTypes);

// Create sync response with registry snapshot
Map<String, Map<String, ServiceInstance>> snapshot = registry.getRegistrySnapshot();
RegistryMessage syncResponse = RegistryMessage.createSyncResponse(peerId, snapshot, version);
```

### Anti-Entropy Mechanism

Periodic reconciliation ensures eventual consistency:

```java
// Create anti-entropy service with custom configuration
AntiEntropyService antiEntropy = new AntiEntropyService(
    peerId, registry, gossipProtocol, conflictResolver,
    60000,  // Reconciliation interval (1 minute)
    3,      // Number of peers to reconcile with
    30000   // Max reconciliation time
);

antiEntropy.start();

// Manually trigger reconciliation
antiEntropy.triggerReconciliation();

// Get reconciliation statistics
Map<String, Object> stats = antiEntropy.getStatistics();
```

## Usage Examples

### Basic Distributed Registry Setup

```java
// Create distributed registry
DistributedServiceRegistry registry = ServiceRegistryFactory.createDistributedRegistry(
    "peer1", 6003, Set.of("bootstrap-peer:6003")
);

registry.start();

// Register services
registry.registerService("web-service", "web-1", "localhost", 8080, 
    Map.of("version", "1.0", "region", "us-east"));

// Discover services (includes services from other peers)
List<ServiceInstance> services = registry.discoverServices("web-service");

// Update service health
registry.updateServiceHealth("web-service", "web-1", false);

registry.stop();
```

### Multi-Peer Network

```java
// Create multiple peers
DistributedServiceRegistry peer1 = new DistributedServiceRegistry("peer1", 6001, Set.of());
DistributedServiceRegistry peer2 = new DistributedServiceRegistry("peer2", 6002, Set.of("localhost:6001"));
DistributedServiceRegistry peer3 = new DistributedServiceRegistry("peer3", 6003, Set.of("localhost:6001"));

// Start all peers
peer1.start();
peer2.start();
peer3.start();

// Register services on different peers
peer1.registerService("web", "web-1", "host1", 8080, null);
peer2.registerService("db", "db-1", "host2", 5432, null);
peer3.registerService("cache", "redis-1", "host3", 6379, null);

// All peers can discover all services via gossip propagation
List<ServiceInstance> allWebServices = peer2.discoverServices("web");
List<ServiceInstance> allDbServices = peer3.discoverServices("db");
```

## Configuration

### Gossip Protocol Configuration

```java
GossipProtocol gossip = new GossipProtocol(
    "peer1",    // Peer ID
    6003,       // Gossip port
    5000,       // Gossip interval (ms)
    3,          // Fanout (peers per round)
    30000       // Message TTL (ms)
);
```

### Conflict Resolution Configuration

```java
// Configure priority peers for origin-based resolution
ConflictResolver resolver = new ConflictResolver(
    ConflictResolver.ResolutionStrategy.COMPOSITE,
    Set.of("trusted-peer-1", "trusted-peer-2")
);

// Add/remove priority peers dynamically
resolver.addPriorityPeer("new-trusted-peer");
resolver.removePriorityPeer("old-trusted-peer");
```

## Testing

The implementation includes comprehensive tests:

- **ConflictResolverTest**: Tests all conflict resolution strategies
- **RegistryMessageTest**: Tests message creation and gossip functionality
- **DistributedServiceRegistryTest**: Tests basic registry operations
- **Integration tests**: Test multi-peer scenarios

Run tests with:
```bash
mvn test
```

## Performance Characteristics

### Gossip Protocol
- **Time Complexity**: O(log N) for message propagation to N peers
- **Space Complexity**: O(M) where M is the number of unique messages in TTL window
- **Network Overhead**: Configurable fanout limits network traffic

### Conflict Resolution
- **Time Complexity**: O(K) where K is the number of conflicting instances
- **Space Complexity**: O(1) for resolution, O(K) for metadata merging

### Anti-Entropy
- **Reconciliation Frequency**: Configurable (default: 1 minute)
- **Peer Selection**: Random selection with configurable count
- **Network Impact**: Periodic full registry exchange with selected peers

## Fault Tolerance

The distributed registry provides several fault tolerance mechanisms:

1. **Peer Failures**: Automatic detection and removal of unresponsive peers
2. **Network Partitions**: Anti-entropy mechanism ensures eventual consistency
3. **Message Loss**: Gossip protocol provides redundant message paths
4. **Conflict Resolution**: Handles simultaneous updates from multiple peers
5. **Service Health**: Health monitoring and propagation of health status

## Future Enhancements

Potential improvements for the distributed registry:

1. **Persistent Storage**: Add optional persistence for registry state
2. **Security**: Implement authentication and authorization for peer communication
3. **Metrics**: Add detailed metrics collection and monitoring
4. **Load Balancing**: Implement service load balancing based on health and capacity
5. **Service Dependencies**: Track and manage service dependencies
6. **Geographic Awareness**: Consider network topology in peer selection

## Conclusion

The distributed service registry provides a robust, scalable solution for service discovery in P2P networks. The combination of gossip protocol, conflict resolution, and anti-entropy mechanisms ensures reliable service discovery even in the presence of network failures and partitions.
