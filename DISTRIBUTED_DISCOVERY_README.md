# Distributed Service Discovery with Gossip Protocol

This document explains how to use the distributed service discovery feature in the P2P Java system, which uses gossip protocol for decentralized peer and service discovery.

## Overview

The distributed service discovery system provides:

- **Decentralized Architecture**: No single point of failure - peers discover each other through gossip protocol
- **Automatic Propagation**: Service registrations automatically propagate throughout the network
- **Eventual Consistency**: All peers eventually learn about all services in the network
- **Resilience**: Network partitions and peer failures are handled gracefully
- **Conflict Resolution**: Multiple strategies for handling conflicting service information

## Quick Start

### 1. Enable Distributed Discovery

Add to your configuration file:

```properties
# Enable distributed service registry
serviceRegistry.distributed.enabled=true
serviceRegistry.distributed.gossipPort=6003
serviceRegistry.distributed.gossipIntervalMs=5000
serviceRegistry.distributed.fanout=3
serviceRegistry.distributed.bootstrapPeers=localhost:6003,localhost:6004,localhost:6005
```

### 2. Run the Example

**Windows:**
```bash
run-distributed-example.bat
```

**Linux/Mac:**
```bash
./run-distributed-example.sh
```

### 3. Manual Testing

You can also run individual peers manually:

```bash
# Terminal 1 - Peer 1
mvn exec:java -Dexec.mainClass=dev.mars.p2pjava.DistributedDiscoveryExample \
    -Dexec.args="peer1 8080 file1.txt" \
    -Dconfig.file=p2p-client/src/main/resources/distributed-config.properties \
    -pl p2p-client

# Terminal 2 - Peer 2  
mvn exec:java -Dexec.mainClass=dev.mars.p2pjava.DistributedDiscoveryExample \
    -Dexec.args="peer2 8081 file2.txt" \
    -Dconfig.file=p2p-client/src/main/resources/distributed-config.properties \
    -pl p2p-client

# Terminal 3 - Peer 3
mvn exec:java -Dexec.mainClass=dev.mars.p2pjava.DistributedDiscoveryExample \
    -Dexec.args="peer3 8082 file3.txt" \
    -Dconfig.file=p2p-client/src/main/resources/distributed-config.properties \
    -pl p2p-client
```

## Using the Distributed P2P Client

### Basic Usage

```java
// Create a distributed P2P client
ConfigurationManager config = ConfigurationManager.getInstance();
config.set("serviceRegistry.distributed.enabled", "true");
DistributedP2PClient client = new DistributedP2PClient(config);

// Register a file-sharing service
Map<String, String> metadata = new HashMap<>();
metadata.put("description", "My shared file");
metadata.put("size", "1024");

client.registerFileService(
    "my-service-id",
    "localhost", 
    8080,
    "myfile.txt",
    metadata
);

// Discover peers with a specific file
List<PeerInfo> peers = client.discoverPeersWithFile("myfile.txt");

// Discover all services of a type
List<ServiceInstance> services = client.discoverServices("file-sharing");
```

### Advanced Usage

```java
// Get the underlying service registry for advanced operations
ServiceRegistry registry = client.getServiceRegistry();

// Check service health
boolean healthy = registry.isServiceHealthy("file-sharing", "my-service-id");

// Deregister a service
client.deregisterFileService("my-service-id");

// Shutdown cleanly
client.shutdown();
```

## Running the Test Harness

### Distributed Test Harness

The `DistributedP2PTestHarness` provides comprehensive testing of the distributed discovery system:

```bash
# Enable distributed mode in configuration
echo "serviceRegistry.distributed.enabled=true" >> config.properties

# Run the distributed test harness
mvn exec:java -Dexec.mainClass=dev.mars.p2pjava.DistributedP2PTestHarness \
    -pl p2p-client
```

The test harness will:
1. Start distributed registries for multiple peers
2. Register file-sharing services
3. Test service discovery from each peer
4. Simulate peer failures to test resilience
5. Report test results

## Configuration Options

### Core Settings

```properties
# Enable/disable distributed discovery
serviceRegistry.distributed.enabled=true

# Gossip protocol port (each peer needs a unique port)
serviceRegistry.distributed.gossipPort=6003

# How often to send gossip messages (milliseconds)
serviceRegistry.distributed.gossipIntervalMs=5000

# Number of peers to gossip to in each round
serviceRegistry.distributed.fanout=3

# How long messages live in the network (milliseconds)
serviceRegistry.distributed.messageTtlMs=30000

# Bootstrap peers to connect to initially
serviceRegistry.distributed.bootstrapPeers=localhost:6003,localhost:6004
```

### Conflict Resolution

```properties
# Strategy for resolving conflicting service information
# Options: TIMESTAMP, COMPOSITE, PRIORITY
serviceRegistry.distributed.conflictResolution=TIMESTAMP
```

### Anti-Entropy (Reconciliation)

```properties
# Enable periodic reconciliation between peers
serviceRegistry.distributed.antiEntropy.enabled=true

# How often to perform reconciliation (milliseconds)
serviceRegistry.distributed.antiEntropy.intervalMs=60000

# Timeout for reconciliation operations
serviceRegistry.distributed.antiEntropy.reconciliationTimeoutMs=10000
```

## What You'll See

When running the distributed discovery example, you should observe:

### 1. Gossip Network Formation
```
INFO: Starting distributed registry for peer1 on port 6003 with bootstrap peers: [localhost:6004, localhost:6005]
INFO: Distributed registry started for peer: peer1
INFO: Connected to bootstrap peer: localhost:6004
```

### 2. Service Registration
```
INFO: Registering file-sharing service...
INFO: Registered file-sharing service: peer1-file-service for file: file1.txt
INFO: Broadcasting service registration via gossip protocol
```

### 3. Gossip Propagation
```
INFO: Received gossip message from peer2: SERVICE_REGISTER
INFO: Processing service registration for peer2-file-service
INFO: Propagating message to 3 peers
```

### 4. Service Discovery
```
INFO: Found 3 file-sharing services:
  - Service: peer1-file-service
    Host: localhost:8080
    Origin: peer1
    File: file1.txt
  - Service: peer2-file-service
    Host: localhost:8081
    Origin: peer2
    File: file2.txt
```

### 5. Failure Detection
```
INFO: Simulating failure of peer1
INFO: Peer peer1 marked as failed
INFO: After peer1 failure, peer2 discovered 2 services
```

## Troubleshooting

### Common Issues

1. **Peers not discovering each other**
   - Check that gossip ports are unique for each peer
   - Verify bootstrap peers are correctly configured
   - Ensure firewall allows gossip traffic

2. **Services not propagating**
   - Increase gossip interval if network is slow
   - Check message TTL is sufficient for network size
   - Verify fanout is appropriate for network topology

3. **High network traffic**
   - Reduce gossip frequency
   - Decrease fanout value
   - Enable message deduplication

### Debug Configuration

```properties
# Enable debug logging
logging.level=FINE

# Increase gossip statistics
gossip.statisticsEnabled=true
gossip.statisticsIntervalMs=10000
```

## Integration with Existing Code

The distributed discovery system is designed to work alongside your existing P2P infrastructure:

### Fallback Mode
```java
// The DistributedP2PClient automatically falls back to centralized discovery
// if distributed mode is disabled or fails to initialize
DistributedP2PClient client = new DistributedP2PClient(config);

// This will use distributed discovery if enabled, otherwise centralized
List<PeerInfo> peers = client.discoverPeersWithFile("file.txt");
```

### Hybrid Mode
You can run both centralized and distributed discovery simultaneously for migration or redundancy purposes.

## Performance Considerations

- **Network Size**: Gossip protocol scales well to hundreds of peers
- **Message Frequency**: Balance between convergence speed and network load
- **Fanout**: Higher fanout = faster propagation but more network traffic
- **TTL**: Longer TTL = better reliability but more duplicate messages

## Next Steps

1. **Test with your existing P2P application**
2. **Tune configuration for your network topology**
3. **Implement custom conflict resolution strategies if needed**
4. **Monitor gossip statistics for optimization**
5. **Consider implementing custom service types beyond file-sharing**

For more detailed information, see the documentation in the `p2p-discovery` module.
