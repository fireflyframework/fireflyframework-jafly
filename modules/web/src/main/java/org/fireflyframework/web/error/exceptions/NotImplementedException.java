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
 * Exception thrown when a requested functionality is not implemented.
 * Results in a 501 NOT IMPLEMENTED response.
 */
public class NotImplementedException extends BusinessException {
    
    /**
     * The feature that is not implemented.
     */
    private final String feature;
    
    /**
     * The expected implementation date, if known.
     */
    private final String expectedImplementationDate;
    
    /**
     * Creates a new NotImplementedException with the given message.
     *
     * @param message the error message
     * @param feature the feature that is not implemented
     */
    public NotImplementedException(String message, String feature) {
        this(message, feature, null);
    }
    
    /**
     * Creates a new NotImplementedException with the given message and expected implementation date.
     *
     * @param message the error message
     * @param feature the feature that is not implemented
     * @param expectedImplementationDate the expected implementation date
     */
    public NotImplementedException(String message, String feature, String expectedImplementationDate) {
        super(HttpStatus.NOT_IMPLEMENTED, "NOT_IMPLEMENTED", message);
        this.feature = feature;
        this.expectedImplementationDate = expectedImplementationDate;
    }
    
    /**
     * Creates a new NotImplementedException with a code, message, and expected implementation date.
     *
     * @param code the error code
     * @param message the error message
     * @param feature the feature that is not implemented
     * @param expectedImplementationDate the expected implementation date
     */
    public NotImplementedException(String code, String message, String feature, String expectedImplementationDate) {
        super(HttpStatus.NOT_IMPLEMENTED, code, message);
        this.feature = feature;
        this.expectedImplementationDate = expectedImplementationDate;
    }
    
    /**
     * Returns the feature that is not implemented.
     *
     * @return the feature
     */
    public String getFeature() {
        return feature;
    }
    
    /**
     * Returns the expected implementation date, if known.
     *
     * @return the expected implementation date, or null if not known
     */
    public String getExpectedImplementationDate() {
        return expectedImplementationDate;
    }
    
    /**
     * Creates a new NotImplementedException for a feature.
     *
     * @param feature the feature that is not implemented
     * @return a new NotImplementedException
     */
    public static NotImplementedException forFeature(String feature) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("feature", feature);
        
        return (NotImplementedException) new NotImplementedException(
                String.format("The requested feature '%s' is not implemented", feature),
                feature
        ).withMetadata(metadata);
    }
    
    /**
     * Creates a new NotImplementedException for a feature with an expected implementation date.
     *
     * @param feature the feature that is not implemented
     * @param expectedImplementationDate the expected implementation date
     * @return a new NotImplementedException
     */
    public static NotImplementedException forFeatureWithDate(String feature, String expectedImplementationDate) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("feature", feature);
        metadata.put("expectedImplementationDate", expectedImplementationDate);
        
        return (NotImplementedException) new NotImplementedException(
                String.format("The requested feature '%s' is not implemented. Expected implementation date: %s", 
                        feature, expectedImplementationDate),
                feature,
                expectedImplementationDate
        ).withMetadata(metadata);
    }
    
    /**
     * Creates a new NotImplementedException for an HTTP method.
     *
     * @param method the HTTP method that is not implemented
     * @param path the path for which the method is not implemented
     * @return a new NotImplementedException
     */
    public static NotImplementedException forMethod(String method, String path) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("method", method);
        metadata.put("path", path);
        
        return (NotImplementedException) new NotImplementedException(
                "METHOD_NOT_IMPLEMENTED",
                String.format("The HTTP method '%s' is not implemented for path '%s'", method, path),
                method + " " + path,
                null
        ).withMetadata(metadata);
    }
    
    /**
     * Creates a new NotImplementedException for an API version.
     *
     * @param version the API version that is not implemented
     * @return a new NotImplementedException
     */
    public static NotImplementedException forApiVersion(String version) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("apiVersion", version);
        
        return (NotImplementedException) new NotImplementedException(
                "API_VERSION_NOT_IMPLEMENTED",
                String.format("API version '%s' is not implemented", version),
                "API v" + version,
                null
        ).withMetadata(metadata);
    }
}
