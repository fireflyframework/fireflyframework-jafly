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

package org.fireflyframework.security.core.key;

import org.fireflyframework.security.api.domain.SigningKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryKeyManagementAdapterTest {

    private final InMemoryKeyManagementAdapter keys = new InMemoryKeyManagementAdapter();

    @Test
    void activeKeyCanSignAndHasKid() {
        SigningKey key = keys.activeSigningKey().block();
        assertThat(key).isNotNull();
        assertThat(key.canSign()).isTrue();
        assertThat(key.kid()).isNotBlank();
        assertThat(key.algorithm()).isEqualTo("RS256");
    }

    @Test
    void jwkSetContainsActiveKid() {
        String kid = keys.activeSigningKey().block().kid();
        String jwks = keys.jwkSetJson().block();
        assertThat(jwks).contains("\"keys\"").contains(kid).contains("\"kty\":\"RSA\"");
    }

    @Test
    void rotationChangesActiveKeyButRetainsPreviousForVerification() {
        SigningKey before = keys.activeSigningKey().block();
        keys.rotate().block();
        SigningKey after = keys.activeSigningKey().block();

        assertThat(after.kid()).isNotEqualTo(before.kid());
        assertThat(keys.verificationKeys().collectList().block())
                .extracting(SigningKey::kid)
                .contains(before.kid(), after.kid());
    }
}
