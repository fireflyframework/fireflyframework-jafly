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
package org.fireflyframework.kernel.logging;

import java.util.Collection;
import java.util.StringJoiner;

/** Single-line rendering for non-secret diagnostic fields; this does not redact secrets. */
public final class LogFields {
    private LogFields() {
    }

    /** Preserves ordinary text and null while making line boundaries visible. */
    public static String text(String value) {
        if (value == null) return null;
        return value.replace("\r", "\\r").replace("\n", "\\n")
                .replace("\u000b", "\\u000b").replace("\f", "\\f")
                .replace("\u0085", "\\u0085").replace("\u2028", "\\u2028")
                .replace("\u2029", "\\u2029");
    }

    /** Renders string-valued fields with normal collection order and null elements. */
    public static String values(Collection<String> values) {
        if (values == null) return null;
        try {
            var result = new StringJoiner(", ", "[", "]");
            for (String value : values) result.add(text(value));
            return result.toString();
        } catch (RuntimeException unavailable) {
            return "[unavailable]";
        }
    }

    /** Omits provider messages and stacks that can contain credentials or response payloads. */
    public static String errorType(Throwable error) {
        return error == null ? null : error.getClass().getName();
    }
}
