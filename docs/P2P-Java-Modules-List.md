# P2P-Java Project Modules

Below is a comprehensive list of all modules in the p2p-java project with their descriptions:

## Core Modules

1. **p2p-common-api**
   - Provides common interfaces and base classes used across the project
   - Contains shared data models and API definitions

2. **p2p-config**
   - Configuration management system
   - Loads settings from properties files, environment variables, and command-line arguments
   - Provides centralized access to configuration values

3. **p2p-bootstrap**
   - Main entry point for starting the P2P system
   - Manages component lifecycle and dependencies
   - Provides different startup modes (start all components, start specific components)
   - Handles graceful shutdown of components

4. **p2p-util**
   - Utility classes and helper functions used across the project
   - Contains common functionality like logging, error handling, etc.

## Network Components

5. **p2p-tracker**
   - Peer registration service
   - Maintains a registry of active peers in the network
   - Base port: 6000
   - Service type: "tracker"

6. **p2p-indexserver**
   - File index service
   - Maps files to peers that have them
   - Base port: 6001
   - Service type: "index server"
   - Dependencies: tracker, storage, cache, connection, auth

7. **p2p-peer**
   - Distributed peer server that shares files
   - Base port: 7000
   - Service type: "peer"
   - Dependencies: auth

8. **p2p-client**
   - Client that requests files from the P2P network
   - Provides user interface for interacting with the P2P system

## Service Modules

9. **p2p-connection**
   - Connection pool management
   - Handles network connections between components
   - Service type: "connection pool"

10. **p2p-discovery**
    - Service registry for component discovery
    - Allows components to find and communicate with each other
    - Service type: "service registry"

11. **p2p-storage**
    - File-based storage system
    - Handles persistent storage of file indexes and peer information
    - Service type: "file storage"
    - Dependencies: discovery

12. **p2p-cache**
    - Caching system for frequently accessed data
    - Improves performance by reducing redundant operations
    - Service type: "cache manager"

13. **p2p-health**
    - Health monitoring system
    - Provides HTTP endpoints for checking component health
    - Tracks the status of all system components

14. **p2p-circuit**
    - Circuit breaker implementation
    - Prevents cascading failures in the system
    - Handles service failures gracefully

15. **p2p-auth**
    - Authentication and authorization service
    - Manages user credentials and access control
    - Service type: "authentication service"

## Additional Information

The P2P system architecture is designed to be modular and scalable. Components can be started independently or together using the bootstrap system. Dependencies between components are managed automatically to ensure proper startup order.

For more detailed information about each module, refer to the documentation in the `docs` directory.