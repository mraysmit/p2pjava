
# P2PClient and P2PTestHarness Documentation

## P2PClient

### Overview
The `P2PClient` class serves as the client interface for the P2P file sharing system. It provides methods for interacting with the P2P network, including discovering peers with specific files and downloading files from peers.

### Key Features
- **Modular Architecture**: Leverages components from various modules like config, common-api, and util
- **Health Monitoring**: Integrates with the health check system for monitoring client status
- **Configurable**: Uses the ConfigurationManager for centralized configuration
- **Retry Mechanism**: Implements retry logic for network operations
- **Error Handling**: Comprehensive error handling and reporting

### Main Methods
1. **discoverPeersWithFile(String fileName)**: Queries the index server to find peers that have a specific file
2. **downloadFileFromPeer(String fileName, String downloadDir, PeerInfo peer)**: Downloads a file from a specified peer
3. **registerFileWithIndexServer(String fileName, String peerId, int port)**: Registers a file with the index server
4. **verifyPeerIsListening(PeerInfo peer)**: Verifies that a peer is listening on its port
5. **isPeerRunning(String peerId)**: Checks if a peer is running by querying the tracker
6. **shutdown()**: Cleans up resources when the client is no longer needed

### Interaction with Other Modules
- **config**: Uses ConfigurationManager for configuration values
- **common-api**: Uses PeerInfo for peer information
- **util**: Uses HealthCheck for health monitoring and ChecksumUtil for file integrity verification

## P2PTestHarness

### Overview
The `P2PTestHarness` class serves as a comprehensive integration test harness for the P2P file sharing system. It demonstrates and validates the interaction between all components of the system, providing a controlled environment for testing end-to-end functionality.

### Purpose
- Validate the correct interaction between all system components
- Demonstrate the complete workflow of the P2P file sharing system
- Test file discovery and download functionality in a multi-peer environment
- Verify system resilience and proper cleanup procedures

### Components Tested
- **Tracker**: Peer registration and discovery service
- **IndexServer**: File indexing and lookup service
- **Peers**: File sharing nodes that register with the Tracker and IndexServer
- **P2PClient**: Client component that interacts with the P2P network

### Test Workflow
1. Creates test directories and files for each peer with different content
2. Uses the BootstrapService to start the Tracker and IndexServer services
3. Uses CountDownLatch to ensure services are ready before peers connect
4. Starts multiple peer instances with different shared files
5. Registers the peers with the Tracker service
6. Registers the peers' files with the IndexServer
7. Verifies that peers are alive and responsive after registration
8. Tests file discovery by querying the IndexServer for specific files
9. Tests file download by transferring files between peers
10. Implements proper cleanup and shutdown procedures

### Advanced Features
- Uses ConfigurationManager for centralized configuration
- Includes health checks to verify component health during testing
- Implements graceful startup with dependency management
- Provides fallback mechanisms if the bootstrap service fails
- Tracks test failures for reporting
- Implements proper resource cleanup on shutdown

## Interaction with p2p-bootstrap Module

### BootstrapService Integration
The P2PTestHarness integrates with the p2p-bootstrap module primarily through the `BootstrapService` class. This integration enables:

1. **Centralized Component Registration**: The P2PTestHarness registers all components defined in `P2PComponent.getAllConfigs()` except for the peer component and components with no class name.

2. **Dependency Management**: It adds all dependencies from `P2PComponent.getAllDependencies()`, ensuring components start in the correct order.

3. **Service Lifecycle Management**: The bootstrap service handles starting and stopping services in the correct order based on their dependencies.

4. **Dynamic Port Allocation**: When configured, the bootstrap service can allocate dynamic ports for services.

5. **Health Monitoring**: The bootstrap service integrates with the health check system to monitor service health.

### Component Registration Process
In the `main` method of P2PTestHarness, the following steps occur:

1. A new `BootstrapService` instance is created
2. Components are registered using `bootstrapService.registerService()`
3. Dependencies are added using `bootstrapService.addDependency()`
4. The bootstrap service is started with `bootstrapService.start()`
5. CountDownLatches are used to ensure services are started before proceeding

### Dependency Chain
The P2PTestHarness relies on the dependency chain defined in P2PComponent:

- **INDEX_SERVER** depends on **TRACKER**
- **INDEX_SERVER** depends on **STORAGE**
- **INDEX_SERVER** depends on **CACHE**
- **INDEX_SERVER** depends on **CONNECTION**
- **STORAGE** depends on **DISCOVERY**

This means that when the P2PTestHarness starts the INDEX_SERVER component, the bootstrap service automatically ensures that TRACKER, STORAGE, CACHE, CONNECTION, and DISCOVERY services are started first.

### Fallback Mechanism
If the bootstrap service fails to start components, the P2PTestHarness includes a fallback mechanism:

```java
try {
    // Start bootstrap service
    if (!bootstrapService.start()) {
        throw new Exception("Failed to start components using bootstrap service");
    }
} catch (Exception e) {
    System.err.println("Failed to start components using bootstrap service: " + e.getMessage());
    System.out.println("Falling back to manual service startup...");

    // Fall back to manual service startup
    executorService.submit(() -> startTracker(TRACKER_SERVER_HOST, TRACKER_SERVER_PORT));
    executorService.submit(() -> startIndexServer(INDEX_SERVER_HOST, INDEX_SERVER_PORT));
}
```

### Cleanup Process
The P2PTestHarness ensures proper cleanup of resources through the `stopAndCleanup()` method, which:

1. Stops all active peers
2. Interrupts any remaining peer threads
3. Clears collections
4. Sets the running flag to false to stop service loops
5. Stops the bootstrap service
6. Deregisters from health check
7. Shuts down the P2PClient
8. Optionally cleans up test files

## Conclusion

The P2PClient and P2PTestHarness classes form a critical part of the P2P file sharing system. The P2PClient provides the interface for interacting with the P2P network, while the P2PTestHarness demonstrates and validates the complete system functionality.

The integration with the p2p-bootstrap module enables centralized component registration, dependency management, and service lifecycle management. This ensures that components are started and stopped in the correct order, with proper dependency resolution.

The P2PTestHarness serves as both a demonstration of the complete system functionality and a comprehensive integration test harness. It validates the correct interaction between all system components and verifies system resilience and proper cleanup procedures.