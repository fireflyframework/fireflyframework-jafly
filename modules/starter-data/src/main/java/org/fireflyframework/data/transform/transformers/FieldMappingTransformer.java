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

package org.fireflyframework.data.transform.transformers;

import org.fireflyframework.data.transform.DataTransformer;
import org.fireflyframework.data.transform.TransformContext;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renames fields in a {@code Map<String, Object>} according to a
 * source-field to target-field mapping.
 *
 * <p>Fields not present in the mapping are passed through unchanged.
 * Fields listed in the mapping but absent from the source are ignored.</p>
 */
public class FieldMappingTransformer implements DataTransformer<Map<String, Object>, Map<String, Object>> {

    private final Map<String, String> fieldMappings;

    /**
     * Creates a transformer with the given field mappings.
     *
     * @param fieldMappings map of source field names to target field names
     */
    public FieldMappingTransformer(Map<String, String> fieldMappings) {
        this.fieldMappings = fieldMappings;
    }

    @Override
    public Mono<Map<String, Object>> transform(Map<String, Object> source, TransformContext context) {
        return Mono.fromCallable(() -> {
            Map<String, Object> result = new LinkedHashMap<>(source);
            fieldMappings.forEach((from, to) -> {
                if (result.containsKey(from)) {
                    result.put(to, result.remove(from));
                }
            });
            return result;
        });
    }
}
