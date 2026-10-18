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

package org.fireflyframework.security.api.domain;

import java.util.Map;

/**
 * An obligation returned with a {@link Decision} that the policy enforcement point must honour
 * when permitting access (for example a data-masking directive or a row-level filter predicate).
 *
 * @param type       the obligation discriminator (e.g. {@code "mask"}, {@code "row-filter"})
 * @param attributes obligation parameters
 */
public record Obligation(String type, Map<String, Object> attributes) {

    public Obligation {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static Obligation of(String type, Map<String, Object> attributes) {
        return new Obligation(type, attributes);
    }
}
