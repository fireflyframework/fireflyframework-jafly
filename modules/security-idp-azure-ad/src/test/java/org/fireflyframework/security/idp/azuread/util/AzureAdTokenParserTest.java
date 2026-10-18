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
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AzureAdTokenParser.
 *
 * <p>Tests JWT payload parsing, user ID extraction, username extraction,
 * and token expiration checks using synthetic JWT tokens.
 */
class AzureAdTokenParserTest {

    /**
     * Creates a test JWT with the given JSON payload.
     * Format: base64url(header).base64url(payload).signature
     */
    private String createTestJwt(String payloadJson) {
        String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
        String encodedHeader = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(header.getBytes());
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.getBytes());
        return encodedHeader + "." + encodedPayload + ".test-signature";
    }

    @Test
    void testParsePayload() {
        // Arrange
        String payloadJson = "{\"sub\":\"user-123\",\"oid\":\"oid-456\",\"upn\":\"user@example.com\",\"exp\":9999999999,\"iat\":1700000000,\"iss\":\"https://login.microsoftonline.com/tenant-id/v2.0\"}";
        String jwt = createTestJwt(payloadJson);

        // Act
        JsonNode payload = AzureAdTokenParser.parsePayload(jwt);

        // Assert
        assertEquals("user-123", payload.path("sub").asText());
        assertEquals("oid-456", payload.path("oid").asText());
        assertEquals("user@example.com", payload.path("upn").asText());
        assertEquals(9999999999L, payload.path("exp").asLong());
        assertEquals(1700000000L, payload.path("iat").asLong());
        assertEquals("https://login.microsoftonline.com/tenant-id/v2.0", payload.path("iss").asText());
    }

    @Test
    void testExtractUserId_UsesOid() {
        // Arrange — token with both oid and sub; oid should be preferred
        String payloadJson = "{\"oid\":\"oid-456\",\"sub\":\"sub-789\"}";
        String jwt = createTestJwt(payloadJson);

        // Act
        String userId = AzureAdTokenParser.extractUserId(jwt);

        // Assert
        assertEquals("oid-456", userId);
    }

    @Test
    void testExtractUserId_FallsBackToSub() {
        // Arrange — token with only sub, no oid
        String payloadJson = "{\"sub\":\"sub-789\"}";
        String jwt = createTestJwt(payloadJson);

        // Act
        String userId = AzureAdTokenParser.extractUserId(jwt);

        // Assert
        assertEquals("sub-789", userId);
    }

    @Test
    void testExtractUsername_UsesUpn() {
        // Arrange — token with upn and preferred_username; upn should be preferred
        String payloadJson = "{\"upn\":\"user@contoso.com\",\"preferred_username\":\"user@example.com\"}";
        String jwt = createTestJwt(payloadJson);

        // Act
        String username = AzureAdTokenParser.extractUsername(jwt);

        // Assert
        assertEquals("user@contoso.com", username);
    }

    @Test
    void testExtractUsername_FallsBackToPreferredUsername() {
        // Arrange — token with only preferred_username, no upn
        String payloadJson = "{\"preferred_username\":\"user@example.com\"}";
        String jwt = createTestJwt(payloadJson);

        // Act
        String username = AzureAdTokenParser.extractUsername(jwt);

        // Assert
        assertEquals("user@example.com", username);
    }

    @Test
    void testIsExpired_ExpiredToken() {
        // Arrange — exp in the past (epoch second 1000)
        String payloadJson = "{\"exp\":1000}";
        String jwt = createTestJwt(payloadJson);

        // Act
        boolean expired = AzureAdTokenParser.isExpired(jwt);

        // Assert
        assertTrue(expired, "Token with past exp should be expired");
    }

    @Test
    void testIsExpired_ValidToken() {
        // Arrange — exp far in the future
        String payloadJson = "{\"exp\":9999999999}";
        String jwt = createTestJwt(payloadJson);

        // Act
        boolean expired = AzureAdTokenParser.isExpired(jwt);

        // Assert
        assertFalse(expired, "Token with future exp should not be expired");
    }

    @Test
    void testParsePayload_InvalidJwt() {
        // Arrange — malformed JWT with no dots
        String invalidJwt = "not-a-valid-jwt";

        // Act & Assert
        assertThrows(RuntimeException.class, () -> AzureAdTokenParser.parsePayload(invalidJwt));
    }
}
