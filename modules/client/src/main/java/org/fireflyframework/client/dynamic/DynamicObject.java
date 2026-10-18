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

package org.fireflyframework.client.dynamic;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A wrapper around DynamicJsonResponse that provides a more DTO-like interface.
 * 
 * <p>This class makes it easier to work with dynamic JSON responses by providing
 * type-safe getter methods without needing to call specific type methods.
 *
 * <p>Example usage:
 * <pre>{@code
 * DynamicObject user = response.toDynamicObject();
 * 
 * // Type-safe access
 * String name = user.get("name", String.class);
 * Integer age = user.get("age", Integer.class);
 * Boolean active = user.get("active", Boolean.class);
 * 
 * // Nested access
 * String city = user.get("address.city", String.class);
 * 
 * // Lists
 * List<String> roles = user.getList("roles", String.class);
 * 
 * // Check existence
 * if (user.has("email")) {
 *     String email = user.get("email", String.class);
 * }
 * }</pre>
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 */
public class DynamicObject {
    
    private final DynamicJsonResponse response;
    
    /**
     * Creates a DynamicObject wrapper.
     *
     * @param response the underlying DynamicJsonResponse
     */
    public DynamicObject(DynamicJsonResponse response) {
        this.response = response;
    }
    
    /**
     * Gets a field value with automatic type conversion.
     *
     * @param path the field path (supports dot notation)
     * @param type the expected type
     * @param <T> the type parameter
     * @return the value, or null if not found
     */
    public <T> T get(String path, Class<T> type) {
        return response.getAs(path, type);
    }
    
    /**
     * Gets a String field.
     *
     * @param path the field path
     * @return the String value
     */
    public String getString(String path) {
        return response.getString(path);
    }
    
    /**
     * Gets an Integer field.
     *
     * @param path the field path
     * @return the Integer value
     */
    public Integer getInt(String path) {
        return response.getInt(path);
    }
    
    /**
     * Gets a Long field.
     *
     * @param path the field path
     * @return the Long value
     */
    public Long getLong(String path) {
        return response.getLong(path);
    }
    
    /**
     * Gets a Double field.
     *
     * @param path the field path
     * @return the Double value
     */
    public Double getDouble(String path) {
        return response.getDouble(path);
    }
    
    /**
     * Gets a Boolean field.
     *
     * @param path the field path
     * @return the Boolean value
     */
    public Boolean getBoolean(String path) {
        return response.getBoolean(path);
    }
    
    /**
     * Gets a nested object.
     *
     * @param path the field path
     * @return a DynamicObject for the nested object
     */
    public DynamicObject getObject(String path) {
        DynamicJsonResponse nested = response.getObject(path);
        return nested != null ? new DynamicObject(nested) : null;
    }
    
    /**
     * Gets a list of values.
     *
     * @param path the field path
     * @param elementType the element type
     * @param <T> the type parameter
     * @return the list of values
     */
    public <T> List<T> getList(String path, Class<T> elementType) {
        return response.getList(path, elementType);
    }
    
    /**
     * Gets a list of nested objects.
     *
     * @param path the field path
     * @return list of DynamicObjects
     */
    public List<DynamicObject> getObjectList(String path) {
        return response.getObjectList(path).stream()
            .map(DynamicObject::new)
            .toList();
    }
    
    /**
     * Checks if a field exists.
     *
     * @param path the field path
     * @return true if the field exists
     */
    public boolean has(String path) {
        return response.has(path);
    }
    
    /**
     * Gets all field names at the root level.
     *
     * @return set of field names
     */
    public Set<String> getFieldNames() {
        return response.getFieldNames();
    }
    
    /**
     * Gets the schema.
     *
     * @return map of field names to types
     */
    public Map<String, String> getSchema() {
        return response.getSchema();
    }
    
    /**
     * Converts to a specific type.
     *
     * @param type the target type
     * @param <T> the type parameter
     * @return the converted object
     */
    public <T> T toObject(Class<T> type) {
        return response.toObject(type);
    }
    
    /**
     * Converts to a Map.
     *
     * @return Map representation
     */
    public Map<String, Object> toMap() {
        return response.toMap();
    }
    
    /**
     * Gets the underlying DynamicJsonResponse.
     *
     * @return the DynamicJsonResponse
     */
    public DynamicJsonResponse getResponse() {
        return response;
    }
    
    @Override
    public String toString() {
        return response.toString();
    }
}

