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
 * Exception thrown when a user is authenticated but does not have permission to access a resource.
 * Results in a 403 FORBIDDEN response.
 */
public class ForbiddenException extends BusinessException {
    
    /**
     * Creates a new ForbiddenException with the given message.
     *
     * @param message the error message
     */
    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
    
    /**
     * Creates a new ForbiddenException with a code and message.
     *
     * @param code the error code
     * @param message the error message
     */
    public ForbiddenException(String code, String message) {
        super(HttpStatus.FORBIDDEN, code, message);
    }
    
    /**
     * Creates a new ForbiddenException for insufficient permissions.
     *
     * @param requiredPermission the permission that is required
     * @return a new ForbiddenException
     */
    public static ForbiddenException insufficientPermissions(String requiredPermission) {
        return new ForbiddenException(
                "INSUFFICIENT_PERMISSIONS",
                String.format("You do not have the required permission: %s", requiredPermission)
        );
    }
    
    /**
     * Creates a new ForbiddenException for a resource that the user does not have access to.
     *
     * @param resourceType the type of resource
     * @param resourceId the ID of the resource
     * @return a new ForbiddenException
     */
    public static ForbiddenException resourceAccessDenied(String resourceType, String resourceId) {
        return new ForbiddenException(
                "RESOURCE_ACCESS_DENIED",
                String.format("You do not have access to %s with id '%s'", resourceType, resourceId)
        );
    }
}
