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

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a request conflicts with the current state of the server.
 * Results in a 409 CONFLICT response.
 */
public class ConflictException extends BusinessException {
    
    /**
     * Creates a new ConflictException with the given message.
     *
     * @param message the error message
     */
    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
    
    /**
     * Creates a new ConflictException with a code and message.
     *
     * @param code the error code
     * @param message the error message
     */
    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
    
    /**
     * Creates a new ConflictException for a resource that already exists.
     *
     * @param resourceType the type of resource that already exists
     * @param identifier the identifier of the resource that already exists
     * @return a new ConflictException
     */
    public static ConflictException resourceAlreadyExists(String resourceType, String identifier) {
        return new ConflictException(
                "RESOURCE_ALREADY_EXISTS",
                String.format("%s with identifier '%s' already exists", resourceType, identifier)
        );
    }
}
