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

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for DynamicJsonResponse.
 */
class DynamicJsonResponseTest {

    private static final String SAMPLE_JSON = """
        {
            "id": 123,
            "name": "John Doe",
            "email": "john@example.com",
            "age": 30,
            "active": true,
            "balance": 1234.56,
            "address": {
                "street": "123 Main St",
                "city": "New York",
                "zipCode": "10001"
            },
            "tags": ["developer", "java", "spring"],
            "orders": [
                {
                    "orderId": "ORD-001",
                    "amount": 99.99
                },
                {
                    "orderId": "ORD-002",
                    "amount": 149.99
                }
            ]
        }
        """;

    @Test
    void shouldParseJsonString() {
        // When
        DynamicJsonResponse response = DynamicJsonResponse.fromJson(SAMPLE_JSON);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isObject()).isTrue();
    }

    @Test
    void shouldGetStringField() {
        // Given
        DynamicJsonResponse response = DynamicJsonResponse.fromJson(SAMPLE_JSON);

        // When
        String name = response.getString("name");
        String email = response.getString("email");

        // Then
        assertThat(name).isEqualTo("John Doe");
        assertThat(email).isEqualTo("john@example.com");
    }

    @Test
    void shouldGetIntegerField() {
        // Given
        DynamicJsonResponse response = DynamicJsonResponse.fromJson(SAMPLE_JSON);

        // When
        Integer id = response.getInt("id");
        Integer age = response.getInt("age");

        // Then
        assertThat(id).isEqualTo(123);
        assertThat(age).isEqualTo(30);
    }

    @Test
    void shouldGetBooleanField() {
        // Given
        DynamicJsonResponse response = DynamicJsonResponse.fromJson(SAMPLE_JSON);

        // When
        Boolean active = response.getBoolean("active");

        // Then
        assertThat(active).isTrue();
    }

    @Test
    void shouldGetDoubleField() {
        // Given
        DynamicJsonResponse response = DynamicJsonResponse.fromJson(SAMPLE_JSON);

        // When
        Double balance = response.getDouble("balance");

        // Then
        assertThat(balance).isEqualTo(1234.56);
    }

    @Test
    void shouldGetNestedFieldWithDotNotation() {
        // Given
        DynamicJsonResponse response = DynamicJsonResponse.fromJson(SAMPLE_JSON);

        // When
        String street = response.getString("address.street");
        String city = response.getString("address.city");
        String zipCode = response.getString("address.zipCode");

        // Then
        assertThat(street).isEqualTo("123 Main St");
        assertThat(city).isEqualTo("New York");
        assertThat(zipCode).isEqualTo("10001");
    }

    @Test
    void shouldGetNestedObject() {
        // Given
        DynamicJsonResponse response = DynamicJsonResponse.fromJson(SAMPLE_JSON);

        // When
        DynamicJsonResponse address = response.getObject("address");

        // Then
        assertThat(address).isNotNull();
        assertThat(address.getString("street")).isEqualTo("123 Main St");
        assertThat(address.getString("city")).isEqualTo("New York");
    }

    @Test
    void shouldGetList() {
        // Given
        DynamicJsonResponse response = DynamicJsonResponse.fromJson(SAMPLE_JSON);

        // When
        List<String> tags = response.getList("tags", String.class);

        // Then
        assertThat(tags).hasSize(3);
        assertThat(tags).containsExactly("developer", "java", "spring");
    }

    @Test
    void shouldGetObjectList() {
        // Given
        DynamicJsonResponse response = DynamicJsonResponse.fromJson(SAMPLE_JSON);

        // When
        List<DynamicJsonResponse> orders = response.getObjectList("orders");

        // Then
        assertThat(orders).hasSize(2);
        assertThat(orders.get(0).getString("orderId")).isEqualTo("ORD-001");
        assertThat(orders.get(0).getDouble("amount")).isEqualTo(99.99);
        assertThat(orders.get(1).getString("orderId")).isEqualTo("ORD-002");
        assertThat(orders.get(1).getDouble("amount")).isEqualTo(149.99);
    }

    @Test
    void shouldCheckFieldExistence() {
        // Given
        DynamicJsonResponse response = DynamicJsonResponse.fromJson(SAMPLE_JSON);

        // When/Then
        assertThat(response.has("name")).isTrue();
        assertThat(response.has("email")).isTrue();
        assertThat(response.has("nonexistent")).isFalse();
        assertThat(response.has("address.city")).isTrue();
        assertThat(response.has("address.country")).isFalse();
    }

    @Test
    void shouldGetFieldNames() {
        // Given
        DynamicJsonResponse response = DynamicJsonResponse.fromJson(SAMPLE_JSON);

        // When
        Set<String> fieldNames = response.getFieldNames();

        // Then
        assertThat(fieldNames).contains("id", "name", "email", "age", "active", "balance", "address", "tags", "orders");
    }

    @Test
    void shouldGetSchema() {
        // Given
        DynamicJsonResponse response = DynamicJsonResponse.fromJson(SAMPLE_JSON);

        // When
        Map<String, String> schema = response.getSchema();

        // Then
        assertThat(schema).containsEntry("id", "integer");
        assertThat(schema).containsEntry("name", "string");
        assertThat(schema).containsEntry("age", "integer");
        assertThat(schema).containsEntry("active", "boolean");
        assertThat(schema).containsEntry("balance", "double");
        assertThat(schema).containsEntry("address", "object");
        assertThat(schema).containsEntry("tags", "array");
        assertThat(schema).containsEntry("orders", "array");
    }

    @Test
    void shouldGetDetailedSchema() {
        // Given
        DynamicJsonResponse response = DynamicJsonResponse.fromJson(SAMPLE_JSON);

        // When
        Map<String, Object> schema = response.getDetailedSchema();

        // Then
        assertThat(schema).containsKey("address");
        assertThat(schema.get("address")).isInstanceOf(Map.class);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> addressSchema = (Map<String, Object>) schema.get("address");
        assertThat(addressSchema).containsEntry("street", "string");
        assertThat(addressSchema).containsEntry("city", "string");
    }

    @Test
    void shouldConvertToMap() {
        // Given
        DynamicJsonResponse response = DynamicJsonResponse.fromJson(SAMPLE_JSON);

        // When
        Map<String, Object> map = response.toMap();

        // Then
        assertThat(map).containsKey("id");
        assertThat(map).containsKey("name");
        assertThat(map).containsKey("address");
        assertThat(map.get("name")).isEqualTo("John Doe");
    }

    @Test
    void shouldConvertToJson() {
        // Given
        DynamicJsonResponse response = DynamicJsonResponse.fromJson(SAMPLE_JSON);

        // When
        String json = response.toJson();

        // Then
        assertThat(json).contains("John Doe");
        assertThat(json).contains("john@example.com");
    }

    @Test
    void shouldConvertToPrettyJson() {
        // Given
        DynamicJsonResponse response = DynamicJsonResponse.fromJson(SAMPLE_JSON);

        // When
        String prettyJson = response.toPrettyJson();

        // Then
        assertThat(prettyJson).contains("John Doe");
        assertThat(prettyJson).contains("\n"); // Should have line breaks
    }

    @Test
    void shouldReturnNullForNonexistentField() {
        // Given
        DynamicJsonResponse response = DynamicJsonResponse.fromJson(SAMPLE_JSON);

        // When
        String nonexistent = response.getString("nonexistent");
        Integer nonexistentInt = response.getInt("nonexistent");

        // Then
        assertThat(nonexistent).isNull();
        assertThat(nonexistentInt).isNull();
    }

    @Test
    void shouldHandleOptionalMethods() {
        // Given
        DynamicJsonResponse response = DynamicJsonResponse.fromJson(SAMPLE_JSON);

        // When/Then
        assertThat(response.getStringOpt("name")).isPresent().contains("John Doe");
        assertThat(response.getStringOpt("nonexistent")).isEmpty();
        assertThat(response.getIntOpt("age")).isPresent().contains(30);
        assertThat(response.getBooleanOpt("active")).isPresent().contains(true);
    }

    @Test
    void shouldCreateFromMap() {
        // Given
        Map<String, Object> map = Map.of(
            "name", "Jane Doe",
            "age", 25,
            "active", true
        );

        // When
        DynamicJsonResponse response = DynamicJsonResponse.fromMap(map);

        // Then
        assertThat(response.getString("name")).isEqualTo("Jane Doe");
        assertThat(response.getInt("age")).isEqualTo(25);
        assertThat(response.getBoolean("active")).isTrue();
    }
}

