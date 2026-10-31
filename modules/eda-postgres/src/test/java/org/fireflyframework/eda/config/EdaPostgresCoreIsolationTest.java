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

package org.fireflyframework.eda.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import java.util.Arrays;
import java.util.stream.Stream;
import org.fireflyframework.eda.serialization.MessageSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.r2dbc.autoconfigure.DataR2dbcAutoConfiguration;
import org.springframework.boot.data.r2dbc.autoconfigure.DataR2dbcRepositoriesAutoConfiguration;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcConnectionDetails;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;

class EdaPostgresCoreIsolationTest {
    @AutoConfigurationPackage
    static class SerializationBeans {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean MessageSerializer messageSerializer() { return mock(MessageSerializer.class); }
        @Bean org.fireflyframework.eda.consumer.postgres.PostgresEventDecoder postgresEventDecoder(MessageSerializer serializer) {
            return new org.fireflyframework.eda.consumer.postgres.impl.RegisteredPostgresEventDecoder(serializer,java.util.Map.of("java.lang.String",String.class));
        }
        @Bean org.fireflyframework.eda.listener.EventListenerProcessor eventListenerProcessor(org.springframework.context.ApplicationContext context,
                org.springframework.context.ApplicationEventPublisher publisher,org.springframework.core.env.Environment environment) {
            return new org.fireflyframework.eda.listener.EventListenerProcessor(context,publisher,mock(org.fireflyframework.eda.error.CustomErrorHandlerRegistry.class),environment);
        }
    }

    static class SharedBusinessResource {
        @Bean(name = {"connectionFactory", "fireflyEdaPostgresPublisherConnectionFactory",
                "fireflyEdaPostgresConsumerConnectionFactory"})
        ConnectionFactory businessFactory() {
            var factory = mock(ConnectionFactory.class);
            when(factory.getMetadata()).thenReturn(() -> "PostgreSQL");
            org.mockito.Mockito.doReturn(reactor.core.publisher.Mono.never()).when(factory).create();
            return factory;
        }
    }

    private ApplicationContextRunner context(String business, String transport) {
        var context = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FireflyEdaPostgresPublisherAutoConfiguration.class,
                        FireflyEdaPostgresConsumerAutoConfiguration.class, R2dbcAutoConfiguration.class,
                        DataR2dbcAutoConfiguration.class, DataR2dbcRepositoriesAutoConfiguration.class,
                        R2dbcTransactionManagerAutoConfiguration.class))
                .withUserConfiguration(SerializationBeans.class)
                .withPropertyValues("spring.r2dbc.pool.initial-size=0", "spring.sql.init.mode=never",
                        "firefly.eda.publishers.enabled=" + !transport.equals("NONE"),
                        "firefly.eda.publishers.postgres.default.enabled=" + (transport.equals("PUBLISHER") || transport.equals("BOTH")),
                        "firefly.eda.publishers.postgres.default.host=127.0.0.1",
                        "firefly.eda.publishers.postgres.default.port=9",
                        "firefly.eda.publishers.postgres.default.database=technical",
                        "firefly.eda.publishers.postgres.default.username=technical",
                        "firefly.eda.publishers.postgres.default.auto-create-schema=false",
                        "firefly.eda.consumer.enabled=" + !transport.equals("NONE"),
                        "firefly.eda.consumer.postgres.default.enabled=" + (transport.equals("CONSUMER") || transport.equals("BOTH")),
                        "firefly.eda.consumer.postgres.default.host=127.0.0.1",
                        "firefly.eda.consumer.postgres.default.port=9",
                        "firefly.eda.consumer.postgres.default.database=technical",
                        "firefly.eda.consumer.postgres.default.username=technical");
        return switch (business) {
            case "URL" -> context.withPropertyValues("spring.r2dbc.url=r2dbc:postgresql://business@127.0.0.1:9/business");
            case "DETAILS" -> context.withBean(R2dbcConnectionDetails.class, () -> () -> ConnectionFactoryOptions.parse("r2dbc:postgresql://business@127.0.0.1:9/business"));
            case "EXPLICIT" -> context.withBean("connectionFactory", ConnectionFactory.class, () -> {
                var factory = mock(ConnectionFactory.class);
                when(factory.getMetadata()).thenReturn(() -> "PostgreSQL");
                return factory;
            });
            case "SHARED" -> context.withUserConfiguration(SharedBusinessResource.class);
            default -> throw new IllegalArgumentException(business);
        };
    }

    static Stream<Arguments> transportCases() {
        return Stream.of("PUBLISHER", "CONSUMER", "BOTH").flatMap(transport ->
                Stream.of("URL", "DETAILS", "EXPLICIT").map(business -> Arguments.of(business, transport)));
    }

    static Stream<String> businessCases() { return Stream.of("URL", "DETAILS", "EXPLICIT"); }

    void assertBusiness(String business, String transport) {
        context(business, transport).run(application -> {
            assertThat(application).hasNotFailed();
            if (transport.equals("PUBLISHER") || transport.equals("BOTH")) {
                assertThat(application).hasBean("fireflyEdaPostgresPublisherConnectionFactory");
                assertThat(application).hasSingleBean(org.fireflyframework.eda.publisher.postgres.PostgresEventPublisher.class);
            }
            if (transport.equals("CONSUMER") || transport.equals("BOTH")) {
                assertThat(application).hasBean("fireflyEdaPostgresConsumerConnectionFactory");
            }
            assertThat(application).hasBean("connectionFactory");
            assertThat(application).hasSingleBean(R2dbcEntityTemplate.class);
            assertThat(application).hasSingleBean(R2dbcTransactionManager.class);
            var factory = application.getBean("connectionFactory", ConnectionFactory.class);
            for (String name : new String[] {"fireflyEdaPostgresPublisherConnectionFactory", "fireflyEdaPostgresConsumerConnectionFactory"}) {
                if (application.containsBean(name)) {
                    if (business.equals("SHARED")) assertThat(factory).isSameAs(application.getBean(name));
                    else assertThat(factory).isNotSameAs(application.getBean(name));
                }
            }
            assertThat(application.getBean(R2dbcEntityTemplate.class).getDatabaseClient().getConnectionFactory()).isSameAs(factory);
            assertThat(application.getBean(R2dbcTransactionManager.class).getConnectionFactory()).isSameAs(factory);
            if (business.equals("SHARED")) {
                var publisher = application.getBean(org.fireflyframework.eda.publisher.postgres.PostgresEventPublisher.class);
                var provider = (org.springframework.beans.factory.ObjectProvider<?>)
                        org.springframework.test.util.ReflectionTestUtils.getField(publisher, "connectionFactoryProvider");
                assertThat(provider.getObject()).isSameAs(factory);
            }
        });
    }

    @ParameterizedTest(name = "Core {0} with {1} transport retains business persistence")
    @MethodSource("transportCases")
    void technicalTransportCannotReplaceOrAmbiguateBusinessPersistence(String business, String transport) {
        assertBusiness(business, transport);
    }

    @ParameterizedTest(name = "Core {0} baseline without transport")
    @MethodSource("businessCases")
    void baselineBusinessPersistenceWorksWithoutTransport(String business) { assertBusiness(business, "NONE"); }

    @Test
    void explicitBusinessFactoryMayAlsoBeNamedForBothTransports() {
        assertBusiness("SHARED", "BOTH");
        var registry = new DefaultListableBeanFactory();
        new ClassPathBeanDefinitionScanner(registry).scan("org.fireflyframework.eda");
        var discovered = Arrays.stream(registry.getBeanDefinitionNames())
                .map(name -> registry.getBeanDefinition(name).getBeanClassName()).toList();
        assertThat(discovered).doesNotContain(SerializationBeans.class.getName(), SharedBusinessResource.class.getName());
        assertThat(discovered).noneMatch(name -> name != null && (name.contains("PostgresConsumerAutoConfigurationTest$")
                || name.contains("PostgresConsumerNativeFixture$") || name.contains("PostgresConsumerRuntimeTest$")));
    }
}
