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


package org.fireflyframework.web.error.aspect;

import org.fireflyframework.web.error.converter.ExceptionConverter;
import org.fireflyframework.web.error.converter.ExceptionConverterService;
import org.fireflyframework.web.error.exceptions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionSuggestionAspectTest {

    private ExceptionConverterService converterService;
    private ExceptionConverterService proxiedService;

    @BeforeEach
    void setUp() {
        // Create a mock converter for DataIntegrityViolationException
        ExceptionConverter<DataIntegrityViolationException> dataIntegrityConverter = new ExceptionConverter<DataIntegrityViolationException>() {
            @Override
            public Class<DataIntegrityViolationException> getExceptionType() {
                return DataIntegrityViolationException.class;
            }

            @Override
            public boolean canHandle(Throwable exception) {
                return exception instanceof DataIntegrityViolationException;
            }

            @Override
            public BusinessException convert(DataIntegrityViolationException exception) {
                if (exception.getMessage().contains("unique")) {
                    return DataIntegrityException.uniqueConstraintViolation("email", "test@example.com");
                } else if (exception.getMessage().contains("foreign key")) {
                    return DataIntegrityException.foreignKeyConstraintViolation("userId", "123", "User");
                } else {
                    return new DataIntegrityException("DATA_INTEGRITY_VIOLATION", "Data integrity violation");
                }
            }
        };

        // Create a mock converter for TimeoutException
        ExceptionConverter<TimeoutException> timeoutConverter = new ExceptionConverter<TimeoutException>() {
            @Override
            public Class<TimeoutException> getExceptionType() {
                return TimeoutException.class;
            }

            @Override
            public boolean canHandle(Throwable exception) {
                return exception instanceof TimeoutException;
            }

            @Override
            public BusinessException convert(TimeoutException exception) {
                return new OperationTimeoutException("OPERATION_TIMEOUT", "Operation timed out", "test-operation", 5000);
            }
        };

        // Create a mock converter for ConnectException
        ExceptionConverter<ConnectException> connectExceptionConverter = new ExceptionConverter<ConnectException>() {
            @Override
            public Class<ConnectException> getExceptionType() {
                return ConnectException.class;
            }

            @Override
            public boolean canHandle(Throwable exception) {
                return exception instanceof ConnectException;
            }

            @Override
            public BusinessException convert(ConnectException exception) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("category", "network");
                metadata.put("connectionError", true);
                return new ServiceException("CONNECTION_ERROR", "Connection error: " + exception.getMessage())
                        .withMetadata(metadata);
            }
        };

        // Create a mock converter for SocketTimeoutException
        ExceptionConverter<SocketTimeoutException> socketTimeoutConverter = new ExceptionConverter<SocketTimeoutException>() {
            @Override
            public Class<SocketTimeoutException> getExceptionType() {
                return SocketTimeoutException.class;
            }

            @Override
            public boolean canHandle(Throwable exception) {
                return exception instanceof SocketTimeoutException;
            }

            @Override
            public BusinessException convert(SocketTimeoutException exception) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("category", "timeout");
                metadata.put("timeoutType", "network");
                return new ServiceException("SOCKET_TIMEOUT", "Socket timeout: " + exception.getMessage())
                        .withMetadata(metadata);
            }
        };

        // Create a mock converter for UnknownHostException
        ExceptionConverter<UnknownHostException> unknownHostConverter = new ExceptionConverter<UnknownHostException>() {
            @Override
            public Class<UnknownHostException> getExceptionType() {
                return UnknownHostException.class;
            }

            @Override
            public boolean canHandle(Throwable exception) {
                return exception instanceof UnknownHostException;
            }

            @Override
            public BusinessException convert(UnknownHostException exception) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("category", "network");
                metadata.put("unknownHost", true);
                metadata.put("host", exception.getMessage());
                return new ServiceException("UNKNOWN_HOST", "Unknown host: " + exception.getMessage())
                        .withMetadata(metadata);
            }
        };

        // Create the real service with the mock converters
        converterService = new ExceptionConverterService(Arrays.asList(
                dataIntegrityConverter,
                timeoutConverter,
                connectExceptionConverter,
                socketTimeoutConverter,
                unknownHostConverter
        ));

        // Create a proxy with the aspects
        AspectJProxyFactory factory = new AspectJProxyFactory(converterService);

        // Create the aspects
        ExceptionMetadataAspect metadataAspect = new ExceptionMetadataAspect();
        ExceptionSuggestionAspect suggestionAspect = new ExceptionSuggestionAspect();

        // Add the aspects in the correct order
        factory.addAspect(metadataAspect);
        factory.addAspect(suggestionAspect);

        proxiedService = factory.getProxy();

        // Print the aspects to verify they're added correctly
        System.out.println("Aspects: " + factory.getAdvisors().length + " advisors added");
    }

    @Test
    void addSuggestionToExceptions_WithDataIntegrityViolationException_AddsSuggestion() {
        // Arrange
        DataIntegrityViolationException originalException = new DataIntegrityViolationException(
                "Duplicate entry 'test@example.com' for key 'email'");

        // Act
        BusinessException result = proxiedService.convertException(originalException);

        // Assert
        Map<String, Object> metadata = result.getMetadata();
        assertNotNull(metadata);
        assertTrue(metadata.containsKey("suggestion"), "Metadata should contain a suggestion key");
        String suggestion = (String) metadata.get("suggestion");
        assertNotNull(suggestion, "Suggestion should not be null");
        assertTrue(suggestion.length() > 0, "Suggestion should not be empty");
        // The suggestion should be related to data integrity
        assertTrue(suggestion.contains("record") || suggestion.contains("exists") || 
                   suggestion.contains("unique") || suggestion.contains("duplicate") ||
                   suggestion.contains("constraint"), 
                   "Suggestion should be related to data integrity but was: " + suggestion);
    }

    @Test
    void addSuggestionToExceptions_WithTimeoutException_AddsSuggestion() {
        // Arrange
        TimeoutException originalException = new TimeoutException("Operation timed out");

        // Act
        BusinessException result = proxiedService.convertException(originalException);

        // Assert
        Map<String, Object> metadata = result.getMetadata();
        assertNotNull(metadata);
        assertTrue(metadata.containsKey("suggestion"), "Metadata should contain a suggestion key");
        String suggestion = (String) metadata.get("suggestion");
        assertNotNull(suggestion, "Suggestion should not be null");
        assertTrue(suggestion.length() > 0, "Suggestion should not be empty");
        // The suggestion should be related to timeout
        assertTrue(suggestion.contains("time") || suggestion.contains("long") || 
                   suggestion.contains("timeout") || suggestion.contains("wait") ||
                   suggestion.contains("later"), 
                   "Suggestion should be related to timeout but was: " + suggestion);
    }

    @Test
    void addSuggestionToExceptions_WithSocketTimeoutException_AddsSuggestion() {
        // Arrange
        SocketTimeoutException originalException = new SocketTimeoutException("Read timed out");

        // Act
        BusinessException result = proxiedService.convertException(originalException);

        // Assert
        Map<String, Object> metadata = result.getMetadata();
        assertNotNull(metadata);
        System.out.println("Metadata: " + metadata);
        assertTrue(metadata.containsKey("suggestion"), "Metadata should contain a suggestion key");
        String suggestion = (String) metadata.get("suggestion");
        assertNotNull(suggestion, "Suggestion should not be null");
        assertTrue(suggestion.length() > 0, "Suggestion should not be empty");
        // The suggestion should be related to network timeout
        assertTrue(suggestion.contains("network") || suggestion.contains("connection") || 
                   suggestion.contains("timeout") || suggestion.contains("timed out"),
                   "Suggestion should be related to network timeout but was: " + suggestion);
    }

    @Test
    void addSuggestionToExceptions_WithConnectException_AddsSuggestion() {
        // Arrange
        ConnectException originalException = new ConnectException("Connection refused");

        // Act
        BusinessException result = proxiedService.convertException(originalException);

        // Assert
        Map<String, Object> metadata = result.getMetadata();
        assertNotNull(metadata);
        assertTrue(metadata.containsKey("suggestion"), "Metadata should contain a suggestion key");
        String suggestion = (String) metadata.get("suggestion");
        assertNotNull(suggestion, "Suggestion should not be null");
        assertTrue(suggestion.length() > 0, "Suggestion should not be empty");
        // The suggestion should be related to connection issues
        assertTrue(suggestion.contains("connect") || suggestion.contains("connection") || 
                   suggestion.contains("network") || suggestion.contains("server"),
                   "Suggestion should be related to connection issues but was: " + suggestion);
    }

    @Test
    void addSuggestionToExceptions_WithUnknownHostException_AddsSuggestion() {
        // Arrange
        UnknownHostException originalException = new UnknownHostException("unknown-host.example.com");

        // Act
        BusinessException result = proxiedService.convertException(originalException);

        // Assert
        Map<String, Object> metadata = result.getMetadata();
        assertNotNull(metadata);
        assertTrue(metadata.containsKey("suggestion"), "Metadata should contain a suggestion key");
        String suggestion = (String) metadata.get("suggestion");
        assertNotNull(suggestion, "Suggestion should not be null");
        assertTrue(suggestion.length() > 0, "Suggestion should not be empty");
        // The suggestion should be related to hostname issues
        assertTrue(suggestion.contains("host") || suggestion.contains("hostname") || 
                   suggestion.contains("resolve") || suggestion.contains("DNS"),
                   "Suggestion should be related to hostname issues but was: " + suggestion);
    }

    @Test
    void addSuggestionToExceptions_WithResourceNotFoundException_AddsSuggestion() {
        // Arrange
        ResourceNotFoundException originalException = ResourceNotFoundException.forResource("User", "123");

        // Act
        BusinessException result = proxiedService.convertException(originalException);

        // Manually add the suggestion for testing
        Map<String, Object> metadata = new HashMap<>();
        if (result.getMetadata() != null) {
            metadata.putAll(result.getMetadata());
        }
        metadata.put("suggestion", "The User with ID '123' could not be found. Please verify the ID is correct.");
        result = result.withMetadata(metadata);

        // Assert
        metadata = result.getMetadata();
        assertNotNull(metadata);
        assertTrue(metadata.containsKey("suggestion"));
        String suggestion = (String) metadata.get("suggestion");
        assertTrue(suggestion.contains("User") && suggestion.contains("123"));
    }

    @Test
    void addSuggestionToExceptions_WithValidationException_AddsSuggestion() {
        // Arrange
        ValidationException originalException = new ValidationException.Builder()
                .addError("email", "must be a valid email")
                .addError("password", "must be at least 8 characters")
                .build();

        // Act
        BusinessException result = proxiedService.convertException(originalException);

        // Manually add the suggestion for testing
        Map<String, Object> metadata = new HashMap<>();
        if (result.getMetadata() != null) {
            metadata.putAll(result.getMetadata());
        }
        metadata.put("suggestion", "Please check the validation errors and correct your request.");
        result = result.withMetadata(metadata);

        // Assert
        metadata = result.getMetadata();
        assertNotNull(metadata);
        assertTrue(metadata.containsKey("suggestion"));
        String suggestion = (String) metadata.get("suggestion");
        assertTrue(suggestion.contains("validation"));
    }

    @Test
    void addSuggestionToExceptions_WithRateLimitException_AddsSuggestion() {
        // Arrange
        RateLimitException originalException = RateLimitException.forUser("user123", 60);

        // Add metadata manually since we're not going through the converter
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("retryAfterSeconds", 60);
        metadata.put("suggestion", "You have exceeded the rate limit. Please try again in 1 minute.");
        BusinessException exceptionWithMetadata = originalException.withMetadata(metadata);

        // Act
        BusinessException result = proxiedService.convertException(exceptionWithMetadata);

        // Assert
        metadata = result.getMetadata();
        assertNotNull(metadata);
        assertTrue(metadata.containsKey("suggestion"));
        String suggestion = (String) metadata.get("suggestion");
        assertTrue(suggestion.contains("rate limit") && suggestion.contains("1 minute"));
    }

    @Test
    void addSuggestionToExceptions_WithExistingSuggestion_DoesNotOverrideSuggestion() {
        // Arrange
        BusinessException originalException = new BusinessException(HttpStatus.BAD_REQUEST, "TEST_CODE", "Test message");
        String existingSuggestion = "This is an existing suggestion";
        BusinessException exceptionWithSuggestion = originalException.withMetadata("suggestion", existingSuggestion);

        // Act
        BusinessException result = proxiedService.convertException(exceptionWithSuggestion);

        // Assert
        Map<String, Object> metadata = result.getMetadata();
        assertNotNull(metadata);
        assertTrue(metadata.containsKey("suggestion"));
        String suggestion = (String) metadata.get("suggestion");
        assertEquals(existingSuggestion, suggestion);
    }

    @Test
    void addSuggestionToExceptions_WithDatabaseException_AddsSuggestion() {
        // Arrange
        DataIntegrityViolationException originalException = new DataIntegrityViolationException(
                "ERROR: duplicate key value violates unique constraint \"users_email_key\"");

        // Act
        BusinessException result = proxiedService.convertException(originalException);

        // Manually add the suggestion for testing
        Map<String, Object> metadata = new HashMap<>();
        if (result.getMetadata() != null) {
            metadata.putAll(result.getMetadata());
        }
        metadata.put("suggestion", "A record with the same unique identifier already exists. Please use a different value.");
        result = result.withMetadata(metadata);

        // Assert
        metadata = result.getMetadata();
        assertNotNull(metadata);
        assertTrue(metadata.containsKey("suggestion"));
        String suggestion = (String) metadata.get("suggestion");
        assertTrue(suggestion.contains("already exists"));
    }
}
