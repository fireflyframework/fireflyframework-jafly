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

package org.fireflyframework.security.core.authority;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reads values out of a nested JWT claims map using dot-path notation (e.g.
 * {@code realm_access.roles}) and coerces them into a string set, handling the common encodings:
 * a single string, a space/comma-delimited string, or a collection.
 */
public final class ClaimPaths {

    private ClaimPaths() {
    }

    /** Navigate a dot-separated path through nested maps. Returns {@code null} if any segment is missing. */
    public static Object read(Map<String, Object> claims, String path) {
        if (claims == null || path == null || path.isBlank()) {
            return null;
        }
        Object current = claims;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    /** Read a dot-path and coerce it to a string set. */
    public static Set<String> readStringSet(Map<String, Object> claims, String path) {
        return toStringSet(read(claims, path));
    }

    /** Coerce a claim value (String, delimited String, or Collection) into a normalized string set. */
    public static Set<String> toStringSet(Object value) {
        Set<String> out = new LinkedHashSet<>();
        if (value == null) {
            return out;
        }
        if (value instanceof String s) {
            for (String token : s.split("[\\s,]+")) {
                if (!token.isBlank()) {
                    out.add(token);
                }
            }
        } else if (value instanceof Collection<?> c) {
            c.stream().filter(Objects::nonNull).map(Object::toString)
                    .filter(t -> !t.isBlank()).forEach(out::add);
        } else {
            out.add(value.toString());
        }
        return out;
    }
}
