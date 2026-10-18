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

package org.fireflyframework.eventsourcing.store.r2dbc;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.fireflyframework.eventsourcing.config.EventSourcingProperties;
import org.fireflyframework.eventsourcing.domain.Event;
import org.fireflyframework.eventsourcing.domain.EventStream;
import org.fireflyframework.eventsourcing.store.ConcurrencyException;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for R2DBC Event Store using PostgreSQL with Testcontainers.
 * This provides a production-like testing environment.
 */
@Testcontainers
class PostgreSqlEventStoreIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("firefly_eventsourcing_test")
            .withUsername("firefly_test")
            .withPassword("test_password")
            .withReuse(false);

    private R2dbcEventStore eventStore;
    private DatabaseClient databaseClient;
    private R2dbcEntityTemplate entityTemplate;
    private ConnectionFactory connectionFactory;
    private ReactiveTransactionManager transactionManager;
    private TransactionalOperator transactionalOperator;

    @BeforeEach
    void setUp() {
        // Create PostgreSQL R2DBC connection factory
        connectionFactory = new PostgresqlConnectionFactory(
            PostgresqlConnectionConfiguration.builder()
                .host(postgres.getHost())
                .port(postgres.getFirstMappedPort())
                .database(postgres.getDatabaseName())
                .username(postgres.getUsername())
                .password(postgres.getPassword())
                .build()
        );

        databaseClient = DatabaseClient.create(connectionFactory);
        entityTemplate = new R2dbcEntityTemplate(connectionFactory);
        transactionManager = new R2dbcTransactionManager(connectionFactory);
        transactionalOperator = TransactionalOperator.create(transactionManager);
        
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        EventSourcingProperties properties = new EventSourcingProperties();
        eventStore = new R2dbcEventStore(
            databaseClient, 
            entityTemplate, 
            objectMapper, 
            properties, 
            transactionManager, 
            transactionalOperator,
            connectionFactory
        );

        // Create schema
        createSchema().block();
    }

    @AfterEach
    void tearDown() {
        // Clean up tables
        databaseClient.sql("TRUNCATE TABLE events RESTART IDENTITY CASCADE").fetch().rowsUpdated().block();
        databaseClient.sql("TRUNCATE TABLE snapshots CASCADE").fetch().rowsUpdated().block();
        databaseClient.sql("TRUNCATE TABLE event_outbox RESTART IDENTITY CASCADE").fetch().rowsUpdated().block();
    }

    private Mono<Void> createSchema() {
        String createEventsSql = """
                CREATE TABLE IF NOT EXISTS events (
                    event_id UUID PRIMARY KEY,
                    aggregate_id UUID NOT NULL,
                    aggregate_type VARCHAR(255) NOT NULL,
                    aggregate_version BIGINT NOT NULL,
                    global_sequence BIGSERIAL UNIQUE,
                    event_type VARCHAR(255) NOT NULL,
                    event_data TEXT NOT NULL,
                    metadata TEXT,
                    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                    CONSTRAINT unique_aggregate_version UNIQUE(aggregate_id, aggregate_version)
                )
                """;

        String createSnapshotsSql = """
                CREATE TABLE IF NOT EXISTS snapshots (
                    aggregate_id UUID NOT NULL,
                    aggregate_type VARCHAR(255) NOT NULL,
                    aggregate_version BIGINT NOT NULL,
                    snapshot_data TEXT NOT NULL,
                    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                    PRIMARY KEY (aggregate_id, aggregate_type)
                )
                """;

        String createOutboxSql = """
                CREATE TABLE IF NOT EXISTS event_outbox (
                    outbox_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    aggregate_id UUID NOT NULL,
                    aggregate_type VARCHAR(255) NOT NULL,
                    event_type VARCHAR(255) NOT NULL,
                    event_data TEXT NOT NULL,
                    metadata TEXT,
                    status VARCHAR(50) DEFAULT 'PENDING' NOT NULL,
                    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                    processed_at TIMESTAMP WITH TIME ZONE,
                    retry_count INTEGER DEFAULT 0 NOT NULL,
                    last_error TEXT,
                    next_retry_at TIMESTAMP WITH TIME ZONE,
                    CONSTRAINT valid_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED'))
                )
                """;
        
        return databaseClient.sql(createEventsSql).fetch().rowsUpdated()
                .then(databaseClient.sql(createSnapshotsSql).fetch().rowsUpdated())
                .then(databaseClient.sql(createOutboxSql).fetch().rowsUpdated())
                .then();
    }

    @Test
    void testAppendAndLoadEvents() {
        UUID aggregateId = UUID.randomUUID();
        List<Event> events = List.of(
            new TestAccountCreatedEvent(aggregateId, "12345", BigDecimal.valueOf(1000)),
            new TestMoneyWithdrawnEvent(aggregateId, BigDecimal.valueOf(100)),
            new TestMoneyDepositedEvent(aggregateId, BigDecimal.valueOf(50))
        );

        // Append events (expected version -1 means aggregate doesn't exist yet)
        StepVerifier.create(
                eventStore.appendEvents(aggregateId, "Account", events, -1L)
            )
            .assertNext(stream -> {
                assertEventStream(stream, aggregateId, "Account", 2, events.size());
            })
            .verifyComplete();

        // Load events
        StepVerifier.create(
                eventStore.loadEventStream(aggregateId, "Account")
            )
            .assertNext(stream -> {
                assertEventStream(stream, aggregateId, "Account", 2, events.size());
                // Verify event order and content
                var envelopes = stream.getEvents();
                assertEquals(events.get(0).getEventType(), envelopes.get(0).getEventType());
                assertEquals(events.get(1).getEventType(), envelopes.get(1).getEventType());
                assertEquals(events.get(2).getEventType(), envelopes.get(2).getEventType());
            })
            .verifyComplete();
    }

    @Test
    void testConcurrencyControl() {
        UUID aggregateId = UUID.randomUUID();
        List<Event> initialEvents = List.of(
            new TestAccountCreatedEvent(aggregateId, "12345", BigDecimal.valueOf(1000))
        );

        // Append initial events (version -1 = new aggregate)
        eventStore.appendEvents(aggregateId, "Account", initialEvents, -1L).block();

        // Try to append with wrong expected version
        List<Event> newEvents = List.of(
            new TestMoneyWithdrawnEvent(aggregateId, BigDecimal.valueOf(100))
        );

        StepVerifier.create(
                eventStore.appendEvents(aggregateId, "Account", newEvents, -1L) // Wrong version (aggregate already exists)
            )
            .expectError(ConcurrencyException.class)
            .verify();

        // Try with correct expected version (after 1 event, version is 0)
        StepVerifier.create(
                eventStore.appendEvents(aggregateId, "Account", newEvents, 0L) // Correct version
            )
            .assertNext(stream -> {
                assertEventStream(stream, aggregateId, "Account", 1, 1);
            })
            .verifyComplete();
    }

    @Test
    void testLoadEventsFromVersion() {
        UUID aggregateId = UUID.randomUUID();
        List<Event> events = List.of(
            new TestAccountCreatedEvent(aggregateId, "12345", BigDecimal.valueOf(1000)),
            new TestMoneyWithdrawnEvent(aggregateId, BigDecimal.valueOf(100)),
            new TestMoneyDepositedEvent(aggregateId, BigDecimal.valueOf(50)),
            new TestMoneyWithdrawnEvent(aggregateId, BigDecimal.valueOf(25))
        );

        // Append events
        eventStore.appendEvents(aggregateId, "Account", events, -1L).block();

        // Load from version 2 (4 events appended, so versions are 0,1,2,3)
        StepVerifier.create(
                eventStore.loadEventStream(aggregateId, "Account", 2L)
            )
            .assertNext(stream -> {
                assertEventStream(stream, aggregateId, "Account", 3, 2); // Events from version 2-3
                assertEquals(2L, stream.getFromVersion());
            })
            .verifyComplete();
    }

    @Test
    void testLoadEventsInRange() {
        UUID aggregateId = UUID.randomUUID();
        List<Event> events = List.of(
            new TestAccountCreatedEvent(aggregateId, "12345", BigDecimal.valueOf(1000)),
            new TestMoneyWithdrawnEvent(aggregateId, BigDecimal.valueOf(100)),
            new TestMoneyDepositedEvent(aggregateId, BigDecimal.valueOf(50)),
            new TestMoneyWithdrawnEvent(aggregateId, BigDecimal.valueOf(25)),
            new TestMoneyDepositedEvent(aggregateId, BigDecimal.valueOf(75))
        );

        // Append events
        eventStore.appendEvents(aggregateId, "Account", events, -1L).block();

        // Load versions 2-4 (5 events appended, so versions are 0,1,2,3,4)
        StepVerifier.create(
                eventStore.loadEventStream(aggregateId, "Account", 2L, 4L)
            )
            .assertNext(stream -> {
                assertEquals(3, stream.size()); // Versions 2, 3, 4
                assertEquals(2L, stream.getFromVersion());
                assertEquals(4L, stream.getCurrentVersion());
            })
            .verifyComplete();
    }

    @Test
    void testGetAggregateVersion() {
        UUID aggregateId = UUID.randomUUID();
        
        // Non-existent aggregate
        StepVerifier.create(
                eventStore.getAggregateVersion(aggregateId, "Account")
            )
            .expectNext(-1L)
            .verifyComplete();

        // Add some events
        List<Event> events = List.of(
            new TestAccountCreatedEvent(aggregateId, "12345", BigDecimal.valueOf(1000)),
            new TestMoneyWithdrawnEvent(aggregateId, BigDecimal.valueOf(100))
        );
        
        eventStore.appendEvents(aggregateId, "Account", events, -1L).block();

        // Check version (2 events appended, so version is 1)
        StepVerifier.create(
                eventStore.getAggregateVersion(aggregateId, "Account")
            )
            .expectNext(1L)
            .verifyComplete();
    }

    @Test
    void testStreamAllEvents() {
        // Create events for multiple aggregates
        UUID account1 = UUID.randomUUID();
        UUID account2 = UUID.randomUUID();
        
        eventStore.appendEvents(account1, "Account", List.of(
            new TestAccountCreatedEvent(account1, "12345", BigDecimal.valueOf(1000))
        ), -1L).block();

        eventStore.appendEvents(account2, "Account", List.of(
            new TestAccountCreatedEvent(account2, "67890", BigDecimal.valueOf(2000))
        ), -1L).block();

        // Stream all events
        StepVerifier.create(
                eventStore.streamAllEvents().take(Duration.ofSeconds(5))
            )
            .expectNextCount(2)
            .verifyComplete();
    }

    @Test
    void testStreamEventsByType() {
        UUID aggregateId = UUID.randomUUID();
        List<Event> events = List.of(
            new TestAccountCreatedEvent(aggregateId, "12345", BigDecimal.valueOf(1000)),
            new TestMoneyWithdrawnEvent(aggregateId, BigDecimal.valueOf(100)),
            new TestMoneyDepositedEvent(aggregateId, BigDecimal.valueOf(50))
        );

        eventStore.appendEvents(aggregateId, "Account", events, -1L).block();

        // Stream only withdrawal events
        StepVerifier.create(
                eventStore.streamEventsByType(List.of("test.money.withdrawn"))
            )
            .assertNext(envelope -> {
                assertEquals("test.money.withdrawn", envelope.getEventType());
            })
            .verifyComplete();
    }

    @Test
    void testEventStoreHealth() {
        StepVerifier.create(
                eventStore.isHealthy()
            )
            .expectNext(true)
            .verifyComplete();
    }

    @Test
    void testAppendEventsWithMetadata() {
        UUID aggregateId = UUID.randomUUID();
        List<Event> events = List.of(
            new TestAccountCreatedEvent(aggregateId, "12345", BigDecimal.valueOf(1000))
        );
        
        Map<String, Object> metadata = Map.of(
            "userId", "user-123",
            "correlationId", "corr-456"
        );

        StepVerifier.create(
                eventStore.appendEvents(aggregateId, "Account", events, -1L, metadata)
            )
            .assertNext(stream -> {
                var envelope = stream.getFirstEvent();
                assertEquals("user-123", envelope.getMetadataValue("userId"));
                assertEquals("corr-456", envelope.getMetadataValue("correlationId"));
            })
            .verifyComplete();
    }

    private void assertEventStream(EventStream stream, UUID expectedAggregateId, 
                                  String expectedType, long expectedVersion, int expectedSize) {
        assertEquals(expectedAggregateId, stream.getAggregateId());
        assertEquals(expectedType, stream.getAggregateType());
        assertEquals(expectedVersion, stream.getCurrentVersion());
        assertEquals(expectedSize, stream.size());
    }

    // Test event implementations with proper Jackson configuration
    @JsonTypeName("test.account.created")
    record TestAccountCreatedEvent(
            UUID aggregateId,
            String accountNumber,
            BigDecimal initialBalance
    ) implements Event {
        @Override
        public String getEventType() {
            return "test.account.created";
        }
        
        @Override
        public UUID getAggregateId() {
            return aggregateId;
        }

        @Override
        public Instant getEventTimestamp() {
            return Instant.now();
        }

        @Override
        public Map<String, Object> getMetadata() {
            return Map.of("source", "test", "version", "1.0");
        }
    }

    @JsonTypeName("test.money.withdrawn")
    record TestMoneyWithdrawnEvent(
            UUID aggregateId,
            BigDecimal amount
    ) implements Event {
        @Override
        public String getEventType() {
            return "test.money.withdrawn";
        }
        
        @Override
        public UUID getAggregateId() {
            return aggregateId;
        }

        @Override
        public Instant getEventTimestamp() {
            return Instant.now();
        }

        @Override
        public Map<String, Object> getMetadata() {
            return Map.of("source", "test", "version", "1.0");
        }
    }

    @JsonTypeName("test.money.deposited")
    record TestMoneyDepositedEvent(
            UUID aggregateId,
            BigDecimal amount
    ) implements Event {
        @Override
        public String getEventType() {
            return "test.money.deposited";
        }
        
        @Override
        public UUID getAggregateId() {
            return aggregateId;
        }

        @Override
        public Instant getEventTimestamp() {
            return Instant.now();
        }

        @Override
        public Map<String, Object> getMetadata() {
            return Map.of("source", "test", "version", "1.0");
        }
    }
}