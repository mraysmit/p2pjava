package dev.mars.p2pjava.discovery;

// import dev.mars.p2pjava.util.ThreadManager; // Temporarily removed due to Java version compatibility

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Anti-entropy service that periodically reconciles the entire registry state
 * between peers to ensure eventual consistency. This service helps recover
 * from network partitions and ensures that all peers eventually converge
 * to the same registry state.
 */
public class AntiEntropyService {
    private static final Logger logger = Logger.getLogger(AntiEntropyService.class.getName());
    
    // Configuration constants
    private static final int DEFAULT_RECONCILIATION_INTERVAL_MS = 60000; // 1 minute
    private static final int DEFAULT_PEER_SELECTION_COUNT = 3;
    private static final int DEFAULT_MAX_RECONCILIATION_TIME_MS = 30000; // 30 seconds
    
    private final String peerId;
    private final DistributedServiceRegistry registry;
    private final GossipProtocol gossipProtocol;
    private final ConflictResolver conflictResolver;
    
    private final int reconciliationIntervalMs;
    private final int peerSelectionCount;
    private final int maxReconciliationTimeMs;
    
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong reconciliationCount = new AtomicLong(0);
    private final AtomicLong conflictsResolved = new AtomicLong(0);
    
    private ScheduledExecutorService scheduledExecutor;
    private ExecutorService reconciliationExecutor;
    
    // Statistics
    private final Map<String, AtomicLong> reconciliationStats = new ConcurrentHashMap<>();
    
    /**
     * Creates an anti-entropy service with default configuration.
     */
    public AntiEntropyService(String peerId, DistributedServiceRegistry registry, 
                             GossipProtocol gossipProtocol, ConflictResolver conflictResolver) {
        this(peerId, registry, gossipProtocol, conflictResolver,
             DEFAULT_RECONCILIATION_INTERVAL_MS, DEFAULT_PEER_SELECTION_COUNT, 
             DEFAULT_MAX_RECONCILIATION_TIME_MS);
    }
    
    /**
     * Creates an anti-entropy service with custom configuration.
     */
    public AntiEntropyService(String peerId, DistributedServiceRegistry registry,
                             GossipProtocol gossipProtocol, ConflictResolver conflictResolver,
                             int reconciliationIntervalMs, int peerSelectionCount, 
                             int maxReconciliationTimeMs) {
        this.peerId = peerId;
        this.registry = registry;
        this.gossipProtocol = gossipProtocol;
        this.conflictResolver = conflictResolver;
        this.reconciliationIntervalMs = reconciliationIntervalMs;
        this.peerSelectionCount = peerSelectionCount;
        this.maxReconciliationTimeMs = maxReconciliationTimeMs;
        
        // Initialize statistics
        reconciliationStats.put("totalReconciliations", new AtomicLong(0));
        reconciliationStats.put("successfulReconciliations", new AtomicLong(0));
        reconciliationStats.put("failedReconciliations", new AtomicLong(0));
        reconciliationStats.put("servicesReconciled", new AtomicLong(0));
        reconciliationStats.put("conflictsDetected", new AtomicLong(0));
    }
    
    /**
     * Starts the anti-entropy service.
     */
    public void start() {
        if (running.getAndSet(true)) {
            logger.warning("Anti-entropy service already running");
            return;
        }
        
        logger.info("Starting anti-entropy service for peer: " + peerId);
        
        // Initialize thread pools
        scheduledExecutor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "AntiEntropy-Scheduler-" + peerId);
            t.setDaemon(true);
            return t;
        });
        
        reconciliationExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "AntiEntropyWorker-" + peerId);
            t.setDaemon(true);
            return t;
        });
        
        // Schedule periodic reconciliation
        scheduledExecutor.scheduleWithFixedDelay(
            this::performReconciliation,
            reconciliationIntervalMs,
            reconciliationIntervalMs,
            TimeUnit.MILLISECONDS
        );
        
        logger.info("Anti-entropy service started successfully");
    }
    
    /**
     * Stops the anti-entropy service.
     */
    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }
        
        logger.info("Stopping anti-entropy service for peer: " + peerId);
        
        // Shutdown schedulers
        if (scheduledExecutor != null) {
            scheduledExecutor.shutdown();
            try {
                if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduledExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduledExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (reconciliationExecutor != null) {
            reconciliationExecutor.shutdown();
            try {
                if (!reconciliationExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    reconciliationExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                reconciliationExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        logger.info("Anti-entropy service stopped");
    }
    
    /**
     * Performs a reconciliation cycle with selected peers.
     */
    private void performReconciliation() {
        if (!running.get()) {
            return;
        }
        
        try {
            reconciliationStats.get("totalReconciliations").incrementAndGet();
            reconciliationCount.incrementAndGet();
            
            logger.fine("Starting reconciliation cycle #" + reconciliationCount.get());
            
            // Select peers for reconciliation
            Set<String> selectedPeers = selectPeersForReconciliation();
            
            if (selectedPeers.isEmpty()) {
                logger.fine("No peers available for reconciliation");
                return;
            }
            
            // Perform reconciliation with selected peers
            List<CompletableFuture<Void>> reconciliationTasks = new ArrayList<>();
            
            for (String peerAddress : selectedPeers) {
                CompletableFuture<Void> task = CompletableFuture.runAsync(
                    () -> reconcileWithPeer(peerAddress),
                    reconciliationExecutor
                ).orTimeout(maxReconciliationTimeMs, TimeUnit.MILLISECONDS);
                
                reconciliationTasks.add(task);
            }
            
            // Wait for all reconciliation tasks to complete
            CompletableFuture<Void> allTasks = CompletableFuture.allOf(
                reconciliationTasks.toArray(new CompletableFuture[0])
            );
            
            allTasks.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    reconciliationStats.get("failedReconciliations").incrementAndGet();
                    logger.warning("Reconciliation cycle failed: " + throwable.getMessage());
                } else {
                    reconciliationStats.get("successfulReconciliations").incrementAndGet();
                    logger.fine("Reconciliation cycle completed successfully");
                }
            });
            
        } catch (Exception e) {
            reconciliationStats.get("failedReconciliations").incrementAndGet();
            logger.warning("Error in reconciliation cycle: " + e.getMessage());
        }
    }
    
    /**
     * Selects peers for reconciliation using a random selection strategy.
     */
    private Set<String> selectPeersForReconciliation() {
        Set<String> knownPeers = gossipProtocol.getKnownPeers();
        
        if (knownPeers.isEmpty()) {
            return Collections.emptySet();
        }
        
        List<String> peerList = new ArrayList<>(knownPeers);
        Collections.shuffle(peerList);
        
        int selectionCount = Math.min(peerSelectionCount, peerList.size());
        return new HashSet<>(peerList.subList(0, selectionCount));
    }
    
    /**
     * Performs reconciliation with a specific peer.
     */
    private void reconcileWithPeer(String peerAddress) {
        try {
            logger.fine("Starting reconciliation with peer: " + peerAddress);
            
            // Get current registry snapshot
            Map<String, Map<String, ServiceInstance>> localSnapshot = registry.getRegistrySnapshot();
            
            // Create anti-entropy message with our registry state
            RegistryMessage antiEntropyMessage = RegistryMessage.createAntiEntropy(peerId, localSnapshot);
            
            // Send anti-entropy message to peer
            // Note: This would need to be implemented as a direct send method in GossipProtocol
            // For now, we'll use the broadcast mechanism
            gossipProtocol.broadcast(antiEntropyMessage);
            
            // Request peer's registry state
            RegistryMessage syncRequest = RegistryMessage.createSyncRequest(peerId, Collections.emptySet());
            gossipProtocol.broadcast(syncRequest);
            
            logger.fine("Reconciliation initiated with peer: " + peerAddress);
            
        } catch (Exception e) {
            logger.warning("Error reconciling with peer " + peerAddress + ": " + e.getMessage());
        }
    }
    
    /**
     * Manually triggers a reconciliation cycle.
     */
    public void triggerReconciliation() {
        if (!running.get()) {
            logger.warning("Cannot trigger reconciliation: service not running");
            return;
        }
        
        logger.info("Manually triggering reconciliation cycle");
        reconciliationExecutor.submit(this::performReconciliation);
    }
    
    /**
     * Gets anti-entropy service statistics.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("running", running.get());
        stats.put("peerId", peerId);
        stats.put("reconciliationCount", reconciliationCount.get());
        stats.put("conflictsResolved", conflictsResolved.get());
        stats.put("reconciliationIntervalMs", reconciliationIntervalMs);
        stats.put("peerSelectionCount", peerSelectionCount);
        
        // Add detailed statistics
        reconciliationStats.forEach((key, value) -> stats.put(key, value.get()));
        
        return stats;
    }
    
    /**
     * Checks if the service is running.
     */
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * Gets the current reconciliation count.
     */
    public long getReconciliationCount() {
        return reconciliationCount.get();
    }
    
    /**
     * Gets the number of conflicts resolved.
     */
    public long getConflictsResolved() {
        return conflictsResolved.get();
    }
}
