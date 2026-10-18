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


package org.fireflyframework.web.error.converter;

import org.fireflyframework.web.error.exceptions.*;
import io.r2dbc.spi.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converter for R2DBC exceptions.
 * Converts R2DBC exceptions to appropriate business exceptions.
 */
@Component
@ConditionalOnClass(Exception.class)
public class R2dbcExceptionConverter implements ExceptionConverter<Exception> {

    /**
     * Creates a new R2dbcExceptionConverter.
     */
    public R2dbcExceptionConverter() {
        // Default constructor
    }

    @Override
    public Class<Exception> getExceptionType() {
        return Exception.class;
    }

    @Override
    public boolean canHandle(Throwable exception) {
        return exception instanceof R2dbcException;
    }

    @Override
    public BusinessException convert(Exception exception) {
        if (!(exception instanceof R2dbcException)) {
            return new ServiceException("R2DBC_ERROR", "Unexpected R2DBC error: " + exception.getMessage());
        }

        R2dbcException r2dbcException = (R2dbcException) exception;
        String sqlState = r2dbcException.getSqlState();
        int errorCode = r2dbcException.getErrorCode();
        String message = r2dbcException.getMessage();

        // Handle transient exceptions (can be retried)
        if (r2dbcException instanceof R2dbcTransientException) {
            if (r2dbcException instanceof R2dbcTimeoutException) {
                return OperationTimeoutException.databaseTimeout("r2dbc-query", 0);
            } else if (r2dbcException instanceof R2dbcRollbackException) {
                return ConcurrencyException.optimisticLockingFailure("database", String.valueOf(errorCode));
            } else {
                // Generic transient resource exception
                return ThirdPartyServiceException.serviceUnavailable("database");
            }
        }

        // Handle non-transient exceptions (should not be retried)
        if (r2dbcException instanceof R2dbcNonTransientException) {
            if (r2dbcException instanceof R2dbcDataIntegrityViolationException) {
                // Try to extract more information from the message
                if (message != null) {
                    if (message.contains("unique constraint") || message.contains("duplicate")) {
                        String column = extractColumnFromConstraint(message);
                        return DataIntegrityException.uniqueConstraintViolation(
                                column != null ? column : "unknown", message);
                    } else if (message.contains("foreign key constraint")) {
                        String constraintName = extractConstraintName(message);
                        String table = extractTableFromMessage(message);
                        String column = extractColumnFromConstraint(message);
                        return DataIntegrityException.foreignKeyConstraintViolation(
                                column != null ? column : (constraintName != null ? constraintName : "unknown"),
                                message,
                                table != null ? table : "referenced relation");
                    }
                }
                return new DataIntegrityException("DATA_INTEGRITY_VIOLATION", "Database integrity violation: " + message);
            } else if (r2dbcException instanceof R2dbcPermissionDeniedException) {
                return new ForbiddenException("DATABASE_PERMISSION_DENIED", "Database permission denied: " + message);
            } else if (r2dbcException instanceof R2dbcBadGrammarException) {
                return InvalidRequestException.withReason("SQL_SYNTAX_ERROR", "Invalid SQL syntax: " + message);
            } else {
                // Generic non-transient resource exception
                return new ServiceException("DATABASE_ERROR", "Database error: " + message);
            }
        }

        // Handle generic R2DBC exceptions based on SQL state if available
        if (sqlState != null) {
            // Connection-related errors
            if (sqlState.startsWith("08")) {
                return ThirdPartyServiceException.serviceUnavailable("database");
            }
            // Data integrity errors
            else if (sqlState.startsWith("23")) {
                if (message != null && (message.contains("unique") || message.contains("duplicate"))) {
                    String column = extractColumnFromConstraint(message);
                    return DataIntegrityException.uniqueConstraintViolation(
                            column != null ? column : "unknown", message);
                } else if (message != null && message.contains("foreign key constraint")) {
                    String constraintName = extractConstraintName(message);
                    String table = extractTableFromMessage(message);
                    String column = extractColumnFromConstraint(message);
                    return DataIntegrityException.foreignKeyConstraintViolation(
                            column != null ? column : (constraintName != null ? constraintName : "unknown"),
                            message,
                            table != null ? table : "referenced relation");
                }
                return new DataIntegrityException("DATA_INTEGRITY_VIOLATION", "Database integrity violation: " + message);
            }
            // Authentication errors
            else if (sqlState.startsWith("28")) {
                return new UnauthorizedException("DATABASE_AUTH_FAILED", "Database authentication failed");
            }
            // Syntax errors
            else if (sqlState.startsWith("42")) {
                return InvalidRequestException.withReason("SQL_SYNTAX_ERROR", "Invalid SQL syntax: " + message);
            }
        }

        // Default fallback
        return new ServiceException("R2DBC_ERROR", "R2DBC database error: " + message);
    }

    // PostgreSQL FK violation message:
    //   insert or update on table "rule" violates foreign key constraint "rule_rule_set_id_fkey"
    // Unique violation message:
    //   duplicate key value violates unique constraint "rule_set_code_key"
    private static final Pattern CONSTRAINT_NAME_PATTERN =
            Pattern.compile("constraint \"([^\"]+)\"");
    private static final Pattern TABLE_NAME_PATTERN =
            Pattern.compile("on table \"([^\"]+)\"");

    /**
     * Extracts the constraint name from a PostgreSQL integrity-violation message.
     * Returns {@code null} when the message does not contain a quoted constraint name.
     */
    private static String extractConstraintName(String message) {
        if (message == null) {
            return null;
        }
        Matcher m = CONSTRAINT_NAME_PATTERN.matcher(message);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Extracts the table name from a PostgreSQL integrity-violation message.
     * Returns {@code null} when the message does not reference a table.
     */
    private static String extractTableFromMessage(String message) {
        if (message == null) {
            return null;
        }
        Matcher m = TABLE_NAME_PATTERN.matcher(message);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Best-effort extraction of the offending column from a PostgreSQL constraint name.
     * Strategy: strip the table prefix and the {@code _fkey}/{@code _key}/{@code _check} suffix
     * (e.g. {@code rule_rule_set_id_fkey} on table {@code rule} -> {@code rule_set_id}).
     * Returns {@code null} when no reasonable column name can be inferred.
     */
    private static String extractColumnFromConstraint(String message) {
        String constraint = extractConstraintName(message);
        if (constraint == null) {
            return null;
        }
        String column = constraint;

        // Strip suffix (Postgres convention)
        for (String suffix : new String[]{"_fkey", "_key", "_check", "_pkey", "_unique"}) {
            if (column.endsWith(suffix)) {
                column = column.substring(0, column.length() - suffix.length());
                break;
            }
        }

        // Strip the table prefix when we know it
        String table = extractTableFromMessage(message);
        if (table != null && column.startsWith(table + "_")) {
            column = column.substring(table.length() + 1);
        }

        return column.isEmpty() ? null : column;
    }
}
