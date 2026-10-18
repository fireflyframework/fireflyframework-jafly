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

import com.microsoft.aad.msal4j.ConfidentialClientApplication;
import org.fireflyframework.security.idp.adapter.IdpAdapter;
import org.fireflyframework.security.idp.azuread.adapter.AzureAdIdpAdapter;
import org.fireflyframework.security.idp.azuread.client.GraphClientFactory;
import org.fireflyframework.security.idp.azuread.client.MsalClientFactory;
import org.fireflyframework.security.idp.azuread.service.AzureAdAdminService;
import org.fireflyframework.security.idp.azuread.service.AzureAdAuthService;
import org.fireflyframework.security.idp.azuread.service.B2CAdminService;
import org.fireflyframework.security.idp.azuread.service.EntraIdAdminService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring auto-configuration for Azure AD IDP Adapter.
 *
 * <p>This configuration class:
 * <ul>
 *   <li>Enables Azure AD configuration properties</li>
 *   <li>Provides explicit bean definitions for all Azure AD components</li>
 *   <li>Is automatically loaded when provider=azure-ad and MSAL4J is on the classpath</li>
 *   <li>Conditionally creates Entra ID or B2C admin service based on the mode property</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnProperty(name = "firefly.security.idp.provider", havingValue = "azure-ad")
@ConditionalOnClass(ConfidentialClientApplication.class)
@EnableConfigurationProperties(AzureAdProperties.class)
@Slf4j
public class AzureAdAutoConfiguration {

    public AzureAdAutoConfiguration() {
        log.info("Azure AD IDP Adapter Configuration loaded");
    }

    @Bean
    @ConditionalOnMissingBean
    public MsalClientFactory msalClientFactory(AzureAdProperties properties) {
        log.info("Configuring MSAL Client Factory for tenant: {}", properties.getTenantId());
        return new MsalClientFactory(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public GraphClientFactory graphClientFactory(AzureAdProperties properties) {
        log.info("Configuring Graph Client Factory for tenant: {}", properties.getTenantId());
        return new GraphClientFactory(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public AzureAdAuthService azureAdAuthService(GraphClientFactory graphClientFactory,
                                                  AzureAdProperties properties) {
        log.info("Configuring Azure AD Auth Service");
        return new AzureAdAuthService(graphClientFactory, properties);
    }

    @Bean
    @ConditionalOnMissingBean(AzureAdAdminService.class)
    @ConditionalOnProperty(name = "firefly.security.idp.azure-ad.mode", havingValue = "entra-id", matchIfMissing = true)
    public AzureAdAdminService entraIdAdminService(GraphClientFactory graphClientFactory,
                                                    AzureAdProperties properties) {
        log.info("Configuring Entra ID Admin Service");
        return new EntraIdAdminService(graphClientFactory, properties);
    }

    @Bean
    @ConditionalOnMissingBean(AzureAdAdminService.class)
    @ConditionalOnProperty(name = "firefly.security.idp.azure-ad.mode", havingValue = "b2c")
    public AzureAdAdminService b2cAdminService(GraphClientFactory graphClientFactory,
                                                AzureAdProperties properties) {
        log.info("Configuring B2C Admin Service");
        return new B2CAdminService(graphClientFactory, properties);
    }

    @Bean
    @ConditionalOnMissingBean(IdpAdapter.class)
    public IdpAdapter azureAdIdpAdapter(AzureAdAuthService authService,
                                         AzureAdAdminService adminService) {
        log.info("Configuring Azure AD IDP Adapter");
        return new AzureAdIdpAdapter(authService, adminService);
    }
}
