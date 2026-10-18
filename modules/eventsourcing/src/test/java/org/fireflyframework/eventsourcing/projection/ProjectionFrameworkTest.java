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

package org.fireflyframework.eventsourcing.projection;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Event Sourcing projection framework components.
 */
class ProjectionFrameworkTest {
    
    private MeterRegistry meterRegistry;
    
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
    }
    
    @Test
    void shouldCreateProjectionMetrics() {
        // Given
        String projectionName = "test-projection";
        
        // When
        ProjectionMetrics metrics = new ProjectionMetrics(projectionName, meterRegistry);
        
        // Then
        assertThat(metrics.getProjectionName()).isEqualTo(projectionName);
        assertThat(metrics.getCurrentPosition()).isEqualTo(0L);
        assertThat(metrics.getLagAmount()).isEqualTo(0L);
        
        // Trigger counter creation by recording an event
        metrics.recordEventProcessed();

        // Verify metrics are registered
        assertThat(meterRegistry.find("firefly.eventsourcing.projection.events.processed")
                .tag("projection", projectionName)
                .counter())
                .isNotNull();
                
        assertThat(meterRegistry.find("firefly.eventsourcing.projection.position.current")
                .tag("projection", projectionName)
                .gauge())
                .isNotNull();
    }
    
    @Test
    void shouldRecordMetricsCorrectly() {
        // Given
        ProjectionMetrics metrics = new ProjectionMetrics("test-projection", meterRegistry);
        
        // When
        metrics.recordEventProcessed();
        metrics.recordEventProcessed();
        metrics.recordEventFailed();
        metrics.updatePosition(50L, 100L);
        
        // Then
        assertThat(meterRegistry.find("firefly.eventsourcing.projection.events.processed")
                .counter().count()).isEqualTo(2.0);
                
        assertThat(meterRegistry.find("firefly.eventsourcing.projection.events.failed")
                .counter().count()).isEqualTo(1.0);
                
        assertThat(metrics.getCurrentPosition()).isEqualTo(50L);
        assertThat(metrics.getLagAmount()).isEqualTo(50L);
    }
    
    @Test
    void shouldCreateProjectionHealth() {
        // Given
        String projectionName = "test-projection";
        Long currentPosition = 80L;
        Long latestGlobalSequence = 100L;
        Long lag = 20L;
        Boolean isHealthy = true;
        
        // When
        ProjectionHealth health = ProjectionHealth.builder()
                .projectionName(projectionName)
                .currentPosition(currentPosition)
                .latestGlobalSequence(latestGlobalSequence)
                .lag(lag)
                .isHealthy(isHealthy)
                .lastUpdated(Instant.now())
                .processingRate(15.5)
                .build();
        
        // Then
        assertThat(health.getProjectionName()).isEqualTo(projectionName);
        assertThat(health.getCurrentPosition()).isEqualTo(currentPosition);
        assertThat(health.getLatestGlobalSequence()).isEqualTo(latestGlobalSequence);
        assertThat(health.getLag()).isEqualTo(lag);
        assertThat(health.getIsHealthy()).isEqualTo(isHealthy);
        assertThat(health.getProcessingRate()).isEqualTo(15.5);
        assertThat(health.getCompletionPercentage()).isEqualTo(0.8); // 80/100
        assertThat(health.getStatusDescription()).startsWith("Healthy:");
    }
    
    @Test
    void shouldCalculateCompletionPercentageCorrectly() {
        // Test various scenarios
        ProjectionHealth health1 = ProjectionHealth.builder()
                .projectionName("test")
                .currentPosition(50L)
                .latestGlobalSequence(100L)
                .lag(50L)
                .isHealthy(true)
                .lastUpdated(Instant.now())
                .build();
        assertThat(health1.getCompletionPercentage()).isEqualTo(0.5);
        
        // Test edge case - no events yet
        ProjectionHealth health2 = ProjectionHealth.builder()
                .projectionName("test")
                .currentPosition(0L)
                .latestGlobalSequence(0L)
                .lag(0L)
                .isHealthy(true)
                .lastUpdated(Instant.now())
                .build();
        assertThat(health2.getCompletionPercentage()).isEqualTo(1.0);
        
        // Test fully caught up
        ProjectionHealth health3 = ProjectionHealth.builder()
                .projectionName("test")
                .currentPosition(100L)
                .latestGlobalSequence(100L)
                .lag(0L)
                .isHealthy(true)
                .lastUpdated(Instant.now())
                .build();
        assertThat(health3.getCompletionPercentage()).isEqualTo(1.0);
    }
}