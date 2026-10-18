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

package org.fireflyframework.security.idp.cognito.config;

import org.fireflyframework.security.idp.adapter.IdpAdapter;
import org.fireflyframework.security.idp.cognito.adapter.CognitoIdpAdapter;
import org.fireflyframework.security.idp.cognito.client.CognitoClientFactory;
import org.fireflyframework.security.idp.cognito.properties.CognitoProperties;
import org.fireflyframework.security.idp.cognito.service.CognitoAdminService;
import org.fireflyframework.security.idp.cognito.service.CognitoUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

/**
 * Spring auto-configuration for AWS Cognito IDP Adapter.
 *
 * <p>This configuration class:
 * <ul>
 *   <li>Enables Cognito configuration properties</li>
 *   <li>Provides explicit bean definitions for all Cognito components</li>
 *   <li>Is automatically loaded when provider=cognito and the Cognito SDK is on the classpath</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnProperty(name = "firefly.security.idp.provider", havingValue = "cognito")
@ConditionalOnClass(CognitoIdentityProviderClient.class)
@EnableConfigurationProperties(CognitoProperties.class)
@Slf4j
public class CognitoAutoConfiguration {

    public CognitoAutoConfiguration() {
        log.info("AWS Cognito IDP Adapter Configuration loaded");
    }

    @Bean
    @ConditionalOnMissingBean
    public CognitoClientFactory cognitoClientFactory(CognitoProperties properties) {
        log.info("Configuring AWS Cognito Client Factory for region: {}", properties.getRegion());

        CognitoClientFactory factory = new CognitoClientFactory(properties);

        // Configure endpoint override if provided (for LocalStack testing)
        if (properties.getEndpointOverride() != null && !properties.getEndpointOverride().isEmpty()) {
            log.info("Configuring Cognito client with endpoint override: {}", properties.getEndpointOverride());
            factory.setEndpointOverride(java.net.URI.create(properties.getEndpointOverride()));
        }

        return factory;
    }

    @Bean
    @ConditionalOnMissingBean
    public CognitoUserService cognitoUserService(CognitoClientFactory clientFactory, CognitoProperties properties) {
        log.info("Configuring Cognito User Service");
        return new CognitoUserService(clientFactory, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public CognitoAdminService cognitoAdminService(CognitoClientFactory clientFactory, CognitoProperties properties) {
        log.info("Configuring Cognito Admin Service");
        return new CognitoAdminService(clientFactory, properties);
    }

    @Bean
    @ConditionalOnMissingBean(IdpAdapter.class)
    public IdpAdapter cognitoIdpAdapter(CognitoUserService userService, CognitoAdminService adminService) {
        log.info("Configuring Cognito IDP Adapter");
        return new CognitoIdpAdapter(userService, adminService);
    }
}
