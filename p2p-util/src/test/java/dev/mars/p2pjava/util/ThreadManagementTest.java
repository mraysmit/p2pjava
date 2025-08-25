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


import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.Map;

/**
 * Test class for the improved thread management capabilities.
 */
class ThreadManagementTest {
    
    @BeforeEach
    void setUp() {
        // Clean up any existing thread pools before each test
        ThreadManager.shutdownAllThreadPools();
        ThreadMonitor.stopMonitoring();
    }
    
    @AfterEach
    void tearDown() {
        // Clean up after each test
        ThreadManager.shutdownAllThreadPools();
        ThreadMonitor.stopMonitoring();
    }
    
    @Test
    void testThreadPoolCreation() {
        // Test creating different types of thread pools
        ThreadManager.getCachedThreadPool("TestCached", "TestCached");
        ThreadManager.getFixedThreadPool("TestFixed", "TestFixed", 5);
        ThreadManager.getScheduledThreadPool("TestScheduled", "TestScheduled", 2);
        
        assertEquals(3, ThreadManager.getActiveThreadPoolCount());
        
        // Test getting existing pools
        assertNotNull(ThreadManager.getThreadPool("TestCached"));
        assertNotNull(ThreadManager.getThreadPool("TestFixed"));
        assertNotNull(ThreadManager.getThreadPool("TestScheduled"));
        assertNull(ThreadManager.getThreadPool("NonExistent"));
    }
    
    @Test
    void testAsyncExecution() throws Exception {
        ThreadManager.getCachedThreadPool("TestAsync", "TestAsync");
        
        CompletableFuture<String> future = ThreadManager.executeAsync("TestAsync", () -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "Test completed";
        });
        
        String result = future.get(5, TimeUnit.SECONDS);
        assertEquals("Test completed", result);
    }
    
    @Test
    void testAsyncChain() throws Exception {
        ThreadManager.getCachedThreadPool("TestChain", "TestChain");
        
        CompletableFuture<String> chain = ThreadManager.executeAsyncChain(
            "TestChain",
            () -> "Step1",
            (step1) -> step1 + "-Step2"
        );
        
        String result = chain.get(5, TimeUnit.SECONDS);
        assertEquals("Step1-Step2", result);
    }
    
    @Test
    void testThreadPoolMonitoring() {
        ThreadManager.getCachedThreadPool("TestMonitor", "TestMonitor");
        
        ThreadPoolMonitorInfo info = ThreadManager.getThreadPoolInfo("TestMonitor");
        assertNotNull(info);
        assertEquals("TestMonitor", info.getPoolName());
        assertTrue(info.isRunning());
    }
    
    @Test
    void testThreadMonitoringService() {
        assertFalse(ThreadMonitor.isMonitoring());
        
        ThreadMonitor.startMonitoring();
        assertTrue(ThreadMonitor.isMonitoring());
        
        // Create a pool and record some metrics
        ThreadManager.getCachedThreadPool("TestMetrics", "TestMetrics");
        ThreadMonitor.recordTaskExecution("TestMetrics", 100, false);
        ThreadMonitor.recordTaskExecution("TestMetrics", 200, true);
        
        ThreadPoolMetrics metrics = ThreadMonitor.getPoolMetrics("TestMetrics");
        assertNotNull(metrics);
        assertEquals(2, metrics.getTotalTasks());
        assertEquals(1, metrics.getFailedTasks());
        assertEquals(50.0, metrics.getFailureRate(), 0.1);
        
        ThreadMonitor.stopMonitoring();
        assertFalse(ThreadMonitor.isMonitoring());
    }
    
    @Test
    void testAsyncOperationManager() throws Exception {
        ThreadManager.getCachedThreadPool("TestAsyncOps", "TestAsyncOps");
        
        // Test timeout operation
        CompletableFuture<String> timeoutOp = AsyncOperationManager.executeWithTimeout(
            "TestAsyncOps",
            () -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "Timeout test";
            },
            1000,
            "TimeoutTest"
        );
        
        String result = timeoutOp.get();
        assertEquals("Timeout test", result);
    }
    
    @Test
    void testAsyncOperationTimeout() {
        ThreadManager.getCachedThreadPool("TestTimeout", "TestTimeout");
        
        CompletableFuture<String> timeoutOp = AsyncOperationManager.executeWithTimeout(
            "TestTimeout",
            () -> {
                try {
                    Thread.sleep(2000); // Sleep longer than timeout
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "Should not complete";
            },
            500, // 500ms timeout
            "TimeoutTest"
        );
        
        assertThrows(Exception.class, () -> timeoutOp.get(1, TimeUnit.SECONDS));
    }
    
    @Test
    void testParallelExecution() throws Exception {
        ThreadManager.getCachedThreadPool("TestParallel", "TestParallel");

        CompletableFuture<java.util.List<String>> parallel = AsyncOperationManager.executeParallel(
            "TestParallel",
            "ParallelTest",
            () -> "Task1",
            () -> "Task2",
            () -> "Task3"
        );

        java.util.List<String> results = parallel.get(5, TimeUnit.SECONDS);
        assertEquals(3, results.size());
        assertEquals("Task1", results.get(0));
        assertEquals("Task2", results.get(1));
        assertEquals("Task3", results.get(2));
    }
    
    @Test
    void testSequentialChain() throws Exception {
        ThreadManager.getCachedThreadPool("TestSequential", "TestSequential");
        
        CompletableFuture<String> sequential = AsyncOperationManager.executeSequentialChain(
            "TestSequential",
            () -> "First",
            (first) -> first + "-Second",
            (second) -> second + "-Third",
            "SequentialTest"
        );
        
        String result = sequential.get(5, TimeUnit.SECONDS);
        assertEquals("First-Second-Third", result);
    }
    
    @Test
    void testThreadPoolShutdown() {
        ThreadManager.getCachedThreadPool("TestShutdown", "TestShutdown");
        assertEquals(1, ThreadManager.getActiveThreadPoolCount());
        
        boolean success = ThreadManager.shutdownThreadPool("TestShutdown");
        assertTrue(success);
        assertEquals(0, ThreadManager.getActiveThreadPoolCount());
        
        // Test shutting down non-existent pool
        boolean failureResult = ThreadManager.shutdownThreadPool("NonExistent");
        assertFalse(failureResult);
    }
    
    @Test
    void testShutdownAllThreadPools() {
        ThreadManager.getCachedThreadPool("Test1", "Test1");
        ThreadManager.getCachedThreadPool("Test2", "Test2");
        ThreadManager.getCachedThreadPool("Test3", "Test3");
        
        assertEquals(3, ThreadManager.getActiveThreadPoolCount());
        
        ThreadManager.shutdownAllThreadPools();
        assertEquals(0, ThreadManager.getActiveThreadPoolCount());
    }
    
    @Test
    void testThreadPoolMetrics() {
        ThreadPoolMetrics metrics = new ThreadPoolMetrics();
        
        // Test initial state
        assertEquals(0, metrics.getTotalTasks());
        assertEquals(0, metrics.getFailedTasks());
        assertEquals(0.0, metrics.getFailureRate());
        assertEquals(0.0, metrics.getAverageExecutionTime());
        
        // Record some tasks
        metrics.recordTask(100, false);
        metrics.recordTask(200, true);
        metrics.recordTask(150, false);
        
        assertEquals(3, metrics.getTotalTasks());
        assertEquals(1, metrics.getFailedTasks());
        assertEquals(33.33, metrics.getFailureRate(), 0.1);
        assertEquals(150.0, metrics.getAverageExecutionTime(), 0.1);
        assertEquals(100, metrics.getMinExecutionTime());
        assertEquals(200, metrics.getMaxExecutionTime());
    }
    
    @Test
    void testSystemThreadInfo() {
        SystemThreadInfo info = ThreadMonitor.getSystemThreadInfo();
        
        assertNotNull(info);
        assertTrue(info.getCurrentThreadCount() > 0);
        assertTrue(info.getPeakThreadCount() >= info.getCurrentThreadCount());
        assertTrue(info.getTotalStartedThreadCount() >= info.getCurrentThreadCount());
        assertTrue(info.getNonDaemonThreadCount() >= 0);
    }
}
