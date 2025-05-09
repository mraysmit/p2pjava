package dev.mars.p2pjava.auth.model;

/**
 * Represents a role in the P2P system.
 * Roles are used to define permissions for users.
 */
public enum Role {
    /**
     * Administrator role with full access to all system functions.
     */
    ADMIN,

    /**
     * User role with standard access to the P2P system.
     */
    USER,

    /**
     * Peer role with permissions to share files.
     */
    PEER,

    /**
     * Guest role with limited access to the P2P system.
     */
    GUEST;

    /**
     * Checks if this role has higher or equal privileges than the specified role.
     *
     * @param role The role to compare with
     * @return true if this role has higher or equal privileges, false otherwise
     */
    public boolean hasPrivilegeOf(Role role) {
        if (this == ADMIN) {
            return true; // Admin has all privileges
        }
        if (this == USER) {
            return role == USER || role == PEER || role == GUEST;
        }
        if (this == PEER) {
            return role == PEER || role == GUEST;
        }
        if (this == GUEST) {
            return role == GUEST;
        }
        return false;
    }
}