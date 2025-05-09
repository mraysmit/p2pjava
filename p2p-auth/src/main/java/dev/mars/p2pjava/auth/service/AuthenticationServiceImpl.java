package dev.mars.p2pjava.auth.service;

import dev.mars.p2pjava.auth.model.AuthToken;
import dev.mars.p2pjava.auth.model.User;
import dev.mars.p2pjava.config.ConfigurationManager;
import dev.mars.p2pjava.util.HealthCheck;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Implementation of the AuthenticationService interface.
 * This implementation stores tokens in memory and provides token-based authentication.
 */
public class AuthenticationServiceImpl implements AuthenticationService {

    private final UserService userService;
    private final ConfigurationManager config;
    private final Map<String, AuthToken> tokensById = new ConcurrentHashMap<>();
    private final Map<String, List<String>> tokensByUserId = new ConcurrentHashMap<>();
    private final HealthCheck.ServiceHealth health;
    private final ScheduledExecutorService scheduler;

    /**
     * Creates a new AuthenticationServiceImpl with the specified user service and configuration.
     *
     * @param userService The user service
     * @param config      The configuration manager
     */
    public AuthenticationServiceImpl(UserService userService, ConfigurationManager config) {
        this.userService = userService;
        this.config = config;
        this.health = HealthCheck.registerService("AuthenticationService");
        this.health.addHealthDetail("startTime", System.currentTimeMillis());
        
        // Schedule token cleanup
        this.scheduler = Executors.newScheduledThreadPool(1);
        int cleanupIntervalMinutes = config.getInt("auth.token.cleanup.interval.minutes", 60);
        scheduler.scheduleAtFixedRate(
            this::cleanupExpiredTokens,
            cleanupIntervalMinutes,
            cleanupIntervalMinutes,
            TimeUnit.MINUTES
        );
    }

    @Override
    public Optional<AuthToken> login(String username, String password) {
        // Authenticate user
        Optional<User> userOpt = userService.authenticate(username, password);
        if (userOpt.isEmpty()) {
            health.addHealthDetail("lastFailedLogin", username);
            return Optional.empty();
        }

        User user = userOpt.get();
        
        // Create token
        AuthToken token = createToken(user.getId());
        
        // Store token
        tokensById.put(token.getTokenId(), token);
        tokensByUserId.computeIfAbsent(user.getId(), k -> new ArrayList<>()).add(token.getTokenId());
        
        // Update health status
        health.addHealthDetail("lastSuccessfulLogin", username);
        health.addHealthDetail("tokenCount", tokensById.size());
        
        return Optional.of(token);
    }

    @Override
    public Optional<User> validateToken(String tokenId) {
        // Get token
        AuthToken token = tokensById.get(tokenId);
        if (token == null || !token.isValid()) {
            if (token != null && token.isExpired()) {
                health.addHealthDetail("lastExpiredToken", tokenId);
            }
            return Optional.empty();
        }
        
        // Get user
        Optional<User> userOpt = userService.getUserById(token.getUserId());
        if (userOpt.isEmpty() || !userOpt.get().isEnabled()) {
            return Optional.empty();
        }
        
        // Update health status
        health.addHealthDetail("lastValidatedToken", tokenId);
        
        return userOpt;
    }

    @Override
    public boolean revokeToken(String tokenId) {
        // Get token
        AuthToken token = tokensById.get(tokenId);
        if (token == null || token.isRevoked()) {
            return false;
        }
        
        // Revoke token
        token.revoke();
        
        // Update health status
        health.addHealthDetail("lastRevokedToken", tokenId);
        
        return true;
    }

    @Override
    public int revokeAllTokensForUser(String userId) {
        // Get tokens for user
        List<String> tokenIds = tokensByUserId.get(userId);
        if (tokenIds == null || tokenIds.isEmpty()) {
            return 0;
        }
        
        // Revoke all tokens
        int count = 0;
        for (String tokenId : tokenIds) {
            if (revokeToken(tokenId)) {
                count++;
            }
        }
        
        // Update health status
        health.addHealthDetail("lastRevokedAllTokens", userId);
        health.addHealthDetail("revokedTokenCount", count);
        
        return count;
    }

    @Override
    public List<AuthToken> getActiveTokensForUser(String userId) {
        // Get tokens for user
        List<String> tokenIds = tokensByUserId.get(userId);
        if (tokenIds == null || tokenIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Filter active tokens
        return tokenIds.stream()
                .map(tokensById::get)
                .filter(token -> token != null && token.isValid())
                .collect(Collectors.toList());
    }

    @Override
    public Optional<AuthToken> refreshToken(String tokenId) {
        // Validate token
        Optional<User> userOpt = validateToken(tokenId);
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }
        
        // Revoke old token
        revokeToken(tokenId);
        
        // Create new token
        AuthToken newToken = createToken(userOpt.get().getId());
        
        // Store new token
        tokensById.put(newToken.getTokenId(), newToken);
        tokensByUserId.computeIfAbsent(userOpt.get().getId(), k -> new ArrayList<>()).add(newToken.getTokenId());
        
        // Update health status
        health.addHealthDetail("lastRefreshedToken", tokenId);
        
        return Optional.of(newToken);
    }

    @Override
    public int cleanupExpiredTokens() {
        // Get expired tokens
        List<String> expiredTokenIds = tokensById.values().stream()
                .filter(token -> token.isExpired() || token.isRevoked())
                .map(AuthToken::getTokenId)
                .collect(Collectors.toList());
        
        // Remove expired tokens
        for (String tokenId : expiredTokenIds) {
            AuthToken token = tokensById.remove(tokenId);
            if (token != null) {
                List<String> userTokens = tokensByUserId.get(token.getUserId());
                if (userTokens != null) {
                    userTokens.remove(tokenId);
                    if (userTokens.isEmpty()) {
                        tokensByUserId.remove(token.getUserId());
                    }
                }
            }
        }
        
        // Update health status
        health.addHealthDetail("lastTokenCleanup", Instant.now().toString());
        health.addHealthDetail("expiredTokensRemoved", expiredTokenIds.size());
        health.addHealthDetail("tokenCount", tokensById.size());
        
        return expiredTokenIds.size();
    }
    
    /**
     * Creates a new authentication token for the specified user.
     *
     * @param userId The user ID
     * @return The created token
     */
    private AuthToken createToken(String userId) {
        // Get token expiration time from configuration
        int tokenExpirationMinutes = config.getInt("auth.token.expiration.minutes", 60);
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(tokenExpirationMinutes));
        
        // Create token
        return new AuthToken(userId, expiresAt);
    }
    
    /**
     * Shuts down the authentication service.
     */
    public void shutdown() {
        // Shutdown scheduler
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Deregister from health check
        HealthCheck.deregisterService("AuthenticationService");
    }
}