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
import java.util.function.Function;

/**
 * Adds a computed field to a {@code Map<String, Object>} by applying
 * a computation function to the current map state.
 *
 * <p>The computation receives an unmodifiable view of the source map
 * and the result is stored under the configured field name. If the
 * field already exists, it is overwritten.</p>
 */
public class ComputedFieldTransformer implements DataTransformer<Map<String, Object>, Map<String, Object>> {

    private final String fieldName;
    private final Function<Map<String, Object>, Object> computation;

    /**
     * Creates a transformer that adds a computed field.
     *
     * @param fieldName   the name of the field to add
     * @param computation the function that computes the field value from the source map
     */
    public ComputedFieldTransformer(String fieldName, Function<Map<String, Object>, Object> computation) {
        this.fieldName = fieldName;
        this.computation = computation;
    }

    @Override
    public Mono<Map<String, Object>> transform(Map<String, Object> source, TransformContext context) {
        return Mono.fromCallable(() -> {
            Map<String, Object> result = new LinkedHashMap<>(source);
            result.put(fieldName, computation.apply(source));
            return result;
        });
    }
}
