# P2P Java - Distributed Peer-to-Peer System

[![Java](https://img.shields.io/badge/Java-23-orange.svg)](https://openjdk.java.net/projects/jdk/23/)
[![Maven](https://img.shields.io/badge/Maven-3.8+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A comprehensive, enterprise-grade peer-to-peer distributed system built with modern Java architecture. Features modular design, distributed service discovery, circuit breaker patterns, and comprehensive monitoring capabilities.

## 🚀 Quick Start

### Prerequisites

- **Java 23** or higher
- **Maven 3.8** or higher
- **4GB RAM** minimum
- Network connectivity for distributed features

### Installation

```bash
# Clone the repository
git clone <repository-url>
cd p2pjava

# Build all modules
mvn clean install

# Verify installation
mvn test -pl p2p-tracker
```

### Running the System

```bash
# Start distributed example (Windows)
run-distributed-example.bat

# Start distributed example (Linux/Mac)
./run-distributed-example.sh
```

## 📋 System Architecture

The system is organized into **16 Maven modules** across four architectural layers:

### Foundation Layer
- **p2p-common-api** - Shared contracts, data models, and messaging protocols
- **p2p-config** - Configuration management with multi-source support
- **p2p-util** - Utility functions, thread management, and error handling

### Infrastructure Layer
- **p2p-bootstrap** - Component lifecycle and dependency management
- **p2p-health** - Health monitoring and status reporting
- **p2p-monitoring** - System metrics and performance monitoring

### Core Services Layer
- **p2p-discovery** - Service discovery (centralized and distributed)
- **p2p-storage** - File storage and indexing capabilities
- **p2p-connection** - Connection management and networking
- **p2p-cache** - Distributed caching layer
- **p2p-circuit** - Circuit breaker pattern implementation
- **p2p-auth** - Authentication and authorization

### Application Layer
- **p2p-peer** - Core peer implementation
- **p2p-tracker** - Centralized tracking service
- **p2p-client** - Client interface and API
- **p2p-indexserver** - Distributed indexing service

## 🔧 Key Features

- **Distributed Service Discovery** with gossip protocol
- **Circuit Breaker Pattern** for fault tolerance
- **Retry Mechanisms** with exponential backoff
- **Comprehensive Monitoring** and health checks
- **Modular Architecture** with dependency injection
- **Enterprise-grade Error Handling**
- **Docker Support** for containerized deployment

## 📚 Documentation

- [Quick Start Guide](docs/QUICK_START_GUIDE.md) - Get up and running quickly
- [Technical Architecture Guide](docs/TECHNICAL_ARCHITECTURE_GUIDE.md) - Deep dive into system design
- [Bootstrap Guide](docs/BOOTSTRAP_GUIDE.md) - Component lifecycle management
- [User Guide](docs/USER_GUIDE.md) - End-user documentation

## 🏗️ Building and Testing

```bash
# Build all modules
mvn clean compile

# Run all tests
mvn test

# Build with integration tests
mvn clean install -DskipTests=false

# Build specific module
mvn clean install -pl p2p-discovery
```

## 🐳 Docker Support

The system includes Docker support for key components:

```bash
# Build peer container
docker build -f p2p-peer/Dockerfile.peer -t p2p-peer .

# Build tracker container
docker build -f p2p-tracker/Dockerfile.tracker -t p2p-tracker .

# Build index server container
docker build -f p2p-indexserver/Dockerfile.index -t p2p-indexserver .
```

## 📊 Monitoring and Logging

- **Logs**: Check `logs/` directory for peer activity logs
- **Monitoring**: Built-in metrics collection and health endpoints
- **Configuration**: Customize via `test-distributed.properties` and `test-p2p-harness.properties`

## 🤝 Contributing

1. Follow the existing code style and architecture patterns
2. Add comprehensive tests for new features
3. Update documentation for significant changes
4. Use the provided scripts in `scripts/` for common tasks

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

© Mark Andrew Ray-Smith Cityline Ltd 2025.

## 🔗 Related Projects

This system implements advanced distributed systems patterns including:
- Gossip protocol for service discovery
- Anti-entropy mechanisms for eventual consistency
- Distributed registry with conflict resolution
- P2P service orchestration with circular dependency detection

---

For detailed setup instructions and advanced configuration, see the [Quick Start Guide](docs/QUICK_START_GUIDE.md).
