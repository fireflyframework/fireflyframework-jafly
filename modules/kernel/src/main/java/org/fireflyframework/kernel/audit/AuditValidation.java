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
package org.fireflyframework.kernel.audit;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

final class AuditValidation {
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}");
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");

    private AuditValidation() { }

    static <T> T required(T value) {
        return Objects.requireNonNull(value, "Required audit field is absent");
    }

    static String token(String value) {
        if (!TOKEN.matcher(required(value)).matches()) throw new IllegalArgumentException("Invalid audit token");
        return value;
    }

    static String digest(String value) {
        if (!DIGEST.matcher(required(value)).matches()) throw new IllegalArgumentException("Invalid audit digest");
        return value;
    }

    static String text(String value) {
        required(value);
        if (value.isBlank() || value.length() > 2048) throw new IllegalArgumentException("Invalid audit text");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c) || c == '\u2028' || c == '\u2029')
                throw new IllegalArgumentException("Invalid audit text");
            if (Character.isHighSurrogate(c)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i)))
                    throw new IllegalArgumentException("Invalid audit text");
            } else if (Character.isLowSurrogate(c)) {
                throw new IllegalArgumentException("Invalid audit text");
            }
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > 2048)
            throw new IllegalArgumentException("Invalid audit text");
        return value;
    }
}
