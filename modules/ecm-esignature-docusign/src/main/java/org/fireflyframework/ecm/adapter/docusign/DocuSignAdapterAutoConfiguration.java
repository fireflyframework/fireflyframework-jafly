/*
 * Copyright 2024 Firefly Software Foundation.
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
package org.fireflyframework.ecm.adapter.docusign;

import com.docusign.esign.client.ApiClient;
import com.docusign.esign.client.auth.OAuth;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;

/**
 * Auto-configuration for the DocuSign ECM adapter.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(ApiClient.class)
@ConditionalOnProperty(name = "firefly.ecm.esignature.provider", havingValue = "docusign")
@EnableConfigurationProperties(DocuSignAdapterProperties.class)
public class DocuSignAdapterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ApiClient docuSignApiClient(DocuSignAdapterProperties properties) {
        log.info("Configuring DocuSign API client for account: {}", properties.getAccountId());

        try {
            ApiClient apiClient = new ApiClient();
            String baseUrl = properties.getSandboxMode() ? 
                    "https://demo.docusign.net/restapi" : properties.getBaseUrl();
            apiClient.setBasePath(baseUrl);
            apiClient.setConnectTimeout((int) properties.getConnectionTimeout().toMillis());
            apiClient.setReadTimeout((int) properties.getReadTimeout().toMillis());
            apiClient.setUserAgent("Firefly-ECM-Library/1.0.0");
            configureJwtAuthentication(apiClient, properties);
            log.info("Successfully configured DocuSign API client");
            return apiClient;
        } catch (Exception e) {
            log.error("Failed to configure DocuSign API client", e);
            throw new RuntimeException("DocuSign API client configuration failed", e);
        }
    }

    private void configureJwtAuthentication(ApiClient apiClient, DocuSignAdapterProperties properties) 
            throws Exception {
        log.info("Configuring JWT authentication for DocuSign");
        String authServer = properties.getSandboxMode() ? 
                "https://account-d.docusign.com" : properties.getAuthServer();
        apiClient.setOAuthBasePath(authServer);
        String[] scopes = {"signature", "impersonation"};
        OAuth.OAuthToken oAuthToken = apiClient.requestJWTUserToken(
                properties.getIntegrationKey(),
                properties.getUserId(),
                Arrays.asList(scopes),
                properties.getPrivateKey().getBytes(),
                properties.getJwtExpiration()
        );
        apiClient.setAccessToken(oAuthToken.getAccessToken(), oAuthToken.getExpiresIn());
        OAuth.UserInfo userInfo = apiClient.getUserInfo(oAuthToken.getAccessToken());
        log.info("Successfully authenticated DocuSign user: {}", userInfo.getName());
        validateAccountAccess(userInfo, properties);
    }

    private void validateAccountAccess(OAuth.UserInfo userInfo, DocuSignAdapterProperties properties) {
        boolean accountFound = userInfo.getAccounts().stream()
                .anyMatch(account -> properties.getAccountId().equals(account.getAccountId()));
        if (!accountFound) {
            log.error("User does not have access to account: {}", properties.getAccountId());
            throw new RuntimeException("Invalid DocuSign account ID or insufficient permissions");
        }
        log.info("Successfully validated access to DocuSign account: {}", properties.getAccountId());
    }
}
