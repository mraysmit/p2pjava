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


import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a user in the P2P system.
 * This class stores user information including credentials and roles.
 */
public class User {
    private final String id;
    private String username;
    private String passwordHash;
    private String salt;
    private Set<Role> roles;
    private boolean enabled;
    private Instant createdAt;
    private Instant lastLogin;
    private int failedLoginAttempts;

    /**
     * Creates a new user with the specified username and password hash.
     *
     * @param username     The username
     * @param passwordHash The hashed password
     * @param salt         The salt used for password hashing
     */
    public User(String username, String passwordHash, String salt) {
        this.id = UUID.randomUUID().toString();
        this.username = username;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.roles = new HashSet<>();
        this.enabled = true;
        this.createdAt = Instant.now();
        this.failedLoginAttempts = 0;
    }

    /**
     * Gets the user ID.
     *
     * @return The user ID
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the username.
     *
     * @return The username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the username.
     *
     * @param username The username
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Gets the password hash.
     *
     * @return The password hash
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * Sets the password hash.
     *
     * @param passwordHash The password hash
     */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * Gets the salt used for password hashing.
     *
     * @return The salt
     */
    public String getSalt() {
        return salt;
    }

    /**
     * Sets the salt used for password hashing.
     *
     * @param salt The salt
     */
    public void setSalt(String salt) {
        this.salt = salt;
    }

    /**
     * Gets the user's roles.
     *
     * @return The roles
     */
    public Set<Role> getRoles() {
        return new HashSet<>(roles);
    }

    /**
     * Adds a role to the user.
     *
     * @param role The role to add
     */
    public void addRole(Role role) {
        this.roles.add(role);
    }

    /**
     * Removes a role from the user.
     *
     * @param role The role to remove
     */
    public void removeRole(Role role) {
        this.roles.remove(role);
    }

    /**
     * Checks if the user has a specific role.
     *
     * @param role The role to check
     * @return true if the user has the role, false otherwise
     */
    public boolean hasRole(Role role) {
        return this.roles.contains(role);
    }

    /**
     * Checks if the user is enabled.
     *
     * @return true if the user is enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether the user is enabled.
     *
     * @param enabled true to enable the user, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Gets the creation time of the user.
     *
     * @return The creation time
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Gets the last login time of the user.
     *
     * @return The last login time
     */
    public Instant getLastLogin() {
        return lastLogin;
    }

    /**
     * Sets the last login time of the user.
     *
     * @param lastLogin The last login time
     */
    public void setLastLogin(Instant lastLogin) {
        this.lastLogin = lastLogin;
    }

    /**
     * Gets the number of failed login attempts.
     *
     * @return The number of failed login attempts
     */
    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    /**
     * Increments the number of failed login attempts.
     */
    public void incrementFailedLoginAttempts() {
        this.failedLoginAttempts++;
    }

    /**
     * Resets the number of failed login attempts.
     */
    public void resetFailedLoginAttempts() {
        this.failedLoginAttempts = 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "User{" +
                "id='" + id + '\'' +
                ", username='" + username + '\'' +
                ", enabled=" + enabled +
                ", roles=" + roles +
                '}';
    }
}