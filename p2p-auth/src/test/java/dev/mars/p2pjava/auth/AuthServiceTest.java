package dev.mars.p2pjava.auth;

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


import dev.mars.p2pjava.auth.model.Role;
import dev.mars.p2pjava.auth.model.User;
import dev.mars.p2pjava.config.ConfigurationManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;

/**
 * Test class for the AuthService.
 */
public class AuthServiceTest {

    private AuthService authService;
    private ConfigurationManager configManager;

    @Before
    public void setUp() {
        // Initialize configuration
        configManager = ConfigurationManager.getInstance();
        configManager.set("auth.create.default.admin", "true");
        configManager.set("auth.default.admin.username", "admin");
        configManager.set("auth.default.admin.password", "adminpass");
        configManager.set("auth.token.expiration.minutes", "60");
        
        // Create auth service
        authService = AuthService.getInstance(configManager);
        authService.start();
    }

    @After
    public void tearDown() {
        // Stop auth service
        authService.stop();
    }

    @Test
    public void testCreateAndAuthenticateUser() {
        // Create a new user
        User user = authService.createUser("testuser", "password123", Role.USER);
        
        // Verify user was created
        assertNotNull(user);
        assertEquals("testuser", user.getUsername());
        assertTrue(user.hasRole(Role.USER));
        
        // Authenticate user
        Optional<String> tokenOpt = authService.login("testuser", "password123");
        assertTrue(tokenOpt.isPresent());
        
        // Validate token
        String tokenId = tokenOpt.get();
        assertTrue(authService.validateToken(tokenId));
        
        // Authorize user
        assertTrue(authService.authorize(tokenId, Role.USER));
        assertFalse(authService.authorize(tokenId, Role.ADMIN));
    }

    @Test
    public void testDefaultAdminUser() {
        // Authenticate as admin
        Optional<String> tokenOpt = authService.login("admin", "adminpass");
        assertTrue(tokenOpt.isPresent());
        
        // Validate token
        String tokenId = tokenOpt.get();
        assertTrue(authService.validateToken(tokenId));
        
        // Authorize admin
        assertTrue(authService.authorize(tokenId, Role.ADMIN));
        assertTrue(authService.authorize(tokenId, Role.USER)); // Admin has all privileges
    }

    @Test
    public void testInvalidCredentials() {
        // Try to authenticate with invalid credentials
        Optional<String> tokenOpt = authService.login("admin", "wrongpassword");
        assertFalse(tokenOpt.isPresent());
    }

    @Test
    public void testInvalidToken() {
        // Try to validate an invalid token
        assertFalse(authService.validateToken("invalid-token"));
        
        // Try to authorize with an invalid token
        assertFalse(authService.authorize("invalid-token", Role.USER));
    }
}