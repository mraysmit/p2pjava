package dev.mars.p2pjava.discovery;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a service instance in the service registry.
 */
public class ServiceInstance {
    private final String serviceType;
    private final String serviceId;
    private final String host;
    private final int port;
    private final Map<String, String> metadata;
    private boolean healthy;
    private long lastUpdated;

    // Version information for distributed registry conflict resolution
    private long version;
    private String originPeerId;

    /**
     * Creates a new service instance.
     *
     * @param serviceType The type of service (e.g., "tracker", "indexserver")
     * @param serviceId A unique identifier for this service instance
     * @param host The hostname or IP address of the service
     * @param port The port number of the service
     * @param metadata Additional metadata about the service (optional)
     */
    public ServiceInstance(String serviceType, String serviceId, String host, int port, Map<String, String> metadata) {
        this.serviceType = serviceType;
        this.serviceId = serviceId;
        this.host = host;
        this.port = port;
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        this.healthy = true;
        this.lastUpdated = System.currentTimeMillis();
        this.version = System.currentTimeMillis(); // Use timestamp as initial version
        this.originPeerId = "local"; // Default origin peer
    }

    /**
     * Creates a new service instance with version information for distributed registry.
     *
     * @param serviceType The type of service
     * @param serviceId A unique identifier for this service instance
     * @param host The hostname or IP address of the service
     * @param port The port number of the service
     * @param metadata Additional metadata about the service (optional)
     * @param version Version number for conflict resolution
     * @param originPeerId ID of the peer that originally registered this service
     */
    public ServiceInstance(String serviceType, String serviceId, String host, int port,
                          Map<String, String> metadata, long version, String originPeerId) {
        this.serviceType = serviceType;
        this.serviceId = serviceId;
        this.host = host;
        this.port = port;
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        this.healthy = true;
        this.lastUpdated = System.currentTimeMillis();
        this.version = version;
        this.originPeerId = originPeerId != null ? originPeerId : "unknown";
    }

    /**
     * Creates a new service instance without metadata.
     *
     * @param serviceType The type of service
     * @param serviceId A unique identifier for this service instance
     * @param host The hostname or IP address of the service
     * @param port The port number of the service
     */
    public ServiceInstance(String serviceType, String serviceId, String host, int port) {
        this(serviceType, serviceId, host, port, null);
    }

    public String getServiceType() {
        return serviceType;
    }

    public String getServiceId() {
        return serviceId;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public Map<String, String> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    public boolean isHealthy() {
        return healthy;
    }

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
        this.lastUpdated = System.currentTimeMillis();
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void updateMetadata(String key, String value) {
        metadata.put(key, value);
        this.lastUpdated = System.currentTimeMillis();
        this.version = System.currentTimeMillis(); // Increment version on update
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
        this.lastUpdated = System.currentTimeMillis();
    }

    public String getOriginPeerId() {
        return originPeerId;
    }

    public void setOriginPeerId(String originPeerId) {
        this.originPeerId = originPeerId != null ? originPeerId : "unknown";
    }

    /**
     * Creates a copy of this service instance with updated version information.
     * Used for distributed registry operations.
     */
    public ServiceInstance withVersion(long newVersion, String newOriginPeerId) {
        return new ServiceInstance(serviceType, serviceId, host, port,
                                 new HashMap<>(metadata), newVersion, newOriginPeerId);
    }

    /**
     * Determines if this service instance is newer than another based on version.
     * Used for conflict resolution in distributed registry.
     */
    public boolean isNewerThan(ServiceInstance other) {
        if (other == null) return true;
        return this.version > other.version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceInstance that = (ServiceInstance) o;
        return port == that.port &&
                Objects.equals(serviceType, that.serviceType) &&
                Objects.equals(serviceId, that.serviceId) &&
                Objects.equals(host, that.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceType, serviceId, host, port);
    }

    @Override
    public String toString() {
        return "ServiceInstance{" +
                "serviceType='" + serviceType + '\'' +
                ", serviceId='" + serviceId + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", healthy=" + healthy +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}