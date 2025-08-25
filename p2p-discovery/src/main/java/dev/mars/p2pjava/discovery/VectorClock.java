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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vector clock implementation for tracking causality in distributed service registry.
 * Provides happens-before relationships between events across different peers.
 */
public class VectorClock {
    
    @JsonProperty("clocks")
    private final Map<String, Long> clocks;

    public VectorClock() {
        this.clocks = new ConcurrentHashMap<>();
    }
    
    /**
     * Creates a vector clock from existing clock map.
     */
    @JsonCreator
    public VectorClock(@JsonProperty("clocks") Map<String, Long> clocks) {
        this.clocks = new ConcurrentHashMap<>(clocks != null ? clocks : Collections.emptyMap());
    }
    
    /**
     * Creates a copy of another vector clock.
     */
    public VectorClock(VectorClock other) {
        this.clocks = new ConcurrentHashMap<>(other.clocks);
    }
    
    /**
     * Increments the clock for the specified peer id.
     */
    public synchronized VectorClock increment(String peerId) {
        VectorClock newClock = new VectorClock(this);
        newClock.clocks.put(peerId, newClock.clocks.getOrDefault(peerId, 0L) + 1);
        return newClock;
    }
    
    /**
     * Updates this clock with/from another clock (merge operation).
     */
    public synchronized VectorClock merge(VectorClock other) {
        VectorClock newClock = new VectorClock();
        
        // Get all peer IDs from both clocks
        Set<String> allPeers = new HashSet<>(this.clocks.keySet());
        allPeers.addAll(other.clocks.keySet());
        
        // For each peer, take the maximum value
        for (String peerId : allPeers) {
            long thisValue = this.clocks.getOrDefault(peerId, 0L);
            long otherValue = other.clocks.getOrDefault(peerId, 0L);
            newClock.clocks.put(peerId, Math.max(thisValue, otherValue));
        }
        
        return newClock;
    }
    
    /**
     * Checks if this clock happens before another clock.
     */
    public boolean isBefore(VectorClock other) {
        if (other == null) return false;
        
        boolean hasSmaller = false;
        
        // Check all peers in both clocks
        Set<String> allPeers = new HashSet<>(this.clocks.keySet());
        allPeers.addAll(other.clocks.keySet());
        
        for (String peerId : allPeers) {
            long thisValue = this.clocks.getOrDefault(peerId, 0L);
            long otherValue = other.clocks.getOrDefault(peerId, 0L);
            
            if (thisValue > otherValue) {
                return false; // This clock is not before other
            }
            if (thisValue < otherValue) {
                hasSmaller = true;
            }
        }
        
        return hasSmaller;
    }
    
    /**
     * Checks if this clock happens after another clock.
     */
    public boolean isAfter(VectorClock other) {
        return other != null && other.isBefore(this);
    }
    
    /**
     * Checks if this clock is concurrent with another clock.
     */
    public boolean isConcurrent(VectorClock other) {
        return other != null && !this.isBefore(other) && !this.isAfter(other) && !this.equals(other);
    }
    
    /**
     * Gets the clock value for a specific peer.
     */
    public long getClock(String peerId) {
        return clocks.getOrDefault(peerId, 0L);
    }
    
    /**
     * Gets all peer IDs in this vector clock.
     */
    public Set<String> getPeerIds() {
        return new HashSet<>(clocks.keySet());
    }
    
    /**
     * Gets a copy of the internal clock map.
     */
    public Map<String, Long> getClocks() {
        return new HashMap<>(clocks);
    }
    
    /**
     * Checks if this vector clock is empty.
     */
    public boolean isEmpty() {
        return clocks.isEmpty() || clocks.values().stream().allMatch(v -> v == 0);
    }
    
    /**
     * Creates a string representation of the vector clock.
     */
    @Override
    public String toString() {
        if (clocks.isEmpty()) {
            return "VectorClock{}";
        }
        
        StringBuilder sb = new StringBuilder("VectorClock{");
        boolean first = true;
        
        // Sort by peer ID for consistent output
        List<String> sortedPeers = new ArrayList<>(clocks.keySet());
        sortedPeers.sort(String::compareTo);
        
        for (String peerId : sortedPeers) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(peerId).append(":").append(clocks.get(peerId));
            first = false;
        }
        
        sb.append("}");
        return sb.toString();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        VectorClock other = (VectorClock) obj;
        
        // Get all peer IDs from both clocks
        Set<String> allPeers = new HashSet<>(this.clocks.keySet());
        allPeers.addAll(other.clocks.keySet());
        
        // Compare values for all peers
        for (String peerId : allPeers) {
            long thisValue = this.clocks.getOrDefault(peerId, 0L);
            long otherValue = other.clocks.getOrDefault(peerId, 0L);
            if (thisValue != otherValue) {
                return false;
            }
        }
        
        return true;
    }
    
    @Override
    public int hashCode() {
        // Create a normalized map for hashing (exclude zero values)
        Map<String, Long> normalized = new HashMap<>();
        for (Map.Entry<String, Long> entry : clocks.entrySet()) {
            if (entry.getValue() > 0) {
                normalized.put(entry.getKey(), entry.getValue());
            }
        }
        return Objects.hash(normalized);
    }
    
    /**
     * Creates a new vector clock with a single peer initialized to 1.
     */
    public static VectorClock create(String peerId) {
        VectorClock clock = new VectorClock();
        clock.clocks.put(peerId, 1L);
        return clock;
    }
    
    /**
     * Creates an empty vector clock.
     */
    public static VectorClock empty() {
        return new VectorClock();
    }
}
