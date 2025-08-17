# P2P Java Quick Start Guide

## Overview

P2P Java is a comprehensive peer-to-peer distributed system built with modern Java architecture. This guide will get you up and running quickly with the essential features.

## Prerequisites

- Java 23 or higher
- Maven 3.8 or higher
- 4GB RAM minimum
- Network connectivity for distributed features

## Installation

### 1. Clone and Build

```bash
git clone <repository-url>
cd p2pjava
mvn clean install
```

### 2. Verify Installation

```bash
mvn test -pl p2p-tracker
```

All tests should pass, confirming the installation is successful.

## Basic Usage

### Starting a Simple Tracker

The tracker is the central registry for peers in the network.

```bash
# Using default configuration
java -jar p2p-tracker/target/p2p-tracker.jar

# Using custom port
java -Dtracker.port=7000 -jar p2p-tracker/target/p2p-tracker.jar
```

### Starting a Peer

```bash
# Start a peer that connects to the tracker
java -jar p2p-peer/target/p2p-peer.jar --tracker-host=localhost --tracker-port=6000
```

### Using the Bootstrap System

For production deployments, use the bootstrap system:

```bash
# Start all components
java -jar p2p-bootstrap/target/p2p-bootstrap.jar start all

# Start specific components
java -jar p2p-bootstrap/target/p2p-bootstrap.jar start tracker,peer
```

## Configuration

### Environment Variables

Set these environment variables for quick configuration:

```bash
export TRACKER_PORT=6000
export DISCOVERY_DISTRIBUTED_ENABLED=true
export TRACKER_THREADPOOL_SIZE=20
```

### Configuration File

Create `tracker.properties`:

```properties
# Core Settings
tracker.port=6000
tracker.threadpool.size=10
tracker.peer.timeout.ms=90000

# Service Discovery
discovery.distributed.enabled=false
discovery.gossip.port=6003

# Health Monitoring
tracker.health.enabled=true
tracker.health.interval.ms=30000
```

## Essential Features

### 1. Peer Registration

```java
// Create tracker service with configuration
TrackerConfiguration config = TrackerConfiguration.builder()
    .trackerPort(6000)
    .threadPoolSize(10)
    .build();

TrackerService tracker = new TrackerService(config);
tracker.start();

// Register a peer
boolean registered = tracker.registerPeer("peer1", "localhost", 8080);
```

### 2. Service Discovery

```java
// Discover other trackers
List<ServiceInstance> trackers = tracker.discoverOtherTrackers();

// Get active peers
List<String> activePeers = tracker.getActivePeers();
```

### 3. Health Monitoring

```bash
# Check health via HTTP
curl http://localhost:8080/health

# Check specific service
curl http://localhost:8080/health/service?name=tracker
```

## Distributed Mode

### Enable Distributed Discovery

```properties
discovery.distributed.enabled=true
discovery.gossip.port=6003
discovery.gossip.bootstrap.peers=peer1:6003,peer2:6003
```

### Start Multiple Peers

```bash
# Terminal 1 - Peer 1
java -Ddiscovery.gossip.port=6003 -jar p2p-peer.jar

# Terminal 2 - Peer 2  
java -Ddiscovery.gossip.port=6004 -jar p2p-peer.jar

# Terminal 3 - Peer 3
java -Ddiscovery.gossip.port=6005 -jar p2p-peer.jar
```

## Testing Your Setup

### 1. Basic Connectivity Test

```java
// Test tracker connectivity
TrackerService tracker = new TrackerService();
tracker.start();

// Should return true if healthy
boolean healthy = tracker.isRunning();
```

### 2. Peer Discovery Test

```java
// Register multiple peers
tracker.registerPeer("peer1", "localhost", 8080);
tracker.registerPeer("peer2", "localhost", 8081);

// Should return 2 active peers
List<String> peers = tracker.getActivePeers();
assert peers.size() == 2;
```

### 3. Distributed Discovery Test

```bash
# Run the distributed example
mvn exec:java -Dexec.mainClass=dev.mars.p2pjava.DistributedDiscoveryExample \
    -pl p2p-client
```

## Common Issues and Solutions

### Port Already in Use

```bash
# Find available port
netstat -an | grep 6000

# Use different port
java -Dtracker.port=6001 -jar p2p-tracker.jar
```

### Peers Not Discovering Each Other

1. Check firewall settings
2. Verify gossip ports are unique
3. Confirm bootstrap peers are reachable

```bash
# Test connectivity
telnet localhost 6003
```

### Configuration Not Loading

1. Verify file location
2. Check file permissions
3. Validate syntax

```bash
# Test configuration loading
java -Dconfig.file=./tracker.properties -jar p2p-tracker.jar
```

## Next Steps

1. **Read the Technical Architecture Guide** for detailed system understanding
2. **Follow the Bootstrap Guide** for production deployment
3. **Consult the User Guide** for advanced features and configuration
4. **Explore the examples** in the p2p-client module

## Quick Reference

### Default Ports
- Tracker: 6000
- Health Check: 8080
- Gossip Protocol: 6003
- Index Server: 6001

### Key Configuration Properties
- `tracker.port` - Tracker service port
- `discovery.distributed.enabled` - Enable distributed discovery
- `tracker.threadpool.size` - Thread pool size
- `tracker.peer.timeout.ms` - Peer timeout

### Essential Commands
```bash
# Start tracker
java -jar p2p-tracker.jar

# Check health
curl http://localhost:8080/health

# View logs
tail -f logs/p2p.log

# Stop gracefully
kill -TERM <pid>
```

This quick start guide covers the essential steps to get P2P Java running. For detailed configuration, architecture details, and advanced features, refer to the other guides in this documentation set.
