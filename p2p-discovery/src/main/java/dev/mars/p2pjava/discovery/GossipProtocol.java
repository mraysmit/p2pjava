package dev.mars.p2pjava.discovery;

// import dev.mars.p2pjava.util.ThreadManager; // Temporarily removed due to Java version compatibility

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * https://en.wikipedia.org/wiki/Gossip_protocol
 * Implements a gossip protocol for efficient service information propagation
 * in the distributed service registry. Uses epidemic-style communication
 * to ensure eventual consistency across all peers.
 */
public class GossipProtocol {
    private static final Logger logger = Logger.getLogger(GossipProtocol.class.getName());
    
    // Configuration constants
    private static final int DEFAULT_GOSSIP_PORT = 6003;
    private static final int DEFAULT_GOSSIP_INTERVAL_MS = 5000;
    private static final int DEFAULT_FANOUT = 3;
    private static final int DEFAULT_MESSAGE_TTL_MS = 30000;
    private static final int SOCKET_TIMEOUT_MS = 5000;
    
    private final String peerId;
    private final int gossipPort;
    private final int gossipIntervalMs;
    private final int fanout;
    private final int messageTtlMs;
    
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong messageCounter = new AtomicLong(0);
    
    // Network components
    private ServerSocket serverSocket;
    private ExecutorService gossipExecutor;
    private ScheduledExecutorService scheduledExecutor;
    
    // Message handling
    private final Map<String, Long> seenMessages = new ConcurrentHashMap<>();
    private final BlockingQueue<RegistryMessage> outgoingMessages = new LinkedBlockingQueue<>();
    
    // Peer management
    private final Set<String> knownPeers = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> peerLastSeen = new ConcurrentHashMap<>();
    
    // Message handlers
    private final Map<RegistryMessage.MessageType, MessageHandler> messageHandlers = new ConcurrentHashMap<>();
    
    /**
     * Interface for handling different types of registry messages.
     */
    public interface MessageHandler {
        void handleMessage(RegistryMessage message, String senderAddress);
    }
    
    /**
     * Creates a gossip protocol instance with default configuration.
     */
    public GossipProtocol(String peerId) {
        this(peerId, DEFAULT_GOSSIP_PORT, DEFAULT_GOSSIP_INTERVAL_MS, 
             DEFAULT_FANOUT, DEFAULT_MESSAGE_TTL_MS);
    }
    
    /**
     * Creates a gossip protocol instance with custom configuration.
     */
    public GossipProtocol(String peerId, int gossipPort, int gossipIntervalMs, 
                         int fanout, int messageTtlMs) {
        this.peerId = peerId;
        this.gossipPort = gossipPort;
        this.gossipIntervalMs = gossipIntervalMs;
        this.fanout = fanout;
        this.messageTtlMs = messageTtlMs;
    }
    
    /**
     * Starts the gossip protocol.
     */
    public void start() throws IOException {
        if (running.getAndSet(true)) {
            logger.warning("Gossip protocol already running");
            return;
        }
        
        logger.info("Starting gossip protocol for peer " + peerId + " on port " + gossipPort);
        
        // Initialize thread pools
        gossipExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "Gossip-" + peerId);
            t.setDaemon(true);
            return t;
        });
        
        scheduledExecutor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "GossipScheduler-" + peerId);
            t.setDaemon(true);
            return t;
        });
        
        // Start server socket
        serverSocket = new ServerSocket(gossipPort);
        serverSocket.setSoTimeout(1000); // Short timeout for responsive shutdown
        
        // Start message processing threads
        startMessageListener();
        startGossipScheduler();
        startMessageCleanup();
        
        logger.info("Gossip protocol started successfully");
    }
    
    /**
     * Stops the gossip protocol.
     */
    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }
        
        logger.info("Stopping gossip protocol for peer " + peerId);
        
        // Close server socket
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logger.warning("Error closing server socket: " + e.getMessage());
            }
        }
        
        // Shutdown thread pools
        if (gossipExecutor != null) {
            gossipExecutor.shutdown();
            try {
                if (!gossipExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    gossipExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                gossipExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (scheduledExecutor != null) {
            scheduledExecutor.shutdown();
            try {
                if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduledExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduledExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        logger.info("Gossip protocol stopped");
    }
    
    /**
     * Broadcasts a message to the network using gossip protocol.
     */
    public void broadcast(RegistryMessage message) {
        if (!running.get()) {
            logger.warning("Cannot broadcast message: gossip protocol not running");
            return;
        }
        
        try {
            outgoingMessages.offer(message, 1, TimeUnit.SECONDS);
            messageCounter.incrementAndGet();
            logger.fine("Queued message for broadcast: " + message.getType());
        } catch (InterruptedException e) {
            logger.warning("Interrupted while queuing message for broadcast");
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Adds a known peer to the gossip network.
     */
    public void addPeer(String peerAddress) {
        knownPeers.add(peerAddress);
        peerLastSeen.put(peerAddress, System.currentTimeMillis());
        logger.fine("Added peer to gossip network: " + peerAddress);
    }
    
    /**
     * Removes a peer from the gossip network.
     */
    public void removePeer(String peerAddress) {
        knownPeers.remove(peerAddress);
        peerLastSeen.remove(peerAddress);
        logger.fine("Removed peer from gossip network: " + peerAddress);
    }
    
    /**
     * Registers a message handler for a specific message type.
     */
    public void registerMessageHandler(RegistryMessage.MessageType messageType, MessageHandler handler) {
        messageHandlers.put(messageType, handler);
        logger.fine("Registered message handler for type: " + messageType);
    }
    
    /**
     * Gets the current set of known peers.
     */
    public Set<String> getKnownPeers() {
        return new HashSet<>(knownPeers);
    }
    
    /**
     * Gets gossip protocol statistics.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("running", running.get());
        stats.put("peerId", peerId);
        stats.put("knownPeers", knownPeers.size());
        stats.put("messagesSent", messageCounter.get());
        stats.put("seenMessages", seenMessages.size());
        stats.put("outgoingQueueSize", outgoingMessages.size());
        return stats;
    }
    
    /**
     * Starts the message listener thread.
     */
    private void startMessageListener() {
        gossipExecutor.submit(() -> {
            logger.info("Starting gossip message listener");
            
            while (running.get()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    gossipExecutor.submit(() -> handleIncomingConnection(clientSocket));
                } catch (SocketTimeoutException e) {
                    // Normal timeout, continue loop
                } catch (IOException e) {
                    if (running.get()) {
                        logger.warning("Error accepting gossip connection: " + e.getMessage());
                    }
                }
            }
            
            logger.info("Gossip message listener stopped");
        });
    }
    
    /**
     * Starts the gossip scheduler for periodic message propagation.
     */
    private void startGossipScheduler() {
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                processOutgoingMessages();
            } catch (Exception e) {
                logger.warning("Error in gossip scheduler: " + e.getMessage());
            }
        }, gossipIntervalMs, gossipIntervalMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Starts the message cleanup scheduler.
     */
    private void startMessageCleanup() {
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                cleanupExpiredMessages();
            } catch (Exception e) {
                logger.warning("Error in message cleanup: " + e.getMessage());
            }
        }, messageTtlMs, messageTtlMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Handles incoming gossip connections.
     */
    private void handleIncomingConnection(Socket clientSocket) {
        try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream())) {
            clientSocket.setSoTimeout(SOCKET_TIMEOUT_MS);

            RegistryMessage message = (RegistryMessage) in.readObject();
            String senderAddress = clientSocket.getInetAddress().getHostAddress();

            logger.fine("Received gossip message from " + senderAddress + ": " + message.getType());

            // Update peer information
            addPeer(senderAddress);

            // Process the message
            processIncomingMessage(message, senderAddress);

        } catch (IOException | ClassNotFoundException e) {
            logger.warning("Error handling incoming gossip connection: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                logger.fine("Error closing client socket: " + e.getMessage());
            }
        }
    }

    /**
     * Processes incoming gossip messages.
     */
    private void processIncomingMessage(RegistryMessage message, String senderAddress) {
        // Check if message is expired
        if (message.isExpired(messageTtlMs)) {
            logger.fine("Ignoring expired message: " + message.getMessageId());
            return;
        }

        // Check if we've already seen this message
        if (seenMessages.containsKey(message.getMessageId())) {
            logger.fine("Ignoring duplicate message: " + message.getMessageId());
            return;
        }

        // Mark message as seen
        seenMessages.put(message.getMessageId(), System.currentTimeMillis());

        // Handle the message
        MessageHandler handler = messageHandlers.get(message.getType());
        if (handler != null) {
            try {
                handler.handleMessage(message, senderAddress);
            } catch (Exception e) {
                logger.warning("Error handling message " + message.getMessageId() + ": " + e.getMessage());
            }
        } else {
            logger.fine("No handler registered for message type: " + message.getType());
        }

        // Propagate the message if it hasn't reached max hops
        if (message.canPropagate() && !message.hasVisited(peerId)) {
            RegistryMessage propagatedMessage = message.incrementHop(peerId);
            outgoingMessages.offer(propagatedMessage);
        }
    }

    /**
     * Processes outgoing messages for gossip propagation.
     */
    private void processOutgoingMessages() {
        List<RegistryMessage> messagesToSend = new ArrayList<>();
        outgoingMessages.drainTo(messagesToSend, fanout * 10); // Process multiple messages at once

        if (messagesToSend.isEmpty()) {
            return;
        }

        // Get random subset of peers for gossip
        List<String> targetPeers = selectGossipTargets();

        if (targetPeers.isEmpty()) {
            logger.fine("No peers available for gossip");
            return;
        }

        // Send messages to selected peers
        for (RegistryMessage message : messagesToSend) {
            for (String peerAddress : targetPeers) {
                if (!message.hasVisited(peerAddress)) {
                    sendMessageToPeer(message, peerAddress);
                }
            }
        }
    }

    /**
     * Selects random peers for gossip propagation.
     */
    private List<String> selectGossipTargets() {
        List<String> availablePeers = new ArrayList<>(knownPeers);
        Collections.shuffle(availablePeers);

        int targetCount = Math.min(fanout, availablePeers.size());
        return availablePeers.subList(0, targetCount);
    }

    /**
     * Sends a message to a specific peer.
     */
    private void sendMessageToPeer(RegistryMessage message, String peerAddress) {
        gossipExecutor.submit(() -> {
            try {
                String[] parts = peerAddress.split(":");
                String host = parts[0];
                int port = parts.length > 1 ? Integer.parseInt(parts[1]) : gossipPort;

                try (Socket socket = new Socket(host, port);
                     ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {

                    socket.setSoTimeout(SOCKET_TIMEOUT_MS);
                    out.writeObject(message);
                    out.flush();

                    logger.fine("Sent gossip message to " + peerAddress + ": " + message.getType());

                } catch (IOException e) {
                    logger.fine("Failed to send gossip message to " + peerAddress + ": " + e.getMessage());
                    // Remove unresponsive peer
                    removePeer(peerAddress);
                }
            } catch (Exception e) {
                logger.warning("Error sending message to peer " + peerAddress + ": " + e.getMessage());
            }
        });
    }

    /**
     * Cleans up expired messages from the seen messages cache.
     */
    private void cleanupExpiredMessages() {
        long currentTime = System.currentTimeMillis();
        seenMessages.entrySet().removeIf(entry ->
            currentTime - entry.getValue() > messageTtlMs);

        logger.fine("Cleaned up expired messages, remaining: " + seenMessages.size());
    }
}
