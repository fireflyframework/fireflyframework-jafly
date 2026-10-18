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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MethodNotAllowedExceptionTest {

    @Test
    void constructor_WithMessageAndMethods_SetsProperties() {
        // Arrange
        String message = "Method not allowed";
        HttpMethod method = HttpMethod.POST;
        Set<HttpMethod> allowedMethods = Set.of(HttpMethod.GET, HttpMethod.PUT);
        
        // Act
        MethodNotAllowedException exception = new MethodNotAllowedException(message, method, allowedMethods);
        
        // Assert
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, exception.getStatus());
        assertEquals("METHOD_NOT_ALLOWED", exception.getCode());
        assertEquals(message, exception.getMessage());
        assertEquals(method, exception.getMethod());
        assertEquals(allowedMethods, exception.getAllowedMethods());
    }
    
    @Test
    void constructor_WithCodeMessageAndMethods_SetsProperties() {
        // Arrange
        String code = "CUSTOM_METHOD_NOT_ALLOWED";
        String message = "Method not allowed";
        HttpMethod method = HttpMethod.POST;
        Set<HttpMethod> allowedMethods = Set.of(HttpMethod.GET, HttpMethod.PUT);
        
        // Act
        MethodNotAllowedException exception = new MethodNotAllowedException(code, message, method, allowedMethods);
        
        // Assert
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, exception.getStatus());
        assertEquals(code, exception.getCode());
        assertEquals(message, exception.getMessage());
        assertEquals(method, exception.getMethod());
        assertEquals(allowedMethods, exception.getAllowedMethods());
    }
    
    @Test
    void forResource_WithMethodAndAllowedMethods_ReturnsException() {
        // Arrange
        HttpMethod method = HttpMethod.POST;
        HttpMethod[] allowedMethods = {HttpMethod.GET, HttpMethod.PUT};
        
        // Act
        MethodNotAllowedException exception = MethodNotAllowedException.forResource(method, allowedMethods);
        
        // Assert
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, exception.getStatus());
        assertEquals("METHOD_NOT_ALLOWED", exception.getCode());
        assertTrue(exception.getMessage().contains("Method POST is not allowed"));
        assertTrue(exception.getMessage().contains("[GET, PUT]"));
        assertEquals(method, exception.getMethod());
        assertEquals(Set.of(allowedMethods), exception.getAllowedMethods());
    }
    
    @Test
    void forResource_WithResourcePathMethodAndAllowedMethods_ReturnsException() {
        // Arrange
        String resourcePath = "/api/users";
        HttpMethod method = HttpMethod.POST;
        HttpMethod[] allowedMethods = {HttpMethod.GET, HttpMethod.PUT};
        
        // Act
        MethodNotAllowedException exception = MethodNotAllowedException.forResource(resourcePath, method, allowedMethods);
        
        // Assert
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, exception.getStatus());
        assertEquals("METHOD_NOT_ALLOWED", exception.getCode());
        assertTrue(exception.getMessage().contains("Method POST is not allowed"));
        assertTrue(exception.getMessage().contains("resource '/api/users'"));
        assertTrue(exception.getMessage().contains("[GET, PUT]"));
        assertEquals(method, exception.getMethod());
        assertEquals(Set.of(allowedMethods), exception.getAllowedMethods());
    }
}
