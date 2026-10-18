/*
 * Copyright 2024-2026 Firefly Software Foundation
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

package org.fireflyframework.resilience;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sliding window implementation for tracking call success/failure rates.
 * 
 * <p>This implementation uses a circular buffer approach to maintain
 * a sliding window of the most recent calls for failure rate calculation.
 * It's thread-safe and optimized for high-throughput scenarios.
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
public class SlidingWindow {
    
    private final int windowSize;
    private final boolean[] callResults; // true = success, false = failure
    private final AtomicInteger currentIndex = new AtomicInteger(0);
    private final AtomicLong totalCalls = new AtomicLong(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final Object lock = new Object();
    
    /**
     * Creates a new sliding window with the specified size.
     *
     * @param windowSize the maximum number of calls to track
     */
    public SlidingWindow(int windowSize) {
        if (windowSize <= 0) {
            throw new IllegalArgumentException("Window size must be positive");
        }
        this.windowSize = windowSize;
        this.callResults = new boolean[windowSize];
    }
    
    /**
     * Records a successful call.
     */
    public void recordSuccess() {
        recordCall(true);
    }
    
    /**
     * Records a failed call.
     */
    public void recordFailure() {
        recordCall(false);
    }
    
    /**
     * Records a call result in the sliding window.
     */
    private void recordCall(boolean success) {
        synchronized (lock) {
            int index = currentIndex.getAndIncrement() % windowSize;
            long totalCallsValue = totalCalls.incrementAndGet();
            
            // If we're overwriting an existing entry, adjust the counters
            if (totalCallsValue > windowSize) {
                boolean oldResult = callResults[index];
                if (oldResult) {
                    successCount.decrementAndGet();
                } else {
                    failureCount.decrementAndGet();
                }
            }
            
            // Record the new result
            callResults[index] = success;
            if (success) {
                successCount.incrementAndGet();
            } else {
                failureCount.incrementAndGet();
            }
        }
    }
    
    /**
     * Gets the current failure rate as a percentage.
     *
     * @return failure rate (0.0 to 100.0)
     */
    public double getFailureRate() {
        synchronized (lock) {
            long totalCallsInWindow = Math.min(totalCalls.get(), windowSize);
            if (totalCallsInWindow == 0) {
                return 0.0;
            }
            return (double) failureCount.get() / totalCallsInWindow * 100.0;
        }
    }
    
    /**
     * Gets the current success rate as a percentage.
     *
     * @return success rate (0.0 to 100.0)
     */
    public double getSuccessRate() {
        return 100.0 - getFailureRate();
    }
    
    /**
     * Gets the total number of calls recorded.
     *
     * @return total calls (may exceed window size)
     */
    public long getTotalCalls() {
        return totalCalls.get();
    }
    
    /**
     * Gets the number of calls currently in the window.
     *
     * @return calls in window (0 to windowSize)
     */
    public int getCallsInWindow() {
        return (int) Math.min(totalCalls.get(), windowSize);
    }
    
    /**
     * Gets the number of successful calls in the window.
     *
     * @return successful calls count
     */
    public int getSuccessCount() {
        return successCount.get();
    }
    
    /**
     * Gets the number of failed calls in the window.
     *
     * @return failed calls count
     */
    public int getFailureCount() {
        return failureCount.get();
    }
    
    /**
     * Resets the sliding window to its initial state.
     */
    public void reset() {
        synchronized (lock) {
            currentIndex.set(0);
            totalCalls.set(0);
            successCount.set(0);
            failureCount.set(0);
            // Clear the array
            for (int i = 0; i < windowSize; i++) {
                callResults[i] = false;
            }
        }
    }
    
    /**
     * Gets the window size.
     *
     * @return window size
     */
    public int getWindowSize() {
        return windowSize;
    }
    
    /**
     * Checks if the window has enough data for reliable failure rate calculation.
     *
     * @param minimumCalls minimum number of calls required
     * @return true if window has sufficient data
     */
    public boolean hasSufficientData(int minimumCalls) {
        return getCallsInWindow() >= minimumCalls;
    }
    
    @Override
    public String toString() {
        return String.format(
            "SlidingWindow[size=%d, calls=%d, success=%d, failure=%d, failureRate=%.1f%%]",
            windowSize, getCallsInWindow(), successCount.get(), failureCount.get(), getFailureRate()
        );
    }
}
