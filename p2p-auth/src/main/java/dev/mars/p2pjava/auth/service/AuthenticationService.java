package dev.mars.p2pjava.auth.service;

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


import dev.mars.p2pjava.auth.model.AuthToken;
import dev.mars.p2pjava.auth.model.User;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for authentication in the P2P system.
 * This service handles user authentication and token management.
 */
public interface AuthenticationService {

    /**
     * Authenticates a user with the specified username and password.
     *
     * @param username The username
     * @param password The password (plain text)
     * @return An Optional containing the authentication token, or empty if authentication failed
     */
    Optional<AuthToken> login(String username, String password);

    /**
     * Validates an authentication token.
     *
     * @param tokenId The token ID
     * @return An Optional containing the user associated with the token, or empty if the token is invalid
     */
    Optional<User> validateToken(String tokenId);

    /**
     * Revokes an authentication token.
     *
     * @param tokenId The token ID
     * @return true if the token was revoked, false if the token was not found or already revoked
     */
    boolean revokeToken(String tokenId);

    /**
     * Revokes all tokens for a user.
     *
     * @param userId The user ID
     * @return The number of tokens revoked
     */
    int revokeAllTokensForUser(String userId);

    /**
     * Gets all active tokens for a user.
     *
     * @param userId The user ID
     * @return A list of active tokens for the user
     */
    List<AuthToken> getActiveTokensForUser(String userId);

    /**
     * Refreshes an authentication token, extending its expiration time.
     *
     * @param tokenId The token ID
     * @return An Optional containing the new token, or empty if the token was not found or is invalid
     */
    Optional<AuthToken> refreshToken(String tokenId);

    /**
     * Cleans up expired tokens.
     *
     * @return The number of tokens removed
     */
    int cleanupExpiredTokens();
}