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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.Arrays;
import java.util.concurrent.TimeoutException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionConverterServiceTest {

    private ExceptionConverterService converterService;

    @BeforeEach
    void setUp() {
        converterService = new ExceptionConverterService(Arrays.asList(
                new DataAccessExceptionConverter(),
                new HttpClientErrorExceptionConverter(),
                new HttpServerErrorExceptionConverter(),
                new OptimisticLockingFailureExceptionConverter()
        ));
    }

    @Test
    void convertException_BusinessException_ReturnsSameException() {
        // Arrange
        BusinessException originalException = new BusinessException("Test message");

        // Act
        BusinessException result = converterService.convertException(originalException);

        // Assert
        assertSame(originalException, result);
    }

    @Test
    void convertException_DataIntegrityViolationException_ReturnsDataIntegrityException() {
        // Arrange
        DataIntegrityViolationException originalException = new DataIntegrityViolationException("Duplicate entry 'test@example.com' for key 'email'");

        // Act
        BusinessException result = converterService.convertException(originalException);

        // Assert
        // The result is a DataIntegrityException with a UNIQUE_CONSTRAINT_VIOLATION code
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatus());
        assertEquals("UNIQUE_CONSTRAINT_VIOLATION", result.getCode());
        assertTrue(result.getMessage().contains("Duplicate entry"));
    }

    @Test
    void convertException_QueryTimeoutException_ReturnsOperationTimeoutException() {
        // Arrange
        QueryTimeoutException originalException = new QueryTimeoutException("Query timed out");

        // Act
        BusinessException result = converterService.convertException(originalException);

        // Assert
        assertTrue(result instanceof OperationTimeoutException);
        assertEquals(HttpStatus.REQUEST_TIMEOUT, result.getStatus());
        assertEquals("query", ((OperationTimeoutException) result).getOperation());
    }

    @Test
    void convertException_OptimisticLockingFailureException_ReturnsConcurrencyException() {
        // Arrange
        OptimisticLockingFailureException originalException = new OptimisticLockingFailureException("Failed to update entity User with id '123'");

        // Act
        BusinessException result = converterService.convertException(originalException);

        // Assert
        // The result is a ServiceException with a TRANSIENT_DATABASE_ERROR code
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatus());
        assertEquals("TRANSIENT_DATABASE_ERROR", result.getCode());
        assertTrue(result.getMessage().contains("temporary database error"));
    }

    @Test
    void convertException_HttpClientErrorException_ReturnsAppropriateException() {
        // Arrange
        HttpClientErrorException originalException = HttpClientErrorException.NotFound.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null);

        // Act
        BusinessException result = converterService.convertException(originalException);

        // Assert
        assertTrue(result instanceof ResourceNotFoundException);
        assertEquals(HttpStatus.NOT_FOUND, result.getStatus());
        assertEquals("RESOURCE_NOT_FOUND", result.getCode());
    }

    @Test
    void convertException_HttpServerErrorException_ReturnsAppropriateException() {
        // Arrange
        HttpServerErrorException originalException = HttpServerErrorException.ServiceUnavailable.create(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", null, null, null);

        // Act
        BusinessException result = converterService.convertException(originalException);

        // Assert
        assertTrue(result instanceof ThirdPartyServiceException);
        assertEquals(HttpStatus.BAD_GATEWAY, result.getStatus());
        assertEquals("unknown", ((ThirdPartyServiceException) result).getServiceName());
    }

    @Test
    void convertException_UnknownException_ReturnsServiceException() {
        // Arrange
        TimeoutException originalException = new TimeoutException("Operation timed out");

        // Act
        BusinessException result = converterService.convertException(originalException);

        // Assert
        assertTrue(result instanceof ServiceException);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatus());
        assertEquals("UNEXPECTED_ERROR", result.getCode());
        assertTrue(result.getMessage().contains("Operation timed out"));
    }

    // Note: Metadata is now added by the ExceptionMetadataAspect, not directly in the ExceptionConverterService
    // These tests have been moved to ExceptionMetadataAspectTest
}
