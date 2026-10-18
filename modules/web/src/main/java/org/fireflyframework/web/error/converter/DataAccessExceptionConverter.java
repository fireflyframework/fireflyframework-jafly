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

import org.fireflyframework.web.error.exceptions.BusinessException;
import org.fireflyframework.web.error.exceptions.DataIntegrityException;
import org.fireflyframework.web.error.exceptions.OperationTimeoutException;
import org.fireflyframework.web.error.exceptions.ServiceException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converter for Spring's DataAccessException.
 * Converts various data access exceptions to the appropriate business exceptions.
 */
@Component
@ConditionalOnClass(DataAccessException.class)
public class DataAccessExceptionConverter implements ExceptionConverter<DataAccessException> {

    /**
     * Creates a new DataAccessExceptionConverter.
     */
    public DataAccessExceptionConverter() {
        // Default constructor
    }

    @Override
    public Class<DataAccessException> getExceptionType() {
        return DataAccessException.class;
    }

    @Override
    public BusinessException convert(DataAccessException exception) {
        if (exception instanceof DataIntegrityViolationException) {
            return handleDataIntegrityViolationException((DataIntegrityViolationException) exception);
        } else if (exception instanceof QueryTimeoutException) {
            return handleQueryTimeoutException((QueryTimeoutException) exception);
        } else if (exception instanceof TransientDataAccessException) {
            return handleTransientDataAccessException((TransientDataAccessException) exception);
        } else {
            return new ServiceException(
                    "DATABASE_ERROR",
                    "A database error occurred: " + exception.getMessage()
            );
        }
    }

    private BusinessException handleDataIntegrityViolationException(DataIntegrityViolationException exception) {
        // Walk the cause chain so wrappers like Spring's DataIntegrityViolationException
        // still expose the underlying PostgreSQL constraint metadata.
        String message = collectMessageChain(exception);

        // Try to extract constraint information from the message
        if (message != null) {
            if (message.contains("unique constraint") || message.contains("Duplicate entry")) {
                String column = extractColumnFromConstraint(message);
                return DataIntegrityException.uniqueConstraintViolation(
                        column != null ? column : "unknown", message);
            } else if (message.contains("foreign key constraint") || message.contains("referenced row")) {
                String constraintName = extractConstraintName(message);
                String table = extractTableFromMessage(message);
                String column = extractColumnFromConstraint(message);
                return DataIntegrityException.foreignKeyConstraintViolation(
                        column != null ? column : (constraintName != null ? constraintName : "unknown"),
                        message,
                        table != null ? table : "referenced relation");
            } else if (message.contains("check constraint")) {
                String constraintName = extractConstraintName(message);
                String column = extractColumnFromConstraint(message);
                return DataIntegrityException.checkConstraintViolation(
                        column != null ? column : "unknown",
                        message,
                        constraintName != null ? constraintName : "unknown");
            }
        }

        return new DataIntegrityException(
                "DATA_INTEGRITY_VIOLATION",
                "A data integrity violation occurred: " + message
        );
    }

    private static String collectMessageChain(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = t;
        int guard = 0;
        while (cur != null && guard++ < 10) {
            String m = cur.getMessage();
            if (m != null) {
                if (sb.length() > 0) {
                    sb.append(" | ");
                }
                sb.append(m);
            }
            cur = cur.getCause();
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    // PostgreSQL FK violation message:
    //   insert or update on table "rule" violates foreign key constraint "rule_rule_set_id_fkey"
    private static final Pattern CONSTRAINT_NAME_PATTERN =
            Pattern.compile("constraint \"([^\"]+)\"");
    private static final Pattern TABLE_NAME_PATTERN =
            Pattern.compile("on table \"([^\"]+)\"");

    private static String extractConstraintName(String message) {
        if (message == null) {
            return null;
        }
        Matcher m = CONSTRAINT_NAME_PATTERN.matcher(message);
        return m.find() ? m.group(1) : null;
    }

    private static String extractTableFromMessage(String message) {
        if (message == null) {
            return null;
        }
        Matcher m = TABLE_NAME_PATTERN.matcher(message);
        return m.find() ? m.group(1) : null;
    }

    private static String extractColumnFromConstraint(String message) {
        String constraint = extractConstraintName(message);
        if (constraint == null) {
            return null;
        }
        String column = constraint;
        for (String suffix : new String[]{"_fkey", "_key", "_check", "_pkey", "_unique"}) {
            if (column.endsWith(suffix)) {
                column = column.substring(0, column.length() - suffix.length());
                break;
            }
        }
        String table = extractTableFromMessage(message);
        if (table != null && column.startsWith(table + "_")) {
            column = column.substring(table.length() + 1);
        }
        return column.isEmpty() ? null : column;
    }

    private BusinessException handleQueryTimeoutException(QueryTimeoutException exception) {
        return OperationTimeoutException.databaseTimeout("query", 0);
    }

    private BusinessException handleTransientDataAccessException(TransientDataAccessException exception) {
        return new ServiceException(
                "TRANSIENT_DATABASE_ERROR",
                "A temporary database error occurred: " + exception.getMessage()
        );
    }
}
