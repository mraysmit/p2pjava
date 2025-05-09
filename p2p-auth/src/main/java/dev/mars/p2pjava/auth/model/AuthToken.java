package dev.mars.p2pjava.auth.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents an authentication token in the P2P system.
 * Tokens are used for authenticating users after initial login.
 */
public class AuthToken {
    private final String tokenId;
    private final String userId;
    private final Instant createdAt;
    private final Instant expiresAt;
    private boolean revoked;

    /**
     * Creates a new authentication token for the specified user with the given expiration time.
     *
     * @param userId    The ID of the user the token is for
     * @param expiresAt The time when the token expires
     */
    public AuthToken(String userId, Instant expiresAt) {
        this.tokenId = UUID.randomUUID().toString();
        this.userId = userId;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
        this.revoked = false;
    }

    /**
     * Gets the token ID.
     *
     * @return The token ID
     */
    public String getTokenId() {
        return tokenId;
    }

    /**
     * Gets the user ID.
     *
     * @return The user ID
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Gets the creation time of the token.
     *
     * @return The creation time
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Gets the expiration time of the token.
     *
     * @return The expiration time
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * Checks if the token is revoked.
     *
     * @return true if the token is revoked, false otherwise
     */
    public boolean isRevoked() {
        return revoked;
    }

    /**
     * Revokes the token.
     */
    public void revoke() {
        this.revoked = true;
    }

    /**
     * Checks if the token is valid (not expired and not revoked).
     *
     * @return true if the token is valid, false otherwise
     */
    public boolean isValid() {
        return !revoked && Instant.now().isBefore(expiresAt);
    }

    /**
     * Checks if the token is expired.
     *
     * @return true if the token is expired, false otherwise
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuthToken authToken = (AuthToken) o;
        return tokenId.equals(authToken.tokenId);
    }

    @Override
    public int hashCode() {
        return tokenId.hashCode();
    }

    @Override
    public String toString() {
        return "AuthToken{" +
                "tokenId='" + tokenId + '\'' +
                ", userId='" + userId + '\'' +
                ", createdAt=" + createdAt +
                ", expiresAt=" + expiresAt +
                ", revoked=" + revoked +
                '}';
    }
}