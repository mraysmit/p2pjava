package dev.mars.p2pjava.discovery;

/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a service instance in the service registry with enhanced versioning support.
 * Includes vector clocks for distributed conflict resolution and causality tracking.
 */
public class ServiceInstance {
    private final String serviceType;
    private final String serviceId;
    private final String host;
    private final int port;
    private final Map<String, String> metadata;
    private boolean healthy;
    private long lastUpdated;

    // Enhanced version information for distributed registry conflict resolution
    private long version;
    private String originPeerId;
    private VectorClock vectorClock;
    private Instant createdAt;
    private int priority;

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
        this.vectorClock = VectorClock.create("local");
        this.createdAt = Instant.now();
        this.priority = 0;
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
        this.vectorClock = VectorClock.create(this.originPeerId);
        this.createdAt = Instant.now();
        this.priority = 0;
    }

    /**
     * Creates a new service instance with full versioning information (JSON constructor).
     */
    @JsonCreator
    public ServiceInstance(@JsonProperty("serviceType") String serviceType,
                          @JsonProperty("serviceId") String serviceId,
                          @JsonProperty("host") String host,
                          @JsonProperty("port") int port,
                          @JsonProperty("metadata") Map<String, String> metadata,
                          @JsonProperty("version") long version,
                          @JsonProperty("originPeerId") String originPeerId,
                          @JsonProperty("vectorClock") VectorClock vectorClock,
                          @JsonProperty("createdAt") Instant createdAt,
                          @JsonProperty("priority") int priority,
                          @JsonProperty("healthy") boolean healthy,
                          @JsonProperty("lastUpdated") long lastUpdated) {
        this.serviceType = serviceType;
        this.serviceId = serviceId;
        this.host = host;
        this.port = port;
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        this.healthy = healthy;
        this.lastUpdated = lastUpdated > 0 ? lastUpdated : System.currentTimeMillis();
        this.version = version;
        this.originPeerId = originPeerId != null ? originPeerId : "unknown";
        this.vectorClock = vectorClock != null ? vectorClock : VectorClock.create(this.originPeerId);
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.priority = priority;
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

    @JsonProperty("vectorClock")
    public VectorClock getVectorClock() {
        return vectorClock;
    }

    public void setVectorClock(VectorClock vectorClock) {
        this.vectorClock = vectorClock != null ? vectorClock : VectorClock.empty();
    }

    @JsonProperty("createdAt")
    public Instant getCreatedAt() {
        return createdAt;
    }

    @JsonProperty("priority")
    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    /**
     * Creates a copy of this service instance with updated version information.
     * Used for distributed registry operations.
     */
    public ServiceInstance withVersion(long newVersion, String newOriginPeerId) {
        return new ServiceInstance(serviceType, serviceId, host, port,
                                 new HashMap<>(metadata), newVersion, newOriginPeerId,
                                 vectorClock, createdAt, priority, healthy, lastUpdated);
    }

    /**
     * Creates a copy with an incremented vector clock.
     */
    public ServiceInstance withIncrementedClock(String peerId) {
        VectorClock newClock = vectorClock.increment(peerId);
        return new ServiceInstance(serviceType, serviceId, host, port,
                                 new HashMap<>(metadata), System.currentTimeMillis(), peerId,
                                 newClock, createdAt, priority, healthy, System.currentTimeMillis());
    }

    /**
     * Determines if this service instance is newer than another based on version.
     * Used for conflict resolution in distributed registry.
     */
    public boolean isNewerThan(ServiceInstance other) {
        if (other == null) return true;
        return this.version > other.version;
    }

    /**
     * Determines causality relationship using vector clocks.
     */
    public boolean happensBefore(ServiceInstance other) {
        if (other == null || other.vectorClock == null) return false;
        return this.vectorClock.isBefore(other.vectorClock);
    }

    /**
     * Determines if this instance is concurrent with another.
     */
    public boolean isConcurrentWith(ServiceInstance other) {
        if (other == null || other.vectorClock == null) return false;
        return this.vectorClock.isConcurrent(other.vectorClock);
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