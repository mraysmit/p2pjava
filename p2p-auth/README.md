# P2P-Java Authentication and Authorization Module

This module provides authentication and authorization functionality for the P2P-Java system. It implements user management, token-based authentication, and role-based authorization.

## Features

### User Management
- User creation with secure password hashing
- Role-based access control with predefined roles (ADMIN, USER, PEER, GUEST)
- User account locking after too many failed login attempts
- User metadata tracking (creation time, last login, etc.)

### Token-based Authentication
- Secure token generation for authenticated users
- Token validation and expiration
- Token refreshing to extend session lifetime
- Token revocation for logout functionality
- Automatic cleanup of expired tokens

### Role-based Authorization
- Role hierarchy with privilege inheritance
- Authorization checks for critical operations
- Support for authorizing by user or token

## Integration with P2P-Java

The auth module integrates with the P2P bootstrap system, allowing it to be started and stopped along with other components. The INDEX_SERVER and PEER components depend on the AUTH component, ensuring that authentication and authorization are available before these components start.

## Usage

### Basic Authentication Flow

```java
// Get the auth service
AuthService authService = AuthService.getInstance(configManager);

// Create a user
User user = authService.createUser("username", "password", Role.USER);

// Authenticate a user (login)
Optional<String> tokenOpt = authService.login("username", "password");
if (tokenOpt.isPresent()) {
    String tokenId = tokenOpt.get();
    
    // Validate token
    boolean isValid = authService.validateToken(tokenId);
    
    // Authorize operation
    boolean isAuthorized = authService.authorize(tokenId, Role.USER);
    
    if (isAuthorized) {
        // Perform operation
    }
}
```

### Configuration Options

The auth module supports the following configuration options:

- `auth.create.default.admin` (boolean): Whether to create a default admin user (default: true)
- `auth.default.admin.username` (string): Username for the default admin user (default: "admin")
- `auth.default.admin.password` (string): Password for the default admin user (default: "admin")
- `auth.token.expiration.minutes` (int): Token expiration time in minutes (default: 60)
- `auth.token.cleanup.interval.minutes` (int): Interval for cleaning up expired tokens in minutes (default: 60)
- `auth.max.failed.attempts` (int): Maximum number of failed login attempts before account lockout (default: 5)

## Security Considerations

- Passwords are hashed using SHA-256 with a random salt
- Tokens are generated using secure random UUIDs
- Failed login attempts are tracked and accounts are locked after too many failures
- Tokens expire after a configurable time period
- Expired and revoked tokens are automatically cleaned up

## Future Improvements

- Persistent storage for users and tokens
- Support for password reset functionality
- Email verification for new accounts
- Two-factor authentication
- More granular permission system
- Audit logging for security events