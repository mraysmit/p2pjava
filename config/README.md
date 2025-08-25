# Configuration Files

This directory contains configuration files for testing and demonstrating the P2P Java system.

## Configuration Files

### `test-distributed.properties`
Configuration for testing **distributed service discovery** with gossip protocol.

**Key Features:**
- Enables distributed service registry (`serviceRegistry.distributed.enabled=true`)
- Configures gossip protocol parameters (port, interval, fanout, TTL)
- Sets up peer configuration for distributed testing
- Includes fallback tracker and index server settings

**Usage:**
```bash
# Use with system property
java -Dconfig.file=config/test-distributed.properties ...

# Or with ConfigurationManager
ConfigurationManager.getInstance().initialize(args);
```

### `test-p2p-harness.properties`
Configuration for the **P2P test harness** with centralized discovery.

**Key Features:**
- Uses centralized service discovery (`serviceRegistry.distributed.enabled=false`)
- Simplified configuration for basic testing
- Fallback to tracker and index server
- Minimal test setup

**Usage:**
```bash
# Use with test harness
java -Dconfig.file=config/test-p2p-harness.properties dev.mars.p2pjava.P2PTestHarness
```

## Configuration Loading Priority

The system loads configuration in the following order (highest to lowest priority):

1. **System Properties** - `-Dproperty=value`
2. **Environment Variables** - `P2P_PROPERTY=value`
3. **Custom Config File** - `-Dconfig.file=path/to/config.properties`
4. **Default Config Paths**:
   - `config/application.properties`
   - `application.properties`
   - `config.properties`

## Common Configuration Properties

### Service Registry
```properties
# Distributed mode
serviceRegistry.distributed.enabled=true
serviceRegistry.distributed.gossipPort=6003
serviceRegistry.distributed.gossipIntervalMs=5000
serviceRegistry.distributed.fanout=3
serviceRegistry.distributed.messageTtlMs=30000
serviceRegistry.distributed.bootstrapPeers=localhost:6003,localhost:6004
serviceRegistry.distributed.conflictResolution=TIMESTAMP
```

### Peer Configuration
```properties
peer.id=test-peer-1
peer.socketTimeoutMs=30000
peer.heartbeat.intervalSeconds=30
peer.heartbeat.enabled=true
```

### Tracker/Index Server (Fallback)
```properties
tracker.host=localhost
tracker.port=6000
indexserver.host=localhost
indexserver.port=6001
```

### Health Check
```properties
healthcheck.enabled=true
healthcheck.port=8080
```

## Creating Custom Configurations

To create your own configuration file:

1. Copy one of the existing files as a template
2. Modify the properties as needed
3. Use with `-Dconfig.file=path/to/your/config.properties`

## Examples

### Running with Distributed Configuration
```bash
mvn exec:java -Dconfig.file=config/test-distributed.properties \
  -Dexec.mainClass=dev.mars.p2pjava.DistributedDiscoveryExample \
  -pl p2p-client
```

### Running with Test Harness Configuration
```bash
mvn exec:java -Dconfig.file=config/test-p2p-harness.properties \
  -Dexec.mainClass=dev.mars.p2pjava.P2PTestHarness \
  -pl p2p-client
```

## Related Documentation

- [Quick Start Guide](../docs/QUICK_START_GUIDE.md)
- [Technical Architecture Guide](../docs/TECHNICAL_ARCHITECTURE_GUIDE.md)
- [Bootstrap Guide](../docs/BOOTSTRAP_GUIDE.md)
