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

package org.fireflyframework.rules.core.services.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.rules.core.services.JsonPathService;
import org.fireflyframework.rules.core.utils.JsonLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of JsonPathService for accessing nested JSON data.
 * Supports dot notation (obj.prop), array indexing (arr[0]), and special properties (arr.length).
 */
@Slf4j
@Service
public class JsonPathServiceImpl implements JsonPathService {

    private final ObjectMapper objectMapper;

    // Pattern to match array indexing: [0], [123], etc.
    private static final Pattern ARRAY_INDEX_PATTERN = Pattern.compile("\\[(\\d+)\\]");

    // Pattern to validate path segments
    private static final Pattern VALID_SEGMENT_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    public JsonPathServiceImpl() {
        this.objectMapper = new ObjectMapper();
    }

    public JsonPathServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Override
    public Object extractValue(Object jsonData, String path) {
        if (jsonData == null || path == null || path.trim().isEmpty()) {
            return null;
        }

        try {
            // Parse JSON string if needed
            Object parsedData = parseJsonIfNeeded(jsonData);
            return navigatePath(parsedData, path);
        } catch (Exception e) {
            JsonLogger.warn(log, String.format("Failed to extract value at path '%s': %s", path, e.getMessage()));
            return null;
        }
    }
    
    @Override
    public boolean pathExists(Object jsonData, String path) {
        return extractValue(jsonData, path) != null;
    }
    
    @Override
    public Class<?> getValueType(Object jsonData, String path) {
        Object value = extractValue(jsonData, path);
        return value != null ? value.getClass() : null;
    }
    
    @Override
    public Map<String, Object> extractMultiple(Object jsonData, List<String> paths) {
        Map<String, Object> results = new HashMap<>();
        
        if (paths != null) {
            for (String path : paths) {
                results.put(path, extractValue(jsonData, path));
            }
        }
        
        return results;
    }
    
    @Override
    public boolean setValue(Object jsonData, String path, Object value) {
        if (jsonData == null || path == null || path.trim().isEmpty()) {
            return false;
        }
        
        try {
            return setValueAtPath(jsonData, path, value);
        } catch (Exception e) {
            JsonLogger.warn(log, String.format("Failed to set value at path '%s': %s", path, e.getMessage()));
            return false;
        }
    }
    
    @Override
    public int getSize(Object jsonData, String path) {
        Object value = extractValue(jsonData, path);
        
        if (value instanceof List<?> list) {
            return list.size();
        } else if (value instanceof Map<?, ?> map) {
            return map.size();
        } else if (value instanceof Object[] array) {
            return array.length;
        }
        
        return -1;
    }
    
    @Override
    public boolean isValidPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        
        try {
            String[] segments = splitPath(path);
            for (String segment : segments) {
                if (!isValidSegment(segment)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Navigate through the JSON structure following the given path
     */
    private Object navigatePath(Object current, String path) {
        String[] segments = splitPath(path);
        
        for (String segment : segments) {
            current = navigateSegment(current, segment);
            if (current == null) {
                return null;
            }
        }
        
        return current;
    }
    
    /**
     * Navigate a single path segment
     */
    private Object navigateSegment(Object current, String segment) {
        if (current == null) {
            return null;
        }
        
        // Handle special properties
        if ("length".equals(segment) || "size".equals(segment)) {
            return getSize(current, "");
        }
        
        // Handle array indexing
        Matcher arrayMatcher = ARRAY_INDEX_PATTERN.matcher(segment);
        if (arrayMatcher.find()) {
            int index = Integer.parseInt(arrayMatcher.group(1));
            String propertyName = segment.substring(0, arrayMatcher.start());
            
            // First navigate to the property if it exists
            Object target = current;
            if (!propertyName.isEmpty()) {
                target = getProperty(current, propertyName);
            }
            
            return getArrayElement(target, index);
        }
        
        // Handle regular property access
        return getProperty(current, segment);
    }
    
    /**
     * Get a property from an object
     */
    private Object getProperty(Object obj, String propertyName) {
        if (obj instanceof Map<?, ?> map) {
            return map.get(propertyName);
        }
        
        // For other object types, we could use reflection here
        // but for now we'll only support Map-based JSON objects
        return null;
    }
    
    /**
     * Get an element from an array or list
     */
    private Object getArrayElement(Object obj, int index) {
        if (obj instanceof List<?> list) {
            return index >= 0 && index < list.size() ? list.get(index) : null;
        } else if (obj instanceof Object[] array) {
            return index >= 0 && index < array.length ? array[index] : null;
        }
        
        return null;
    }
    
    /**
     * Split a path into segments, handling array notation
     */
    private String[] splitPath(String path) {
        // Replace array notation with dot notation for easier splitting
        // e.g., "users[0].name" becomes "users.[0].name"
        String normalizedPath = path.replaceAll("\\[", ".[");
        
        return normalizedPath.split("\\.");
    }
    
    /**
     * Check if a path segment is valid
     */
    private boolean isValidSegment(String segment) {
        if (segment.isEmpty()) {
            return true; // Empty segments can occur from splitting
        }
        
        // Check for array indexing
        if (ARRAY_INDEX_PATTERN.matcher(segment).matches()) {
            return true;
        }
        
        // Check for special properties
        if ("length".equals(segment) || "size".equals(segment)) {
            return true;
        }
        
        // Check for valid identifier
        return VALID_SEGMENT_PATTERN.matcher(segment).matches();
    }
    
    /**
     * Set a value at the specified path (simplified implementation)
     */
    private boolean setValueAtPath(Object jsonData, String path, Object value) {
        // This is a simplified implementation - in a full implementation,
        // we would need to handle creating intermediate objects
        String[] segments = splitPath(path);
        
        if (segments.length == 0) {
            return false;
        }
        
        Object current = jsonData;
        
        // Navigate to the parent of the target
        for (int i = 0; i < segments.length - 1; i++) {
            current = navigateSegment(current, segments[i]);
            if (current == null) {
                return false;
            }
        }
        
        // Set the final value
        String finalSegment = segments[segments.length - 1];
        if (current instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> mutableMap = (Map<String, Object>) map;
            mutableMap.put(finalSegment, value);
            return true;
        }
        
        return false;
    }

    /**
     * Parse JSON string if the input is a string, otherwise return as-is
     */
    private Object parseJsonIfNeeded(Object jsonData) {
        if (jsonData instanceof String) {
            String jsonString = (String) jsonData;
            // Check if it looks like JSON (starts with { or [)
            String trimmed = jsonString.trim();
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                try {
                    return objectMapper.readValue(jsonString, Object.class);
                } catch (Exception e) {
                    JsonLogger.warn(log, String.format("Failed to parse JSON string: %s", e.getMessage()));
                    // Return the original string if parsing fails
                    return jsonData;
                }
            }
        }
        // Return as-is if not a JSON string
        return jsonData;
    }
}
