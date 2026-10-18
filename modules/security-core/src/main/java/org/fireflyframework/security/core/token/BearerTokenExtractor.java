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

package org.fireflyframework.security.core.token;

import org.fireflyframework.security.api.domain.BearerToken;

import java.util.Optional;

/**
 * Parses the HTTP {@code Authorization} header into a classified {@link BearerToken}. Matches the
 * {@code Bearer} scheme case-insensitively per RFC 6750.
 */
public final class BearerTokenExtractor {

    private static final String PREFIX = "Bearer ";

    private BearerTokenExtractor() {
    }

    public static Optional<BearerToken> extract(String authorizationHeader) {
        if (authorizationHeader == null
                || !authorizationHeader.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            return Optional.empty();
        }
        String value = authorizationHeader.substring(PREFIX.length()).trim();
        return value.isEmpty() ? Optional.empty() : Optional.of(BearerToken.of(value));
    }
}
