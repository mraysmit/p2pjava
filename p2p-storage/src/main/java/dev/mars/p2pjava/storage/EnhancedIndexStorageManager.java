package dev.mars.p2pjava.storage;

import dev.mars.p2pjava.discovery.ServiceRegistry;
import dev.mars.p2pjava.discovery.DistributedServiceRegistry;
import dev.mars.p2pjava.discovery.ServiceInstance;
import dev.mars.p2pjava.discovery.ConflictResolutionStrategy;
import dev.mars.p2pjava.config.PeerConfig;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Enhanced storage manager that integrates with the distributed service registry
 * for intelligent index server discovery and load balancing.
 */
public class EnhancedIndexStorageManager {
    private static final Logger logger = Logger.getLogger(EnhancedIndexStorageManager.class.getName());
    
    private final String nodeId;
    private final ServiceRegistry serviceRegistry;
    private final FileIndexStorage localStorage;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService syncExecutor;
    
    // Index server selection strategy
    private final IndexServerSelectionStrategy selectionStrategy;
    
    // Metrics and monitoring
    private final Map<String, IndexServerMetrics> serverMetrics = new ConcurrentHashMap<>();
    private volatile boolean running = false;
    
    public EnhancedIndexStorageManager(String nodeId, ServiceRegistry serviceRegistry, 
                                     FileIndexStorage localStorage) {
        this.nodeId = nodeId;
        this.serviceRegistry = serviceRegistry;
        this.localStorage = localStorage;
        this.selectionStrategy = new LoadBalancedSelectionStrategy();
        
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "IndexStorageManager-Scheduler-" + nodeId);
            t.setDaemon(true);
            return t;
        });
        
        this.syncExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "IndexStorageManager-Sync-" + nodeId);
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * Starts the enhanced storage manager.
     */
    public void start() {
        if (running) {
            logger.warning("Enhanced storage manager already running");
            return;
        }
        
        running = true;
        logger.info("Starting enhanced index storage manager for node: " + nodeId);
        
        // Start periodic index server discovery and health checks
        scheduler.scheduleWithFixedDelay(
            this::updateIndexServerMetrics,
            10, // Initial delay
            30, // Every 30 seconds
            TimeUnit.SECONDS
        );
        
        // Start periodic synchronization with index servers
        scheduler.scheduleWithFixedDelay(
            this::synchronizeWithIndexServers,
            60, // Initial delay
            300, // Every 5 minutes
            TimeUnit.SECONDS
        );
        
        logger.info("Enhanced index storage manager started");
    }
    
    /**
     * Stops the enhanced storage manager.
     */
    public void stop() {
        if (!running) {
            return;
        }
        
        running = false;
        logger.info("Stopping enhanced index storage manager");
        
        scheduler.shutdown();
        syncExecutor.shutdown();
        
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            if (!syncExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                syncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        logger.info("Enhanced index storage manager stopped");
    }
    
    /**
     * Registers a file with the best available index server.
     */
    public CompletableFuture<Boolean> registerFileAsync(String fileName, PeerInfo peerInfo) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // First, register locally
                boolean localSuccess = localStorage.registerFile(fileName, peerInfo);
                if (!localSuccess) {
                    logger.warning("Failed to register file locally: " + fileName);
                    return false;
                }
                
                // Then, register with the best index server
                ServiceInstance indexServer = selectBestIndexServer();
                if (indexServer != null) {
                    boolean remoteSuccess = registerFileWithServer(fileName, peerInfo, indexServer);
                    if (remoteSuccess) {
                        updateServerMetrics(indexServer.getServiceId(), true, System.currentTimeMillis());
                        logger.fine("Successfully registered file with index server: " + fileName);
                    } else {
                        updateServerMetrics(indexServer.getServiceId(), false, System.currentTimeMillis());
                        logger.warning("Failed to register file with index server: " + fileName);
                    }
                    return remoteSuccess;
                } else {
                    logger.warning("No index servers available for file registration");
                    return localSuccess; // At least we have local registration
                }
                
            } catch (Exception e) {
                logger.warning("Error registering file: " + e.getMessage());
                return false;
            }
        }, syncExecutor);
    }
    
    /**
     * Searches for files across multiple index servers with intelligent aggregation.
     */
    public CompletableFuture<Map<String, List<PeerInfo>>> searchFilesAsync(String pattern) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, List<PeerInfo>> aggregatedResults = new ConcurrentHashMap<>();
            
            try {
                // Start with local search
                Map<String, List<PeerInfo>> localResults = localStorage.searchFiles(pattern);
                aggregatedResults.putAll(localResults);
                
                // Discover available index servers
                List<ServiceInstance> indexServers = discoverIndexServers();
                if (indexServers.isEmpty()) {
                    logger.fine("No index servers available, returning local results only");
                    return aggregatedResults;
                }
                
                // Search across multiple index servers in parallel
                List<CompletableFuture<Map<String, List<PeerInfo>>>> searchFutures = indexServers.stream()
                    .map(server -> searchFilesOnServer(pattern, server))
                    .collect(Collectors.toList());
                
                // Wait for all searches to complete (with timeout)
                CompletableFuture<Void> allSearches = CompletableFuture.allOf(
                    searchFutures.toArray(new CompletableFuture[0]));
                
                try {
                    allSearches.get(10, TimeUnit.SECONDS); // 10 second timeout
                } catch (TimeoutException e) {
                    logger.warning("Some index server searches timed out");
                }
                
                // Aggregate results from all servers
                for (CompletableFuture<Map<String, List<PeerInfo>>> future : searchFutures) {
                    try {
                        if (future.isDone() && !future.isCompletedExceptionally()) {
                            Map<String, List<PeerInfo>> serverResults = future.get();
                            mergeSearchResults(aggregatedResults, serverResults);
                        }
                    } catch (Exception e) {
                        logger.fine("Failed to get results from one index server: " + e.getMessage());
                    }
                }
                
                logger.fine("Aggregated search results from " + indexServers.size() + " index servers");
                return aggregatedResults;
                
            } catch (Exception e) {
                logger.warning("Error during distributed file search: " + e.getMessage());
                return aggregatedResults; // Return what we have
            }
        }, syncExecutor);
    }
    
    /**
     * Discovers available index servers using the service registry.
     */
    public List<ServiceInstance> discoverIndexServers() {
        if (serviceRegistry == null) {
            return Collections.emptyList();
        }
        
        try {
            List<ServiceInstance> servers = serviceRegistry.discoverServices("indexserver");
            
            // Filter healthy servers and sort by reliability
            return servers.stream()
                .filter(ServiceInstance::isHealthy)
                .sorted((s1, s2) -> {
                    IndexServerMetrics m1 = serverMetrics.get(s1.getServiceId());
                    IndexServerMetrics m2 = serverMetrics.get(s2.getServiceId());
                    
                    if (m1 == null && m2 == null) return 0;
                    if (m1 == null) return 1;
                    if (m2 == null) return -1;
                    
                    return Double.compare(m2.getReliabilityScore(), m1.getReliabilityScore());
                })
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            logger.warning("Failed to discover index servers: " + e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Selects the best index server based on current metrics and load.
     */
    private ServiceInstance selectBestIndexServer() {
        List<ServiceInstance> servers = discoverIndexServers();
        if (servers.isEmpty()) {
            return null;
        }
        
        return selectionStrategy.selectServer(servers, serverMetrics);
    }
    
    /**
     * Updates metrics for index servers.
     */
    private void updateIndexServerMetrics() {
        if (!running) return;
        
        try {
            List<ServiceInstance> servers = discoverIndexServers();
            
            for (ServiceInstance server : servers) {
                // Perform health check
                CompletableFuture.runAsync(() -> {
                    try {
                        boolean healthy = performHealthCheck(server);
                        updateServerMetrics(server.getServiceId(), healthy, System.currentTimeMillis());
                    } catch (Exception e) {
                        logger.fine("Health check failed for server " + server.getServiceId() + ": " + e.getMessage());
                        updateServerMetrics(server.getServiceId(), false, System.currentTimeMillis());
                    }
                }, syncExecutor);
            }
            
        } catch (Exception e) {
            logger.warning("Error updating index server metrics: " + e.getMessage());
        }
    }
    
    /**
     * Synchronizes local index with remote index servers.
     */
    private void synchronizeWithIndexServers() {
        if (!running) return;
        
        logger.fine("Starting index synchronization with remote servers");
        
        // Implementation would depend on the specific synchronization protocol
        // This is a placeholder for the synchronization logic
        
        logger.fine("Index synchronization completed");
    }
    
    /**
     * Registers a file with a specific index server.
     */
    private boolean registerFileWithServer(String fileName, PeerInfo peerInfo, ServiceInstance server) {
        try {
            // This would implement the actual network call to the index server
            // For now, we'll simulate the operation
            long startTime = System.currentTimeMillis();

            // Simulate network call (replace with actual implementation)
            boolean success = simulateIndexServerCall(server, "register", fileName);

            long responseTime = System.currentTimeMillis() - startTime;
            updateServerMetrics(server.getServiceId(), success, responseTime);

            return success;

        } catch (Exception e) {
            logger.warning("Failed to register file with server " + server.getServiceId() + ": " + e.getMessage());
            updateServerMetrics(server.getServiceId(), false, System.currentTimeMillis());
            return false;
        }
    }

    /**
     * Searches for files on a specific index server.
     */
    private CompletableFuture<Map<String, List<PeerInfo>>> searchFilesOnServer(String pattern, ServiceInstance server) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();

                // Simulate search operation (replace with actual implementation)
                Map<String, List<PeerInfo>> results = simulateSearchOperation(server, pattern);

                long responseTime = System.currentTimeMillis() - startTime;
                updateServerMetrics(server.getServiceId(), true, responseTime);

                return results;

            } catch (Exception e) {
                logger.fine("Search failed on server " + server.getServiceId() + ": " + e.getMessage());
                updateServerMetrics(server.getServiceId(), false, System.currentTimeMillis());
                return Collections.emptyMap();
            }
        }, syncExecutor);
    }

    /**
     * Performs a health check on an index server.
     */
    private boolean performHealthCheck(ServiceInstance server) {
        try {
            // Simulate health check (replace with actual implementation)
            return simulateIndexServerCall(server, "health", null);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Merges search results from multiple servers, removing duplicates.
     */
    private void mergeSearchResults(Map<String, List<PeerInfo>> target, Map<String, List<PeerInfo>> source) {
        for (Map.Entry<String, List<PeerInfo>> entry : source.entrySet()) {
            String fileName = entry.getKey();
            List<PeerInfo> sourcePeers = entry.getValue();

            target.computeIfAbsent(fileName, k -> new ArrayList<>());
            List<PeerInfo> targetPeers = target.get(fileName);

            // Add peers that aren't already in the target list
            for (PeerInfo peer : sourcePeers) {
                boolean exists = targetPeers.stream()
                    .anyMatch(existing -> existing.getPeerId().equals(peer.getPeerId()));
                if (!exists) {
                    targetPeers.add(peer);
                }
            }
        }
    }

    /**
     * Updates metrics for a specific server.
     */
    private void updateServerMetrics(String serverId, boolean success, long responseTime) {
        IndexServerMetrics metrics = serverMetrics.computeIfAbsent(serverId, k -> new IndexServerMetrics());
        metrics.recordRequest(success, responseTime);
    }

    /**
     * Simulates an index server call (replace with actual implementation).
     */
    private boolean simulateIndexServerCall(ServiceInstance server, String operation, String fileName) {
        // This is a placeholder - replace with actual network communication
        try {
            Thread.sleep(50 + (long)(Math.random() * 100)); // Simulate network delay
            return Math.random() > 0.1; // 90% success rate for simulation
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Simulates a search operation (replace with actual implementation).
     */
    private Map<String, List<PeerInfo>> simulateSearchOperation(ServiceInstance server, String pattern) {
        // This is a placeholder - replace with actual search implementation
        Map<String, List<PeerInfo>> results = new HashMap<>();

        // Simulate some results
        if (pattern.contains("test")) {
            List<PeerInfo> peers = new ArrayList<>();
            peers.add(new PeerInfo("peer1", "192.168.1.10", 8001));
            results.put("test-file.txt", peers);
        }

        return results;
    }

    /**
     * Gets current metrics for all index servers.
     */
    public Map<String, IndexServerMetrics> getServerMetrics() {
        return new HashMap<>(serverMetrics);
    }
    
    /**
     * Interface for index server selection strategies.
     */
    public interface IndexServerSelectionStrategy {
        ServiceInstance selectServer(List<ServiceInstance> servers, Map<String, IndexServerMetrics> metrics);
    }
    
    /**
     * Load-balanced selection strategy.
     */
    private static class LoadBalancedSelectionStrategy implements IndexServerSelectionStrategy {
        @Override
        public ServiceInstance selectServer(List<ServiceInstance> servers, Map<String, IndexServerMetrics> metrics) {
            if (servers.isEmpty()) return null;
            
            // Simple round-robin for now, could be enhanced with actual load metrics
            return servers.get((int) (System.currentTimeMillis() % servers.size()));
        }
    }
}
