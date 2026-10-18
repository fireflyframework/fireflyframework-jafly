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
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.MethodNotAllowedException;
import org.springframework.web.server.NotAcceptableStatusException;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebFluxExceptionConverterTest {

    private WebFluxExceptionConverter converter;

    @BeforeEach
    void setUp() {
        converter = new WebFluxExceptionConverter();
    }

    @Test
    void canHandle_MethodNotAllowedException_ReturnsTrue() {
        // Arrange
        MethodNotAllowedException exception = new MethodNotAllowedException("GET", Collections.emptySet());

        // Act
        boolean result = converter.canHandle(exception);

        // Assert
        assertTrue(result);
    }

    @Test
    void canHandle_UnsupportedMediaTypeStatusException_ReturnsTrue() {
        // Arrange
        UnsupportedMediaTypeStatusException exception = mock(UnsupportedMediaTypeStatusException.class);

        // Act
        boolean result = converter.canHandle(exception);

        // Assert
        assertTrue(result);
    }

    @Test
    void canHandle_OtherException_ReturnsFalse() {
        // Arrange
        RuntimeException exception = new RuntimeException("Test exception");

        // Act
        boolean result = converter.canHandle(exception);

        // Assert
        assertFalse(result);
    }

    @Test
    void convert_MethodNotAllowedException_ReturnsMethodNotAllowedException() {
        // Arrange
        Set<HttpMethod> allowedMethods = Set.of(HttpMethod.GET, HttpMethod.PUT);
        MethodNotAllowedException exception = new MethodNotAllowedException("POST", allowedMethods);

        // Act
        BusinessException result = converter.convert(exception);

        // Assert
        assertTrue(result instanceof org.fireflyframework.web.error.exceptions.MethodNotAllowedException);
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, result.getStatus());
        assertEquals("METHOD_NOT_ALLOWED", result.getCode());

        org.fireflyframework.web.error.exceptions.MethodNotAllowedException typedResult =
                (org.fireflyframework.web.error.exceptions.MethodNotAllowedException) result;
        assertEquals(HttpMethod.POST, typedResult.getMethod());
        assertEquals(allowedMethods, typedResult.getAllowedMethods());
    }

    @Test
    void convert_UnsupportedMediaTypeStatusException_ReturnsUnsupportedMediaTypeException() {
        // Arrange
        MediaType contentType = MediaType.APPLICATION_XML;
        List<MediaType> supportedTypesList = List.of(MediaType.APPLICATION_JSON);
        UnsupportedMediaTypeStatusException exception = mock(UnsupportedMediaTypeStatusException.class);
        when(exception.getContentType()).thenReturn(contentType);
        when(exception.getSupportedMediaTypes()).thenReturn(supportedTypesList);

        // Act
        BusinessException result = converter.convert(exception);

        // Assert
        assertTrue(result instanceof org.fireflyframework.web.error.exceptions.UnsupportedMediaTypeException);
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, result.getStatus());
        assertEquals("UNSUPPORTED_MEDIA_TYPE", result.getCode());

        org.fireflyframework.web.error.exceptions.UnsupportedMediaTypeException typedResult =
                (org.fireflyframework.web.error.exceptions.UnsupportedMediaTypeException) result;
        assertEquals(contentType.toString(), typedResult.getMediaType());
        assertTrue(typedResult.getSupportedMediaTypes().contains(MediaType.APPLICATION_JSON.toString()));
    }

    @Test
    void convert_DataBufferLimitException_ReturnsPayloadTooLargeException() {
        // Arrange
        DataBufferLimitException exception = mock(DataBufferLimitException.class);
        when(exception.getMessage()).thenReturn("Exceeded limit on max bytes to buffer : 10 > 5");

        // Act
        BusinessException result = converter.convert(exception);

        // Assert
        assertTrue(result instanceof org.fireflyframework.web.error.exceptions.PayloadTooLargeException);
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, result.getStatus());
        assertEquals("PAYLOAD_TOO_LARGE", result.getCode());

        org.fireflyframework.web.error.exceptions.PayloadTooLargeException typedResult =
                (org.fireflyframework.web.error.exceptions.PayloadTooLargeException) result;
        assertEquals(5, typedResult.getMaxSizeBytes());
        assertEquals(10, typedResult.getActualSizeBytes());
    }

    @Test
    void convert_ServerWebInputExceptionWithPreconditionFailed_ReturnsPreconditionFailedException() {
        // Arrange
        ServerWebInputException exception = mock(ServerWebInputException.class);
        when(exception.getStatusCode()).thenReturn(HttpStatus.PRECONDITION_FAILED);
        when(exception.getReason()).thenReturn("ETag does not match");

        // Act
        BusinessException result = converter.convert(exception);

        // Assert
        assertTrue(result instanceof org.fireflyframework.web.error.exceptions.PreconditionFailedException);
        assertEquals(HttpStatus.PRECONDITION_FAILED, result.getStatus());
        assertEquals("PRECONDITION_FAILED", result.getCode());

        org.fireflyframework.web.error.exceptions.PreconditionFailedException typedResult =
                (org.fireflyframework.web.error.exceptions.PreconditionFailedException) result;
        assertEquals("unknown", typedResult.getPrecondition());
        assertEquals("ETag does not match", typedResult.getMessage());
    }
}
