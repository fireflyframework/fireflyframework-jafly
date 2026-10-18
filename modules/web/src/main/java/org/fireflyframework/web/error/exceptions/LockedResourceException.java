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
 * Exception thrown when a resource is locked and cannot be modified.
 * Results in a 423 LOCKED response.
 */
public class LockedResourceException extends BusinessException {
    
    /**
     * The type of resource that is locked.
     */
    private final String resourceType;
    
    /**
     * The ID of the resource that is locked.
     */
    private final String resourceId;
    
    /**
     * The ID of the lock owner, if known.
     */
    private final String lockOwner;
    
    /**
     * Creates a new LockedResourceException with the given message.
     *
     * @param message the error message
     * @param resourceType the type of resource that is locked
     * @param resourceId the ID of the resource that is locked
     */
    public LockedResourceException(String message, String resourceType, String resourceId) {
        this(message, resourceType, resourceId, null);
    }
    
    /**
     * Creates a new LockedResourceException with the given message and lock owner.
     *
     * @param message the error message
     * @param resourceType the type of resource that is locked
     * @param resourceId the ID of the resource that is locked
     * @param lockOwner the ID of the lock owner, if known
     */
    public LockedResourceException(String message, String resourceType, String resourceId, String lockOwner) {
        super(HttpStatus.LOCKED, "RESOURCE_LOCKED", message);
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.lockOwner = lockOwner;
    }
    
    /**
     * Creates a new LockedResourceException with a code and message.
     *
     * @param code the error code
     * @param message the error message
     * @param resourceType the type of resource that is locked
     * @param resourceId the ID of the resource that is locked
     * @param lockOwner the ID of the lock owner, if known
     */
    public LockedResourceException(String code, String message, String resourceType, String resourceId, String lockOwner) {
        super(HttpStatus.LOCKED, code, message);
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.lockOwner = lockOwner;
    }
    
    /**
     * Returns the type of resource that is locked.
     *
     * @return the resource type
     */
    public String getResourceType() {
        return resourceType;
    }
    
    /**
     * Returns the ID of the resource that is locked.
     *
     * @return the resource ID
     */
    public String getResourceId() {
        return resourceId;
    }
    
    /**
     * Returns the ID of the lock owner, if known.
     *
     * @return the lock owner, or null if not known
     */
    public String getLockOwner() {
        return lockOwner;
    }
    
    /**
     * Creates a new LockedResourceException for a resource.
     *
     * @param resourceType the type of resource that is locked
     * @param resourceId the ID of the resource that is locked
     * @return a new LockedResourceException
     */
    public static LockedResourceException forResource(String resourceType, String resourceId) {
        return new LockedResourceException(
                String.format("The %s with ID '%s' is currently locked and cannot be modified", 
                        resourceType, resourceId),
                resourceType,
                resourceId
        );
    }
    
    /**
     * Creates a new LockedResourceException for a resource with a known lock owner.
     *
     * @param resourceType the type of resource that is locked
     * @param resourceId the ID of the resource that is locked
     * @param lockOwner the ID of the lock owner
     * @return a new LockedResourceException
     */
    public static LockedResourceException forResource(String resourceType, String resourceId, String lockOwner) {
        return new LockedResourceException(
                String.format("The %s with ID '%s' is currently locked by '%s' and cannot be modified", 
                        resourceType, resourceId, lockOwner),
                resourceType,
                resourceId,
                lockOwner
        );
    }
    
    /**
     * Creates a new LockedResourceException for a resource with a lock expiration time.
     *
     * @param resourceType the type of resource that is locked
     * @param resourceId the ID of the resource that is locked
     * @param expirationSeconds the number of seconds until the lock expires
     * @return a new LockedResourceException
     */
    public static LockedResourceException forResourceWithExpiration(String resourceType, String resourceId, int expirationSeconds) {
        return (LockedResourceException) new LockedResourceException(
                "RESOURCE_LOCKED_WITH_EXPIRATION",
                String.format("The %s with ID '%s' is currently locked and cannot be modified. The lock will expire in %d seconds", 
                        resourceType, resourceId, expirationSeconds),
                resourceType,
                resourceId
        ).withMetadata("expirationSeconds", expirationSeconds);
    }
}
