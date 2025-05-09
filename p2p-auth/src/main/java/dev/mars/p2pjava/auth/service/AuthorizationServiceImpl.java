package dev.mars.p2pjava.auth.service;

import dev.mars.p2pjava.auth.model.Role;
import dev.mars.p2pjava.auth.model.User;
import dev.mars.p2pjava.util.HealthCheck;

import java.util.Optional;

/**
 * Implementation of the AuthorizationService interface.
 * This implementation provides authorization checks for critical operations.
 */
public class AuthorizationServiceImpl implements AuthorizationService {

    private final UserService userService;
    private final AuthenticationService authenticationService;
    private final HealthCheck.ServiceHealth health;

    /**
     * Creates a new AuthorizationServiceImpl with the specified services.
     *
     * @param userService The user service
     * @param authenticationService The authentication service
     */
    public AuthorizationServiceImpl(UserService userService, AuthenticationService authenticationService) {
        this.userService = userService;
        this.authenticationService = authenticationService;
        this.health = HealthCheck.registerService("AuthorizationService");
        this.health.addHealthDetail("startTime", System.currentTimeMillis());
    }

    @Override
    public boolean hasRole(User user, Role requiredRole) {
        if (user == null || !user.isEnabled()) {
            return false;
        }
        
        boolean hasRole = user.getRoles().stream().anyMatch(role -> role.hasPrivilegeOf(requiredRole));
        
        // Update health status
        if (hasRole) {
            health.addHealthDetail("lastSuccessfulRoleCheck", user.getUsername() + " has " + requiredRole);
        } else {
            health.addHealthDetail("lastFailedRoleCheck", user.getUsername() + " lacks " + requiredRole);
        }
        
        return hasRole;
    }

    @Override
    public boolean hasRoleByUserId(String userId, Role requiredRole) {
        Optional<User> userOpt = userService.getUserById(userId);
        return userOpt.isPresent() && hasRole(userOpt.get(), requiredRole);
    }

    @Override
    public boolean hasRoleByTokenId(String tokenId, Role requiredRole) {
        Optional<User> userOpt = authenticationService.validateToken(tokenId);
        return userOpt.isPresent() && hasRole(userOpt.get(), requiredRole);
    }

    @Override
    public Optional<User> authorize(User user, Role requiredRole) {
        return hasRole(user, requiredRole) ? Optional.of(user) : Optional.empty();
    }

    @Override
    public Optional<User> authorizeByUserId(String userId, Role requiredRole) {
        Optional<User> userOpt = userService.getUserById(userId);
        return userOpt.flatMap(user -> authorize(user, requiredRole));
    }

    @Override
    public Optional<User> authorizeByTokenId(String tokenId, Role requiredRole) {
        Optional<User> userOpt = authenticationService.validateToken(tokenId);
        return userOpt.flatMap(user -> authorize(user, requiredRole));
    }
    
    /**
     * Shuts down the authorization service.
     */
    public void shutdown() {
        // Deregister from health check
        HealthCheck.deregisterService("AuthorizationService");
    }
}