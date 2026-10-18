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


package org.fireflyframework.security.idp.adapter.keycloak;

import org.fireflyframework.security.idp.dtos.LoginRequest;
import org.fireflyframework.security.idp.properties.KeycloakProperties;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Factory for Keycloak OpenID Connect API calls used by API-based services.
 * Extracted from KeycloakClientFactory to separate WebClient/OIDC helpers
 * from Admin client builders.
 */
@Slf4j
public class KeycloakAPIFactory {

    private final KeycloakProperties properties;

    public KeycloakAPIFactory(KeycloakProperties properties) {
        this.properties = properties;
    }

    /**
     * Provides a WebClient configured to call the Keycloak token-related endpoints.
     */
    public WebClient tokenWebClient() {
        String baseUrl = properties.serverUrl() + "realms/" + properties.realm() + "/protocol/openid-connect";
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * Builds the form body for a password grant token request using Keycloak properties.
     */
    public MultiValueMap<String, String> passwordGrantBody(LoginRequest request) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", OAuth2Constants.PASSWORD);
        form.add("username", request.getUsername());
        form.add("password", request.getPassword());
        form.add("client_id", properties.clientId());

        if (request.getScope() != null && !request.getScope().isBlank()) {
            form.add("scope", request.getScope());
        }

        if (properties.clientSecret() != null && !properties.clientSecret().isEmpty()) {
            form.add("client_secret", properties.clientSecret());
        }
        return form;
    }

    /**
     * Builds the form body for a refresh_token request to Keycloak.
     */
    public MultiValueMap<String, String> refreshTokenBody(String refreshToken) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", OAuth2Constants.REFRESH_TOKEN);
        formData.add("client_id", properties.clientId());
        formData.add("client_secret", properties.clientSecret());
        formData.add("refresh_token", refreshToken);
        return formData;
    }

    /**
     * Builds the form body for logout requests. Contains client credentials as required.
     */
    public MultiValueMap<String, String> logoutBody(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        if (properties.clientSecret() != null && !properties.clientSecret().isEmpty()) {
            form.add("client_secret", properties.clientSecret());
        }
        form.add("refresh_token", refreshToken);
        return form;
    }

    /**
     * Builds the form body for token introspection requests.
     */
    public MultiValueMap<String, String> introspectionBody(String token) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", token);
        form.add("client_id", properties.clientId());
        if (properties.clientSecret() != null && !properties.clientSecret().isEmpty()) {
            form.add("client_secret", properties.clientSecret());
        }
        // Optional but useful hint
        form.add("token_type_hint", "access_token");
        return form;
    }

    /**
     * Builds the form body for token revocation (refresh token).
     */
    public MultiValueMap<String, String> revocationBody(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        if (properties.clientSecret() != null && !properties.clientSecret().isEmpty()) {
            form.add("client_secret", properties.clientSecret());
        }
        form.add("token", refreshToken);
        form.add("token_type_hint", "refresh_token");
        return form;
    }
}
