package dev.mars.p2pjava.common.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

/**
 * Base class for tracker-related messages.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "action"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = TrackerMessage.RegisterRequest.class, name = "register"),
    @JsonSubTypes.Type(value = TrackerMessage.DeregisterRequest.class, name = "deregister"),
    @JsonSubTypes.Type(value = TrackerMessage.DiscoverRequest.class, name = "discover"),
    @JsonSubTypes.Type(value = TrackerMessage.IsAliveRequest.class, name = "isAlive"),
    @JsonSubTypes.Type(value = TrackerMessage.RegisterResponse.class, name = "registerResponse"),
    @JsonSubTypes.Type(value = TrackerMessage.DeregisterResponse.class, name = "deregisterResponse"),
    @JsonSubTypes.Type(value = TrackerMessage.DiscoverResponse.class, name = "discoverResponse"),
    @JsonSubTypes.Type(value = TrackerMessage.IsAliveResponse.class, name = "isAliveResponse")
})
public abstract class TrackerMessage extends JsonMessage {
    
    @JsonProperty("action")
    private String action;
    
    protected TrackerMessage() {
        super();
    }
    
    protected TrackerMessage(String senderId, String receiverId, String action) {
        super(senderId, receiverId);
        this.action = action;
    }
    
    @Override
    public String getMessageType() {
        return "tracker";
    }
    
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    
    /**
     * Register peer request message.
     */
    public static class RegisterRequest extends TrackerMessage {
        @JsonProperty("peerId")
        private String peerId;
        
        @JsonProperty("host")
        private String host;
        
        @JsonProperty("port")
        private int port;
        
        public RegisterRequest() {
            super();
            setAction("register");
        }
        
        public RegisterRequest(String senderId, String peerId, String host, int port) {
            super(senderId, "tracker", "register");
            this.peerId = peerId;
            this.host = host;
            this.port = port;
        }
        
        @Override
        public boolean isValid() {
            return peerId != null && !peerId.trim().isEmpty() &&
                   host != null && !host.trim().isEmpty() &&
                   port > 0 && port <= 65535;
        }
        
        // Getters and setters
        public String getPeerId() { return peerId; }
        public void setPeerId(String peerId) { this.peerId = peerId; }
        
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
    }
    
    /**
     * Register peer response message.
     */
    public static class RegisterResponse extends TrackerMessage {
        @JsonProperty("success")
        private boolean success;
        
        @JsonProperty("message")
        private String message;
        
        @JsonProperty("peerId")
        private String peerId;
        
        public RegisterResponse() {
            super();
            setAction("registerResponse");
        }
        
        public RegisterResponse(String senderId, String receiverId, boolean success, String peerId, String message) {
            super(senderId, receiverId, "registerResponse");
            this.success = success;
            this.peerId = peerId;
            this.message = message;
        }
        
        @Override
        public boolean isValid() {
            return message != null;
        }
        
        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public String getPeerId() { return peerId; }
        public void setPeerId(String peerId) { this.peerId = peerId; }
    }
    
    /**
     * Deregister peer request message.
     */
    public static class DeregisterRequest extends TrackerMessage {
        @JsonProperty("peerId")
        private String peerId;
        
        public DeregisterRequest() {
            super();
            setAction("deregister");
        }
        
        public DeregisterRequest(String senderId, String peerId) {
            super(senderId, "tracker", "deregister");
            this.peerId = peerId;
        }
        
        @Override
        public boolean isValid() {
            return peerId != null && !peerId.trim().isEmpty();
        }
        
        public String getPeerId() { return peerId; }
        public void setPeerId(String peerId) { this.peerId = peerId; }
    }
    
    /**
     * Deregister peer response message.
     */
    public static class DeregisterResponse extends TrackerMessage {
        @JsonProperty("success")
        private boolean success;
        
        @JsonProperty("message")
        private String message;
        
        @JsonProperty("peerId")
        private String peerId;
        
        public DeregisterResponse() {
            super();
            setAction("deregisterResponse");
        }
        
        public DeregisterResponse(String senderId, String receiverId, boolean success, String peerId, String message) {
            super(senderId, receiverId, "deregisterResponse");
            this.success = success;
            this.peerId = peerId;
            this.message = message;
        }
        
        @Override
        public boolean isValid() {
            return message != null;
        }
        
        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public String getPeerId() { return peerId; }
        public void setPeerId(String peerId) { this.peerId = peerId; }
    }
    
    /**
     * Discover peers request message.
     */
    public static class DiscoverRequest extends TrackerMessage {
        public DiscoverRequest() {
            super();
            setAction("discover");
        }
        
        public DiscoverRequest(String senderId) {
            super(senderId, "tracker", "discover");
        }
        
        @Override
        public boolean isValid() {
            return true; // No specific validation needed
        }
    }
    
    /**
     * Discover peers response message.
     */
    public static class DiscoverResponse extends TrackerMessage {
        @JsonProperty("peers")
        private List<PeerInfo> peers;
        
        public DiscoverResponse() {
            super();
            setAction("discoverResponse");
        }
        
        public DiscoverResponse(String senderId, String receiverId, List<PeerInfo> peers) {
            super(senderId, receiverId, "discoverResponse");
            this.peers = peers;
        }
        
        @Override
        public boolean isValid() {
            return peers != null;
        }
        
        public List<PeerInfo> getPeers() { return peers; }
        public void setPeers(List<PeerInfo> peers) { this.peers = peers; }
        
        /**
         * Peer information for discovery response.
         */
        public static class PeerInfo {
            @JsonProperty("peerId")
            private String peerId;
            
            @JsonProperty("host")
            private String host;
            
            @JsonProperty("port")
            private int port;
            
            @JsonProperty("lastSeen")
            private long lastSeen;
            
            public PeerInfo() {}
            
            public PeerInfo(String peerId, String host, int port, long lastSeen) {
                this.peerId = peerId;
                this.host = host;
                this.port = port;
                this.lastSeen = lastSeen;
            }
            
            // Getters and setters
            public String getPeerId() { return peerId; }
            public void setPeerId(String peerId) { this.peerId = peerId; }
            
            public String getHost() { return host; }
            public void setHost(String host) { this.host = host; }
            
            public int getPort() { return port; }
            public void setPort(int port) { this.port = port; }
            
            public long getLastSeen() { return lastSeen; }
            public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }
        }
    }
    
    /**
     * Is peer alive request message.
     */
    public static class IsAliveRequest extends TrackerMessage {
        @JsonProperty("peerId")
        private String peerId;
        
        public IsAliveRequest() {
            super();
            setAction("isAlive");
        }
        
        public IsAliveRequest(String senderId, String peerId) {
            super(senderId, "tracker", "isAlive");
            this.peerId = peerId;
        }
        
        @Override
        public boolean isValid() {
            return peerId != null && !peerId.trim().isEmpty();
        }
        
        public String getPeerId() { return peerId; }
        public void setPeerId(String peerId) { this.peerId = peerId; }
    }
    
    /**
     * Is peer alive response message.
     */
    public static class IsAliveResponse extends TrackerMessage {
        @JsonProperty("peerId")
        private String peerId;
        
        @JsonProperty("alive")
        private boolean alive;
        
        public IsAliveResponse() {
            super();
            setAction("isAliveResponse");
        }
        
        public IsAliveResponse(String senderId, String receiverId, String peerId, boolean alive) {
            super(senderId, receiverId, "isAliveResponse");
            this.peerId = peerId;
            this.alive = alive;
        }
        
        @Override
        public boolean isValid() {
            return peerId != null && !peerId.trim().isEmpty();
        }
        
        public String getPeerId() { return peerId; }
        public void setPeerId(String peerId) { this.peerId = peerId; }
        
        public boolean isAlive() { return alive; }
        public void setAlive(boolean alive) { this.alive = alive; }
    }
}
