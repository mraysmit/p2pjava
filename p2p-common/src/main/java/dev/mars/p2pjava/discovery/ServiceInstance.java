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