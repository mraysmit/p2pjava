package dev.mars.p2pjava.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Monitoring and metrics configuration settings.
 */
public class MonitoringConfig {
    
    @JsonProperty("enabled")
    private boolean enabled = true;
    
    @JsonProperty("intervalMs")
    private long intervalMs = 30000;
    
    @JsonProperty("metrics")
    private MetricsConfig metrics = new MetricsConfig();
    
    @JsonProperty("alerts")
    private AlertsConfig alerts = new AlertsConfig();
    
    @JsonProperty("export")
    private ExportConfig export = new ExportConfig();
    
    // Getters and setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    public long getIntervalMs() { return intervalMs; }
    public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }
    
    public MetricsConfig getMetrics() { return metrics; }
    public void setMetrics(MetricsConfig metrics) { this.metrics = metrics; }
    
    public AlertsConfig getAlerts() { return alerts; }
    public void setAlerts(AlertsConfig alerts) { this.alerts = alerts; }
    
    public ExportConfig getExport() { return export; }
    public void setExport(ExportConfig export) { this.export = export; }
    
    /**
     * Metrics collection configuration
     */
    public static class MetricsConfig {
        @JsonProperty("threadPools")
        private boolean threadPools = true;
        
        @JsonProperty("memory")
        private boolean memory = true;
        
        @JsonProperty("network")
        private boolean network = true;
        
        @JsonProperty("fileTransfers")
        private boolean fileTransfers = true;
        
        @JsonProperty("performance")
        private boolean performance = true;
        
        @JsonProperty("retentionDays")
        private int retentionDays = 7;
        
        // Getters and setters
        public boolean isThreadPools() { return threadPools; }
        public void setThreadPools(boolean threadPools) { this.threadPools = threadPools; }
        
        public boolean isMemory() { return memory; }
        public void setMemory(boolean memory) { this.memory = memory; }
        
        public boolean isNetwork() { return network; }
        public void setNetwork(boolean network) { this.network = network; }
        
        public boolean isFileTransfers() { return fileTransfers; }
        public void setFileTransfers(boolean fileTransfers) { this.fileTransfers = fileTransfers; }
        
        public boolean isPerformance() { return performance; }
        public void setPerformance(boolean performance) { this.performance = performance; }
        
        public int getRetentionDays() { return retentionDays; }
        public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
    }
    
    /**
     * Alerting configuration
     */
    public static class AlertsConfig {
        @JsonProperty("enabled")
        private boolean enabled = true;
        
        @JsonProperty("thresholds")
        private ThresholdsConfig thresholds = new ThresholdsConfig();
        
        @JsonProperty("notifications")
        private NotificationsConfig notifications = new NotificationsConfig();
        
        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public ThresholdsConfig getThresholds() { return thresholds; }
        public void setThresholds(ThresholdsConfig thresholds) { this.thresholds = thresholds; }
        
        public NotificationsConfig getNotifications() { return notifications; }
        public void setNotifications(NotificationsConfig notifications) { this.notifications = notifications; }
    }
    
    /**
     * Alert thresholds configuration
     */
    public static class ThresholdsConfig {
        @JsonProperty("memoryUsagePercent")
        private double memoryUsagePercent = 85.0;
        
        @JsonProperty("threadPoolUtilizationPercent")
        private double threadPoolUtilizationPercent = 80.0;
        
        @JsonProperty("diskUsagePercent")
        private double diskUsagePercent = 90.0;
        
        @JsonProperty("errorRatePercent")
        private double errorRatePercent = 5.0;
        
        @JsonProperty("responseTimeMs")
        private long responseTimeMs = 5000;
        
        // Getters and setters
        public double getMemoryUsagePercent() { return memoryUsagePercent; }
        public void setMemoryUsagePercent(double memoryUsagePercent) { this.memoryUsagePercent = memoryUsagePercent; }
        
        public double getThreadPoolUtilizationPercent() { return threadPoolUtilizationPercent; }
        public void setThreadPoolUtilizationPercent(double threadPoolUtilizationPercent) { this.threadPoolUtilizationPercent = threadPoolUtilizationPercent; }
        
        public double getDiskUsagePercent() { return diskUsagePercent; }
        public void setDiskUsagePercent(double diskUsagePercent) { this.diskUsagePercent = diskUsagePercent; }
        
        public double getErrorRatePercent() { return errorRatePercent; }
        public void setErrorRatePercent(double errorRatePercent) { this.errorRatePercent = errorRatePercent; }
        
        public long getResponseTimeMs() { return responseTimeMs; }
        public void setResponseTimeMs(long responseTimeMs) { this.responseTimeMs = responseTimeMs; }
    }
    
    /**
     * Notification configuration
     */
    public static class NotificationsConfig {
        @JsonProperty("email")
        private boolean email = false;
        
        @JsonProperty("webhook")
        private boolean webhook = false;
        
        @JsonProperty("log")
        private boolean log = true;
        
        @JsonProperty("webhookUrl")
        private String webhookUrl = "";
        
        @JsonProperty("emailRecipients")
        private String[] emailRecipients = {};
        
        // Getters and setters
        public boolean isEmail() { return email; }
        public void setEmail(boolean email) { this.email = email; }
        
        public boolean isWebhook() { return webhook; }
        public void setWebhook(boolean webhook) { this.webhook = webhook; }
        
        public boolean isLog() { return log; }
        public void setLog(boolean log) { this.log = log; }
        
        public String getWebhookUrl() { return webhookUrl; }
        public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
        
        public String[] getEmailRecipients() { return emailRecipients; }
        public void setEmailRecipients(String[] emailRecipients) { this.emailRecipients = emailRecipients; }
    }
    
    /**
     * Metrics export configuration
     */
    public static class ExportConfig {
        @JsonProperty("enabled")
        private boolean enabled = false;
        
        @JsonProperty("format")
        private String format = "prometheus";
        
        @JsonProperty("endpoint")
        private String endpoint = "/metrics";
        
        @JsonProperty("port")
        private int port = 9090;
        
        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
        
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
    }
}
