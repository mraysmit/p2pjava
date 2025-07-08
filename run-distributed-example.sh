#!/bin/bash

# Script to run the Distributed Discovery Example
# This script demonstrates the distributed service discovery with gossip protocol

echo "Starting Distributed P2P Discovery Example"
echo "=========================================="

# Set the configuration file
CONFIG_FILE="p2p-client/src/main/resources/distributed-config.properties"

# Check if Maven is available
if ! command -v mvn &> /dev/null; then
    echo "Maven not found in PATH. Please install Maven or add it to PATH."
    exit 1
fi

# Compile the project
echo "Compiling the project..."
mvn clean compile -q
if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

echo ""
echo "Starting 3 peer instances..."
echo "Press Ctrl+C to stop all peers"
echo ""

# Function to cleanup background processes
cleanup() {
    echo ""
    echo "Stopping all peers..."
    kill $(jobs -p) 2>/dev/null
    exit 0
}

# Set trap to cleanup on script exit
trap cleanup SIGINT SIGTERM

# Start peer 1
echo "Starting Peer 1..."
mvn exec:java -Dexec.mainClass=dev.mars.p2pjava.DistributedDiscoveryExample \
    -Dexec.args="peer1 8080 file1.txt" \
    -Dconfig.file=$CONFIG_FILE \
    -pl p2p-client &

# Wait a bit before starting next peer
sleep 3

# Start peer 2
echo "Starting Peer 2..."
mvn exec:java -Dexec.mainClass=dev.mars.p2pjava.DistributedDiscoveryExample \
    -Dexec.args="peer2 8081 file2.txt" \
    -Dconfig.file=$CONFIG_FILE \
    -pl p2p-client &

# Wait a bit before starting next peer
sleep 3

# Start peer 3
echo "Starting Peer 3..."
mvn exec:java -Dexec.mainClass=dev.mars.p2pjava.DistributedDiscoveryExample \
    -Dexec.args="peer3 8082 file3.txt" \
    -Dconfig.file=$CONFIG_FILE \
    -pl p2p-client &

echo ""
echo "All peers started!"
echo ""
echo "You should see:"
echo "- Each peer registering its file-sharing service"
echo "- Gossip protocol propagating service information"
echo "- Peers discovering each other's services"
echo "- Continuous discovery updates every 30 seconds"
echo ""
echo "To test the distributed registry:"
echo "1. Watch the gossip propagation in the console output"
echo "2. Stop one peer (Ctrl+C) and observe how others detect the failure"
echo "3. Start additional peers to see them join the network"
echo ""
echo "Press Ctrl+C to stop all peers"

# Wait for all background processes
wait
