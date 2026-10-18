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

package org.fireflyframework.security.webflux.bridge;

import org.fireflyframework.security.core.tenant.ConfigurableTenantResolver;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.fireflyframework.security.spi.TenantResolverPort;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Opt-in auto-configuration of the principal&rarr;header bridge. Disabled by default
 * ({@code firefly.security.webflux.header-bridge.enabled=false}) so no existing consumer is
 * affected; when enabled it contributes a default {@link TenantResolverPort} (unless the product
 * supplies its own) and the {@link PrincipalHeaderBridgeWebFilter}. Both beans are
 * {@link ConditionalOnMissingBean} so applications can override either piece.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(prefix = "firefly.security.webflux.header-bridge", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(PrincipalHeaderBridgeProperties.class)
public class WebFluxBridgeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TenantResolverPort fireflyTenantResolverPort(PrincipalHeaderBridgeProperties properties) {
        return new ConfigurableTenantResolver(properties.getTenantClaimPaths());
    }

    @Bean
    @ConditionalOnMissingBean
    public PrincipalHeaderBridgeWebFilter fireflyPrincipalHeaderBridgeWebFilter(
            SecurityContextPort securityContext,
            TenantResolverPort tenantResolver,
            PrincipalHeaderBridgeProperties properties) {
        return new PrincipalHeaderBridgeWebFilter(securityContext, tenantResolver, properties);
    }
}
