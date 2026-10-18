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

package org.fireflyframework.data.transform;

import org.fireflyframework.data.transform.transformers.ComputedFieldTransformer;
import org.fireflyframework.data.transform.transformers.FieldMappingTransformer;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TransformationChain}, {@link FieldMappingTransformer},
 * and {@link ComputedFieldTransformer}.
 */
class TransformationChainTest {

    private final TransformContext context = TransformContext.builder()
            .requestId("test-request-1")
            .tenantId(UUID.randomUUID())
            .build();

    @Test
    void chain_shouldApplyTransformersInOrder() {
        // Given - rename "first_name" to "firstName", then add a computed greeting
        TransformationChain<Map<String, Object>, Map<String, Object>> chain =
                TransformationChain.<Map<String, Object>, Map<String, Object>>create()
                        .then(new FieldMappingTransformer(Map.of("first_name", "firstName")))
                        .then(new ComputedFieldTransformer("greeting",
                                m -> "Hello, " + m.get("firstName") + "!"));

        Map<String, Object> source = new HashMap<>(Map.of("first_name", "Alice", "age", 30));

        // When & Then
        StepVerifier.create(chain.execute(source, context))
                .assertNext(result -> {
                    assertThat(result).doesNotContainKey("first_name");
                    assertThat(result).containsEntry("firstName", "Alice");
                    assertThat(result).containsEntry("greeting", "Hello, Alice!");
                    assertThat(result).containsEntry("age", 30);
                })
                .verifyComplete();
    }

    @Test
    void chain_shouldReturnSourceWhenEmpty() {
        // Given - an empty chain
        TransformationChain<Map<String, Object>, Map<String, Object>> chain =
                TransformationChain.create();

        Map<String, Object> source = Map.of("key", "value");

        // When & Then
        StepVerifier.create(chain.execute(source, context))
                .assertNext(result -> assertThat(result).containsEntry("key", "value"))
                .verifyComplete();

        assertThat(chain.size()).isZero();
    }

    @Test
    void chain_shouldComposeMultipleTransformers() {
        // Given - three transformers: rename -> compute -> filter (remove nulls)
        DataTransformer<Map<String, Object>, Map<String, Object>> filterNulls =
                (src, ctx) -> {
                    Map<String, Object> filtered = src.entrySet().stream()
                            .filter(e -> e.getValue() != null)
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    Map.Entry::getValue,
                                    (a, b) -> a,
                                    LinkedHashMap::new));
                    return reactor.core.publisher.Mono.just(filtered);
                };

        TransformationChain<Map<String, Object>, Map<String, Object>> chain =
                TransformationChain.<Map<String, Object>, Map<String, Object>>create()
                        .then(new FieldMappingTransformer(Map.of("old_key", "newKey")))
                        .then(new ComputedFieldTransformer("status", m -> "processed"))
                        .then(filterNulls);

        Map<String, Object> source = new HashMap<>();
        source.put("old_key", "data");
        source.put("keep", "yes");
        source.put("remove_me", null);

        // When & Then
        StepVerifier.create(chain.execute(source, context))
                .assertNext(result -> {
                    assertThat(result).containsEntry("newKey", "data");
                    assertThat(result).containsEntry("keep", "yes");
                    assertThat(result).containsEntry("status", "processed");
                    assertThat(result).doesNotContainKey("old_key");
                    assertThat(result).doesNotContainKey("remove_me");
                })
                .verifyComplete();

        assertThat(chain.size()).isEqualTo(3);
    }

    @Test
    void fieldMappingTransformer_shouldRenameFields() {
        // Given
        FieldMappingTransformer transformer = new FieldMappingTransformer(
                Map.of("first_name", "firstName", "last_name", "lastName"));

        Map<String, Object> source = new HashMap<>(
                Map.of("first_name", "Alice", "last_name", "Smith", "age", 30));

        // When & Then
        StepVerifier.create(transformer.transform(source, context))
                .assertNext(result -> {
                    assertThat(result).containsEntry("firstName", "Alice");
                    assertThat(result).containsEntry("lastName", "Smith");
                    assertThat(result).containsEntry("age", 30);
                    assertThat(result).doesNotContainKey("first_name");
                    assertThat(result).doesNotContainKey("last_name");
                })
                .verifyComplete();
    }

    @Test
    void computedFieldTransformer_shouldAddComputedField() {
        // Given
        ComputedFieldTransformer transformer = new ComputedFieldTransformer(
                "fullName",
                m -> m.get("first") + " " + m.get("last"));

        Map<String, Object> source = Map.of("first", "Alice", "last", "Smith");

        // When & Then
        StepVerifier.create(transformer.transform(source, context))
                .assertNext(result -> {
                    assertThat(result).containsEntry("fullName", "Alice Smith");
                    assertThat(result).containsEntry("first", "Alice");
                    assertThat(result).containsEntry("last", "Smith");
                })
                .verifyComplete();
    }
}
