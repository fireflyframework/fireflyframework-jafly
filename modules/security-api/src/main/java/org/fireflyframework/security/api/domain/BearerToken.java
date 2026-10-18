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

/**
 * A raw bearer credential presented to the resource server, with a best-effort classification
 * into JWT vs opaque so the validation layer can pick the right strategy.
 *
 * @param value the raw token string (never logged in full)
 * @param type  the classified token type
 */
public record BearerToken(String value, TokenType type) {

    public enum TokenType {JWT, OPAQUE, UNKNOWN}

    /** Classify a raw token by structure: a three-segment dotted token is treated as a JWS/JWT. */
    public static BearerToken of(String value) {
        return new BearerToken(value, classify(value));
    }

    private static TokenType classify(String value) {
        if (value == null || value.isBlank()) {
            return TokenType.UNKNOWN;
        }
        long dots = value.chars().filter(c -> c == '.').count();
        return dots == 2 ? TokenType.JWT : TokenType.OPAQUE;
    }

    /** @return the token value masked for safe logging. */
    public String masked() {
        if (value == null || value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "…" + value.substring(value.length() - 4);
    }
}
