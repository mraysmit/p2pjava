package dev.mars.p2pjava.discovery;

import java.io.Serializable;
import java.util.*;

/**
 * Represents a message in the gossip protocol for distributed service registry.
 * Messages are used to propagate service registration, deregistration, and synchronization
 * information between peers in the P2P network.
 */
public class RegistryMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * Types of registry messages
     */
    public enum MessageType {
        SERVICE_REGISTER,    // Register a new service
        SERVICE_DEREGISTER,  // Deregister an existing service
        SERVICE_UPDATE,      // Update service information
        SYNC_REQUEST,        // Request full registry synchronization
        SYNC_RESPONSE,       // Response to sync request with registry data
        HEARTBEAT,          // Periodic heartbeat with service status
        ANTI_ENTROPY        // Anti-entropy reconciliation message
    }
    
    private final MessageType type;
    private final String senderId;
    private final long timestamp;
    private final String messageId;
    
    // Service-specific fields
    private ServiceInstance serviceInstance;
    private String serviceType;
    private String serviceId;
    
    // Synchronization fields
    private Map<String, Map<String, ServiceInstance>> registrySnapshot;
    private Set<String> requestedServiceTypes;
    private long syncVersion;
    
    // Gossip protocol fields
    private int hopCount;
    private Set<String> visitedPeers;
    private int maxHops;
    
    /**
     * Creates a service registration message.
     */
    public static RegistryMessage createServiceRegister(String senderId, ServiceInstance serviceInstance) {
        RegistryMessage message = new RegistryMessage(MessageType.SERVICE_REGISTER, senderId);
        message.serviceInstance = serviceInstance;
        message.serviceType = serviceInstance.getServiceType();
        message.serviceId = serviceInstance.getServiceId();
        return message;
    }
    
    /**
     * Creates a service deregistration message.
     */
    public static RegistryMessage createServiceDeregister(String senderId, String serviceType, String serviceId) {
        RegistryMessage message = new RegistryMessage(MessageType.SERVICE_DEREGISTER, senderId);
        message.serviceType = serviceType;
        message.serviceId = serviceId;
        return message;
    }
    
    /**
     * Creates a service update message.
     */
    public static RegistryMessage createServiceUpdate(String senderId, ServiceInstance serviceInstance) {
        RegistryMessage message = new RegistryMessage(MessageType.SERVICE_UPDATE, senderId);
        message.serviceInstance = serviceInstance;
        message.serviceType = serviceInstance.getServiceType();
        message.serviceId = serviceInstance.getServiceId();
        return message;
    }
    
    /**
     * Creates a synchronization request message.
     */
    public static RegistryMessage createSyncRequest(String senderId, Set<String> requestedServiceTypes) {
        RegistryMessage message = new RegistryMessage(MessageType.SYNC_REQUEST, senderId);
        message.requestedServiceTypes = requestedServiceTypes != null ? new HashSet<>(requestedServiceTypes) : null;
        return message;
    }
    
    /**
     * Creates a synchronization response message.
     */
    public static RegistryMessage createSyncResponse(String senderId,
                                                   Map<String, Map<String, ServiceInstance>> registrySnapshot,
                                                   long syncVersion) {
        RegistryMessage message = new RegistryMessage(MessageType.SYNC_RESPONSE, senderId);
        message.registrySnapshot = registrySnapshot != null ? new HashMap<>(registrySnapshot) : null;
        message.syncVersion = syncVersion;
        return message;
    }
    
    /**
     * Creates a heartbeat message.
     */
    public static RegistryMessage createHeartbeat(String senderId, ServiceInstance serviceInstance) {
        RegistryMessage message = new RegistryMessage(MessageType.HEARTBEAT, senderId);
        message.serviceInstance = serviceInstance;
        message.serviceType = serviceInstance.getServiceType();
        message.serviceId = serviceInstance.getServiceId();
        return message;
    }
    
    /**
     * Creates an anti-entropy message.
     */
    public static RegistryMessage createAntiEntropy(String senderId,
                                                  Map<String, Map<String, ServiceInstance>> registrySnapshot) {
        RegistryMessage message = new RegistryMessage(MessageType.ANTI_ENTROPY, senderId);
        message.registrySnapshot = registrySnapshot != null ? new HashMap<>(registrySnapshot) : null;
        return message;
    }
    
    /**
     * Private constructor for creating messages.
     */
    private RegistryMessage(MessageType type, String senderId) {
        this.type = type;
        this.senderId = senderId;
        this.timestamp = System.currentTimeMillis();
        this.messageId = UUID.randomUUID().toString();
        this.hopCount = 0;
        this.visitedPeers = new HashSet<>();
        this.maxHops = 5; // Default maximum hops for gossip propagation
    }
    
    // Getters
    public MessageType getType() {
        return type;
    }
    
    public String getSenderId() {
        return senderId;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public String getMessageId() {
        return messageId;
    }
    
    public ServiceInstance getServiceInstance() {
        return serviceInstance;
    }
    
    public String getServiceType() {
        return serviceType;
    }
    
    public String getServiceId() {
        return serviceId;
    }
    
    public Map<String, Map<String, ServiceInstance>> getRegistrySnapshot() {
        return registrySnapshot != null ? new HashMap<>(registrySnapshot) : null;
    }
    
    public Set<String> getRequestedServiceTypes() {
        return requestedServiceTypes != null ? new HashSet<>(requestedServiceTypes) : null;
    }
    
    public long getSyncVersion() {
        return syncVersion;
    }
    
    public int getHopCount() {
        return hopCount;
    }
    
    public Set<String> getVisitedPeers() {
        return new HashSet<>(visitedPeers);
    }
    
    public int getMaxHops() {
        return maxHops;
    }
    
    // Gossip protocol methods
    public boolean canPropagate() {
        return hopCount < maxHops;
    }
    
    public boolean hasVisited(String peerId) {
        return visitedPeers.contains(peerId);
    }
    
    public RegistryMessage incrementHop(String peerId) {
        RegistryMessage copy = this.copy();
        copy.hopCount++;
        copy.visitedPeers.add(peerId);
        return copy;
    }
    
    public void setMaxHops(int maxHops) {
        this.maxHops = Math.max(1, maxHops);
    }
    
    /**
     * Creates a copy of this message for propagation.
     */
    private RegistryMessage copy() {
        RegistryMessage copy = new RegistryMessage(this.type, this.senderId);
        copy.serviceInstance = this.serviceInstance;
        copy.serviceType = this.serviceType;
        copy.serviceId = this.serviceId;
        copy.registrySnapshot = this.registrySnapshot;
        copy.requestedServiceTypes = this.requestedServiceTypes;
        copy.syncVersion = this.syncVersion;
        copy.hopCount = this.hopCount;
        copy.visitedPeers = new HashSet<>(this.visitedPeers);
        copy.maxHops = this.maxHops;
        return copy;
    }
    
    /**
     * Checks if this message is expired based on a TTL.
     */
    public boolean isExpired(long ttlMs) {
        return System.currentTimeMillis() - timestamp > ttlMs;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegistryMessage that = (RegistryMessage) o;
        return Objects.equals(messageId, that.messageId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(messageId);
    }
    
    @Override
    public String toString() {
        return "RegistryMessage{" +
                "type=" + type +
                ", senderId='" + senderId + '\'' +
                ", messageId='" + messageId + '\'' +
                ", timestamp=" + timestamp +
                ", hopCount=" + hopCount +
                ", serviceType='" + serviceType + '\'' +
                ", serviceId='" + serviceId + '\'' +
                '}';
    }
}
