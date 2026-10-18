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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Exception thrown when a precondition for a request is not met.
 * Results in a 412 PRECONDITION FAILED response.
 */
public class PreconditionFailedException extends BusinessException {
    
    /**
     * The name of the precondition that failed.
     */
    private final String precondition;
    
    /**
     * Creates a new PreconditionFailedException with the given message.
     *
     * @param message the error message
     * @param precondition the name of the precondition that failed
     */
    public PreconditionFailedException(String message, String precondition) {
        super(HttpStatus.PRECONDITION_FAILED, "PRECONDITION_FAILED", message);
        this.precondition = precondition;
    }
    
    /**
     * Creates a new PreconditionFailedException with a code and message.
     *
     * @param code the error code
     * @param message the error message
     * @param precondition the name of the precondition that failed
     */
    public PreconditionFailedException(String code, String message, String precondition) {
        super(HttpStatus.PRECONDITION_FAILED, code, message);
        this.precondition = precondition;
    }
    
    /**
     * Returns the name of the precondition that failed.
     *
     * @return the precondition
     */
    public String getPrecondition() {
        return precondition;
    }
    
    /**
     * Creates a new PreconditionFailedException for an If-Match header.
     *
     * @param expectedETag the expected ETag
     * @param actualETag the actual ETag
     * @return a new PreconditionFailedException
     */
    public static PreconditionFailedException forIfMatch(String expectedETag, String actualETag) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("expectedETag", expectedETag);
        metadata.put("actualETag", actualETag);
        
        return (PreconditionFailedException) new PreconditionFailedException(
                "IF_MATCH_FAILED",
                String.format("The expected ETag '%s' does not match the current ETag '%s'", 
                        expectedETag, actualETag),
                "If-Match"
        ).withMetadata(metadata);
    }
    
    /**
     * Creates a new PreconditionFailedException for an If-None-Match header.
     *
     * @param eTag the ETag that matched
     * @return a new PreconditionFailedException
     */
    public static PreconditionFailedException forIfNoneMatch(String eTag) {
        return (PreconditionFailedException) new PreconditionFailedException(
                "IF_NONE_MATCH_FAILED",
                String.format("The resource with ETag '%s' already exists", eTag),
                "If-None-Match"
        ).withMetadata("eTag", eTag);
    }
    
    /**
     * Creates a new PreconditionFailedException for an If-Modified-Since header.
     *
     * @param ifModifiedSince the If-Modified-Since date
     * @param lastModified the last modified date
     * @return a new PreconditionFailedException
     */
    public static PreconditionFailedException forIfModifiedSince(String ifModifiedSince, String lastModified) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("ifModifiedSince", ifModifiedSince);
        metadata.put("lastModified", lastModified);
        
        return (PreconditionFailedException) new PreconditionFailedException(
                "IF_MODIFIED_SINCE_FAILED",
                String.format("The resource has not been modified since %s", ifModifiedSince),
                "If-Modified-Since"
        ).withMetadata(metadata);
    }
    
    /**
     * Creates a new PreconditionFailedException for a custom precondition.
     *
     * @param precondition the name of the precondition
     * @param message the error message
     * @return a new PreconditionFailedException
     */
    public static PreconditionFailedException forPrecondition(String precondition, String message) {
        return new PreconditionFailedException(
                "PRECONDITION_FAILED",
                message,
                precondition
        );
    }
}
