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

package org.fireflyframework.eda.serialization;

import java.util.Set;

/** Exact additional class tokens for one serializer's declared Avro contracts. */
public record AvroTypeBindings(Set<Class<?>> schemaTypes, Class<?> objectRoot) {
    public AvroTypeBindings {
        schemaTypes = Set.copyOf(schemaTypes);
        if (schemaTypes.size() > 16) {
            throw new IllegalArgumentException("At most 16 additional Avro schema types are supported");
        }
    }

    public static AvroTypeBindings strict() {
        return new AvroTypeBindings(Set.of(), null);
    }
}
