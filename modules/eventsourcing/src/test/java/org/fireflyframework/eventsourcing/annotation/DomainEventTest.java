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

package org.fireflyframework.eventsourcing.annotation;

import org.fireflyframework.eventsourcing.domain.AbstractDomainEvent;
import org.fireflyframework.eventsourcing.domain.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for @DomainEvent annotation functionality.
 */
class DomainEventTest {

    @Test
    void shouldReadEventTypeFromAnnotation() {
        // Given
        UUID aggregateId = UUID.randomUUID();
        TestEvent event = TestEvent.builder()
                .aggregateId(aggregateId)
                .data("test-data")
                .build();

        // When
        String eventType = event.getEventType();

        // Then
        assertEquals("test.event", eventType);
    }

    @Test
    void shouldReadEventTypeFromAnnotationWithAbstractDomainEvent() {
        // Given
        UUID aggregateId = UUID.randomUUID();
        AccountCreatedEvent event = AccountCreatedEvent.builder()
                .aggregateId(aggregateId)
                .accountNumber("12345")
                .initialBalance(BigDecimal.valueOf(1000))
                .build();

        // When
        String eventType = event.getEventType();

        // Then
        assertEquals("account.created", eventType);
    }

    @Test
    void shouldThrowExceptionWhenAnnotationMissing() {
        // Given
        UUID aggregateId = UUID.randomUUID();
        EventWithoutAnnotation event = new EventWithoutAnnotation(aggregateId);

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, event::getEventType);
        assertTrue(exception.getMessage().contains("must be annotated with @DomainEvent"));
        assertTrue(exception.getMessage().contains("EventWithoutAnnotation"));
    }

    @Test
    void shouldReadAnnotationMetadata() {
        // Given
        DomainEvent annotation = AccountCreatedEvent.class.getAnnotation(DomainEvent.class);

        // Then
        assertNotNull(annotation);
        assertEquals("account.created", annotation.value());
        assertEquals("Account creation event", annotation.description());
        assertEquals(1, annotation.version());
        assertTrue(annotation.publishable());
        assertArrayEquals(new String[]{"account", "lifecycle"}, annotation.tags());
    }

    @Test
    void shouldUseDefaultAnnotationValues() {
        // Given
        DomainEvent annotation = TestEvent.class.getAnnotation(DomainEvent.class);

        // Then
        assertNotNull(annotation);
        assertEquals("test.event", annotation.value());
        assertEquals("", annotation.description());
        assertEquals(1, annotation.version());
        assertTrue(annotation.publishable());
        assertArrayEquals(new String[]{}, annotation.tags());
    }

    @Test
    void shouldSupportNonPublishableEvents() {
        // Given
        DomainEvent annotation = InternalEvent.class.getAnnotation(DomainEvent.class);

        // Then
        assertNotNull(annotation);
        assertFalse(annotation.publishable());
    }

    @Test
    void shouldSupportEventVersioning() {
        // Given
        DomainEvent annotation = VersionedEvent.class.getAnnotation(DomainEvent.class);

        // Then
        assertNotNull(annotation);
        assertEquals(2, annotation.version());
    }

    @Test
    void shouldSupportEventTags() {
        // Given
        DomainEvent annotation = TaggedEvent.class.getAnnotation(DomainEvent.class);

        // Then
        assertNotNull(annotation);
        assertArrayEquals(new String[]{"financial", "audit-required", "high-priority"}, annotation.tags());
    }

    // Test event classes

    @DomainEvent("test.event")
    @SuperBuilder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class TestEvent extends AbstractDomainEvent {
        private String data;
    }

    @DomainEvent(
        value = "account.created",
        description = "Account creation event",
        version = 1,
        publishable = true,
        tags = {"account", "lifecycle"}
    )
    @SuperBuilder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class AccountCreatedEvent extends AbstractDomainEvent {
        private String accountNumber;
        private BigDecimal initialBalance;
    }

    @DomainEvent(value = "internal.event", publishable = false)
    @SuperBuilder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class InternalEvent extends AbstractDomainEvent {
        private String data;
    }

    @DomainEvent(value = "versioned.event", version = 2)
    @SuperBuilder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class VersionedEvent extends AbstractDomainEvent {
        private String data;
    }

    @DomainEvent(value = "tagged.event", tags = {"financial", "audit-required", "high-priority"})
    @SuperBuilder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class TaggedEvent extends AbstractDomainEvent {
        private String data;
    }

    // Event without annotation - should fail
    static class EventWithoutAnnotation implements Event {
        private final UUID aggregateId;

        EventWithoutAnnotation(UUID aggregateId) {
            this.aggregateId = aggregateId;
        }

        @Override
        public UUID getAggregateId() {
            return aggregateId;
        }
    }
}

