package dev.mars.p2pjava.discovery;

import dev.mars.p2pjava.config.PeerConfig;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Enhanced gossip protocol with adaptive fanout, priority propagation, and compression.
 * Provides more efficient service information propagation in P2P networks.
 */
public class EnhancedGossipProtocol {
    private static final Logger logger = Logger.getLogger(EnhancedGossipProtocol.class.getName());
    
    private final String peerId;
    private final int port;
    private final PeerConfig.GossipConfig config;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    // Network components
    private DatagramSocket socket;
    private ExecutorService messageProcessor;
    private ScheduledExecutorService scheduler;
    
    // Peer management
    private final Set<String> activePeers = ConcurrentHashMap.newKeySet();
    private final Map<String, PeerMetrics> peerMetrics = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSeen = new ConcurrentHashMap<>();
    
    // Message handling
    private final Map<String, Long> seenMessages = new ConcurrentHashMap<>();
    private final BlockingQueue<PriorityMessage> outgoingMessages = new PriorityBlockingQueue<>();
    private final Map<String, MessageHandler> messageHandlers = new ConcurrentHashMap<>();
    
    // Metrics
    private final AtomicLong messagesSent = new AtomicLong(0);
    private final AtomicLong messagesReceived = new AtomicLong(0);
    private final AtomicLong bytesCompressed = new AtomicLong(0);
    
    public EnhancedGossipProtocol(String peerId, PeerConfig.GossipConfig config) {
        this.peerId = peerId;
        this.port = config.getPort();
        this.config = config;
    }
    
    /**
     * Starts the enhanced gossip protocol.
     */
    public void start() throws IOException {
        if (running.getAndSet(true)) {
            logger.warning("Enhanced gossip protocol already running");
            return;
        }
        
        logger.info("Starting enhanced gossip protocol on port " + port);
        
        // Initialize network socket
        socket = new DatagramSocket(port);
        socket.setReuseAddress(true);
        
        // Initialize thread pools
        messageProcessor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "EnhancedGossip-Processor-" + peerId);
            t.setDaemon(true);
            return t;
        });
        
        scheduler = Executors.newScheduledThreadPool(3, r -> {
            Thread t = new Thread(r, "EnhancedGossip-Scheduler-" + peerId);
            t.setDaemon(true);
            return t;
        });
        
        // Start message receiver
        messageProcessor.submit(this::receiveMessages);
        
        // Start gossip sender
        scheduler.scheduleWithFixedDelay(
            this::processOutgoingMessages,
            1000, // Initial delay
            config.getIntervalMs(),
            TimeUnit.MILLISECONDS
        );
        
        // Start peer maintenance
        scheduler.scheduleWithFixedDelay(
            this::maintainPeers,
            30000, // Initial delay
            30000, // Every 30 seconds
            TimeUnit.MILLISECONDS
        );
        
        // Start message cleanup
        scheduler.scheduleWithFixedDelay(
            this::cleanupOldMessages,
            60000, // Initial delay
            60000, // Every minute
            TimeUnit.MILLISECONDS
        );
        
        logger.info("Enhanced gossip protocol started successfully");
    }
    
    /**
     * Stops the enhanced gossip protocol.
     */
    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }
        
        logger.info("Stopping enhanced gossip protocol");
        
        // Shutdown thread pools
        if (scheduler != null) {
            scheduler.shutdown();
        }
        if (messageProcessor != null) {
            messageProcessor.shutdown();
        }
        
        // Close socket
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        
        // Wait for shutdown
        try {
            if (scheduler != null && !scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            if (messageProcessor != null && !messageProcessor.awaitTermination(5, TimeUnit.SECONDS)) {
                messageProcessor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        logger.info("Enhanced gossip protocol stopped");
    }
    
    /**
     * Broadcasts a message with priority.
     */
    public void broadcast(RegistryMessage message, MessagePriority priority) {
        if (!running.get()) {
            logger.warning("Cannot broadcast: gossip protocol not running");
            return;
        }
        
        PriorityMessage priorityMessage = new PriorityMessage(message, priority);
        outgoingMessages.offer(priorityMessage);
    }
    
    /**
     * Broadcasts a message with normal priority.
     */
    public void broadcast(RegistryMessage message) {
        broadcast(message, MessagePriority.NORMAL);
    }
    
    /**
     * Registers a message handler.
     */
    public void registerMessageHandler(String messageType, MessageHandler handler) {
        messageHandlers.put(messageType, handler);
    }
    
    /**
     * Adds a peer to the active peer set.
     */
    public void addPeer(String peerAddress) {
        activePeers.add(peerAddress);
        peerMetrics.putIfAbsent(peerAddress, new PeerMetrics());
        lastSeen.put(peerAddress, System.currentTimeMillis());
        logger.fine("Added peer: " + peerAddress);
    }
    
    /**
     * Removes a peer from the active peer set.
     */
    public void removePeer(String peerAddress) {
        activePeers.remove(peerAddress);
        peerMetrics.remove(peerAddress);
        lastSeen.remove(peerAddress);
        logger.fine("Removed peer: " + peerAddress);
    }
    
    /**
     * Gets current gossip metrics.
     */
    public GossipMetrics getMetrics() {
        return new GossipMetrics(
            activePeers.size(),
            messagesSent.get(),
            messagesReceived.get(),
            bytesCompressed.get(),
            outgoingMessages.size()
        );
    }
    
    /**
     * Processes outgoing messages with adaptive fanout.
     */
    private void processOutgoingMessages() {
        List<PriorityMessage> messagesToSend = new ArrayList<>();
        outgoingMessages.drainTo(messagesToSend, config.getBatchSize());
        
        if (messagesToSend.isEmpty()) {
            return;
        }
        
        // Calculate adaptive fanout
        int fanout = calculateAdaptiveFanout();
        
        // Select target peers based on priority and metrics
        List<String> targetPeers = selectOptimalPeers(fanout);
        
        if (targetPeers.isEmpty()) {
            logger.fine("No peers available for gossip");
            return;
        }
        
        // Send messages to selected peers
        for (PriorityMessage priorityMessage : messagesToSend) {
            RegistryMessage message = priorityMessage.getMessage();
            
            // Skip if message has reached max hops
            if (!message.canPropagate()) {
                continue;
            }
            
            for (String peerAddress : targetPeers) {
                if (!message.hasVisited(peerAddress)) {
                    sendMessageToPeer(message, peerAddress);
                }
            }
        }
    }
    
    /**
     * Calculates adaptive fanout based on network conditions.
     */
    private int calculateAdaptiveFanout() {
        if (!config.isAdaptiveFanout()) {
            return config.getFanout();
        }
        
        int baseFanout = config.getFanout();
        int peerCount = activePeers.size();
        
        if (peerCount <= baseFanout) {
            return peerCount;
        }
        
        // Adjust fanout based on network size and message load
        double loadFactor = Math.min(1.0, outgoingMessages.size() / 100.0);
        double sizeFactor = Math.log(peerCount) / Math.log(10); // Logarithmic scaling
        
        int adaptiveFanout = (int) Math.ceil(baseFanout * (1 + loadFactor * 0.5) * sizeFactor);
        return Math.min(adaptiveFanout, Math.max(peerCount / 2, baseFanout));
    }
    
    /**
     * Selects optimal peers for gossip based on metrics and priority.
     */
    private List<String> selectOptimalPeers(int count) {
        List<String> peers = new ArrayList<>(activePeers);
        
        if (peers.size() <= count) {
            return peers;
        }
        
        // Sort peers by reliability and responsiveness
        peers.sort((p1, p2) -> {
            PeerMetrics m1 = peerMetrics.get(p1);
            PeerMetrics m2 = peerMetrics.get(p2);
            
            if (m1 == null && m2 == null) return 0;
            if (m1 == null) return 1;
            if (m2 == null) return -1;
            
            // Compare by success rate first, then by response time
            int reliabilityCompare = Double.compare(m2.getSuccessRate(), m1.getSuccessRate());
            if (reliabilityCompare != 0) return reliabilityCompare;
            
            return Long.compare(m1.getAverageResponseTime(), m2.getAverageResponseTime());
        });
        
        return peers.subList(0, count);
    }
    
    /**
     * Receives and processes incoming messages.
     */
    private void receiveMessages() {
        byte[] buffer = new byte[8192];

        while (running.get()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                messagesReceived.incrementAndGet();

                // Process message in separate thread
                messageProcessor.submit(() -> processIncomingMessage(packet));

            } catch (IOException e) {
                if (running.get()) {
                    logger.warning("Error receiving message: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Processes an incoming message packet.
     */
    private void processIncomingMessage(DatagramPacket packet) {
        try {
            String senderAddress = packet.getAddress().getHostAddress() + ":" + packet.getPort();

            // Update peer metrics
            updatePeerMetrics(senderAddress, true, System.currentTimeMillis());

            // Decompress if needed
            byte[] data = packet.getData();
            if (config.isCompressionEnabled() && isCompressed(data)) {
                data = decompress(data);
            }

            // Deserialize message
            RegistryMessage message = deserializeMessage(data);
            if (message == null) {
                logger.warning("Failed to deserialize message from " + senderAddress);
                return;
            }

            // Check if we've seen this message before
            if (seenMessages.containsKey(message.getMessageId())) {
                logger.fine("Ignoring duplicate message: " + message.getMessageId());
                return;
            }

            // Mark message as seen
            seenMessages.put(message.getMessageId(), System.currentTimeMillis());

            // Handle the message
            MessageHandler handler = messageHandlers.get(message.getType());
            if (handler != null) {
                handler.handleMessage(message, senderAddress);
            }

            // Propagate if needed
            if (message.canPropagate() && !message.hasVisited(peerId)) {
                RegistryMessage propagated = message.incrementHop(peerId);
                broadcast(propagated, MessagePriority.NORMAL);
            }

        } catch (Exception e) {
            logger.warning("Error processing incoming message: " + e.getMessage());
        }
    }

    /**
     * Sends a message to a specific peer.
     */
    private void sendMessageToPeer(RegistryMessage message, String peerAddress) {
        try {
            String[] parts = peerAddress.split(":");
            if (parts.length != 2) {
                logger.warning("Invalid peer address format: " + peerAddress);
                return;
            }

            InetAddress address = InetAddress.getByName(parts[0]);
            int port = Integer.parseInt(parts[1]);

            // Serialize message
            byte[] data = serializeMessage(message);

            // Compress if enabled and beneficial
            if (config.isCompressionEnabled() && data.length > 512) {
                byte[] compressed = compress(data);
                if (compressed.length < data.length * 0.9) { // Only use if 10%+ savings
                    data = compressed;
                    bytesCompressed.addAndGet(data.length);
                }
            }

            // Send packet
            DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
            socket.send(packet);

            messagesSent.incrementAndGet();
            updatePeerMetrics(peerAddress, true, System.currentTimeMillis());

        } catch (Exception e) {
            logger.warning("Failed to send message to " + peerAddress + ": " + e.getMessage());
            updatePeerMetrics(peerAddress, false, System.currentTimeMillis());
        }
    }

    /**
     * Maintains peer list by removing inactive peers.
     */
    private void maintainPeers() {
        long now = System.currentTimeMillis();
        long timeout = 5 * 60 * 1000; // 5 minutes

        Iterator<Map.Entry<String, Long>> iterator = lastSeen.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (now - entry.getValue() > timeout) {
                String peer = entry.getKey();
                removePeer(peer);
                iterator.remove();
                logger.fine("Removed inactive peer: " + peer);
            }
        }
    }

    /**
     * Cleans up old seen messages.
     */
    private void cleanupOldMessages() {
        long now = System.currentTimeMillis();
        long ttl = config.getMessageTtlMs();

        seenMessages.entrySet().removeIf(entry -> now - entry.getValue() > ttl);
    }

    /**
     * Updates metrics for a peer.
     */
    private void updatePeerMetrics(String peerAddress, boolean success, long responseTime) {
        PeerMetrics metrics = peerMetrics.computeIfAbsent(peerAddress, k -> new PeerMetrics());
        metrics.recordOperation(success, responseTime);
        lastSeen.put(peerAddress, System.currentTimeMillis());
    }

    /**
     * Compresses data using GZIP.
     */
    private byte[] compress(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
            gzos.write(data);
        }
        return baos.toByteArray();
    }

    /**
     * Decompresses GZIP data.
     */
    private byte[] decompress(byte[] compressedData) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (GZIPInputStream gzis = new GZIPInputStream(bais)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzis.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
        }

        return baos.toByteArray();
    }

    /**
     * Checks if data is compressed (simple GZIP magic number check).
     */
    private boolean isCompressed(byte[] data) {
        return data.length >= 2 &&
               (data[0] & 0xFF) == 0x1F &&
               (data[1] & 0xFF) == 0x8B;
    }

    /**
     * Serializes a registry message to bytes.
     */
    private byte[] serializeMessage(RegistryMessage message) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(message);
        }
        return baos.toByteArray();
    }

    /**
     * Deserializes bytes to a registry message.
     */
    private RegistryMessage deserializeMessage(byte[] data) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            try (ObjectInputStream ois = new ObjectInputStream(bais)) {
                return (RegistryMessage) ois.readObject();
            }
        } catch (Exception e) {
            logger.warning("Failed to deserialize message: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Message priority enumeration.
     */
    public enum MessagePriority {
        HIGH(1), NORMAL(2), LOW(3);
        
        private final int value;
        
        MessagePriority(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
    }
    
    /**
     * Priority message wrapper.
     */
    private static class PriorityMessage implements Comparable<PriorityMessage> {
        private final RegistryMessage message;
        private final MessagePriority priority;
        private final long timestamp;
        
        public PriorityMessage(RegistryMessage message, MessagePriority priority) {
            this.message = message;
            this.priority = priority;
            this.timestamp = System.currentTimeMillis();
        }
        
        public RegistryMessage getMessage() { return message; }
        public MessagePriority getPriority() { return priority; }
        
        @Override
        public int compareTo(PriorityMessage other) {
            int priorityCompare = Integer.compare(this.priority.getValue(), other.priority.getValue());
            if (priorityCompare != 0) return priorityCompare;
            return Long.compare(this.timestamp, other.timestamp);
        }
    }
}
