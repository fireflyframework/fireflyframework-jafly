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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.function.Function;

/**
 * Dynamic JSON response handler that allows working with JSON responses
 * without predefined DTOs.
 *
 * <p>This class provides a flexible way to work with JSON responses when you don't
 * want to create DTOs or when the response structure is dynamic or unknown at compile time.
 *
 * <p><b>Features:</b>
 * <ul>
 *   <li><b>Reactive Support:</b> Full integration with Project Reactor (Mono/Flux)</li>
 *   <li><b>Dot Notation:</b> Access nested fields: {@code response.get("user.address.city")}</li>
 *   <li><b>Type-Safe Conversions:</b> {@code getString()}, {@code getInt()}, {@code getBoolean()}, etc.</li>
 *   <li><b>Array Support:</b> {@code getList()}, {@code getObjectList()}</li>
 *   <li><b>Schema Introspection:</b> {@code getFieldNames()}, {@code getSchema()}, {@code getDetailedSchema()}</li>
 *   <li><b>Dynamic Class Generation:</b> {@code toDynamicClass()} creates runtime classes</li>
 *   <li><b>Null-Safe Operations:</b> Optional-based methods</li>
 *   <li><b>Nested Navigation:</b> Navigate complex JSON structures easily</li>
 * </ul>
 *
 * <p><b>Reactive Usage (Recommended):</b>
 * <pre>{@code
 * // Work within reactive context - NO .block() needed!
 * Mono<String> userName = client.get("/users/123", DynamicJsonResponse.class)
 *     .execute()
 *     .map(response -> response.getString("name"));
 *
 * // Chain multiple operations
 * Mono<String> cityName = client.get("/users/123", DynamicJsonResponse.class)
 *     .execute()
 *     .map(response -> response.getString("address.city"))
 *     .defaultIfEmpty("Unknown");
 *
 * // Transform to another type
 * Mono<UserDTO> user = client.get("/users/123", DynamicJsonResponse.class)
 *     .execute()
 *     .map(response -> response.toObject(UserDTO.class));
 *
 * // Work with nested objects reactively
 * Mono<List<String>> roles = client.get("/users/123", DynamicJsonResponse.class)
 *     .execute()
 *     .map(response -> response.getList("roles", String.class));
 *
 * // Conditional processing
 * Mono<String> email = client.get("/users/123", DynamicJsonResponse.class)
 *     .execute()
 *     .filter(response -> response.has("email"))
 *     .map(response -> response.getString("email"));
 * }</pre>
 *
 * <p><b>Dynamic Class Generation:</b>
 * <pre>{@code
 * // Generate a dynamic class at runtime from JSON schema
 * Mono<Object> dynamicInstance = client.get("/users/123", DynamicJsonResponse.class)
 *     .execute()
 *     .map(response -> response.toDynamicClass("User"));
 *
 * // The generated class will have getters/setters for all fields
 * // and can be used like a regular DTO
 * }</pre>
 *
 * <p><b>Traditional Usage (when needed):</b>
 * <pre>{@code
 * DynamicJsonResponse response = client.get("/users/123", DynamicJsonResponse.class)
 *     .execute()
 *     .block();
 *
 * String name = response.getString("name");
 * Integer age = response.getInt("age");
 * String city = response.getString("address.city");
 * }</pre>
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 */
public class DynamicJsonResponse {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JsonNode rootNode;
    private final String rawJson;

    /**
     * Creates a DynamicJsonResponse from a JSON string.
     *
     * @param json the JSON string
     * @return DynamicJsonResponse instance
     * @throws IllegalArgumentException if JSON is invalid
     */
    public static DynamicJsonResponse fromJson(String json) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json);
            return new DynamicJsonResponse(node, json);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a DynamicJsonResponse from a JsonNode.
     *
     * @param node the JsonNode
     * @return DynamicJsonResponse instance
     */
    public static DynamicJsonResponse fromNode(JsonNode node) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(node);
            return new DynamicJsonResponse(node, json);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize JsonNode: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a DynamicJsonResponse from a Map.
     *
     * @param map the map
     * @return DynamicJsonResponse instance
     */
    public static DynamicJsonResponse fromMap(Map<String, Object> map) {
        JsonNode node = OBJECT_MAPPER.valueToTree(map);
        return fromNode(node);
    }

    private DynamicJsonResponse(JsonNode rootNode, String rawJson) {
        this.rootNode = rootNode;
        this.rawJson = rawJson;
    }

    // ========================================
    // Field Access Methods
    // ========================================

    /**
     * Gets a value at the specified path.
     * Supports dot notation for nested fields: "user.address.city"
     *
     * @param path the field path
     * @return the value as Object, or null if not found
     */
    public Object get(String path) {
        JsonNode node = navigateToNode(path);
        if (node == null || node.isNull()) {
            return null;
        }
        return convertNodeToObject(node);
    }

    /**
     * Gets a String value at the specified path.
     *
     * @param path the field path
     * @return the String value, or null if not found
     */
    public String getString(String path) {
        JsonNode node = navigateToNode(path);
        return node != null && !node.isNull() ? node.asText() : null;
    }

    /**
     * Gets an Integer value at the specified path.
     *
     * @param path the field path
     * @return the Integer value, or null if not found
     */
    public Integer getInt(String path) {
        JsonNode node = navigateToNode(path);
        return node != null && !node.isNull() && node.isNumber() ? node.asInt() : null;
    }

    /**
     * Gets a Long value at the specified path.
     *
     * @param path the field path
     * @return the Long value, or null if not found
     */
    public Long getLong(String path) {
        JsonNode node = navigateToNode(path);
        return node != null && !node.isNull() && node.isNumber() ? node.asLong() : null;
    }

    /**
     * Gets a Double value at the specified path.
     *
     * @param path the field path
     * @return the Double value, or null if not found
     */
    public Double getDouble(String path) {
        JsonNode node = navigateToNode(path);
        return node != null && !node.isNull() && node.isNumber() ? node.asDouble() : null;
    }

    /**
     * Gets a Boolean value at the specified path.
     *
     * @param path the field path
     * @return the Boolean value, or null if not found
     */
    public Boolean getBoolean(String path) {
        JsonNode node = navigateToNode(path);
        return node != null && !node.isNull() && node.isBoolean() ? node.asBoolean() : null;
    }

    /**
     * Gets a BigDecimal value at the specified path.
     *
     * @param path the field path
     * @return the BigDecimal value, or null if not found
     */
    public BigDecimal getBigDecimal(String path) {
        JsonNode node = navigateToNode(path);
        if (node != null && !node.isNull() && node.isNumber()) {
            return node.decimalValue();
        }
        return null;
    }

    /**
     * Gets a nested object as DynamicJsonResponse.
     *
     * @param path the field path
     * @return DynamicJsonResponse for the nested object, or null if not found
     */
    public DynamicJsonResponse getObject(String path) {
        JsonNode node = navigateToNode(path);
        if (node != null && !node.isNull() && node.isObject()) {
            return fromNode(node);
        }
        return null;
    }

    /**
     * Gets a value and converts it to the specified type.
     *
     * @param path the field path
     * @param type the target type
     * @param <T> the type parameter
     * @return the converted value, or null if not found
     */
    public <T> T getAs(String path, Class<T> type) {
        JsonNode node = navigateToNode(path);
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.treeToValue(node, type);
        } catch (Exception e) {
            // Failed to convert - returning null
            return null;
        }
    }

    /**
     * Gets a list of values at the specified path.
     *
     * @param path the field path
     * @param elementType the element type
     * @param <T> the type parameter
     * @return list of values, or empty list if not found
     */
    public <T> List<T> getList(String path, Class<T> elementType) {
        JsonNode node = navigateToNode(path);
        if (node == null || node.isNull() || !node.isArray()) {
            return Collections.emptyList();
        }

        try {
            return OBJECT_MAPPER.convertValue(
                node,
                OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, elementType)
            );
        } catch (Exception e) {
            // Failed to convert - returning empty list
            return Collections.emptyList();
        }
    }

    /**
     * Gets a list of nested objects as DynamicJsonResponse.
     *
     * @param path the field path
     * @return list of DynamicJsonResponse, or empty list if not found
     */
    public List<DynamicJsonResponse> getObjectList(String path) {
        JsonNode node = navigateToNode(path);
        if (node == null || node.isNull() || !node.isArray()) {
            return Collections.emptyList();
        }

        return StreamSupport.stream(node.spliterator(), false)
            .filter(JsonNode::isObject)
            .map(DynamicJsonResponse::fromNode)
            .collect(Collectors.toList());
    }

    // ========================================
    // Schema Introspection Methods
    // ========================================

    /**
     * Checks if a field exists at the specified path.
     *
     * @param path the field path
     * @return true if the field exists
     */
    public boolean has(String path) {
        JsonNode node = navigateToNode(path);
        return node != null && !node.isNull();
    }

    /**
     * Gets all field names at the root level.
     *
     * @return set of field names
     */
    public Set<String> getFieldNames() {
        if (!rootNode.isObject()) {
            return Collections.emptySet();
        }

        Set<String> fieldNames = new HashSet<>();
        rootNode.fieldNames().forEachRemaining(fieldNames::add);
        return fieldNames;
    }

    /**
     * Gets the schema of the JSON response.
     * Returns a map of field names to their types.
     *
     * @return map of field name to type
     */
    public Map<String, String> getSchema() {
        if (!rootNode.isObject()) {
            return Collections.emptyMap();
        }

        Map<String, String> schema = new LinkedHashMap<>();
        rootNode.properties().forEach(entry -> {
            String fieldName = entry.getKey();
            JsonNode fieldNode = entry.getValue();
            schema.put(fieldName, getNodeType(fieldNode));
        });
        return schema;
    }

    /**
     * Gets a detailed schema including nested objects.
     *
     * @return nested map representing the schema
     */
    public Map<String, Object> getDetailedSchema() {
        return buildDetailedSchema(rootNode);
    }

    /**
     * Checks if the response is an array.
     *
     * @return true if the root node is an array
     */
    public boolean isArray() {
        return rootNode.isArray();
    }

    /**
     * Checks if the response is an object.
     *
     * @return true if the root node is an object
     */
    public boolean isObject() {
        return rootNode.isObject();
    }

    /**
     * Gets the size of the array (if the root is an array).
     *
     * @return array size, or 0 if not an array
     */
    public int size() {
        return rootNode.isArray() ? rootNode.size() : 0;
    }

    // ========================================
    // Conversion Methods
    // ========================================

    /**
     * Converts the entire response to a specific type.
     *
     * @param type the target type
     * @param <T> the type parameter
     * @return the converted object
     */
    public <T> T toObject(Class<T> type) {
        try {
            return OBJECT_MAPPER.treeToValue(rootNode, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert to " + type.getName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Converts the entire response to a Map.
     *
     * @return Map representation
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> toMap() {
        return OBJECT_MAPPER.convertValue(rootNode, Map.class);
    }

    /**
     * Gets the raw JSON string.
     *
     * @return raw JSON
     */
    public String toJson() {
        return rawJson;
    }

    /**
     * Gets the raw JSON string with pretty printing.
     *
     * @return pretty-printed JSON
     */
    public String toPrettyJson() {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);
        } catch (Exception e) {
            return rawJson;
        }
    }

    /**
     * Gets the underlying JsonNode.
     *
     * @return the JsonNode
     */
    public JsonNode getJsonNode() {
        return rootNode;
    }

    // ========================================
    // Helper Methods
    // ========================================

    /**
     * Navigates to a node using dot notation path.
     */
    private JsonNode navigateToNode(String path) {
        if (path == null || path.isEmpty()) {
            return rootNode;
        }

        String[] parts = path.split("\\.");
        JsonNode current = rootNode;

        for (String part : parts) {
            if (current == null || current.isNull()) {
                return null;
            }

            // Handle array index notation: items[0]
            if (part.contains("[") && part.contains("]")) {
                String fieldName = part.substring(0, part.indexOf('['));
                int index = Integer.parseInt(part.substring(part.indexOf('[') + 1, part.indexOf(']')));

                current = current.get(fieldName);
                if (current != null && current.isArray() && index < current.size()) {
                    current = current.get(index);
                } else {
                    return null;
                }
            } else {
                current = current.get(part);
            }
        }

        return current;
    }

    /**
     * Converts a JsonNode to a Java Object.
     */
    private Object convertNodeToObject(JsonNode node) {
        if (node.isNull()) {
            return null;
        } else if (node.isBoolean()) {
            return node.asBoolean();
        } else if (node.isInt()) {
            return node.asInt();
        } else if (node.isLong()) {
            return node.asLong();
        } else if (node.isDouble() || node.isFloat()) {
            return node.asDouble();
        } else if (node.isTextual()) {
            return node.asText();
        } else if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(item -> list.add(convertNodeToObject(item)));
            return list;
        } else if (node.isObject()) {
            return fromNode(node);
        }
        return node.asText();
    }

    /**
     * Gets the type name of a JsonNode.
     */
    private String getNodeType(JsonNode node) {
        if (node.isNull()) {
            return "null";
        } else if (node.isBoolean()) {
            return "boolean";
        } else if (node.isInt()) {
            return "integer";
        } else if (node.isLong()) {
            return "long";
        } else if (node.isDouble() || node.isFloat()) {
            return "double";
        } else if (node.isTextual()) {
            return "string";
        } else if (node.isArray()) {
            return "array";
        } else if (node.isObject()) {
            return "object";
        }
        return "unknown";
    }

    /**
     * Builds a detailed schema recursively.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildDetailedSchema(JsonNode node) {
        if (!node.isObject()) {
            return Collections.emptyMap();
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        node.properties().forEach(entry -> {
            String fieldName = entry.getKey();
            JsonNode fieldNode = entry.getValue();

            if (fieldNode.isObject()) {
                schema.put(fieldName, buildDetailedSchema(fieldNode));
            } else if (fieldNode.isArray() && fieldNode.size() > 0 && fieldNode.get(0).isObject()) {
                schema.put(fieldName, Map.of(
                    "type", "array",
                    "elementSchema", buildDetailedSchema(fieldNode.get(0))
                ));
            } else {
                schema.put(fieldName, getNodeType(fieldNode));
            }
        });
        return schema;
    }

    // ========================================
    // Optional-based Methods
    // ========================================

    /**
     * Gets an Optional String value.
     *
     * @param path the field path
     * @return Optional containing the value
     */
    public Optional<String> getStringOpt(String path) {
        return Optional.ofNullable(getString(path));
    }

    /**
     * Gets an Optional Integer value.
     *
     * @param path the field path
     * @return Optional containing the value
     */
    public Optional<Integer> getIntOpt(String path) {
        return Optional.ofNullable(getInt(path));
    }

    /**
     * Gets an Optional Long value.
     *
     * @param path the field path
     * @return Optional containing the value
     */
    public Optional<Long> getLongOpt(String path) {
        return Optional.ofNullable(getLong(path));
    }

    /**
     * Gets an Optional Double value.
     *
     * @param path the field path
     * @return Optional containing the value
     */
    public Optional<Double> getDoubleOpt(String path) {
        return Optional.ofNullable(getDouble(path));
    }

    /**
     * Gets an Optional Boolean value.
     *
     * @param path the field path
     * @return Optional containing the value
     */
    public Optional<Boolean> getBooleanOpt(String path) {
        return Optional.ofNullable(getBoolean(path));
    }

    // ========================================
    // Reactive Methods
    // ========================================

    /**
     * Maps a field value reactively within a Mono context.
     * This is the recommended way to work with DynamicJsonResponse in reactive pipelines.
     *
     * <p>Example:
     * <pre>{@code
     * Mono<String> userName = client.get("/users/123", DynamicJsonResponse.class)
     *     .execute()
     *     .flatMap(response -> response.mapMono(r -> r.getString("name")));
     * }</pre>
     *
     * @param mapper function to extract value from response
     * @param <T> the result type
     * @return Mono containing the mapped value
     */
    public <T> Mono<T> mapMono(Function<DynamicJsonResponse, T> mapper) {
        return Mono.just(this).map(mapper);
    }

    /**
     * Maps a field value to a Mono reactively.
     *
     * <p>Example:
     * <pre>{@code
     * Mono<UserDTO> user = client.get("/users/123", DynamicJsonResponse.class)
     *     .execute()
     *     .flatMap(response -> response.flatMapMono(r ->
     *         Mono.just(r.toObject(UserDTO.class))
     *     ));
     * }</pre>
     *
     * @param mapper function that returns a Mono
     * @param <T> the result type
     * @return Mono containing the mapped value
     */
    public <T> Mono<T> flatMapMono(Function<DynamicJsonResponse, Mono<T>> mapper) {
        return Mono.just(this).flatMap(mapper);
    }

    /**
     * Converts an array field to a Flux for reactive streaming.
     *
     * <p>Example:
     * <pre>{@code
     * Flux<DynamicJsonResponse> orders = client.get("/users/123", DynamicJsonResponse.class)
     *     .execute()
     *     .flatMapMany(response -> response.toFlux("orders"));
     * }</pre>
     *
     * @param arrayPath path to the array field
     * @return Flux of DynamicJsonResponse objects
     */
    public Flux<DynamicJsonResponse> toFlux(String arrayPath) {
        List<DynamicJsonResponse> list = getObjectList(arrayPath);
        return Flux.fromIterable(list);
    }

    /**
     * Converts an array field to a Flux with type conversion.
     *
     * <p>Example:
     * <pre>{@code
     * Flux<String> roles = client.get("/users/123", DynamicJsonResponse.class)
     *     .execute()
     *     .flatMapMany(response -> response.toFlux("roles", String.class));
     * }</pre>
     *
     * @param arrayPath path to the array field
     * @param elementType the element type
     * @param <T> the element type
     * @return Flux of typed elements
     */
    public <T> Flux<T> toFlux(String arrayPath, Class<T> elementType) {
        List<T> list = getList(arrayPath, elementType);
        return Flux.fromIterable(list);
    }

    // ========================================
    // Dynamic Class Generation
    // ========================================

    /**
     * Generates a dynamic class at runtime based on the JSON schema.
     * The generated class will have fields, getters, and setters for all JSON properties.
     *
     * <p>This allows you to work with the response as if it were a real DTO,
     * with full IDE support for field access (after casting).
     *
     * <p><b>Note:</b> This uses runtime bytecode generation and reflection.
     * For production use, consider caching generated classes.
     *
     * <p>Example:
     * <pre>{@code
     * DynamicJsonResponse response = ...;
     * Object userInstance = response.toDynamicClass("User");
     *
     * // Access via reflection or cast to generated interface
     * Method getName = userInstance.getClass().getMethod("getName");
     * String name = (String) getName.invoke(userInstance);
     * }</pre>
     *
     * @param className the name for the generated class
     * @return an instance of the dynamically generated class
     */
    public Object toDynamicClass(String className) {
        return DynamicClassGenerator.generateClass(className, this.rootNode, OBJECT_MAPPER);
    }

    /**
     * Generates a dynamic class and returns it as a Map-like proxy.
     * This provides a simpler interface for accessing fields without reflection.
     *
     * <p>Example:
     * <pre>{@code
     * DynamicObject user = response.toDynamicObject();
     * String name = user.get("name", String.class);
     * Integer age = user.get("age", Integer.class);
     * }</pre>
     *
     * @return a DynamicObject wrapper
     */
    public DynamicObject toDynamicObject() {
        return new DynamicObject(this);
    }

    @Override
    public String toString() {
        return toPrettyJson();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DynamicJsonResponse that = (DynamicJsonResponse) o;
        return Objects.equals(rootNode, that.rootNode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rootNode);
    }
}
