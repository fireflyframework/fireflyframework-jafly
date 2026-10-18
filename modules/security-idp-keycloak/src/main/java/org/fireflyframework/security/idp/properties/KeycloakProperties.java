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


package org.fireflyframework.security.idp.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "firefly.security.idp.keycloak")
public record KeycloakProperties(
        @NotBlank String serverUrl,
        @NotBlank String realm,
        @NotBlank String clientId,
        String clientSecret,
        @NotNull @Positive Integer connectionPoolSize,
        @NotNull @Positive Integer connectionTimeout,
        @NotNull @Positive Integer requestTimeout
) {

    public KeycloakProperties {
        // Normalize server URL
        if (serverUrl != null && !serverUrl.endsWith("/")) {
            serverUrl = serverUrl + "/";
        }

        // Default values
        if (connectionPoolSize == null) connectionPoolSize = 10;
        if (connectionTimeout == null) connectionTimeout = 30000;
        if (requestTimeout == null) requestTimeout = 60000;
    }
}
