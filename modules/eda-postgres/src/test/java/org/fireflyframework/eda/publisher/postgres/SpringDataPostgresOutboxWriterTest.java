/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.eda.publisher.postgres;

import io.r2dbc.postgresql.codec.Json;
import org.fireflyframework.eda.publisher.postgres.impl.SpringDataPostgresOutboxWriter;
import org.fireflyframework.eda.publisher.postgres.model.PostgresOutboxAppend;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.r2dbc.core.DefaultReactiveDataAccessStrategy;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.FetchSpec;
import org.springframework.r2dbc.core.PreparedOperation;
import org.springframework.r2dbc.core.binding.BindTarget;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SpringDataPostgresOutboxWriterTest {
    private final DatabaseClient database = mock(DatabaseClient.class);
    private final DatabaseClient.GenericExecuteSpec execution = mock(DatabaseClient.GenericExecuteSpec.class);
    private final List<Object> values = new ArrayList<>();
    private String sql;

    @SuppressWarnings("unchecked")
    private SpringDataPostgresOutboxWriter writer(Mono<Long> count) {
        FetchSpec<Map<String, Object>> fetch = mock(FetchSpec.class);
        when(database.sql(any(Supplier.class))).thenAnswer(call -> {
            PreparedOperation<?> statement = call.getArgument(0);
            sql = statement.toQuery();
            statement.bindTo(new BindTarget() {
                public void bind(String name, Object value) { values.add(value); }
                public void bind(int index, Object value) { values.add(value); }
                public void bindNull(String name, Class<?> type) { values.add(null); }
                public void bindNull(int index, Class<?> type) { values.add(null); }
            });
            return execution;
        });
        when(execution.fetch()).thenReturn(fetch);
        when(fetch.rowsUpdated()).thenReturn(count);
        return new SpringDataPostgresOutboxWriter(new R2dbcEntityTemplate(database,
                new DefaultReactiveDataAccessStrategy(PostgresDialect.INSTANCE)));
    }

    private PostgresOutboxAppend row() {
        return new PostgresOutboxAppend(null, "orders.created", "orders_created", null,
                "exact bytes é".getBytes(StandardCharsets.UTF_8), Json.of("{\"amount\":12.3400}"),
                null, "POSTGRES", "tenant-connection", "synthetic.Event");
    }

    @Test
    void configuredIdentifiersAreQuotedAndInitialColumnsKeepTheirNativeBindings() {
        assertThat(writer(Mono.just(1L)).append("custom\"schema", "custom\"table", row()).block()).isEqualTo(1L);

        assertThat(sql).startsWith("INSERT INTO \"custom\"\"schema\".\"custom\"\"table\"");
        assertThat(sql).doesNotContain("created_at", "processed_at", "failed_at", "error_message");
        assertThat(values).contains("orders.created", "orders_created", "POSTGRES", "tenant-connection", "synthetic.Event", "PENDING", 0);
        assertThat(values.stream().filter(value -> value == null)).hasSize(2);
        assertThat(values.stream().filter(Json.class::isInstance).map(Json.class::cast).map(Json::asString))
                .containsExactly("{\"amount\":12.3400}");
        assertThat(values.stream().filter(byte[].class::isInstance).map(byte[].class::cast).findFirst().orElseThrow())
                .containsExactly("exact bytes é".getBytes(StandardCharsets.UTF_8));
    }

    static Stream<Mono<Long>> invalidCounts() {
        return Stream.of(Mono.empty(), Mono.just(0L), Mono.just(2L));
    }

    @ParameterizedTest
    @MethodSource("invalidCounts")
    void absentOrNonSingleInsertCountsCannotAcknowledgePublication(Mono<Long> count) {
        StepVerifier.create(writer(count).append("public", "firefly_eda_outbox", row()))
                .expectError(DataIntegrityViolationException.class).verify();
    }
}
