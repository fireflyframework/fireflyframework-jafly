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

package org.fireflyframework.security.rs;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for the secure-by-default resource server. With no configuration every route
 * requires a validated bearer token (default-deny); {@code permitMatchers} is the explicit,
 * named opt-out for public routes.
 */
@Data
@ConfigurationProperties(prefix = "firefly.security.resource-server")
public class ResourceServerProperties {

    public enum JwtAdmissionMode { LOCAL_JWT, LIVE }

    /** Existing local verification remains the general default. */
    private JwtAdmissionMode admissionMode = JwtAdmissionMode.LOCAL_JWT;
    private LiveState liveState = new LiveState();

    /** No generated toString: these settings include an introspection credential. */
    @lombok.Getter
    @lombok.Setter
    public static class LiveState {
        private String introspectionUri;
        private String introspectionClientId;
        private String introspectionClientSecret;
        private String tokenClientClaim;
        private List<String> allowedClients = new ArrayList<>();
        private java.time.Duration timeout = java.time.Duration.ofSeconds(5);
    }


    /** Ant-style path patterns that are publicly accessible without authentication. */
    private List<String> permitMatchers = new ArrayList<>();

    /**
     * Requires explicit configured policy decisions in the default embedded provider. Empty rules,
     * empty results, and failed rules deny; permit obligations remain for the enforcing caller.
     * False retains the legacy no-rules deferral to a separate RBAC gate.
     */
    private boolean strictPolicy;

    /** Expected token issuer ({@code iss}); when set, an issuer validator is enforced. */
    private String issuer;

    /** Acceptable audiences ({@code aud}); when non-empty, an audience validator is enforced. */
    private List<String> audiences = new ArrayList<>();

    /** Prefix applied to mapped authorities (e.g. {@code ROLE_}); empty by default. */
    private String authorityPrefix = "";

    /** Claim paths inspected for roles/groups (defaults cover Keycloak/Cognito/Entra). */
    private List<String> roleClaimPaths = new ArrayList<>();

    /** Claim paths inspected for OAuth2 scopes. */
    private List<String> scopeClaimPaths = new ArrayList<>();

    /** Signed claim dot-paths projected into the principal tenant; empty preserves single-tenant behavior. */
    private List<String> tenantClaimPaths = new ArrayList<>();

    /** Required unresolved or ambiguous tenant claims deny authentication; optional projection yields null. */
    private boolean tenantRequired;


    /**
     * Remote JWKS endpoint of an external IdP (e.g. Keycloak), e.g.
     * {@code https://auth.example/realms/r/protocol/openid-connect/certs}. When set, the default
     * decoder validates tokens against this JWKS — so a resource server fronting an external IdP needs
     * no decoder override. When blank, the in-memory signing key ({@code KeyManagementPort}) is used,
     * exactly as before (backward compatible).
     */
    private String jwkSetUri;

    /**
     * JWS signature algorithm allowlist applied to the external-JWKS decoder (RFC 8725): rejects
     * {@code none} and symmetric algorithms. Asymmetric only; defaults to RS256/PS256/ES256.
     */
    private List<String> signatureAlgorithms = new ArrayList<>(List.of("RS256", "PS256", "ES256"));
}
