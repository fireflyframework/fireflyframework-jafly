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

import lombok.Builder;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Immutable, product-agnostic representation of an authenticated subject.
 *
 * <p>This is the framework's stack-neutral principal facade — a projection of a
 * signature-validated authentication. It deliberately carries <strong>no</strong>
 * product-domain concepts (no party, contract, or product). Products enrich it
 * through {@code PrincipalAttributeContributorPort} and read their domain off
 * {@link #claims()} / {@link #attributes()}.
 *
 * @param subject     the unique subject identifier (OIDC {@code sub})
 * @param issuer      the token issuer (OIDC {@code iss}); may be {@code null} for non-token auth
 * @param tenantId    generic tenant discriminator; {@code null} when single-tenant
 * @param authorities granted authorities / roles (already normalized, prefix-free or prefixed per policy)
 * @param scopes      OAuth2 scopes granted to the token
 * @param claims      raw validated token claims
 * @param authTime    time the end-user authentication occurred (OIDC {@code auth_time}); may be {@code null}
 * @param acr         authentication context class reference; may be {@code null}
 * @param amr         authentication methods references
 * @param attributes  framework/product-contributed attributes (ABAC inputs)
 */
@Builder(toBuilder = true)
public record SecurityPrincipal(
        String subject,
        String issuer,
        String tenantId,
        Set<String> authorities,
        Set<String> scopes,
        Map<String, Object> claims,
        Instant authTime,
        String acr,
        Set<String> amr,
        Map<String, Object> attributes
) {

    public SecurityPrincipal {
        authorities = authorities == null ? Set.of() : Set.copyOf(authorities);
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
        claims = claims == null ? Map.of() : Map.copyOf(claims);
        amr = amr == null ? Set.of() : Set.copyOf(amr);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /** @return {@code true} if the subject holds the given authority. */
    public boolean hasAuthority(String authority) {
        return authorities.contains(authority);
    }

    /** @return {@code true} if the subject holds at least one of the given authorities. */
    public boolean hasAnyAuthority(Collection<String> candidates) {
        for (String c : candidates) {
            if (authorities.contains(c)) {
                return true;
            }
        }
        return false;
    }

    /** @return {@code true} if the subject holds every one of the given authorities. */
    public boolean hasAllAuthorities(Collection<String> candidates) {
        return authorities.containsAll(candidates);
    }

    /** @return {@code true} if the token carries the given OAuth2 scope. */
    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }

    /** @return a claim value coerced to the requested type, or {@code null} if absent/mismatched. */
    public <T> T claim(String name, Class<T> type) {
        Object value = claims.get(name);
        return type.isInstance(value) ? type.cast(value) : null;
    }
}
