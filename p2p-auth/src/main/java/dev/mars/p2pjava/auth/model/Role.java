package dev.mars.p2pjava.auth.model;

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
 * Represents a role in the P2P system.
 * Roles are used to define permissions for users.
 */
public enum Role {
    /**
     * Administrator role with full access to all system functions.
     */
    ADMIN,

    /**
     * User role with standard access to the P2P system.
     */
    USER,

    /**
     * Peer role with permissions to share files.
     */
    PEER,

    /**
     * Guest role with limited access to the P2P system.
     */
    GUEST;

    /**
     * Checks if this role has higher or equal privileges than the specified role.
     *
     * @param role The role to compare with
     * @return true if this role has higher or equal privileges, false otherwise
     */
    public boolean hasPrivilegeOf(Role role) {
        if (this == ADMIN) {
            return true; // Admin has all privileges
        }
        if (this == USER) {
            return role == USER || role == PEER || role == GUEST;
        }
        if (this == PEER) {
            return role == PEER || role == GUEST;
        }
        if (this == GUEST) {
            return role == GUEST;
        }
        return false;
    }
}