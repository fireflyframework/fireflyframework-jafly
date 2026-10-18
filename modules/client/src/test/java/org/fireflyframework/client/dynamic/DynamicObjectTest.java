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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for DynamicObject.
 */
class DynamicObjectTest {

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
                "city": "New York"
            },
            "roles": ["admin", "user"]
        }
        """;

    @Test
    void shouldAccessFieldsLikeDTO() {
        // Given
        DynamicJsonResponse response = DynamicJsonResponse.fromJson(SAMPLE_JSON);
        DynamicObject obj = response.toDynamicObject();

        // When/Then
        assertThat(obj.getString("name")).isEqualTo("John Doe");
        assertThat(obj.getInt("age")).isEqualTo(30);
        assertThat(obj.getBoolean("active")).isTrue();
        assertThat(obj.getDouble("balance")).isEqualTo(1234.56);
    }

    @Test
    void shouldAccessNestedObjects() {
        // Given
        DynamicJsonResponse response = DynamicJsonResponse.fromJson(SAMPLE_JSON);
        DynamicObject obj = response.toDynamicObject();

        // When
        DynamicObject address = obj.getObject("address");

        // Then
        assertThat(address).isNotNull();
        assertThat(address.getString("street")).isEqualTo("123 Main St");
        assertThat(address.getString("city")).isEqualTo("New York");
    }

    @Test
    void shouldAccessLists() {
        // Given
        DynamicJsonResponse response = DynamicJsonResponse.fromJson(SAMPLE_JSON);
        DynamicObject obj = response.toDynamicObject();

        // When
        List<String> roles = obj.getList("roles", String.class);

        // Then
        assertThat(roles).containsExactly("admin", "user");
    }

    @Test
    void shouldCheckFieldExistence() {
        // Given
        DynamicJsonResponse response = DynamicJsonResponse.fromJson(SAMPLE_JSON);
        DynamicObject obj = response.toDynamicObject();

        // When/Then
        assertThat(obj.has("name")).isTrue();
        assertThat(obj.has("nonexistent")).isFalse();
    }

    @Test
    void shouldGetTypeSafeValues() {
        // Given
        DynamicJsonResponse response = DynamicJsonResponse.fromJson(SAMPLE_JSON);
        DynamicObject obj = response.toDynamicObject();

        // When/Then
        String name = obj.get("name", String.class);
        Integer age = obj.get("age", Integer.class);
        Boolean active = obj.get("active", Boolean.class);

        assertThat(name).isEqualTo("John Doe");
        assertThat(age).isEqualTo(30);
        assertThat(active).isTrue();
    }

    @Test
    void shouldConvertToMap() {
        // Given
        DynamicJsonResponse response = DynamicJsonResponse.fromJson(SAMPLE_JSON);
        DynamicObject obj = response.toDynamicObject();

        // When
        var map = obj.toMap();

        // Then
        assertThat(map).containsKey("name");
        assertThat(map).containsKey("age");
        assertThat(map.get("name")).isEqualTo("John Doe");
    }
}

