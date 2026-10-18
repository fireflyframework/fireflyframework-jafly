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
 * Exception thrown when a concurrency conflict occurs.
 * Results in a 409 CONFLICT response.
 */
public class ConcurrencyException extends BusinessException {
    
    /**
     * The resource that has a concurrency conflict.
     */
    private final String resource;
    
    /**
     * The ID of the resource.
     */
    private final String resourceId;
    
    /**
     * Creates a new ConcurrencyException with the given message.
     *
     * @param message the error message
     * @param resource the resource that has a concurrency conflict
     * @param resourceId the ID of the resource
     */
    public ConcurrencyException(String message, String resource, String resourceId) {
        super(HttpStatus.CONFLICT, "CONCURRENCY_CONFLICT", message);
        this.resource = resource;
        this.resourceId = resourceId;
    }
    
    /**
     * Creates a new ConcurrencyException with a code and message.
     *
     * @param code the error code
     * @param message the error message
     * @param resource the resource that has a concurrency conflict
     * @param resourceId the ID of the resource
     */
    public ConcurrencyException(String code, String message, String resource, String resourceId) {
        super(HttpStatus.CONFLICT, code, message);
        this.resource = resource;
        this.resourceId = resourceId;
    }
    
    /**
     * Returns the resource that has a concurrency conflict.
     *
     * @return the resource
     */
    public String getResource() {
        return resource;
    }
    
    /**
     * Returns the ID of the resource.
     *
     * @return the resource ID
     */
    public String getResourceId() {
        return resourceId;
    }
    
    /**
     * Creates a new ConcurrencyException for an optimistic locking failure.
     *
     * @param resource the resource that has a concurrency conflict
     * @param resourceId the ID of the resource
     * @return a new ConcurrencyException
     */
    public static ConcurrencyException optimisticLockingFailure(String resource, String resourceId) {
        return new ConcurrencyException(
                "OPTIMISTIC_LOCKING_FAILURE",
                String.format("The %s with id '%s' was updated by another transaction", resource, resourceId),
                resource,
                resourceId
        );
    }
    
    /**
     * Creates a new ConcurrencyException for a resource that is locked.
     *
     * @param resource the resource that is locked
     * @param resourceId the ID of the resource
     * @param lockedBy the entity that has locked the resource
     * @return a new ConcurrencyException
     */
    public static ConcurrencyException resourceLocked(String resource, String resourceId, String lockedBy) {
        return new ConcurrencyException(
                "RESOURCE_LOCKED",
                String.format("The %s with id '%s' is locked by %s", resource, resourceId, lockedBy),
                resource,
                resourceId
        );
    }
}
