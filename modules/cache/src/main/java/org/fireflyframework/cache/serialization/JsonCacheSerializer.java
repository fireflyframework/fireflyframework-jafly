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

package org.fireflyframework.cache.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * JSON-based cache serializer using Jackson ObjectMapper.
 * <p>
 * This serializer converts objects to JSON strings for storage and back
 * to objects for retrieval. It provides human-readable cache values and
 * good compatibility across different systems.
 */
@Slf4j
public class JsonCacheSerializer implements CacheSerializer {

    private final ObjectMapper objectMapper;
    private final Set<Class<?>> supportedTypes;

    public JsonCacheSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        // Most types are supported by JSON, except for very special cases
        this.supportedTypes = Set.of(); // Empty means all types are supported by default
    }

    @Override
    public Object serialize(Object object) throws SerializationException {
        if (object == null) {
            return null;
        }

        // For primitive types and strings, return as-is for better performance
        if (isPrimitive(object.getClass())) {
            return object;
        }

        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            log.error("Failed to serialize object of type {} to JSON: {}", 
                     object.getClass().getName(), e.getMessage(), e);
            throw new SerializationException("Failed to serialize object to JSON", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(Object data, Class<T> type) throws SerializationException {
        if (data == null) {
            return null;
        }

        // For primitive types and strings, cast directly if compatible
        if (isPrimitive(type) && type.isInstance(data)) {
            return (T) data;
        }

        // Handle string data (JSON)
        if (data instanceof String jsonString) {
            try {
                return objectMapper.readValue(jsonString, type);
            } catch (Exception e) {
                log.error("Failed to deserialize JSON string to type {}: {}", 
                         type.getName(), e.getMessage(), e);
                throw new SerializationException("Failed to deserialize JSON to object", e);
            }
        }

        // Try direct cast as fallback
        if (type.isInstance(data)) {
            return type.cast(data);
        }

        // Try converting via JSON
        try {
            String json = objectMapper.writeValueAsString(data);
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.error("Failed to convert object of type {} to type {} via JSON: {}", 
                     data.getClass().getName(), type.getName(), e.getMessage(), e);
            throw new SerializationException("Failed to convert object via JSON", e);
        }
    }

    @Override
    public boolean supports(Class<?> type) {
        // JSON serializer supports most types except for very special cases
        return !isUnsupportedType(type);
    }

    @Override
    public String getType() {
        return "json";
    }

    /**
     * Checks if a type is a primitive or commonly used simple type.
     *
     * @param type the type to check
     * @return true if the type is primitive-like
     */
    private boolean isPrimitive(Class<?> type) {
        return type.isPrimitive() ||
               type == String.class ||
               type == Boolean.class ||
               type == Integer.class ||
               type == Long.class ||
               type == Double.class ||
               type == Float.class ||
               type == Short.class ||
               type == Byte.class ||
               type == Character.class ||
               Number.class.isAssignableFrom(type);
    }

    /**
     * Checks if a type is known to be unsupported by JSON serialization.
     *
     * @param type the type to check
     * @return true if the type is unsupported
     */
    private boolean isUnsupportedType(Class<?> type) {
        // Examples of types that might not serialize well to JSON
        return type == Thread.class ||
               type == Class.class ||
               type.getName().startsWith("sun.") ||
               type.getName().startsWith("com.sun.");
    }

    /**
     * Gets the underlying ObjectMapper.
     *
     * @return the ObjectMapper instance
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}