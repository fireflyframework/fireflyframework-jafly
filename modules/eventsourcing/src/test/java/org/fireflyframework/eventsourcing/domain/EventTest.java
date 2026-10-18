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

package org.fireflyframework.eventsourcing.domain;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Event interface and implementations.
 */
class EventTest {

    @Test
    void testEventBasicProperties() {
        UUID aggregateId = UUID.randomUUID();
        TestEvent event = new TestEvent(aggregateId, "test-data");
        
        assertEquals(aggregateId, event.getAggregateId());
        assertEquals("test.event", event.getEventType());
        assertEquals(1, event.getEventVersion());
        assertNotNull(event.getEventTimestamp());
        assertTrue(event.getMetadata().isEmpty());
    }

    @Test
    void testEventWithCustomMetadata() {
        UUID aggregateId = UUID.randomUUID();
        TestEventWithMetadata event = new TestEventWithMetadata(aggregateId, "test-data");
        
        Map<String, Object> metadata = event.getMetadata();
        assertEquals("test-source", metadata.get("source"));
        assertEquals("user-123", metadata.get("userId"));
    }

    @Test
    void testEventWithCustomTimestamp() {
        UUID aggregateId = UUID.randomUUID();
        Instant customTime = Instant.parse("2023-01-01T10:00:00Z");
        TestEventWithTimestamp event = new TestEventWithTimestamp(aggregateId, customTime);
        
        assertEquals(customTime, event.getEventTimestamp());
    }

    @Test
    void testEventEnvelopeCreation() {
        UUID aggregateId = UUID.randomUUID();
        TestEvent event = new TestEvent(aggregateId, "test-data");
        
        StoredEventEnvelope envelope = StoredEventEnvelope.of(event, "TestAggregate", 1L, 100L);
        
        assertNotNull(envelope.getEventId());
        assertEquals(event, envelope.getEvent());
        assertEquals(aggregateId, envelope.getAggregateId());
        assertEquals("TestAggregate", envelope.getAggregateType());
        assertEquals(1L, envelope.getAggregateVersion());
        assertEquals(100L, envelope.getGlobalSequence());
        assertEquals("test.event", envelope.getEventType());
        assertNotNull(envelope.getCreatedAt());
        assertTrue(envelope.getMetadata().isEmpty());
    }

    @Test
    void testEventEnvelopeWithMetadata() {
        UUID aggregateId = UUID.randomUUID();
        TestEvent event = new TestEvent(aggregateId, "test-data");
        Map<String, Object> metadata = Map.of(
            "correlationId", "corr-123",
            "causationId", "cause-456",
            "userId", "user-789"
        );
        
        StoredEventEnvelope envelope = StoredEventEnvelope.of(event, "TestAggregate", 1L, 100L, metadata);
        
        assertEquals(metadata, envelope.getMetadata());
        assertEquals("corr-123", envelope.getCorrelationId());
        assertEquals("cause-456", envelope.getCausationId());
        assertEquals("user-789", envelope.getUserId());
    }

    @Test
    void testEventEnvelopeMetadataAccess() {
        UUID aggregateId = UUID.randomUUID();
        TestEvent event = new TestEvent(aggregateId, "test-data");
        Map<String, Object> metadata = Map.of(
            "stringValue", "test",
            "intValue", 42,
            "boolValue", true
        );
        
        StoredEventEnvelope envelope = StoredEventEnvelope.of(event, "TestAggregate", 1L, 100L, metadata);
        
        assertEquals("test", envelope.getMetadataValue("stringValue"));
        assertEquals(42, envelope.getMetadataValue("intValue"));
        assertEquals(true, envelope.getMetadataValue("boolValue"));
        assertNull(envelope.getMetadataValue("nonExistent"));
        
        assertEquals("test", envelope.getMetadataValue("stringValue", String.class));
        assertEquals(Integer.valueOf(42), envelope.getMetadataValue("intValue", Integer.class));
        assertNull(envelope.getMetadataValue("stringValue", Integer.class)); // Wrong type
    }

    // Test event implementations
    @JsonTypeName("test.event")
    static class TestEvent implements Event {
        private final UUID aggregateId;
        private final String data;

        public TestEvent(UUID aggregateId, String data) {
            this.aggregateId = aggregateId;
            this.data = data;
        }

        @Override
        public UUID getAggregateId() {
            return aggregateId;
        }

        @Override
        public String getEventType() {
            return "test.event";
        }

        public String getData() {
            return data;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof TestEvent that)) return false;
            return aggregateId.equals(that.aggregateId) && data.equals(that.data);
        }

        @Override
        public int hashCode() {
            return aggregateId.hashCode();
        }
    }

    @JsonTypeName("test.event.with.metadata")
    static class TestEventWithMetadata implements Event {
        private final UUID aggregateId;
        private final String data;

        public TestEventWithMetadata(UUID aggregateId, String data) {
            this.aggregateId = aggregateId;
            this.data = data;
        }

        @Override
        public UUID getAggregateId() {
            return aggregateId;
        }

        @Override
        public String getEventType() {
            return "test.event.with.metadata";
        }

        @Override
        public Map<String, Object> getMetadata() {
            return Map.of(
                "source", "test-source",
                "userId", "user-123"
            );
        }

        public String getData() {
            return data;
        }
    }

    @JsonTypeName("test.event.with.timestamp")
    static class TestEventWithTimestamp implements Event {
        private final UUID aggregateId;
        private final Instant timestamp;

        public TestEventWithTimestamp(UUID aggregateId, Instant timestamp) {
            this.aggregateId = aggregateId;
            this.timestamp = timestamp;
        }

        @Override
        public UUID getAggregateId() {
            return aggregateId;
        }

        @Override
        public String getEventType() {
            return "test.event.with.timestamp";
        }

        @Override
        public Instant getEventTimestamp() {
            return timestamp;
        }
    }
}