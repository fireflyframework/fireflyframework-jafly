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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.UUID;

class PostgresBatchRoleProvisioningTest extends PostgresBatchFixture {
    private static String freshRole() {
        return "batch_role_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String freshPassword() {
        return UUID.randomUUID().toString();
    }

    private void provision(String name, String secret) throws Exception {
        try (var connection = owner().getConnection()) {
            createRuntimeRole(connection, name, secret);
        }
    }

    private long roleCount() throws Exception {
        try (var connection = owner().getConnection();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT count(*) FROM pg_catalog.pg_roles")) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private void assertLoginAndPrivileges(String name, String secret) throws Exception {
        var runtime = new PGSimpleDataSource();
        runtime.setURL(database.getJdbcUrl());
        runtime.setUser(name);
        runtime.setPassword(secret);
        try (var connection = runtime.getConnection();
                var query =
                        connection.prepareStatement(
                                """
                                SELECT current_user, rolcanlogin, rolsuper, rolbypassrls,
                                       rolcreatedb, rolcreaterole, rolreplication,
                                       (SELECT count(*) FROM pg_catalog.pg_auth_members WHERE member = r.oid)
                                FROM pg_catalog.pg_roles r WHERE rolname = ?
                                """)) {
            query.setString(1, name);
            try (var rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo(name);
                assertThat(rows.getBoolean(2)).isTrue();
                for (int column = 3; column <= 7; column++) {
                    assertThat(rows.getBoolean(column)).isFalse();
                }
                assertThat(rows.getLong(8)).isZero();
                assertThat(rows.next()).isFalse();
            }
            try (var forbidden = connection.createStatement()) {
                assertThatExceptionOfType(SQLException.class)
                        .isThrownBy(
                                () -> forbidden.execute("CREATE ROLE forbidden_fixture_privilege"))
                        .satisfies(failure -> assertThat(failure.getSQLState()).isEqualTo("42501"));
                assertThatExceptionOfType(SQLException.class)
                        .isThrownBy(
                                () ->
                                        forbidden.execute(
                                                "CREATE DATABASE forbidden_fixture_database"))
                        .satisfies(failure -> assertThat(failure.getSQLState()).isEqualTo("42501"));
            }
        }
    }

    @Test
    void ordinaryRuntimeRoleLogsInWithoutElevatedPrivileges() throws Exception {
        String name = freshRole();
        String secret = freshPassword();
        provision(name, secret);
        assertLoginAndPrivileges(name, secret);
    }

    @Test
    void quotedRoleAndPasswordRemainDataAndCreateExactlyOneRole() throws Exception {
        String name = "batch_😀\"';--" + UUID.randomUUID().toString().replace("-", "");
        String secret = freshPassword() + "'\"; Ω😀";
        long before = roleCount();
        provision(name, secret);
        assertThat(roleCount()).isEqualTo(before + 1);
        assertLoginAndPrivileges(name, secret);
    }

    @Test
    void identifierByteLimitPreservesExactMultibyteRole() throws Exception {
        String[] names = {"b".repeat(61) + "é", "c".repeat(59) + "😀"};
        for (String name : names) {
            assertThat(name.getBytes(StandardCharsets.UTF_8)).hasSize(63);
            String secret = freshPassword();
            provision(name, secret);
            assertLoginAndPrivileges(name, secret);
        }
    }

    @Test
    void invalidRoleNamesFailBeforeAnyRoleEffect() throws Exception {
        long before = roleCount();
        String[] invalid = {
            null,
            "",
            " ",
            "a\0b",
            "b".repeat(64),
            "b".repeat(62) + "é",
            "b".repeat(60) + "😀",
            "bad\uD800"
        };
        for (String name : invalid) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> provision(name, freshPassword()))
                    .withMessage("Invalid runtime role credentials");
            assertThat(roleCount()).isEqualTo(before);
        }
    }

    @Test
    void invalidPasswordsFailBeforeAnyRoleEffect() throws Exception {
        long before = roleCount();
        String[] invalid = {null, "", " ", "a\0b", "bad\uDC00"};
        for (String secret : invalid) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> provision(freshRole(), secret))
                    .withMessage("Invalid runtime role credentials");
            assertThat(roleCount()).isEqualTo(before);
        }
    }

    @Test
    void nativeProvisioningFailureRetainsSqlStateWithoutCredentialDiagnostics() throws Exception {
        String name = freshRole();
        String secret = freshPassword();
        provision(name, secret);
        assertThatExceptionOfType(SQLException.class)
                .isThrownBy(() -> provision(name, secret))
                .satisfies(
                        failure -> {
                            assertThat(failure.getSQLState()).isEqualTo("42710");
                            var output = new java.io.StringWriter();
                            failure.printStackTrace(new java.io.PrintWriter(output));
                            assertThat(output.toString()).doesNotContain(secret);
                            assertThat(failure.getMessage())
                                    .isEqualTo("Runtime role provisioning failed");
                        });
        assertLoginAndPrivileges(name, secret);
    }

    @Test
    void provisioningFunctionIsTemporaryInvokerWithFixedSearchPath() throws Exception {
        try (var connection = owner().getConnection()) {
            createRuntimeRole(connection, freshRole(), freshPassword());
            try (var statement =
                            connection.prepareStatement(
                                    """
                                    SELECT p.prosecdef, p.proconfig, n.nspname
                                    FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON p.pronamespace = n.oid
                                    WHERE p.oid = pg_catalog.to_regprocedure('pg_temp.create_batch_runtime_role(text,text)')
                                    """);
                    var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBoolean(1)).isFalse();
                assertThat((String[]) rows.getArray(2).getArray())
                        .containsExactly("search_path=pg_catalog");
                assertThat(rows.getString(3)).startsWith("pg_temp_");
                assertThat(rows.next()).isFalse();
            }
        }
    }
}
