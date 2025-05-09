package dev.mars.p2pjava.auth;

import dev.mars.p2pjava.auth.model.Role;
import dev.mars.p2pjava.auth.model.User;
import dev.mars.p2pjava.auth.service.AuthenticationService;
import dev.mars.p2pjava.auth.service.AuthenticationServiceImpl;
import dev.mars.p2pjava.auth.service.AuthorizationService;
import dev.mars.p2pjava.auth.service.AuthorizationServiceImpl;
import dev.mars.p2pjava.auth.service.UserService;
import dev.mars.p2pjava.auth.service.UserServiceImpl;
import dev.mars.p2pjava.config.ConfigurationManager;
import dev.mars.p2pjava.util.HealthCheck;

import java.util.Optional;

/**
 * Main service class for authentication and authorization in the P2P system.
 * This class integrates with the P2P bootstrap system and provides a facade for all auth-related operations.
 */
public class AuthService {

    private static AuthService instance;
    
    private final UserService userService;
    private final AuthenticationService authenticationService;
    private final AuthorizationService authorizationService;
    private final HealthCheck.ServiceHealth health;
    
    /**
     * Creates a new AuthService with the specified configuration.
     *
     * @param config The configuration manager
     */
    private AuthService(ConfigurationManager config) {
        // Create services
        this.userService = new UserServiceImpl(config);
        this.authenticationService = new AuthenticationServiceImpl(userService, config);
        this.authorizationService = new AuthorizationServiceImpl(userService, authenticationService);
        
        // Register with health check
        this.health = HealthCheck.registerService("AuthService");
        this.health.addHealthDetail("startTime", System.currentTimeMillis());
    }
    
    /**
     * Gets the singleton instance of the AuthService.
     *
     * @param config The configuration manager
     * @return The AuthService instance
     */
    public static synchronized AuthService getInstance(ConfigurationManager config) {
        if (instance == null) {
            instance = new AuthService(config);
        }
        return instance;
    }
    
    /**
     * Starts the auth service.
     *
     * @return true if the service was started successfully, false otherwise
     */
    public boolean start() {
        health.addHealthDetail("status", "RUNNING");
        return true;
    }
    
    /**
     * Stops the auth service.
     *
     * @return true if the service was stopped successfully, false otherwise
     */
    public boolean stop() {
        // Shutdown services
        if (authenticationService instanceof AuthenticationServiceImpl) {
            ((AuthenticationServiceImpl) authenticationService).shutdown();
        }
        
        if (authorizationService instanceof AuthorizationServiceImpl) {
            ((AuthorizationServiceImpl) authorizationService).shutdown();
        }
        
        // Deregister from health check
        HealthCheck.deregisterService("AuthService");
        
        return true;
    }
    
    /**
     * Gets the user service.
     *
     * @return The user service
     */
    public UserService getUserService() {
        return userService;
    }
    
    /**
     * Gets the authentication service.
     *
     * @return The authentication service
     */
    public AuthenticationService getAuthenticationService() {
        return authenticationService;
    }
    
    /**
     * Gets the authorization service.
     *
     * @return The authorization service
     */
    public AuthorizationService getAuthorizationService() {
        return authorizationService;
    }
    
    /**
     * Authenticates a user with the specified username and password.
     *
     * @param username The username
     * @param password The password (plain text)
     * @return An Optional containing the authentication token, or empty if authentication failed
     */
    public Optional<String> login(String username, String password) {
        return authenticationService.login(username, password)
                .map(token -> token.getTokenId());
    }
    
    /**
     * Validates an authentication token.
     *
     * @param tokenId The token ID
     * @return true if the token is valid, false otherwise
     */
    public boolean validateToken(String tokenId) {
        return authenticationService.validateToken(tokenId).isPresent();
    }
    
    /**
     * Authorizes a token to perform an operation that requires a specific role.
     *
     * @param tokenId The token ID
     * @param requiredRole The required role
     * @return true if the token's user has the required role, false otherwise
     */
    public boolean authorize(String tokenId, Role requiredRole) {
        return authorizationService.hasRoleByTokenId(tokenId, requiredRole);
    }
    
    /**
     * Creates a new user with the specified username and password.
     *
     * @param username The username
     * @param password The password (plain text)
     * @param roles    The roles to assign to the user
     * @return The created user
     * @throws IllegalArgumentException if the username is already taken
     */
    public User createUser(String username, String password, Role... roles) {
        return userService.createUser(username, password, roles);
    }
}