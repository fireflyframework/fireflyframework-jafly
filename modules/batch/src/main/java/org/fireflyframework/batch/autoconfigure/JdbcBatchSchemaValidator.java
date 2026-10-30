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
package org.fireflyframework.batch.autoconfigure;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

final class JdbcBatchSchemaValidator {
    private static final Map<String, String> COLUMNS =
            Map.of(
                    "BATCH_JOB_INSTANCE", "JOB_INSTANCE_ID,VERSION,JOB_NAME,JOB_KEY",
                    "BATCH_JOB_EXECUTION",
                            "JOB_EXECUTION_ID,VERSION,JOB_INSTANCE_ID,CREATE_TIME,START_TIME,END_TIME,STATUS,EXIT_CODE,EXIT_MESSAGE,LAST_UPDATED",
                    "BATCH_JOB_EXECUTION_PARAMS",
                            "JOB_EXECUTION_ID,PARAMETER_NAME,PARAMETER_TYPE,PARAMETER_VALUE,IDENTIFYING",
                    "BATCH_STEP_EXECUTION",
                            "STEP_EXECUTION_ID,VERSION,STEP_NAME,JOB_EXECUTION_ID,CREATE_TIME,START_TIME,END_TIME,STATUS,COMMIT_COUNT,READ_COUNT,FILTER_COUNT,WRITE_COUNT,READ_SKIP_COUNT,WRITE_SKIP_COUNT,PROCESS_SKIP_COUNT,ROLLBACK_COUNT,EXIT_CODE,EXIT_MESSAGE,LAST_UPDATED",
                    "BATCH_STEP_EXECUTION_CONTEXT",
                            "STEP_EXECUTION_ID,SHORT_CONTEXT,SERIALIZED_CONTEXT",
                    "BATCH_JOB_EXECUTION_CONTEXT",
                            "JOB_EXECUTION_ID,SHORT_CONTEXT,SERIALIZED_CONTEXT");
    private static final Set<String> NUMBERS =
            Set.of(
                    "JOB_INSTANCE_ID",
                    "VERSION",
                    "JOB_EXECUTION_ID",
                    "STEP_EXECUTION_ID",
                    "COMMIT_COUNT",
                    "READ_COUNT",
                    "FILTER_COUNT",
                    "WRITE_COUNT",
                    "READ_SKIP_COUNT",
                    "WRITE_SKIP_COUNT",
                    "PROCESS_SKIP_COUNT",
                    "ROLLBACK_COUNT");
    private static final Set<String> TIMES =
            Set.of("CREATE_TIME", "START_TIME", "END_TIME", "LAST_UPDATED");
    private static final String[] SEQUENCES = {
        "BATCH_JOB_INSTANCE_SEQ", "BATCH_JOB_EXECUTION_SEQ", "BATCH_STEP_EXECUTION_SEQ"
    };

    private JdbcBatchSchemaValidator() {}

    static void validate(DataSource source) {
        try (Connection connection = source.getConnection()) {
            if (!"PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())) {
                throw new IllegalStateException(
                        "Durable Batch currently requires PostgreSQL metadata");
            }
            boolean wasReadOnly = connection.isReadOnly();
            try {
                connection.setReadOnly(true);
                for (var table : COLUMNS.entrySet()) {
                    try (var statement = connection.createStatement();
                            var result =
                                    statement.executeQuery(
                                            "SELECT "
                                                    + table.getValue()
                                                    + " FROM "
                                                    + table.getKey()
                                                    + " WHERE 1=0")) {
                        var metadata = result.getMetaData();
                        String[] columns = table.getValue().split(",");
                        for (int index = 0; index < columns.length; index++) {
                            String column = columns[index];
                            int expected =
                                    NUMBERS.contains(column)
                                            ? Types.BIGINT
                                            : TIMES.contains(column)
                                                    ? Types.TIMESTAMP
                                                    : column.equals("IDENTIFYING")
                                                            ? Types.CHAR
                                                            : Types.VARCHAR;
                            if (metadata.getColumnType(index + 1) != expected) {
                                throw new IllegalStateException(
                                        "Durable Batch has incompatible native column: "
                                                + table.getKey()
                                                + "."
                                                + column);
                            }
                        }
                    }
                    try (var statement =
                            connection.prepareStatement(
                                    "SELECT has_table_privilege(current_user,CAST(? AS"
                                        + " regclass),'SELECT') AND"
                                        + " has_table_privilege(current_user,CAST(? AS"
                                        + " regclass),'INSERT') AND"
                                        + " has_table_privilege(current_user,CAST(? AS"
                                        + " regclass),'UPDATE')")) {
                        for (int index = 1; index <= 3; index++) {
                            statement.setString(
                                    index, table.getKey().toLowerCase(java.util.Locale.ROOT));
                        }
                        try (var result = statement.executeQuery()) {
                            if (!result.next() || !result.getBoolean(1)) {
                                throw new IllegalStateException(
                                        "Durable Batch lacks native table access: "
                                                + table.getKey());
                            }
                        }
                    }
                }
                for (String sequence : SEQUENCES) {
                    try (var statement =
                            connection.prepareStatement(
                                    "SELECT c.relkind='S' AND"
                                        + " has_sequence_privilege(current_user,c.oid,'USAGE') AND"
                                        + " has_sequence_privilege(current_user,c.oid,'SELECT')"
                                        + " FROM pg_catalog.pg_class c WHERE c.oid=CAST(? AS"
                                        + " regclass)")) {
                        statement.setString(1, sequence.toLowerCase(java.util.Locale.ROOT));
                        try (var result = statement.executeQuery()) {
                            if (!result.next() || !result.getBoolean(1)) {
                                throw new IllegalStateException(
                                        "Durable Batch lacks native sequence access: " + sequence);
                            }
                        }
                    }
                }
            } finally {
                connection.setReadOnly(wasReadOnly);
            }
        } catch (SQLException error) {
            throw new IllegalStateException(
                    "Durable Batch requires the provisioned Spring Batch 6.0.5 PostgreSQL schema",
                    error);
        }
    }
}
