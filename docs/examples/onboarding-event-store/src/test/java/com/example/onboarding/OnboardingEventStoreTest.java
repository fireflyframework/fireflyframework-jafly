package com.example.onboarding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.R2dbcException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.fireflyframework.eventsourcing.config.EventSourcingProperties;
import org.fireflyframework.eventsourcing.domain.Event;
import org.fireflyframework.eventsourcing.store.ConcurrencyException;
import org.fireflyframework.eventsourcing.store.r2dbc.R2dbcEventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class OnboardingEventStoreTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("onboarding")
            .withUsername("onboarding_test")
            .withPassword("onboarding_test")
            .withInitScript("events.sql");

    private DatabaseClient database;
    private R2dbcEventStore store;
    private OnboardingEventService service;

    @BeforeEach
    void connectToPostgres() {
        var connectionFactory = new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
                .host(postgres.getHost()).port(postgres.getFirstMappedPort())
                .database(postgres.getDatabaseName()).username(postgres.getUsername())
                .password(postgres.getPassword()).build());
        database = DatabaseClient.create(connectionFactory);
        var manager = new R2dbcTransactionManager(connectionFactory);
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mapper.registerSubtypes(new NamedType(ApplicationSubmitted.class, "onboarding.application.submitted"),
                new NamedType(ApplicationApproved.class, "onboarding.application.approved"));
        store = new R2dbcEventStore(database, new R2dbcEntityTemplate(connectionFactory), mapper,
                new EventSourcingProperties(), manager, TransactionalOperator.create(manager), connectionFactory);
        service = new OnboardingEventService(store);
    }

    @Test
    void reconstructsTheApplicationAndItsApprovalFromStoredEvents() {
        UUID id = UUID.randomUUID();
        var application = new OnboardingAggregate(id);
        application.submit("Ada Lovelace", "ada@example.com");
        service.save(application).block(TIMEOUT);
        assertThat(application.hasUncommittedEvents()).isFalse();

        var submitted = service.load(id).block(TIMEOUT);
        assertThat(submitted).isNotSameAs(application);
        assertThat(submitted.getApplicantName()).isEqualTo("Ada Lovelace");
        assertThat(submitted.getEmail()).isEqualTo("ada@example.com");
        assertThat(submitted.getStatus()).isEqualTo(OnboardingAggregate.Status.SUBMITTED);
        assertThat(submitted.getCurrentVersion()).isZero();
        submitted.approve("reviewer-1");
        service.save(submitted).block(TIMEOUT);

        var reloaded = service.load(id).block(TIMEOUT);
        assertThat(reloaded.getStatus()).isEqualTo(OnboardingAggregate.Status.APPROVED);
        assertThat(reloaded.getReviewer()).isEqualTo("reviewer-1");
        assertThat(reloaded.getCurrentVersion()).isEqualTo(1);
        assertThat(reloaded.hasUncommittedEvents()).isFalse();
        var stream = store.loadEventStream(id, OnboardingAggregate.TYPE).block(TIMEOUT);
        assertThat(stream.getEvents()).hasSize(2);
        assertThat(stream.getEvents().get(0).getEvent()).isInstanceOf(ApplicationSubmitted.class);
        assertThat(stream.getEvents().get(1).getEvent()).isInstanceOf(ApplicationApproved.class);
        assertThat(rowCount(id)).isEqualTo(2);
    }

    @Test
    void rejectsAStaleWriterWithoutOverwritingTheCommittedApproval() {
        UUID id = UUID.randomUUID();
        var application = new OnboardingAggregate(id);
        application.submit("Grace Hopper", "grace@example.com");
        service.save(application).block(TIMEOUT);
        var firstReviewer = service.load(id).block(TIMEOUT);
        var staleReviewer = service.load(id).block(TIMEOUT);
        firstReviewer.approve("reviewer-a");
        staleReviewer.approve("reviewer-b");
        service.save(firstReviewer).block(TIMEOUT);

        StepVerifier.create(service.save(staleReviewer))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ConcurrencyException.class);
                    var conflict = (ConcurrencyException) error;
                    assertThat(conflict.getExpectedVersion()).isZero();
                    assertThat(conflict.getActualVersion()).isEqualTo(1);
                }).verify(TIMEOUT);
        assertThat(staleReviewer.hasUncommittedEvents()).isTrue();
        assertThat(service.load(id).block(TIMEOUT).getReviewer()).isEqualTo("reviewer-a");
        assertThat(rowCount(id)).isEqualTo(2);
    }

    @Test
    void rollsBackTheWholeAppendWhenOneEventViolatesTheSchema() {
        UUID id = UUID.randomUUID();
        var submitted = new ApplicationSubmitted(id, "Ada Lovelace", "ada@example.com");
        var invalid = new InvalidStoredEvent(id);
        StepVerifier.create(store.appendEvents(id, OnboardingAggregate.TYPE,
                        List.of(submitted, invalid), -1L))
                .expectErrorSatisfies(error -> {
                    Throwable cause = error;
                    while (cause != null && !(cause instanceof R2dbcException)) {
                        cause = cause.getCause();
                    }
                    assertThat(cause).isInstanceOf(R2dbcException.class);
                    assertThat(((R2dbcException) cause).getSqlState()).isEqualTo("22001");
                }).verify(TIMEOUT);
        assertThat(rowCount(id)).isZero();
        assertThat(store.getAggregateVersion(id, OnboardingAggregate.TYPE).block(TIMEOUT)).isEqualTo(-1);
    }

    @Test
    void refusesApprovalBeforeSubmissionAndLeavesUnknownApplicationsAbsent() {
        UUID id = UUID.randomUUID();
        var application = new OnboardingAggregate(id);
        assertThatThrownBy(() -> application.approve("reviewer-1"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(application.hasUncommittedEvents()).isFalse();
        StepVerifier.create(service.load(id)).verifyComplete();
        assertThat(rowCount(id)).isZero();
    }

    private long rowCount(UUID id) {
        return database.sql("SELECT count(*) AS total FROM events WHERE aggregate_id = :id")
                .bind("id", id).map((row, metadata) -> row.get("total", Long.class))
                .one().block(TIMEOUT);
    }

    record InvalidStoredEvent(UUID aggregateId) implements Event {
        @Override public UUID getAggregateId() { return aggregateId; }
        @Override public String getEventType() { return "x".repeat(256); }
    }
}
