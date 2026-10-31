/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.eda.publisher.postgres.impl;

import org.fireflyframework.eda.publisher.postgres.PostgresOutboxWriter;
import org.fireflyframework.eda.publisher.postgres.model.PostgresOutboxAppend;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.r2dbc.core.Parameter;
import reactor.core.publisher.Mono;

import java.util.Objects;

public final class SpringDataPostgresOutboxWriter implements PostgresOutboxWriter {
    private final R2dbcEntityTemplate operations;

    public SpringDataPostgresOutboxWriter(R2dbcEntityTemplate operations) {
        this.operations = Objects.requireNonNull(operations);
    }

    @Override
    public Mono<Long> append(String schema, String table, PostgresOutboxAppend row) {
        return Mono.defer(() -> {
            if (row.id() != null) {
                return Mono.error(new DataIntegrityViolationException("Outbox identity must be database generated"));
            }
            var strategy = operations.getDataAccessStrategy();
            var mapper = strategy.getStatementMapper();
            var target = SqlIdentifier.from(quoted(schema), quoted(table));
            var insert = mapper.createInsert(target);
            for (var entry : strategy.getOutboundRow(row).entrySet()) {
                if (!entry.getKey().getReference().equals("id")) {
                    insert = insert.withColumn(entry.getKey(), entry.getValue());
                }
            }
            insert = insert.withColumn(SqlIdentifier.unquoted("status"), Parameter.from("PENDING"))
                    .withColumn(SqlIdentifier.unquoted("attempts"), Parameter.from(0));
            // The omitted id and created_at retain BIGSERIAL and NOW() database defaults.
            return operations.getDatabaseClient().sql(mapper.getMappedObject(insert)).fetch().rowsUpdated()
                    .switchIfEmpty(Mono.error(new DataIntegrityViolationException("Outbox insert count missing")))
                    .flatMap(count -> count == 1 ? Mono.just(count)
                            : Mono.error(new DataIntegrityViolationException("Exactly one outbox append required")));
        });
    }

    private static SqlIdentifier quoted(String name) {
        return SqlIdentifier.quoted(name.replace("\"", "\"\""));
    }
}
