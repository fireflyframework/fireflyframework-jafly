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

import java.util.Set;

/**
 * A trusted token issuer in a multi-tenant resource server. The {@code issuer} is matched against
 * the {@code iss} claim; {@code audiences} constrains the acceptable {@code aud} values.
 *
 * @param issuer    the expected issuer URI ({@code iss})
 * @param jwksUri   the JWKS endpoint for signature verification ({@code null} for opaque-only issuers)
 * @param audiences acceptable audiences ({@code aud}); empty means "do not constrain audience"
 * @param tenantId  the tenant this issuer maps to ({@code null} when issuer is not tenant-scoped)
 */
public record TrustedIssuer(String issuer, String jwksUri, Set<String> audiences, String tenantId) {

    public TrustedIssuer {
        audiences = audiences == null ? Set.of() : Set.copyOf(audiences);
    }
}
