package dev.mars.p2pjava.auth.service;

import dev.mars.p2pjava.auth.model.Role;
import dev.mars.p2pjava.auth.model.User;
import dev.mars.p2pjava.auth.util.PasswordUtil;
import dev.mars.p2pjava.config.ConfigurationManager;
import dev.mars.p2pjava.util.HealthCheck;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the UserService interface.
 * This implementation stores users in memory.
 */
public class UserServiceImpl implements UserService {

    private final Map<String, User> usersById = new ConcurrentHashMap<>();
    private final Map<String, User> usersByUsername = new ConcurrentHashMap<>();
    private final HealthCheck.ServiceHealth health;
    private final ConfigurationManager config;

    /**
     * Creates a new UserServiceImpl with the specified configuration.
     *
     * @param config The configuration manager
     */
    public UserServiceImpl(ConfigurationManager config) {
        this.config = config;
        this.health = HealthCheck.registerService("UserService");
        this.health.addHealthDetail("startTime", System.currentTimeMillis());

        // Create default admin user if enabled
        if (config.getBoolean("auth.create.default.admin", true)) {
            String defaultAdminUsername = config.get("auth.default.admin.username", "admin");
            String defaultAdminPassword = config.get("auth.default.admin.password", "admin");

            if (!usersByUsername.containsKey(defaultAdminUsername)) {
                User adminUser = createUser(defaultAdminUsername, defaultAdminPassword, Role.ADMIN);
                health.addHealthDetail("defaultAdminCreated", true);
                health.addHealthDetail("defaultAdminId", adminUser.getId());
            }
        }
    }

    @Override
    public User createUser(String username, String password) {
        return createUser(username, password, Role.USER);
    }

    @Override
    public synchronized User createUser(String username, String password, Role... roles) {
        // Check if username is already taken
        if (usersByUsername.containsKey(username)) {
            throw new IllegalArgumentException("Username already taken: " + username);
        }

        // Generate salt and hash password
        String salt = PasswordUtil.generateSalt();
        String hashedPassword = PasswordUtil.hashPassword(password, salt);

        // Create user
        User user = new User(username, hashedPassword, salt);

        // Add roles
        for (Role role : roles) {
            user.addRole(role);
        }

        // Store user
        usersById.put(user.getId(), user);
        usersByUsername.put(user.getUsername(), user);

        // Update health status
        health.addHealthDetail("userCount", usersById.size());
        health.addHealthDetail("lastUserCreated", user.getId());

        return user;
    }

    @Override
    public Optional<User> getUserById(String id) {
        return Optional.ofNullable(usersById.get(id));
    }

    @Override
    public Optional<User> getUserByUsername(String username) {
        return Optional.ofNullable(usersByUsername.get(username));
    }

    @Override
    public synchronized User updateUser(User user) {
        // Check if user exists
        if (!usersById.containsKey(user.getId())) {
            throw new IllegalArgumentException("User not found: " + user.getId());
        }

        // Get old username
        String oldUsername = usersById.get(user.getId()).getUsername();

        // Update username mapping if changed
        if (!oldUsername.equals(user.getUsername())) {
            usersByUsername.remove(oldUsername);
            usersByUsername.put(user.getUsername(), user);
        }

        // Update user
        usersById.put(user.getId(), user);

        // Update health status
        health.addHealthDetail("lastUserUpdated", user.getId());

        return user;
    }

    @Override
    public synchronized boolean deleteUser(String id) {
        // Check if user exists
        User user = usersById.get(id);
        if (user == null) {
            return false;
        }

        // Remove user
        usersById.remove(id);
        usersByUsername.remove(user.getUsername());

        // Update health status
        health.addHealthDetail("userCount", usersById.size());
        health.addHealthDetail("lastUserDeleted", id);

        return true;
    }

    @Override
    public List<User> getAllUsers() {
        return new ArrayList<>(usersById.values());
    }

    @Override
    public synchronized boolean changePassword(String userId, String oldPassword, String newPassword) {
        // Check if user exists
        User user = usersById.get(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }

        // Verify old password
        if (!PasswordUtil.verifyPassword(oldPassword, user.getPasswordHash(), user.getSalt())) {
            return false;
        }

        // Generate new salt and hash new password
        String salt = PasswordUtil.generateSalt();
        String hashedPassword = PasswordUtil.hashPassword(newPassword, salt);

        // Update user
        user.setPasswordHash(hashedPassword);
        user.setSalt(salt);
        usersById.put(userId, user);

        // Update health status
        health.addHealthDetail("lastPasswordChanged", userId);

        return true;
    }

    @Override
    public synchronized User addRole(String userId, Role role) {
        // Check if user exists
        User user = usersById.get(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }

        // Add role
        user.addRole(role);

        // Update health status
        health.addHealthDetail("lastRoleAdded", role + " to " + userId);

        return user;
    }

    @Override
    public synchronized User removeRole(String userId, Role role) {
        // Check if user exists
        User user = usersById.get(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }

        // Remove role
        user.removeRole(role);

        // Update health status
        health.addHealthDetail("lastRoleRemoved", role + " from " + userId);

        return user;
    }

    @Override
    public synchronized User setEnabled(String userId, boolean enabled) {
        // Check if user exists
        User user = usersById.get(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }

        // Set enabled
        user.setEnabled(enabled);

        // Update health status
        health.addHealthDetail("lastUserEnabled", userId + " = " + enabled);

        return user;
    }

    @Override
    public synchronized Optional<User> authenticate(String username, String password) {
        // Check if user exists
        User user = usersByUsername.get(username);
        if (user == null) {
            return Optional.empty();
        }

        // Check if user is enabled
        if (!user.isEnabled()) {
            return Optional.empty();
        }

        // Verify password
        if (PasswordUtil.verifyPassword(password, user.getPasswordHash(), user.getSalt())) {
            // Update last login time
            user.setLastLogin(Instant.now());
            // Reset failed login attempts
            user.resetFailedLoginAttempts();
            // Update health status
            health.addHealthDetail("lastSuccessfulLogin", username);
            return Optional.of(user);
        } else {
            // Increment failed login attempts
            user.incrementFailedLoginAttempts();

            // Disable user if too many failed attempts
            int maxFailedAttempts = config.getInt("auth.max.failed.attempts", 5);
            if (user.getFailedLoginAttempts() >= maxFailedAttempts) {
                user.setEnabled(false);
                health.addHealthDetail("userDisabled", username);
            }

            // Update health status
            health.addHealthDetail("lastFailedLogin", username);
            return Optional.empty();
        }
    }
}
