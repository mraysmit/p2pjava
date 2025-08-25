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


/**
 * Configuration class for gossip protocol settings.
 */
public class GossipConfig {
    private int port = 6003;
    private long intervalMs = 5000;
    private int fanout = 3;
    private long messageTtlMs = 30000;
    private boolean adaptiveFanout = false;
    private boolean priorityPropagation = false;
    private boolean compressionEnabled = false;

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    public void setIntervalMs(long intervalMs) {
        this.intervalMs = intervalMs;
    }

    public int getFanout() {
        return fanout;
    }

    public void setFanout(int fanout) {
        this.fanout = fanout;
    }

    public long getMessageTtlMs() {
        return messageTtlMs;
    }

    public void setMessageTtlMs(long messageTtlMs) {
        this.messageTtlMs = messageTtlMs;
    }

    public boolean isAdaptiveFanout() {
        return adaptiveFanout;
    }

    public void setAdaptiveFanout(boolean adaptiveFanout) {
        this.adaptiveFanout = adaptiveFanout;
    }

    public boolean isPriorityPropagation() {
        return priorityPropagation;
    }

    public void setPriorityPropagation(boolean priorityPropagation) {
        this.priorityPropagation = priorityPropagation;
    }

    public boolean isCompressionEnabled() {
        return compressionEnabled;
    }

    public void setCompressionEnabled(boolean compressionEnabled) {
        this.compressionEnabled = compressionEnabled;
    }
}
