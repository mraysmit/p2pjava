package dev.mars.p2pjava.common.protocol;

import java.net.InetAddress;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Context information for message processing.
 * Contains connection details, session information, and metadata.
 */
public class MessageContext {
    
    private final String connectionId;
    private final InetAddress remoteAddress;
    private final int remotePort;
    private final Instant receivedAt;
    private final String protocol;
    private final Map<String, Object> attributes;
    
    /**
     * Creates a new message context.
     *
     * @param connectionId Unique identifier for the connection
     * @param remoteAddress Remote peer address
     * @param remotePort Remote peer port
     * @param protocol Protocol used (e.g., "TCP", "UDP", "HTTP")
     */
    public MessageContext(String connectionId, InetAddress remoteAddress, int remotePort, String protocol) {
        this.connectionId = connectionId;
        this.remoteAddress = remoteAddress;
        this.remotePort = remotePort;
        this.protocol = protocol;
        this.receivedAt = Instant.now();
        this.attributes = new HashMap<>();
    }
    
    /**
     * Gets the connection identifier.
     */
    public String getConnectionId() {
        return connectionId;
    }
    
    /**
     * Gets the remote peer address.
     */
    public InetAddress getRemoteAddress() {
        return remoteAddress;
    }
    
    /**
     * Gets the remote peer port.
     */
    public int getRemotePort() {
        return remotePort;
    }
    
    /**
     * Gets the timestamp when the message was received.
     */
    public Instant getReceivedAt() {
        return receivedAt;
    }
    
    /**
     * Gets the protocol used for communication.
     */
    public String getProtocol() {
        return protocol;
    }
    
    /**
     * Gets all context attributes.
     */
    public Map<String, Object> getAttributes() {
        return new HashMap<>(attributes);
    }
    
    /**
     * Gets a context attribute.
     *
     * @param key The attribute key
     * @return The attribute value, or null if not found
     */
    public Object getAttribute(String key) {
        return attributes.get(key);
    }
    
    /**
     * Gets a context attribute with type casting.
     *
     * @param key The attribute key
     * @param type The expected type
     * @param <T> The attribute type
     * @return The attribute value cast to the specified type, or null if not found or wrong type
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, Class<T> type) {
        Object value = attributes.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * Sets a context attribute.
     *
     * @param key The attribute key
     * @param value The attribute value
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }
    
    /**
     * Removes a context attribute.
     *
     * @param key The attribute key
     * @return The previous value, or null if not found
     */
    public Object removeAttribute(String key) {
        return attributes.remove(key);
    }
    
    /**
     * Checks if an attribute exists.
     *
     * @param key The attribute key
     * @return true if the attribute exists, false otherwise
     */
    public boolean hasAttribute(String key) {
        return attributes.containsKey(key);
    }
    
    /**
     * Gets the remote peer identifier from attributes.
     */
    public String getRemotePeerId() {
        return getAttribute("peerId", String.class);
    }
    
    /**
     * Sets the remote peer identifier.
     */
    public void setRemotePeerId(String peerId) {
        setAttribute("peerId", peerId);
    }
    
    /**
     * Gets the session identifier from attributes.
     */
    public String getSessionId() {
        return getAttribute("sessionId", String.class);
    }
    
    /**
     * Sets the session identifier.
     */
    public void setSessionId(String sessionId) {
        setAttribute("sessionId", sessionId);
    }
    
    /**
     * Checks if the connection is authenticated.
     */
    public boolean isAuthenticated() {
        Boolean authenticated = getAttribute("authenticated", Boolean.class);
        return authenticated != null && authenticated;
    }
    
    /**
     * Sets the authentication status.
     */
    public void setAuthenticated(boolean authenticated) {
        setAttribute("authenticated", authenticated);
    }
    
    /**
     * Gets the user role from attributes.
     */
    public String getUserRole() {
        return getAttribute("userRole", String.class);
    }
    
    /**
     * Sets the user role.
     */
    public void setUserRole(String userRole) {
        setAttribute("userRole", userRole);
    }
    
    @Override
    public String toString() {
        return String.format("MessageContext{connectionId='%s', remoteAddress=%s:%d, protocol='%s', receivedAt=%s, attributes=%d}",
                           connectionId, remoteAddress, remotePort, protocol, receivedAt, attributes.size());
    }
}
