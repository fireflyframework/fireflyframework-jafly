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

package org.fireflyframework.domain.config;

import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.cqrs.config.CqrsAutoConfiguration;
import org.fireflyframework.cqrs.config.CqrsProperties;
import org.fireflyframework.cqrs.query.QueryBus;
import org.fireflyframework.cqrs.tracing.CorrelationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Comprehensive test suite for Auto-Configuration classes in the Firefly Common Domain library.
 * Tests cover conditional bean creation, property binding, and integration scenarios
 * for banking domain microservices auto-configuration.
 */
@DisplayName("Common Domain Auto-Configuration - Component Initialization")
class AutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            CqrsAutoConfiguration.class
        ))
        .withBean(CorrelationContext.class);

    @Test
    @DisplayName("Should auto-configure CQRS framework when enabled")
    void shouldAutoConfigureCqrsFrameworkWhenEnabled() {
        contextRunner
            .withPropertyValues(
                "firefly.cqrs.enabled=true"
            )
            .run(context -> {
                assertThat(context).hasSingleBean(CommandBus.class);
                assertThat(context).hasSingleBean(QueryBus.class);
                assertThat(context).hasSingleBean(CorrelationContext.class);

                CommandBus commandBus = context.getBean(CommandBus.class);
                assertThat(commandBus).isNotNull();

                QueryBus queryBus = context.getBean(QueryBus.class);
                assertThat(queryBus).isNotNull();
            });
    }

    @Test
    @DisplayName("Should not auto-configure CQRS framework when disabled")
    void shouldNotAutoConfigureCqrsFrameworkWhenDisabled() {
        contextRunner
            .withPropertyValues(
                "firefly.cqrs.enabled=false"
            )
            .run(context -> {
                assertThat(context).doesNotHaveBean(CommandBus.class);
                assertThat(context).doesNotHaveBean(QueryBus.class);
            });
    }

    @Test
    @DisplayName("Should bind configuration properties correctly")
    void shouldBindConfigurationPropertiesCorrectly() {
        contextRunner
            .withPropertyValues(
                "firefly.cqrs.enabled=true"
            )
            .run(context -> {
                assertThat(context).hasSingleBean(CqrsProperties.class);
            });
    }

    @Test
    @DisplayName("Should support custom bean overrides")
    void shouldSupportCustomBeanOverrides() {
        contextRunner
            .withPropertyValues(
                "firefly.cqrs.enabled=true"
            )
            .withBean("customCommandBus", CommandBus.class, () -> {
                return mock(CommandBus.class);
            })
            .run(context -> {
                assertThat(context).hasBean("customCommandBus");

                CommandBus customCommandBus = context.getBean("customCommandBus", CommandBus.class);
                assertThat(customCommandBus).isNotNull();
            });
    }
}
