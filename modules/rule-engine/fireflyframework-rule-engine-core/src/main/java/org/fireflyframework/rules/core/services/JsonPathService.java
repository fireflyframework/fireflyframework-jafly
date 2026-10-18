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

package org.fireflyframework.rules.core.services;

import java.util.List;
import java.util.Map;

/**
 * Service interface for accessing nested JSON data using path expressions.
 * Supports dot notation, array indexing, and special properties like length.
 */
public interface JsonPathService {
    
    /**
     * Extract a value from a JSON object using a path expression
     * 
     * @param jsonData the source JSON data (Map, List, or primitive)
     * @param path the path expression (e.g., "user.name", "items[0].price", "users.length")
     * @return the extracted value, or null if path doesn't exist
     */
    Object extractValue(Object jsonData, String path);
    
    /**
     * Check if a path exists in the JSON data
     * 
     * @param jsonData the source JSON data
     * @param path the path expression
     * @return true if the path exists, false otherwise
     */
    boolean pathExists(Object jsonData, String path);
    
    /**
     * Get the type of value at the specified path
     * 
     * @param jsonData the source JSON data
     * @param path the path expression
     * @return the class type of the value, or null if path doesn't exist
     */
    Class<?> getValueType(Object jsonData, String path);
    
    /**
     * Extract multiple values using multiple paths
     * 
     * @param jsonData the source JSON data
     * @param paths list of path expressions
     * @return Map of path -> extracted value
     */
    Map<String, Object> extractMultiple(Object jsonData, List<String> paths);
    
    /**
     * Set a value at the specified path (creates intermediate objects if needed)
     * 
     * @param jsonData the target JSON data (must be mutable Map or List)
     * @param path the path expression
     * @param value the value to set
     * @return true if successful, false otherwise
     */
    boolean setValue(Object jsonData, String path, Object value);
    
    /**
     * Get the size/length of an array or object at the specified path
     *
     * @param jsonData the source JSON data
     * @param path the path expression (can end with .length or .size)
     * @return the size/length, or -1 if not applicable
     */
    int getSize(Object jsonData, String path);

    /**
     * Validate that a path expression is syntactically correct
     *
     * @param path the path expression to validate
     * @return true if valid, false otherwise
     */
    boolean isValidPath(String path);
}
