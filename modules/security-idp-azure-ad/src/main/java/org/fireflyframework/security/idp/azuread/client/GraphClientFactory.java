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

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import org.fireflyframework.security.idp.azuread.config.AzureAdProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PreDestroy;

@RequiredArgsConstructor
@Slf4j
public class GraphClientFactory {

    private final AzureAdProperties properties;
    private volatile GraphServiceClient client;

    public GraphServiceClient getClient() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = createClient();
                }
            }
        }
        return client;
    }

    protected GraphServiceClient createClient() {
        log.info("Initializing Microsoft Graph client for tenant: {}", properties.getTenantId());
        ClientSecretCredential credential = new ClientSecretCredentialBuilder()
                .clientId(properties.getClientId())
                .clientSecret(properties.getClientSecret())
                .tenantId(properties.getTenantId())
                .build();
        String[] scopes = properties.getGraph().getScopes().toArray(new String[0]);
        return new GraphServiceClient(credential, scopes);
    }

    @PreDestroy
    public void destroy() {
        log.info("Microsoft Graph client shutdown");
        client = null;
    }
}
