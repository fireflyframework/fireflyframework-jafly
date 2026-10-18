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

import java.util.HashMap;
import java.util.Map;

/**
 * Exception thrown when a resource is no longer available and will not be available again.
 * Results in a 410 GONE response.
 */
public class GoneException extends BusinessException {
    
    /**
     * The type of resource that is gone.
     */
    private final String resourceType;
    
    /**
     * The ID of the resource that is gone.
     */
    private final String resourceId;
    
    /**
     * The date when the resource was removed, if known.
     */
    private final String removalDate;
    
    /**
     * The reason why the resource was removed, if known.
     */
    private final String removalReason;
    
    /**
     * Creates a new GoneException with the given message.
     *
     * @param message the error message
     * @param resourceType the type of resource that is gone
     * @param resourceId the ID of the resource that is gone
     */
    public GoneException(String message, String resourceType, String resourceId) {
        this(message, resourceType, resourceId, null, null);
    }
    
    /**
     * Creates a new GoneException with the given message, removal date, and removal reason.
     *
     * @param message the error message
     * @param resourceType the type of resource that is gone
     * @param resourceId the ID of the resource that is gone
     * @param removalDate the date when the resource was removed
     * @param removalReason the reason why the resource was removed
     */
    public GoneException(String message, String resourceType, String resourceId, String removalDate, String removalReason) {
        super(HttpStatus.GONE, "RESOURCE_GONE", message);
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.removalDate = removalDate;
        this.removalReason = removalReason;
    }
    
    /**
     * Creates a new GoneException with a code, message, removal date, and removal reason.
     *
     * @param code the error code
     * @param message the error message
     * @param resourceType the type of resource that is gone
     * @param resourceId the ID of the resource that is gone
     * @param removalDate the date when the resource was removed
     * @param removalReason the reason why the resource was removed
     */
    public GoneException(String code, String message, String resourceType, String resourceId, String removalDate, String removalReason) {
        super(HttpStatus.GONE, code, message);
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.removalDate = removalDate;
        this.removalReason = removalReason;
    }
    
    /**
     * Returns the type of resource that is gone.
     *
     * @return the resource type
     */
    public String getResourceType() {
        return resourceType;
    }
    
    /**
     * Returns the ID of the resource that is gone.
     *
     * @return the resource ID
     */
    public String getResourceId() {
        return resourceId;
    }
    
    /**
     * Returns the date when the resource was removed, if known.
     *
     * @return the removal date, or null if not known
     */
    public String getRemovalDate() {
        return removalDate;
    }
    
    /**
     * Returns the reason why the resource was removed, if known.
     *
     * @return the removal reason, or null if not known
     */
    public String getRemovalReason() {
        return removalReason;
    }
    
    /**
     * Creates a new GoneException for a resource.
     *
     * @param resourceType the type of resource that is gone
     * @param resourceId the ID of the resource that is gone
     * @return a new GoneException
     */
    public static GoneException forResource(String resourceType, String resourceId) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("resourceType", resourceType);
        metadata.put("resourceId", resourceId);
        
        return (GoneException) new GoneException(
                String.format("The requested %s with ID '%s' is no longer available", 
                        resourceType, resourceId),
                resourceType,
                resourceId
        ).withMetadata(metadata);
    }
    
    /**
     * Creates a new GoneException for a resource with a removal date and reason.
     *
     * @param resourceType the type of resource that is gone
     * @param resourceId the ID of the resource that is gone
     * @param removalDate the date when the resource was removed
     * @param removalReason the reason why the resource was removed
     * @return a new GoneException
     */
    public static GoneException forResourceWithDetails(String resourceType, String resourceId, String removalDate, String removalReason) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("resourceType", resourceType);
        metadata.put("resourceId", resourceId);
        metadata.put("removalDate", removalDate);
        metadata.put("removalReason", removalReason);
        
        return (GoneException) new GoneException(
                String.format("The requested %s with ID '%s' was removed on %s. Reason: %s", 
                        resourceType, resourceId, removalDate, removalReason),
                resourceType,
                resourceId,
                removalDate,
                removalReason
        ).withMetadata(metadata);
    }
    
    /**
     * Creates a new GoneException for a deprecated API version.
     *
     * @param version the API version that is deprecated
     * @param deprecationDate the date when the API version was deprecated
     * @param newVersion the new API version to use
     * @return a new GoneException
     */
    public static GoneException forDeprecatedApiVersion(String version, String deprecationDate, String newVersion) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("apiVersion", version);
        metadata.put("deprecationDate", deprecationDate);
        metadata.put("newVersion", newVersion);
        
        return (GoneException) new GoneException(
                "API_VERSION_DEPRECATED",
                String.format("API version '%s' was deprecated on %s. Please use version '%s' instead", 
                        version, deprecationDate, newVersion),
                "API",
                version,
                deprecationDate,
                "API version upgrade"
        ).withMetadata(metadata);
    }
}
