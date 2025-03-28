# Simple P2P System Architecture

This P2P system consists of three main components that work together:

## Components

1. **Tracker Service (Port 6000)**
   - Maintains a registry of all active peers
   - Handles peer registration through `registerPeer()`
   - Provides list of all available peers via `getPeers()`

2. **IndexServer Service (Port 6001)**
   - Maintains a file index mapping files to the peers that have them
   - Tracks which files are available on which peers
   - Handles file registration and lookup requests

3. **Peers** (Client components)
   - Creates peer instances with the specified ID and port
   - Adds shared files to each peer
   - Registers those files with the index server
   - Starts each peer in its own thread
   - Register themselves with the Tracker
   - Register their files with the IndexServer
   - Query the IndexServer to find files
   - Connect directly to other peers to transfer files


## How They Work Together

1. **Startup Sequence**:
   - Start the Tracker service first
   - Start the IndexServer service second
   - Start peer clients last

2. **Data Flow**:
   ```
   Peer Registration:
   Peer → Tracker: "REGISTER <peerId> <port>"
   Tracker → Peer: "Peer registered: <peerInfo>"

   File Registration:
   Peer → IndexServer: "REGISTER_FILE <fileName> <peerId> <port>"
   IndexServer → Peer: "File registered: <fileName> by <peerInfo>"

   File Discovery:
   Peer → IndexServer: "GET_PEERS_WITH_FILE <fileName>"
   IndexServer → Peer: [List of PeerInfo objects]

   Peer Discovery:
   Peer → Tracker: "GET_PEERS"
   Tracker → Peer: [Map of peerId to PeerInfo]
   ```

3. **File Transfer**:
   - After discovering peers with the desired file, peers connect directly to each other
   - Peers use the `PeerHandler` to manage file transfers
   - Files are transmitted using buffered streams for efficiency

## Test Harness

The system includes a `P2PTestHarness` class that simulates a complete P2P workflow:

1. **Setup**: Creates test directories and files for each peer
2. **Service Startup**: Launches Tracker and IndexServer in separate threads
3. **Peer Initialization**: Starts multiple peers with different shared files
4. **Registration**: Registers peers with the Tracker and files with the IndexServer
5. **Discovery**: Simulates file lookup through the IndexServer
6. **Download**: Demonstrates the file transfer mechanism between peers

To run the test harness:
```
java -cp <classpath> dev.mars.p2pjava.P2PTestHarness
```

## IndexServer Features

The IndexServer currently supports:
- File registration: `registerFile(String fileName, PeerInfo peerInfo)`
- File discovery: `getPeersWithFile(String fileName)`

Planned features include:
- File deregistration
- Peer deregistration when disconnected
- File searching by partial names
- Enhanced file metadata storage
- Improved concurrency handling

## Project Structure

The project is organized as a Maven multi-module project:
- `p2p-peer`: Contains the Peer implementation
- `p2p-tracker`: Peer registration service
- `p2p-indexserver`: File index and lookup service
- `p2p-client`: Test harness and client classes
- 

