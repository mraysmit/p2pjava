package dev.mars.p2pjava.util;

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


import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * Instance-based retry manager that replaces the static RetryHelper.
 * Provides configurable retry policies and proper dependency injection support.
 */
public class RetryManager {
    private static final Logger logger = Logger.getLogger(RetryManager.class.getName());
    
    // Default configuration
    private final int defaultMaxRetries;
    private final long defaultInitialBackoffMs;
    private final long defaultMaxBackoffMs;
    private final double defaultBackoffMultiplier;
    private final Predicate<Exception> defaultRetryCondition;
    
    /**
     * Creates a RetryManager with default configuration.
     */
    public RetryManager() {
        this(3, 1000, 10000, 2.0, 
             e -> e instanceof java.io.IOException || e instanceof java.net.SocketException);
    }
    
    /**
     * Creates a RetryManager with custom configuration.
     */
    public RetryManager(int defaultMaxRetries, long defaultInitialBackoffMs, 
                       long defaultMaxBackoffMs, double defaultBackoffMultiplier,
                       Predicate<Exception> defaultRetryCondition) {
        this.defaultMaxRetries = defaultMaxRetries;
        this.defaultInitialBackoffMs = defaultInitialBackoffMs;
        this.defaultMaxBackoffMs = defaultMaxBackoffMs;
        this.defaultBackoffMultiplier = defaultBackoffMultiplier;
        this.defaultRetryCondition = defaultRetryCondition;
        
        logger.info("RetryManager created with defaults: maxRetries=" + defaultMaxRetries + 
                   ", initialBackoff=" + defaultInitialBackoffMs + "ms, maxBackoff=" + defaultMaxBackoffMs + "ms");
    }
    
    /**
     * Executes an operation with default retry configuration.
     */
    public <T> T executeWithRetry(Callable<T> operation) throws Exception {
        return executeWithRetry(operation, defaultMaxRetries, defaultInitialBackoffMs, 
                               defaultMaxBackoffMs, defaultRetryCondition);
    }
    
    /**
     * Executes an operation with custom retry configuration.
     */
    public <T> T executeWithRetry(Callable<T> operation, int maxRetries, 
                                 long initialBackoffMs, long maxBackoffMs,
                                 Predicate<Exception> retryCondition) throws Exception {
        Exception lastException = null;
        long currentBackoff = initialBackoffMs;
        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                T result = operation.call();
                if (attempt > 0) {
                    logger.info("Operation succeeded on attempt " + (attempt + 1));
                }
                return result;
                
            } catch (Exception e) {
                lastException = e;
                
                if (attempt == maxRetries) {
                    logger.warning("Operation failed after " + (maxRetries + 1) + " attempts: " + e.getMessage());
                    break;
                }
                
                if (!retryCondition.test(e)) {
                    logger.info("Exception not retryable: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                    throw e;
                }
                
                logger.info("Attempt " + (attempt + 1) + " failed, retrying in " + currentBackoff + "ms: " + e.getMessage());
                
                try {
                    Thread.sleep(currentBackoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }
                
                // Exponential backoff with jitter
                currentBackoff = Math.min(
                    (long) (currentBackoff * defaultBackoffMultiplier * (0.5 + Math.random() * 0.5)),
                    maxBackoffMs
                );
            }
        }
        
        throw lastException;
    }
    
    /**
     * Executes a void operation with default retry configuration.
     */
    public void executeWithRetry(Runnable operation) throws Exception {
        executeWithRetry(() -> {
            operation.run();
            return null;
        });
    }
    
    /**
     * Executes a void operation with custom retry configuration.
     */
    public void executeWithRetry(Runnable operation, int maxRetries, 
                                long initialBackoffMs, long maxBackoffMs,
                                Predicate<Exception> retryCondition) throws Exception {
        executeWithRetry(() -> {
            operation.run();
            return null;
        }, maxRetries, initialBackoffMs, maxBackoffMs, retryCondition);
    }
    
    /**
     * Creates a retry policy builder.
     */
    public RetryPolicyBuilder policy() {
        return new RetryPolicyBuilder(this);
    }
    
    /**
     * Gets the default configuration.
     */
    public RetryConfig getDefaultConfig() {
        return new RetryConfig(defaultMaxRetries, defaultInitialBackoffMs, 
                              defaultMaxBackoffMs, defaultBackoffMultiplier, defaultRetryCondition);
    }
    
    /**
     * Retry policy builder for fluent configuration.
     */
    public static class RetryPolicyBuilder {
        private final RetryManager retryManager;
        private int maxRetries;
        private long initialBackoffMs;
        private long maxBackoffMs;
        private Predicate<Exception> retryCondition;
        
        RetryPolicyBuilder(RetryManager retryManager) {
            this.retryManager = retryManager;
            this.maxRetries = retryManager.defaultMaxRetries;
            this.initialBackoffMs = retryManager.defaultInitialBackoffMs;
            this.maxBackoffMs = retryManager.defaultMaxBackoffMs;
            this.retryCondition = retryManager.defaultRetryCondition;
        }
        
        public RetryPolicyBuilder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }
        
        public RetryPolicyBuilder initialBackoff(long backoffMs) {
            this.initialBackoffMs = backoffMs;
            return this;
        }
        
        public RetryPolicyBuilder maxBackoff(long maxBackoffMs) {
            this.maxBackoffMs = maxBackoffMs;
            return this;
        }
        
        public RetryPolicyBuilder retryOn(Class<? extends Exception> exceptionClass) {
            this.retryCondition = exceptionClass::isInstance;
            return this;
        }
        
        public RetryPolicyBuilder retryOn(Predicate<Exception> condition) {
            this.retryCondition = condition;
            return this;
        }
        
        public RetryPolicyBuilder retryOnAny() {
            this.retryCondition = e -> true;
            return this;
        }
        
        public <T> T execute(Callable<T> operation) throws Exception {
            return retryManager.executeWithRetry(operation, maxRetries, initialBackoffMs, 
                                               maxBackoffMs, retryCondition);
        }
        
        public void execute(Runnable operation) throws Exception {
            retryManager.executeWithRetry(operation, maxRetries, initialBackoffMs, 
                                        maxBackoffMs, retryCondition);
        }
    }
    
    /**
     * Retry configuration holder.
     */
    public static class RetryConfig {
        private final int maxRetries;
        private final long initialBackoffMs;
        private final long maxBackoffMs;
        private final double backoffMultiplier;
        private final Predicate<Exception> retryCondition;
        
        RetryConfig(int maxRetries, long initialBackoffMs, long maxBackoffMs, 
                   double backoffMultiplier, Predicate<Exception> retryCondition) {
            this.maxRetries = maxRetries;
            this.initialBackoffMs = initialBackoffMs;
            this.maxBackoffMs = maxBackoffMs;
            this.backoffMultiplier = backoffMultiplier;
            this.retryCondition = retryCondition;
        }
        
        public int getMaxRetries() { return maxRetries; }
        public long getInitialBackoffMs() { return initialBackoffMs; }
        public long getMaxBackoffMs() { return maxBackoffMs; }
        public double getBackoffMultiplier() { return backoffMultiplier; }
        public Predicate<Exception> getRetryCondition() { return retryCondition; }
        
        @Override
        public String toString() {
            return "RetryConfig{maxRetries=" + maxRetries + 
                   ", initialBackoff=" + initialBackoffMs + "ms" +
                   ", maxBackoff=" + maxBackoffMs + "ms" +
                   ", multiplier=" + backoffMultiplier + "}";
        }
    }
    
    /**
     * Builder for RetryManager.
     */
    public static class Builder {
        private int maxRetries = 3;
        private long initialBackoffMs = 1000;
        private long maxBackoffMs = 10000;
        private double backoffMultiplier = 2.0;
        private Predicate<Exception> retryCondition = 
            e -> e instanceof java.io.IOException || e instanceof java.net.SocketException;
        
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }
        
        public Builder initialBackoff(long backoffMs) {
            this.initialBackoffMs = backoffMs;
            return this;
        }
        
        public Builder maxBackoff(long maxBackoffMs) {
            this.maxBackoffMs = maxBackoffMs;
            return this;
        }
        
        public Builder backoffMultiplier(double multiplier) {
            this.backoffMultiplier = multiplier;
            return this;
        }
        
        public Builder retryCondition(Predicate<Exception> condition) {
            this.retryCondition = condition;
            return this;
        }
        
        public RetryManager build() {
            return new RetryManager(maxRetries, initialBackoffMs, maxBackoffMs, 
                                  backoffMultiplier, retryCondition);
        }
    }
    
    /**
     * Creates a builder for RetryManager.
     */
    public static Builder builder() {
        return new Builder();
    }
}
