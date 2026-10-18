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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class R2dbcExceptionConverterTest {

    private R2dbcExceptionConverter converter;

    @BeforeEach
    void setUp() {
        converter = new R2dbcExceptionConverter();
    }

    @Test
    void canHandle_R2dbcException_ReturnsTrue() {
        R2dbcException exception = mock(R2dbcException.class);
        assertTrue(converter.canHandle(exception));
    }

    @Test
    void canHandle_OtherException_ReturnsFalse() {
        Exception exception = new RuntimeException("Test exception");
        assertFalse(converter.canHandle(exception));
    }

    @Test
    void convert_R2dbcTimeoutException_ReturnsOperationTimeoutException() {
        R2dbcTimeoutException exception = mock(R2dbcTimeoutException.class);
        when(exception.getMessage()).thenReturn("Query timeout");

        BusinessException result = converter.convert(exception);

        assertTrue(result instanceof OperationTimeoutException);
        assertEquals(HttpStatus.REQUEST_TIMEOUT, result.getStatus());
        assertEquals("DATABASE_TIMEOUT", result.getCode());
    }

    @Test
    void convert_R2dbcRollbackException_ReturnsConcurrencyException() {
        R2dbcRollbackException exception = mock(R2dbcRollbackException.class);
        when(exception.getMessage()).thenReturn("Transaction rolled back");
        when(exception.getErrorCode()).thenReturn(1234);

        BusinessException result = converter.convert(exception);

        assertTrue(result instanceof ConcurrencyException);
        assertEquals(HttpStatus.CONFLICT, result.getStatus());
        assertEquals("OPTIMISTIC_LOCKING_FAILURE", result.getCode());
    }

    @Test
    void convert_R2dbcDataIntegrityViolationException_ReturnsDataIntegrityException() {
        R2dbcDataIntegrityViolationException exception = mock(R2dbcDataIntegrityViolationException.class);
        when(exception.getMessage()).thenReturn("Duplicate key value violates unique constraint");

        BusinessException result = converter.convert(exception);

        assertTrue(result instanceof DataIntegrityException);
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatus());
        assertEquals("UNIQUE_CONSTRAINT_VIOLATION", result.getCode());
    }

    @Test
    void convert_R2dbcPermissionDeniedException_ReturnsForbiddenException() {
        R2dbcPermissionDeniedException exception = mock(R2dbcPermissionDeniedException.class);
        when(exception.getMessage()).thenReturn("Permission denied");

        BusinessException result = converter.convert(exception);

        assertTrue(result instanceof ForbiddenException);
        assertEquals(HttpStatus.FORBIDDEN, result.getStatus());
        assertEquals("DATABASE_PERMISSION_DENIED", result.getCode());
    }

    @Test
    void convert_R2dbcBadGrammarException_ReturnsInvalidRequestException() {
        R2dbcBadGrammarException exception = mock(R2dbcBadGrammarException.class);
        when(exception.getMessage()).thenReturn("Syntax error in SQL statement");

        BusinessException result = converter.convert(exception);

        assertTrue(result instanceof InvalidRequestException);
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatus());
        assertEquals("SQL_SYNTAX_ERROR", result.getCode());
    }

    @Test
    void convert_GenericR2dbcException_WithSqlState23_ReturnsDataIntegrityException() {
        R2dbcException exception = mock(R2dbcException.class);
        when(exception.getMessage()).thenReturn("Integrity constraint violation");
        when(exception.getSqlState()).thenReturn("23505");

        BusinessException result = converter.convert(exception);

        assertTrue(result instanceof DataIntegrityException);
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatus());
        assertEquals("DATA_INTEGRITY_VIOLATION", result.getCode());
    }

    @Test
    void convert_GenericR2dbcException_WithSqlState42_ReturnsInvalidRequestException() {
        R2dbcException exception = mock(R2dbcException.class);
        when(exception.getMessage()).thenReturn("Syntax error");
        when(exception.getSqlState()).thenReturn("42000");

        BusinessException result = converter.convert(exception);

        assertTrue(result instanceof InvalidRequestException);
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatus());
        assertEquals("SQL_SYNTAX_ERROR", result.getCode());
    }

    @Test
    void convert_GenericR2dbcException_WithNoSqlState_ReturnsServiceException() {
        R2dbcException exception = mock(R2dbcException.class);
        when(exception.getMessage()).thenReturn("Generic database error");
        when(exception.getSqlState()).thenReturn(null);

        BusinessException result = converter.convert(exception);

        assertTrue(result instanceof ServiceException);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatus());
        assertEquals("R2DBC_ERROR", result.getCode());
    }

    @Test
    void convert_NonR2dbcException_ReturnsServiceException() {
        Exception exception = new RuntimeException("Not an R2DBC exception");

        BusinessException result = converter.convert(exception);

        assertTrue(result instanceof ServiceException);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatus());
        assertEquals("R2DBC_ERROR", result.getCode());
    }
}
