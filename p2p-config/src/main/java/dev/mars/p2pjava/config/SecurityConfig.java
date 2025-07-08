package dev.mars.p2pjava.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Security configuration settings.
 */
public class SecurityConfig {
    
    @JsonProperty("enabled")
    private boolean enabled = false;
    
    @JsonProperty("encryption")
    private EncryptionConfig encryption = new EncryptionConfig();
    
    @JsonProperty("authentication")
    private AuthenticationConfig authentication = new AuthenticationConfig();
    
    @JsonProperty("authorization")
    private AuthorizationConfig authorization = new AuthorizationConfig();
    
    @JsonProperty("rateLimit")
    private RateLimitConfig rateLimit = new RateLimitConfig();
    
    // Getters and setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    public EncryptionConfig getEncryption() { return encryption; }
    public void setEncryption(EncryptionConfig encryption) { this.encryption = encryption; }
    
    public AuthenticationConfig getAuthentication() { return authentication; }
    public void setAuthentication(AuthenticationConfig authentication) { this.authentication = authentication; }
    
    public AuthorizationConfig getAuthorization() { return authorization; }
    public void setAuthorization(AuthorizationConfig authorization) { this.authorization = authorization; }
    
    public RateLimitConfig getRateLimit() { return rateLimit; }
    public void setRateLimit(RateLimitConfig rateLimit) { this.rateLimit = rateLimit; }
    
    /**
     * Encryption configuration
     */
    public static class EncryptionConfig {
        @JsonProperty("enabled")
        private boolean enabled = false;
        
        @JsonProperty("algorithm")
        private String algorithm = "AES-256-GCM";
        
        @JsonProperty("keySize")
        private int keySize = 256;
        
        @JsonProperty("keyRotationDays")
        private int keyRotationDays = 30;
        
        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public String getAlgorithm() { return algorithm; }
        public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
        
        public int getKeySize() { return keySize; }
        public void setKeySize(int keySize) { this.keySize = keySize; }
        
        public int getKeyRotationDays() { return keyRotationDays; }
        public void setKeyRotationDays(int keyRotationDays) { this.keyRotationDays = keyRotationDays; }
    }
    
    /**
     * Authentication configuration
     */
    public static class AuthenticationConfig {
        @JsonProperty("enabled")
        private boolean enabled = false;
        
        @JsonProperty("method")
        private String method = "token";
        
        @JsonProperty("tokenExpiryHours")
        private int tokenExpiryHours = 24;
        
        @JsonProperty("allowAnonymous")
        private boolean allowAnonymous = true;
        
        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        
        public int getTokenExpiryHours() { return tokenExpiryHours; }
        public void setTokenExpiryHours(int tokenExpiryHours) { this.tokenExpiryHours = tokenExpiryHours; }
        
        public boolean isAllowAnonymous() { return allowAnonymous; }
        public void setAllowAnonymous(boolean allowAnonymous) { this.allowAnonymous = allowAnonymous; }
    }
    
    /**
     * Authorization configuration
     */
    public static class AuthorizationConfig {
        @JsonProperty("enabled")
        private boolean enabled = false;
        
        @JsonProperty("defaultRole")
        private String defaultRole = "user";
        
        @JsonProperty("adminRole")
        private String adminRole = "admin";
        
        @JsonProperty("guestAccess")
        private boolean guestAccess = true;
        
        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public String getDefaultRole() { return defaultRole; }
        public void setDefaultRole(String defaultRole) { this.defaultRole = defaultRole; }
        
        public String getAdminRole() { return adminRole; }
        public void setAdminRole(String adminRole) { this.adminRole = adminRole; }
        
        public boolean isGuestAccess() { return guestAccess; }
        public void setGuestAccess(boolean guestAccess) { this.guestAccess = guestAccess; }
    }
    
    /**
     * Rate limiting configuration
     */
    public static class RateLimitConfig {
        @JsonProperty("enabled")
        private boolean enabled = false;
        
        @JsonProperty("requestsPerMinute")
        private int requestsPerMinute = 100;
        
        @JsonProperty("burstSize")
        private int burstSize = 20;
        
        @JsonProperty("blockDurationMs")
        private long blockDurationMs = 60000;
        
        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public int getRequestsPerMinute() { return requestsPerMinute; }
        public void setRequestsPerMinute(int requestsPerMinute) { this.requestsPerMinute = requestsPerMinute; }
        
        public int getBurstSize() { return burstSize; }
        public void setBurstSize(int burstSize) { this.burstSize = burstSize; }
        
        public long getBlockDurationMs() { return blockDurationMs; }
        public void setBlockDurationMs(long blockDurationMs) { this.blockDurationMs = blockDurationMs; }
    }
}
