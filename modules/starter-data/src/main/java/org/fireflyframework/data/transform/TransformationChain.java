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

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * A composable chain that applies {@link DataTransformer}s sequentially,
 * threading the output of each step into the input of the next.
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * TransformationChain<Map<String, Object>, Map<String, Object>> chain =
 *     TransformationChain.<Map<String, Object>, Map<String, Object>>create()
 *         .then(new FieldMappingTransformer(Map.of("old_name", "newName")))
 *         .then(new ComputedFieldTransformer("fullName", m -> m.get("first") + " " + m.get("last")));
 *
 * chain.execute(inputMap, context).subscribe(result -> ...);
 * }</pre>
 *
 * @param <S> the source (input) type of the chain
 * @param <T> the target (output) type of the chain
 */
@Slf4j
public class TransformationChain<S, T> {

    private final List<DataTransformer<Object, Object>> transformers = new ArrayList<>();

    private TransformationChain() {}

    /**
     * Appends a transformer to the end of this chain.
     *
     * @param transformer the transformer to append
     * @return this chain for fluent composition
     */
    @SuppressWarnings("unchecked")
    public TransformationChain<S, T> then(DataTransformer<?, ?> transformer) {
        transformers.add((DataTransformer<Object, Object>) transformer);
        return this;
    }

    /**
     * Executes the chain by feeding the source value through each transformer
     * in order. If the chain is empty, the source is returned as-is.
     *
     * @param source  the initial input value
     * @param context the transformation context
     * @return a {@link Mono} emitting the final transformed result
     */
    @SuppressWarnings("unchecked")
    public Mono<T> execute(S source, TransformContext context) {
        if (transformers.isEmpty()) {
            return Mono.just((T) source);
        }

        Mono<Object> result = Mono.just((Object) source);
        for (DataTransformer<Object, Object> transformer : transformers) {
            result = result.flatMap(current -> transformer.transform(current, context));
        }
        return result.map(r -> (T) r);
    }

    /**
     * Returns the number of transformers in this chain.
     *
     * @return the chain length
     */
    public int size() {
        return transformers.size();
    }

    /**
     * Creates a new empty transformation chain.
     *
     * @param <S> the source type
     * @param <T> the target type
     * @return a new chain instance
     */
    public static <S, T> TransformationChain<S, T> create() {
        return new TransformationChain<>();
    }
}
