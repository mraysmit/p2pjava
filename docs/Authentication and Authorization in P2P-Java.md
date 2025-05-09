# Authentication and Authorization in P2P-Java

This document describes the authentication and authorization features implemented in the P2P-Java system.

## Overview

The P2P-Java system now includes a comprehensive authentication and authorization module that provides:

1. **User Management**: Create, update, and delete users with secure password handling
2. **Token-based Authentication**: Secure authentication using tokens with expiration
3. **Role-based Authorization**: Control access to system functions based on user roles

These features address the security requirements mentioned in the "P2P-Java Project Analysis and Improvement Recommendations" document, specifically the "Missing Features" section that highlighted the need for authentication and authorization.

## Architecture

The authentication and authorization functionality is implemented in a new module called `p2p-auth`. This module integrates with the existing P2P bootstrap system and provides a centralized service for all auth-related operations.

### Key Components

#### User Management

- **User**: Represents a user in the system with credentials and roles
- **Role**: Enum defining the available roles (ADMIN, USER, PEER, GUEST)
- **UserService**: Interface for user management operations
- **UserServiceImpl**: Implementation of UserService that stores users in memory

#### Token-based Authentication

- **AuthToken**: Represents an authentication token with expiration
- **AuthenticationService**: Interface for authentication operations
- **AuthenticationServiceImpl**: Implementation of AuthenticationService that manages tokens

#### Role-based Authorization

- **AuthorizationService**: Interface for authorization operations
- **AuthorizationServiceImpl**: Implementation of AuthorizationService that checks user roles

#### Integration

- **AuthService**: Main service class that integrates with the P2P bootstrap system
- **P2PComponent**: Updated to include the AUTH component and dependencies

## Security Features

### Password Security

- Passwords are never stored in plain text
- Passwords are hashed using SHA-256 with a random salt
- Each user has a unique salt to prevent rainbow table attacks

### Token Security

- Tokens are generated using secure random UUIDs
- Tokens have a configurable expiration time
- Tokens can be revoked for logout functionality
- Expired and revoked tokens are automatically cleaned up

### Account Security

- Failed login attempts are tracked
- Accounts are locked after too many failed attempts
- User status (enabled/disabled) is tracked

## Configuration Options

The auth module supports the following configuration options:

- `auth.create.default.admin` (boolean): Whether to create a default admin user (default: true)
- `auth.default.admin.username` (string): Username for the default admin user (default: "admin")
- `auth.default.admin.password` (string): Password for the default admin user (default: "admin")
- `auth.token.expiration.minutes` (int): Token expiration time in minutes (default: 60)
- `auth.token.cleanup.interval.minutes` (int): Interval for cleaning up expired tokens in minutes (default: 60)
- `auth.max.failed.attempts` (int): Maximum number of failed login attempts before account lockout (default: 5)

## Integration with P2P Components

The auth module is integrated with the P2P bootstrap system, allowing it to be started and stopped along with other components. The INDEX_SERVER and PEER components now depend on the AUTH component, ensuring that authentication and authorization are available before these components start.

## Usage Examples

### Authenticating a User

```java
// Get the auth service
AuthService authService = AuthService.getInstance(configManager);

// Authenticate a user (login)
Optional<String> tokenOpt = authService.login("username", "password");
if (tokenOpt.isPresent()) {
    String tokenId = tokenOpt.get();
    // Use the token for subsequent operations
}
```

### Authorizing an Operation

```java
// Check if the user has the required role
if (authService.authorize(tokenId, Role.USER)) {
    // Perform the operation
} else {
    // Return an authorization error
}
```

## Future Improvements

While the current implementation provides a solid foundation for authentication and authorization, there are several areas for future improvement:

1. **Persistent Storage**: Currently, users and tokens are stored in memory. A future improvement would be to store them in a persistent database.

2. **Password Reset**: Add functionality for users to reset their passwords.

3. **Email Verification**: Add email verification for new accounts.

4. **Two-Factor Authentication**: Implement two-factor authentication for enhanced security.

5. **More Granular Permissions**: Implement a more granular permission system beyond the current role-based approach.

6. **Audit Logging**: Add comprehensive audit logging for security events.

7. **TLS/SSL Integration**: Integrate with TLS/SSL for secure communications, as mentioned in the "Implemented Features for P2P-Java" document.

## Conclusion

The addition of authentication and authorization features significantly enhances the security of the P2P-Java system. These features provide a foundation for secure user management, authentication, and authorization, addressing key security requirements identified in the project analysis.