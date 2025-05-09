package dev.mars.p2pjava.auth.service;

import dev.mars.p2pjava.auth.model.Role;
import dev.mars.p2pjava.auth.model.User;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing users in the P2P system.
 */
public interface UserService {

    /**
     * Creates a new user with the specified username and password.
     *
     * @param username The username
     * @param password The password (plain text)
     * @return The created user
     * @throws IllegalArgumentException if the username is already taken
     */
    User createUser(String username, String password);

    /**
     * Creates a new user with the specified username, password, and roles.
     *
     * @param username The username
     * @param password The password (plain text)
     * @param roles    The roles to assign to the user
     * @return The created user
     * @throws IllegalArgumentException if the username is already taken
     */
    User createUser(String username, String password, Role... roles);

    /**
     * Gets a user by ID.
     *
     * @param id The user ID
     * @return An Optional containing the user, or empty if not found
     */
    Optional<User> getUserById(String id);

    /**
     * Gets a user by username.
     *
     * @param username The username
     * @return An Optional containing the user, or empty if not found
     */
    Optional<User> getUserByUsername(String username);

    /**
     * Updates a user.
     *
     * @param user The user to update
     * @return The updated user
     * @throws IllegalArgumentException if the user does not exist
     */
    User updateUser(User user);

    /**
     * Deletes a user.
     *
     * @param id The user ID
     * @return true if the user was deleted, false if the user was not found
     */
    boolean deleteUser(String id);

    /**
     * Gets all users.
     *
     * @return A list of all users
     */
    List<User> getAllUsers();

    /**
     * Changes a user's password.
     *
     * @param userId      The user ID
     * @param oldPassword The old password (plain text)
     * @param newPassword The new password (plain text)
     * @return true if the password was changed, false if the old password was incorrect
     * @throws IllegalArgumentException if the user does not exist
     */
    boolean changePassword(String userId, String oldPassword, String newPassword);

    /**
     * Adds a role to a user.
     *
     * @param userId The user ID
     * @param role   The role to add
     * @return The updated user
     * @throws IllegalArgumentException if the user does not exist
     */
    User addRole(String userId, Role role);

    /**
     * Removes a role from a user.
     *
     * @param userId The user ID
     * @param role   The role to remove
     * @return The updated user
     * @throws IllegalArgumentException if the user does not exist
     */
    User removeRole(String userId, Role role);

    /**
     * Enables or disables a user.
     *
     * @param userId  The user ID
     * @param enabled true to enable the user, false to disable
     * @return The updated user
     * @throws IllegalArgumentException if the user does not exist
     */
    User setEnabled(String userId, boolean enabled);

    /**
     * Authenticates a user with the specified username and password.
     *
     * @param username The username
     * @param password The password (plain text)
     * @return An Optional containing the authenticated user, or empty if authentication failed
     */
    Optional<User> authenticate(String username, String password);
}