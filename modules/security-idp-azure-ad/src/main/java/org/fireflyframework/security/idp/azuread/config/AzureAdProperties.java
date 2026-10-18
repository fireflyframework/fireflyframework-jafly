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
package org.fireflyframework.security.idp.azuread.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Data
@Validated
@ConfigurationProperties(prefix = "firefly.security.idp.azure-ad")
public class AzureAdProperties {

    /** Azure AD mode: "entra-id" or "b2c" */
    private String mode = "entra-id";

    @NotBlank(message = "Tenant ID is required")
    private String tenantId;

    @NotBlank(message = "Client ID is required")
    private String clientId;

    @NotBlank(message = "Client secret is required")
    private String clientSecret;

    private B2CProperties b2c = new B2CProperties();
    private RoleMappingProperties roleMapping = new RoleMappingProperties();
    private Integer connectionTimeout = 30000;
    private Integer requestTimeout = 60000;
    private GraphProperties graph = new GraphProperties();

    public boolean isB2CMode() {
        return "b2c".equalsIgnoreCase(mode);
    }

    public String getAuthority() {
        if (isB2CMode() && b2c.getDomain() != null) {
            return "https://" + b2c.getDomain() + "/" + tenantId;
        }
        return "https://login.microsoftonline.com/" + tenantId;
    }

    @Data
    public static class B2CProperties {
        private String domain;
        private String signUpSignInPolicy = "B2C_1_signup_signin";
        private String resetPasswordPolicy = "B2C_1_password_reset";
    }

    @Data
    public static class RoleMappingProperties {
        private String strategy = "app-roles";
        private String groupPrefix = "";
    }

    @Data
    public static class GraphProperties {
        private List<String> scopes = List.of("https://graph.microsoft.com/.default");
    }
}
