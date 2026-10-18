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

package org.fireflyframework.security.idp.config;

import org.fireflyframework.security.idp.adapter.IdpAdapter;
import org.fireflyframework.security.idp.adapter.impl.IdpAdapterImpl;
import org.fireflyframework.security.idp.adapter.keycloak.KeycloakAPIFactory;
import org.fireflyframework.security.idp.adapter.keycloak.KeycloakClientFactory;
import org.fireflyframework.security.idp.adapter.service.IdpAdminService;
import org.fireflyframework.security.idp.adapter.service.IdpUserService;
import org.fireflyframework.security.idp.adapter.service.TokenService;
import org.fireflyframework.security.idp.adapter.service.impl.IdpAdminServiceImpl;
import org.fireflyframework.security.idp.adapter.service.impl.IdpUserServiceImpl;
import org.fireflyframework.security.idp.adapter.service.impl.TokenServiceImpl;
import org.fireflyframework.security.idp.properties.KeycloakProperties;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Auto-configuration for Keycloak IDP adapter.
 * Registers all Keycloak adapter beans when the keycloak provider is selected.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "firefly.security.idp.provider", havingValue = "keycloak")
@ConditionalOnClass(Keycloak.class)
@EnableConfigurationProperties(KeycloakProperties.class)
@Slf4j
public class KeycloakAutoConfiguration {

    public KeycloakAutoConfiguration() {
        log.info("Keycloak IDP adapter auto-configuration loaded");
    }

    @Bean
    @ConditionalOnMissingBean
    public KeycloakClientFactory keycloakClientFactory(KeycloakProperties properties) {
        return new KeycloakClientFactory(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public KeycloakAPIFactory keycloakAPIFactory(KeycloakProperties properties) {
        return new KeycloakAPIFactory(properties);
    }

    @Bean
    @ConditionalOnMissingBean(TokenService.class)
    public TokenService tokenService() {
        return new TokenServiceImpl();
    }

    @Bean
    @ConditionalOnMissingBean(IdpAdminService.class)
    public IdpAdminService idpAdminService(KeycloakClientFactory keycloakClientFactory, TokenService tokenService) {
        return new IdpAdminServiceImpl(keycloakClientFactory, tokenService);
    }

    @Bean
    @ConditionalOnMissingBean(IdpUserService.class)
    public IdpUserService idpUserService(KeycloakAPIFactory keycloakAPIFactory) {
        return new IdpUserServiceImpl(keycloakAPIFactory);
    }

    @Bean
    @ConditionalOnMissingBean(IdpAdapter.class)
    public IdpAdapter idpAdapter(IdpUserService idpUserService, IdpAdminService idpAdminService) {
        return new IdpAdapterImpl(idpUserService, idpAdminService);
    }

    @Bean
    @ConditionalOnMissingBean(CorsWebFilter.class)
    public CorsWebFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);
        config.addAllowedOriginPattern("*");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}
