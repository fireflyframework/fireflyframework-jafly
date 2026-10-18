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


package org.fireflyframework.web.error.exceptions;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BadGatewayExceptionTest {

    @Test
    void constructor_WithMessageAndServerInfo_SetsProperties() {
        // Arrange
        String message = "Bad gateway";
        String serverName = "PaymentService";
        String serverUrl = "https://api.payment.com";
        String serverErrorCode = "INVALID_RESPONSE";
        
        // Act
        BadGatewayException exception = new BadGatewayException(message, serverName, serverUrl, serverErrorCode);
        
        // Assert
        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
        assertEquals("BAD_GATEWAY", exception.getCode());
        assertEquals(message, exception.getMessage());
        assertEquals(serverName, exception.getServerName());
        assertEquals(serverUrl, exception.getServerUrl());
        assertEquals(serverErrorCode, exception.getServerErrorCode());
    }
    
    @Test
    void constructor_WithCodeMessageAndServerInfo_SetsProperties() {
        // Arrange
        String code = "CUSTOM_BAD_GATEWAY";
        String message = "Bad gateway";
        String serverName = "PaymentService";
        String serverUrl = "https://api.payment.com";
        String serverErrorCode = "INVALID_RESPONSE";
        
        // Act
        BadGatewayException exception = new BadGatewayException(code, message, serverName, serverUrl, serverErrorCode);
        
        // Assert
        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
        assertEquals(code, exception.getCode());
        assertEquals(message, exception.getMessage());
        assertEquals(serverName, exception.getServerName());
        assertEquals(serverUrl, exception.getServerUrl());
        assertEquals(serverErrorCode, exception.getServerErrorCode());
    }
    
    @Test
    void forServer_WithServerInfo_ReturnsException() {
        // Arrange
        String serverName = "PaymentService";
        String serverUrl = "https://api.payment.com";
        String serverErrorCode = "INVALID_RESPONSE";
        
        // Act
        BadGatewayException exception = BadGatewayException.forServer(serverName, serverUrl, serverErrorCode);
        
        // Assert
        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
        assertEquals("BAD_GATEWAY", exception.getCode());
        assertTrue(exception.getMessage().contains("Request to upstream server 'PaymentService' failed"));
        assertTrue(exception.getMessage().contains("INVALID_RESPONSE"));
        assertEquals(serverName, exception.getServerName());
        assertEquals(serverUrl, exception.getServerUrl());
        assertEquals(serverErrorCode, exception.getServerErrorCode());
        
        // Check metadata
        Map<String, Object> metadata = exception.getMetadata();
        assertNotNull(metadata);
        assertEquals(serverName, metadata.get("serverName"));
        assertEquals(serverUrl, metadata.get("serverUrl"));
        assertEquals(serverErrorCode, metadata.get("serverErrorCode"));
    }
    
    @Test
    void forServerWithMessage_WithServerInfoAndErrorMessage_ReturnsException() {
        // Arrange
        String serverName = "PaymentService";
        String serverUrl = "https://api.payment.com";
        String serverErrorCode = "INVALID_RESPONSE";
        String errorMessage = "Invalid payment details";
        
        // Act
        BadGatewayException exception = BadGatewayException.forServerWithMessage(serverName, serverUrl, serverErrorCode, errorMessage);
        
        // Assert
        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
        assertEquals("BAD_GATEWAY", exception.getCode());
        assertTrue(exception.getMessage().contains("Request to upstream server 'PaymentService' failed"));
        assertTrue(exception.getMessage().contains("INVALID_RESPONSE"));
        assertTrue(exception.getMessage().contains("Invalid payment details"));
        assertEquals(serverName, exception.getServerName());
        assertEquals(serverUrl, exception.getServerUrl());
        assertEquals(serverErrorCode, exception.getServerErrorCode());
        
        // Check metadata
        Map<String, Object> metadata = exception.getMetadata();
        assertNotNull(metadata);
        assertEquals(serverName, metadata.get("serverName"));
        assertEquals(serverUrl, metadata.get("serverUrl"));
        assertEquals(serverErrorCode, metadata.get("serverErrorCode"));
        assertEquals(errorMessage, metadata.get("errorMessage"));
    }
    
    @Test
    void forInvalidResponse_WithServerInfoAndResponseStatus_ReturnsException() {
        // Arrange
        String serverName = "PaymentService";
        String serverUrl = "https://api.payment.com";
        int responseStatus = 500;
        
        // Act
        BadGatewayException exception = BadGatewayException.forInvalidResponse(serverName, serverUrl, responseStatus);
        
        // Assert
        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
        assertEquals("INVALID_UPSTREAM_RESPONSE", exception.getCode());
        assertTrue(exception.getMessage().contains("Upstream server 'PaymentService' returned an invalid response"));
        assertTrue(exception.getMessage().contains("500"));
        assertEquals(serverName, exception.getServerName());
        assertEquals(serverUrl, exception.getServerUrl());
        assertEquals("500", exception.getServerErrorCode());
        
        // Check metadata
        Map<String, Object> metadata = exception.getMetadata();
        assertNotNull(metadata);
        assertEquals(serverName, metadata.get("serverName"));
        assertEquals(serverUrl, metadata.get("serverUrl"));
        assertEquals(responseStatus, metadata.get("responseStatus"));
    }
}
