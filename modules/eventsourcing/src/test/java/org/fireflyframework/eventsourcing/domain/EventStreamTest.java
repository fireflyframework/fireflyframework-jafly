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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EventStream.
 */
class EventStreamTest {

    @Test
    void testEmptyEventStream() {
        UUID aggregateId = UUID.randomUUID();
        EventStream stream = EventStream.empty(aggregateId, "TestAggregate");
        
        assertEquals(aggregateId, stream.getAggregateId());
        assertEquals("TestAggregate", stream.getAggregateType());
        assertEquals(0L, stream.getCurrentVersion());
        assertEquals(0L, stream.getFromVersion());
        assertTrue(stream.isEmpty());
        assertEquals(0, stream.size());
        assertNull(stream.getFirstEvent());
        assertNull(stream.getLastEvent());
    }

    @Test
    void testEventStreamWithEvents() {
        UUID aggregateId = UUID.randomUUID();
        
        // Create test events
        Event event1 = new TestEvent(aggregateId, "event1");
        Event event2 = new TestEvent(aggregateId, "event2");
        Event event3 = new TestEvent(aggregateId, "event3");
        
        // Create envelopes with different versions
        StoredEventEnvelope envelope1 = StoredEventEnvelope.of(event1, "TestAggregate", 1L, 100L);
        StoredEventEnvelope envelope2 = StoredEventEnvelope.of(event2, "TestAggregate", 2L, 101L);
        StoredEventEnvelope envelope3 = StoredEventEnvelope.of(event3, "TestAggregate", 3L, 102L);
        
        List<StoredEventEnvelope> envelopes = List.of(envelope1, envelope2, envelope3);
        EventStream stream = EventStream.of(aggregateId, "TestAggregate", envelopes);
        
        assertEquals(aggregateId, stream.getAggregateId());
        assertEquals("TestAggregate", stream.getAggregateType());
        assertEquals(3L, stream.getCurrentVersion());
        assertEquals(1L, stream.getFromVersion());
        assertFalse(stream.isEmpty());
        assertEquals(3, stream.size());
        assertEquals(envelope1, stream.getFirstEvent());
        assertEquals(envelope3, stream.getLastEvent());
    }

    @Test
    void testEventStreamFiltering() {
        UUID aggregateId = UUID.randomUUID();
        
        // Create test events with versions 1, 2, 3, 4, 5
        List<StoredEventEnvelope> envelopes = List.of(
            StoredEventEnvelope.of(new TestEvent(aggregateId, "event1"), "TestAggregate", 1L, 100L),
            StoredEventEnvelope.of(new TestEvent(aggregateId, "event2"), "TestAggregate", 2L, 101L),
            StoredEventEnvelope.of(new TestEvent(aggregateId, "event3"), "TestAggregate", 3L, 102L),
            StoredEventEnvelope.of(new TestEvent(aggregateId, "event4"), "TestAggregate", 4L, 103L),
            StoredEventEnvelope.of(new TestEvent(aggregateId, "event5"), "TestAggregate", 5L, 104L)
        );
        
        EventStream stream = EventStream.of(aggregateId, "TestAggregate", envelopes);
        
        // Test getEventsFromVersion
        List<StoredEventEnvelope> fromVersion3 = stream.getEventsFromVersion(3L);
        assertEquals(3, fromVersion3.size());
        assertEquals(3L, fromVersion3.get(0).getAggregateVersion());
        assertEquals(5L, fromVersion3.get(2).getAggregateVersion());
        
        // Test getEventsToVersion
        List<StoredEventEnvelope> toVersion3 = stream.getEventsToVersion(3L);
        assertEquals(3, toVersion3.size());
        assertEquals(1L, toVersion3.get(0).getAggregateVersion());
        assertEquals(3L, toVersion3.get(2).getAggregateVersion());
        
        // Test getEventsInRange
        List<StoredEventEnvelope> inRange = stream.getEventsInRange(2L, 4L);
        assertEquals(3, inRange.size());
        assertEquals(2L, inRange.get(0).getAggregateVersion());
        assertEquals(4L, inRange.get(2).getAggregateVersion());
    }

    @Test
    void testEventStreamValidation() {
        UUID aggregateId = UUID.randomUUID();
        UUID differentAggregateId = UUID.randomUUID();
        
        Event validEvent = new TestEvent(aggregateId, "valid");
        Event invalidEvent = new TestEvent(differentAggregateId, "invalid");
        
        StoredEventEnvelope validEnvelope = StoredEventEnvelope.of(validEvent, "TestAggregate", 1L, 100L);
        StoredEventEnvelope invalidEnvelope = StoredEventEnvelope.of(invalidEvent, "TestAggregate", 2L, 101L);
        
        List<StoredEventEnvelope> envelopes = List.of(validEnvelope, invalidEnvelope);
        
        // Should throw exception for mismatched aggregate IDs
        assertThrows(IllegalArgumentException.class, () -> {
            EventStream.of(aggregateId, "TestAggregate", envelopes);
        });
    }

    @Test
    void testEventStreamWithDifferentAggregateTypes() {
        UUID aggregateId = UUID.randomUUID();
        
        Event event = new TestEvent(aggregateId, "test");
        StoredEventEnvelope envelope1 = StoredEventEnvelope.of(event, "TestAggregate", 1L, 100L);
        StoredEventEnvelope envelope2 = StoredEventEnvelope.of(event, "DifferentAggregate", 2L, 101L);
        
        List<StoredEventEnvelope> envelopes = List.of(envelope1, envelope2);
        
        // Should throw exception for mismatched aggregate types
        assertThrows(IllegalArgumentException.class, () -> {
            EventStream.of(aggregateId, "TestAggregate", envelopes);
        });
    }

    @Test
    void testEventStreamWithNullOrEmptyEvents() {
        UUID aggregateId = UUID.randomUUID();
        
        // Null events
        EventStream nullStream = EventStream.of(aggregateId, "TestAggregate", null);
        assertTrue(nullStream.isEmpty());
        assertEquals(0, nullStream.size());
        
        // Empty events
        EventStream emptyStream = EventStream.of(aggregateId, "TestAggregate", List.of());
        assertTrue(emptyStream.isEmpty());
        assertEquals(0, emptyStream.size());
    }

    @Test
    void testEventStreamFilteringWithEmptyStream() {
        UUID aggregateId = UUID.randomUUID();
        EventStream emptyStream = EventStream.empty(aggregateId, "TestAggregate");
        
        assertTrue(emptyStream.getEventsFromVersion(1L).isEmpty());
        assertTrue(emptyStream.getEventsToVersion(10L).isEmpty());
        assertTrue(emptyStream.getEventsInRange(1L, 10L).isEmpty());
    }

    @Test
    void testEventStreamVersionCalculation() {
        UUID aggregateId = UUID.randomUUID();
        
        // Create events with non-sequential versions (3, 1, 5, 2)
        List<StoredEventEnvelope> envelopes = List.of(
            StoredEventEnvelope.of(new TestEvent(aggregateId, "event3"), "TestAggregate", 3L, 102L),
            StoredEventEnvelope.of(new TestEvent(aggregateId, "event1"), "TestAggregate", 1L, 100L),
            StoredEventEnvelope.of(new TestEvent(aggregateId, "event5"), "TestAggregate", 5L, 104L),
            StoredEventEnvelope.of(new TestEvent(aggregateId, "event2"), "TestAggregate", 2L, 101L)
        );
        
        EventStream stream = EventStream.of(aggregateId, "TestAggregate", envelopes);
        
        // Should calculate min and max versions correctly
        assertEquals(5L, stream.getCurrentVersion()); // Max version
        assertEquals(1L, stream.getFromVersion());    // Min version
    }

    // Helper test event class
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
            return aggregateId.hashCode() + data.hashCode();
        }
    }
}