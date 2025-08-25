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

module dev.mars.p2ptracker {
    // Export the package containing the Tracker and TrackerHandler classes
    exports dev.mars.p2pjava;

    // Require the java.logging module as specified in the issue description
    requires java.logging;

    // Require other Java modules used by the p2p-tracker module
    requires java.base; // This is implicit, but included for clarity

    requires java.net.http; // For HTTP client functionality

    // Require the p2p-common-api module for PeerInfo
    requires p2p.common.api;

    // Require the p2p-discovery module
    requires p2p.discovery;

    // Require the p2p-util module for ThreadManager
    requires p2p.util;
}
