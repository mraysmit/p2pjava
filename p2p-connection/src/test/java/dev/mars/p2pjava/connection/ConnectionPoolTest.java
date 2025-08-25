package dev.mars.p2pjava.connection;

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


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the ConnectionPool class.
 */
public class ConnectionPoolTest {

    private ConnectionPool connectionPool;
    private static final int MAX_CONNECTIONS = 5;
    private static final long CONNECTION_TIMEOUT_MS = 1000;
    
    @BeforeEach
    void setUp() {
        // Create a new connection pool for each test
        connectionPool = new ConnectionPool(MAX_CONNECTIONS, CONNECTION_TIMEOUT_MS);
    }
    
    @AfterEach
    void tearDown() {
        // Shutdown the connection pool after each test
        if (connectionPool != null) {
            connectionPool.shutdown();
        }
    }
    
    @Test
    void testExecuteWithConnectionCallable() throws Exception {
        // Execute a task with a connection
        String result = connectionPool.executeWithConnection(() -> "test-result");
        
        // Verify the result
        assertEquals("test-result", result, "Task result should match expected value");
        
        // Verify connection statistics
        assertEquals(0, connectionPool.getActiveConnections(), "Active connections should be 0 after task completion");
        assertEquals(1, connectionPool.getTotalConnections(), "Total connections should be 1");
    }
    
    @Test
    void testExecuteWithConnectionRunnable() throws Exception {
        // Create a flag to track task execution
        final boolean[] taskExecuted = {false};
        
        // Execute a task with a connection
        connectionPool.executeWithConnection(() -> taskExecuted[0] = true);
        
        // Verify the task was executed
        assertTrue(taskExecuted[0], "Task should be executed");
        
        // Verify connection statistics
        assertEquals(0, connectionPool.getActiveConnections(), "Active connections should be 0 after task completion");
        assertEquals(1, connectionPool.getTotalConnections(), "Total connections should be 1");
    }
    
    @Test
    void testConcurrentExecution() throws Exception {
        // Create a counter to track concurrent executions
        final AtomicInteger concurrentExecutions = new AtomicInteger(0);
        final AtomicInteger maxConcurrentExecutions = new AtomicInteger(0);
        
        // Create a latch to synchronize task start
        final CountDownLatch startLatch = new CountDownLatch(1);
        
        // Create a latch to wait for all tasks to complete
        final int TASK_COUNT = 10;
        final CountDownLatch completionLatch = new CountDownLatch(TASK_COUNT);
        
        // Create and execute tasks
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < TASK_COUNT; i++) {
            Thread thread = new Thread(() -> {
                try {
                    // Wait for the start signal
                    startLatch.await();
                    
                    // Execute a task with a connection
                    connectionPool.executeWithConnection(() -> {
                        // Increment the concurrent executions counter
                        int current = concurrentExecutions.incrementAndGet();
                        
                        // Update the max concurrent executions counter
                        int max;
                        do {
                            max = maxConcurrentExecutions.get();
                            if (current <= max) break;
                        } while (!maxConcurrentExecutions.compareAndSet(max, current));
                        
                        // Simulate some work
                        try {
                            TimeUnit.MILLISECONDS.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        
                        // Decrement the concurrent executions counter
                        concurrentExecutions.decrementAndGet();
                        
                        return null;
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    completionLatch.countDown();
                }
            });
            thread.start();
            threads.add(thread);
        }
        
        // Start all tasks at once
        startLatch.countDown();
        
        // Wait for all tasks to complete
        assertTrue(completionLatch.await(5, TimeUnit.SECONDS), "All tasks should complete within timeout");
        
        // Verify the max concurrent executions
        assertTrue(maxConcurrentExecutions.get() <= MAX_CONNECTIONS, 
                "Max concurrent executions should not exceed max connections");
        
        // Verify connection statistics
        assertEquals(0, connectionPool.getActiveConnections(), "Active connections should be 0 after all tasks complete");
        assertTrue(connectionPool.getTotalConnections() <= MAX_CONNECTIONS, 
                "Total connections should not exceed max connections");
    }
    
    @Test
    void testConnectionTimeout() throws Exception {
        // Create a connection pool with a very short timeout
        ConnectionPool shortTimeoutPool = new ConnectionPool(1, 10); // 10ms timeout
        
        // Create a latch to hold the first task
        final CountDownLatch taskLatch = new CountDownLatch(1);
        
        // Execute a task that holds the connection
        Thread thread1 = new Thread(() -> {
            try {
                shortTimeoutPool.executeWithConnection(() -> {
                    try {
                        // Hold the connection until signaled
                        taskLatch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread1.start();
        
        // Wait for the first task to acquire the connection
        TimeUnit.MILLISECONDS.sleep(50);
        
        // Try to execute another task, which should time out
        final boolean[] timeoutOccurred = {false};
        Thread thread2 = new Thread(() -> {
            try {
                shortTimeoutPool.executeWithConnection(() -> "result");
            } catch (Exception e) {
                timeoutOccurred[0] = true;
            }
        });
        thread2.start();
        
        // Wait for the second task to time out
        TimeUnit.MILLISECONDS.sleep(100);
        
        // Release the first task
        taskLatch.countDown();
        
        // Wait for threads to complete
        thread1.join(1000);
        thread2.join(1000);
        
        // Verify that a timeout occurred
        assertTrue(timeoutOccurred[0], "Connection acquisition should time out");
        
        // Verify connection timeout statistics
        assertTrue(shortTimeoutPool.getConnectionTimeouts() > 0, "Connection timeouts should be greater than 0");
        
        // Clean up
        shortTimeoutPool.shutdown();
    }
    
    @Test
    void testGetStatistics() {
        // Execute a task with a connection
        try {
            connectionPool.executeWithConnection(() -> "test-result");
        } catch (Exception e) {
            fail("Task execution should not throw an exception");
        }
        
        // Get the statistics
        String statistics = connectionPool.getStatistics();
        
        // Verify the statistics contains expected information
        assertNotNull(statistics, "Statistics should not be null");
        assertTrue(statistics.contains("active"), "Statistics should contain active connections information");
        assertTrue(statistics.contains("total"), "Statistics should contain total connections information");
        assertTrue(statistics.contains("wait time"), "Statistics should contain wait time information");
    }
    
    @Test
    void testGetters() {
        // Verify the getters return the expected values
        assertEquals(MAX_CONNECTIONS, connectionPool.getMaxConnections(), "Max connections should match");
        assertEquals(CONNECTION_TIMEOUT_MS, connectionPool.getConnectionTimeoutMs(), "Connection timeout should match");
    }
}