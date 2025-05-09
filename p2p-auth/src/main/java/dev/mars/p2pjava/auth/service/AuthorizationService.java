package dev.mars.p2pjava.auth.service;

import dev.mars.p2pjava.auth.model.Role;
import dev.mars.p2pjava.auth.model.User;

import java.util.Optional;

/**
 * Service interface for authorization in the P2P system.
 * This service handles authorization checks for critical operations.
 */
public interface AuthorizationService {

    /**
     * Checks if a user has the required role to perform an operation.
     *
     * @param user The user to check
     * @param requiredRole The required role
     * @return true if the user has the required role, false otherwise
     */
    boolean hasRole(User user, Role requiredRole);

    /**
     * Checks if a user has the required role to perform an operation.
     *
     * @param userId The ID of the user to check
     * @param requiredRole The required role
     * @return true if the user has the required role, false otherwise
     */
    boolean hasRoleByUserId(String userId, Role requiredRole);

    /**
     * Checks if a token has the required role to perform an operation.
     *
     * @param tokenId The token ID
     * @param requiredRole The required role
     * @return true if the token's user has the required role, false otherwise
     */
    boolean hasRoleByTokenId(String tokenId, Role requiredRole);

    /**
     * Authorizes a user to perform an operation that requires a specific role.
     *
     * @param user The user to authorize
     * @param requiredRole The required role
     * @return An Optional containing the authorized user, or empty if authorization failed
     */
    Optional<User> authorize(User user, Role requiredRole);

    /**
     * Authorizes a user to perform an operation that requires a specific role.
     *
     * @param userId The ID of the user to authorize
     * @param requiredRole The required role
     * @return An Optional containing the authorized user, or empty if authorization failed
     */
    Optional<User> authorizeByUserId(String userId, Role requiredRole);

    /**
     * Authorizes a token to perform an operation that requires a specific role.
     *
     * @param tokenId The token ID
     * @param requiredRole The required role
     * @return An Optional containing the authorized user, or empty if authorization failed
     */
    Optional<User> authorizeByTokenId(String tokenId, Role requiredRole);
}
