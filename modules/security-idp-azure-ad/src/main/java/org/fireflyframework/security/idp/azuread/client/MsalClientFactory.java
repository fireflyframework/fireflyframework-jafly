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
package org.fireflyframework.security.idp.azuread.client;

import com.microsoft.aad.msal4j.ConfidentialClientApplication;
import com.microsoft.aad.msal4j.ClientCredentialFactory;
import com.microsoft.aad.msal4j.IClientCredential;
import org.fireflyframework.security.idp.azuread.config.AzureAdProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PreDestroy;
import java.net.MalformedURLException;

@RequiredArgsConstructor
@Slf4j
public class MsalClientFactory {

    private final AzureAdProperties properties;
    private volatile ConfidentialClientApplication client;

    public ConfidentialClientApplication getClient() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = createClient();
                }
            }
        }
        return client;
    }

    protected ConfidentialClientApplication createClient() {
        log.info("Initializing MSAL Confidential Client for tenant: {}", properties.getTenantId());
        try {
            IClientCredential credential = ClientCredentialFactory.createFromSecret(properties.getClientSecret());
            return ConfidentialClientApplication.builder(
                    properties.getClientId(),
                    credential)
                    .authority(properties.getAuthority())
                    .connectTimeoutForDefaultHttpClient(properties.getConnectionTimeout())
                    .readTimeoutForDefaultHttpClient(properties.getRequestTimeout())
                    .build();
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Invalid Azure AD authority URL: " + properties.getAuthority(), e);
        }
    }

    @PreDestroy
    public void destroy() {
        log.info("MSAL client shutdown");
        client = null;
    }
}
