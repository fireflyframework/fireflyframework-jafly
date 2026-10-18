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
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExternalServiceExceptionConverterTest {

    private ExternalServiceExceptionConverter converter;

    @BeforeEach
    void setUp() {
        converter = new ExternalServiceExceptionConverter();
    }

    @Test
    void canHandle_WebClientResponseException_ReturnsTrue() {
        // Arrange
        WebClientResponseException exception = WebClientResponseException.create(
                HttpStatus.BAD_GATEWAY.value(),
                "Bad Gateway",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        );

        // Act
        boolean result = converter.canHandle(exception);

        // Assert
        assertTrue(result);
    }

    @Test
    void canHandle_HttpClientErrorException_ReturnsTrue() {
        // Arrange
        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        );

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
    void canHandle_WrappedWebClientResponseException_ReturnsTrue() {
        // Arrange — a downstream WebClientResponseException nested inside a wrapper (as the CQRS bus does)
        WebClientResponseException downstream = WebClientResponseException.create(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        );
        RuntimeException wrapped = new RuntimeException("Failed to process query", downstream);

        // Act
        boolean result = converter.canHandle(wrapped);

        // Assert
        assertTrue(result);
    }

    @Test
    void convert_WrappedWebClientResponseExceptionNotFound_ReturnsResourceNotFound() {
        // Arrange — the 404 is wrapped, so a top-level check would miss it and degrade to a 500
        WebClientResponseException downstream = WebClientResponseException.create(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        );
        RuntimeException wrapped = new RuntimeException("Failed to process query", downstream);

        // Act
        BusinessException result = converter.convert(wrapped);

        // Assert — the downstream 404 is preserved as a 404 instead of being masked as 500
        assertTrue(result instanceof ResourceNotFoundException);
        assertEquals(HttpStatus.NOT_FOUND, result.getStatus());
    }

    @Test
    void convert_WebClientResponseExceptionWithBadGateway_ReturnsBadGatewayException() {
        // Arrange
        // Create a mock HttpRequest
        HttpRequest request = Mockito.mock(HttpRequest.class);
        URI uri = URI.create("https://api.payment.com/process");
        Mockito.when(request.getURI()).thenReturn(uri);

        // Create a mock WebClientResponseException
        WebClientResponseException exception = Mockito.mock(WebClientResponseException.class);
        Mockito.when(exception.getStatusCode()).thenReturn(HttpStatus.BAD_GATEWAY);
        Mockito.when(exception.getMessage()).thenReturn("Bad Gateway");
        Mockito.when(exception.getRequest()).thenReturn(request);

        // Act
        BusinessException result = converter.convert(exception);

        // Assert
        assertTrue(result instanceof BadGatewayException);
        assertEquals(HttpStatus.BAD_GATEWAY, result.getStatus());

        BadGatewayException typedResult = (BadGatewayException) result;
        assertEquals("payment", typedResult.getServerName());
        assertEquals("https://api.payment.com/process", typedResult.getServerUrl());
        assertEquals("502", typedResult.getServerErrorCode());
    }

    @Test
    void convert_WebClientResponseExceptionWithGatewayTimeout_ReturnsGatewayTimeoutException() {
        // Arrange
        // Create a mock HttpRequest
        HttpRequest request = Mockito.mock(HttpRequest.class);
        URI uri = URI.create("https://api.payment.com/process");
        Mockito.when(request.getURI()).thenReturn(uri);

        // Create a mock WebClientResponseException
        WebClientResponseException exception = Mockito.mock(WebClientResponseException.class);
        Mockito.when(exception.getStatusCode()).thenReturn(HttpStatus.GATEWAY_TIMEOUT);
        Mockito.when(exception.getMessage()).thenReturn("Gateway Timeout");
        Mockito.when(exception.getRequest()).thenReturn(request);

        // Act
        BusinessException result = converter.convert(exception);

        // Assert
        assertTrue(result instanceof GatewayTimeoutException);
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, result.getStatus());

        GatewayTimeoutException typedResult = (GatewayTimeoutException) result;
        assertEquals("payment", typedResult.getServerName());
        assertEquals("https://api.payment.com/process", typedResult.getServerUrl());
    }

    @Test
    void convert_WebClientResponseExceptionWithServiceUnavailable_ReturnsServiceUnavailableException() {
        // Arrange
        // Create a mock HttpRequest
        HttpRequest request = Mockito.mock(HttpRequest.class);
        URI uri = URI.create("https://api.payment.com/process");
        Mockito.when(request.getURI()).thenReturn(uri);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", "30");

        // Create a mock WebClientResponseException
        WebClientResponseException exception = Mockito.mock(WebClientResponseException.class);
        Mockito.when(exception.getStatusCode()).thenReturn(HttpStatus.SERVICE_UNAVAILABLE);
        Mockito.when(exception.getMessage()).thenReturn("Service Unavailable");
        Mockito.when(exception.getHeaders()).thenReturn(headers);
        Mockito.when(exception.getRequest()).thenReturn(request);

        // Act
        BusinessException result = converter.convert(exception);

        // Assert
        assertTrue(result instanceof ServiceUnavailableException);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, result.getStatus());

        ServiceUnavailableException typedResult = (ServiceUnavailableException) result;
        assertEquals("payment", typedResult.getServiceName());
        assertEquals(Integer.valueOf(30), typedResult.getRetryAfterSeconds());
    }

    @Test
    void convert_ConnectException_ReturnsServiceUnavailableException() {
        // Arrange
        ConnectException exception = new ConnectException("Connection refused");

        // Act
        BusinessException result = converter.convert(exception);

        // Assert
        assertTrue(result instanceof ServiceUnavailableException);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, result.getStatus());

        ServiceUnavailableException typedResult = (ServiceUnavailableException) result;
        assertEquals("unknown", typedResult.getServiceName());
    }

    @Test
    void convert_SocketTimeoutException_ReturnsGatewayTimeoutException() {
        // Arrange
        SocketTimeoutException exception = new SocketTimeoutException("Read timed out");

        // Act
        BusinessException result = converter.convert(exception);

        // Assert
        assertTrue(result instanceof GatewayTimeoutException);
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, result.getStatus());

        GatewayTimeoutException typedResult = (GatewayTimeoutException) result;
        assertEquals("unknown", typedResult.getServerName());
        assertEquals("unknown", typedResult.getServerUrl());
    }

    @Test
    void convert_TimeoutException_ReturnsGatewayTimeoutException() {
        // Arrange
        TimeoutException exception = new TimeoutException("Operation timed out");

        // Act
        BusinessException result = converter.convert(exception);

        // Assert
        assertTrue(result instanceof GatewayTimeoutException);
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, result.getStatus());
    }

    @Test
    void convert_UnknownHostException_ReturnsBadGatewayException() {
        // Arrange
        UnknownHostException exception = new UnknownHostException("Unknown host: api.payment.com");

        // Act
        BusinessException result = converter.convert(exception);

        // Assert
        assertTrue(result instanceof BadGatewayException);
        assertEquals(HttpStatus.BAD_GATEWAY, result.getStatus());

        BadGatewayException typedResult = (BadGatewayException) result;
        assertEquals("UNKNOWN_HOST", typedResult.getServerErrorCode());
    }
}
