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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for DynamicClassGenerator.
 */
class DynamicClassGeneratorTest {

    @Test
    void shouldGenerateDynamicClass() {
        // Given
        String json = """
            {
                "id": 123,
                "name": "John Doe",
                "email": "john@example.com"
            }
            """;

        DynamicJsonResponse response = DynamicJsonResponse.fromJson(json);

        // When
        Object instance = response.toDynamicClass("User");

        // Then
        assertThat(instance).isNotNull();
        assertThat(instance).isInstanceOf(DynamicClassGenerator.DynamicDTO.class);
    }

    @Test
    void shouldAccessFieldsViaInterface() {
        // Given
        String json = """
            {
                "id": 123,
                "name": "John Doe",
                "email": "john@example.com"
            }
            """;

        DynamicJsonResponse response = DynamicJsonResponse.fromJson(json);

        // When
        Object instance = response.toDynamicClass("User");
        DynamicClassGenerator.DynamicDTO dto = (DynamicClassGenerator.DynamicDTO) instance;

        // Then
        assertThat(dto.get("name")).isEqualTo("John Doe");
        assertThat(dto.get("email")).isEqualTo("john@example.com");
        assertThat(dto.get("id")).isEqualTo(123);
    }

    @Test
    void shouldSetFieldsViaInterface() {
        // Given
        String json = """
            {
                "id": 123,
                "name": "John Doe"
            }
            """;

        DynamicJsonResponse response = DynamicJsonResponse.fromJson(json);
        Object instance = response.toDynamicClass("User");
        DynamicClassGenerator.DynamicDTO dto = (DynamicClassGenerator.DynamicDTO) instance;

        // When
        dto.set("name", "Jane Doe");
        dto.set("email", "jane@example.com");

        // Then
        assertThat(dto.get("name")).isEqualTo("Jane Doe");
        assertThat(dto.get("email")).isEqualTo("jane@example.com");
    }

    @Test
    void shouldGetFieldNames() {
        // Given
        String json = """
            {
                "id": 123,
                "name": "John Doe",
                "email": "john@example.com"
            }
            """;

        DynamicJsonResponse response = DynamicJsonResponse.fromJson(json);
        Object instance = response.toDynamicClass("User");
        DynamicClassGenerator.DynamicDTO dto = (DynamicClassGenerator.DynamicDTO) instance;

        // When
        var fieldNames = dto.getFieldNames();

        // Then
        assertThat(fieldNames).contains("id", "name", "email");
    }

    @Test
    void shouldConvertToMap() {
        // Given
        String json = """
            {
                "id": 123,
                "name": "John Doe"
            }
            """;

        DynamicJsonResponse response = DynamicJsonResponse.fromJson(json);
        Object instance = response.toDynamicClass("User");
        DynamicClassGenerator.DynamicDTO dto = (DynamicClassGenerator.DynamicDTO) instance;

        // When
        var map = dto.toMap();

        // Then
        assertThat(map).containsEntry("id", 123);
        assertThat(map).containsEntry("name", "John Doe");
    }

    @Test
    void shouldHaveClassName() {
        // Given
        String json = """
            {
                "id": 123
            }
            """;

        DynamicJsonResponse response = DynamicJsonResponse.fromJson(json);
        Object instance = response.toDynamicClass("User");
        DynamicClassGenerator.DynamicDTO dto = (DynamicClassGenerator.DynamicDTO) instance;

        // When
        String className = dto.getClassName();

        // Then
        assertThat(className).isEqualTo("User");
    }
}

