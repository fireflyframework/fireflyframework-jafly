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
package org.fireflyframework.security.idp.azuread.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Base64;

@Slf4j
public final class AzureAdTokenParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AzureAdTokenParser() {}

    public static JsonNode parsePayload(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                throw new IllegalArgumentException("Invalid JWT format");
            }
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            return MAPPER.readTree(decoded);
        } catch (Exception e) {
            log.error("Failed to parse JWT payload", e);
            throw new RuntimeException("Failed to parse JWT token", e);
        }
    }

    public static String extractUserId(String token) {
        JsonNode payload = parsePayload(token);
        String oid = payload.path("oid").asText(null);
        return oid != null ? oid : payload.path("sub").asText("");
    }

    public static String extractUsername(String token) {
        JsonNode payload = parsePayload(token);
        String upn = payload.path("upn").asText(null);
        if (upn != null) return upn;
        return payload.path("preferred_username").asText(
                payload.path("unique_name").asText(""));
    }

    public static long extractExpiration(String token) {
        return parsePayload(token).path("exp").asLong(0);
    }

    public static boolean isExpired(String token) {
        long exp = extractExpiration(token);
        return exp > 0 && exp < (System.currentTimeMillis() / 1000);
    }
}
