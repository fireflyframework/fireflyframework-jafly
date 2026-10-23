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
package org.fireflyframework.batch.fixture;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import javax.sql.DataSource;

public abstract class PostgresBatchFixture {
    protected static PostgreSQLContainer<?> database;
    protected static String role;
    protected static String password;

    @BeforeAll
    static void openPrivateDatabase() throws Exception {
        database =
                new PostgreSQLContainer<>("postgres:17-alpine")
                        .withDatabaseName("owned_batch_fixture")
                        .withUsername("owned_bootstrap")
                        .withPassword(UUID.randomUUID().toString());
        database.start();
        role = "batch_runtime_" + UUID.randomUUID().toString().replace("-", "");
        password = UUID.randomUUID().toString();
        try (var connection = owner().getConnection();
                var statement = connection.createStatement()) {
            statement.execute(
                    "CREATE ROLE "
                            + role
                            + " LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD '"
                            + password
                            + "'");
        }
    }

    @AfterAll
    static void closePrivateDatabase() {
        if (database != null) database.stop();
    }

    protected static PGSimpleDataSource owner() {
        var source = new PGSimpleDataSource();
        source.setURL(database.getJdbcUrl());
        source.setUser(database.getUsername());
        source.setPassword(database.getPassword());
        return source;
    }

    protected static DataSource schema(String variant) throws Exception {
        String name = "owned_" + UUID.randomUUID().toString().replace("-", "");
        String ddl;
        try (var resource =
                PostgresBatchFixture.class.getResourceAsStream(
                        "/org/springframework/batch/core/schema-postgresql.sql")) {
            if (resource == null)
                throw new IllegalStateException("Native Batch PostgreSQL schema missing");
            ddl = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (variant.equals("missing-column")) ddl = ddl.replace("\tFILTER_COUNT BIGINT,", "");
        if (variant.equals("wrong-column-type"))
            ddl = ddl.replace("\tFILTER_COUNT BIGINT,", "\tFILTER_COUNT VARCHAR(100),");
        if (variant.equals("missing-sequence"))
            ddl =
                    ddl.replace(
                            "CREATE SEQUENCE BATCH_JOB_INSTANCE_SEQ MAXVALUE 9223372036854775807 NO"
                                + " CYCLE;",
                            "");
        try (var connection = owner().getConnection();
                var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + name);
            connection.setSchema(name);
            if (!variant.equals("empty")) statement.execute(ddl);
            if (!variant.equals("no-access")) {
                statement.execute("GRANT USAGE ON SCHEMA " + name + " TO " + role);
                statement.execute(
                        "GRANT "
                                + (variant.equals("read-only") ? "SELECT" : "SELECT,INSERT,UPDATE")
                                + " ON ALL TABLES IN SCHEMA "
                                + name
                                + " TO "
                                + role);
                statement.execute(
                        "GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA " + name + " TO " + role);
            }
        }
        var runtime = new PGSimpleDataSource();
        runtime.setURL(database.getJdbcUrl());
        runtime.setUser(role);
        runtime.setPassword(password);
        runtime.setCurrentSchema(name);
        return runtime;
    }

    protected static ThreadPoolTaskExecutor executor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("owned-batch-");
        return executor;
    }
}
