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
package org.fireflyframework.web.jackson.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonNullableAutoConfigurationTest {
    private static final String AUTO_CONFIGURATION =
            "org.fireflyframework.web.jackson.config.JsonNullableAutoConfiguration";

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        Jackson2AutoConfiguration.class, JsonNullableAutoConfiguration.class));
    }

    @Test
    void declaredAutoConfigurationIsDiscoverable() {
        assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()))
                .contains(AUTO_CONFIGURATION);
    }

    @Test
    void defaultModulePreservesUndefinedNullAndValuesThroughBootBuilder() {
        runner().run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(JsonNullableModule.class);
            ObjectMapper mapper = context.getBean(Jackson2ObjectMapperBuilder.class).build();
            var payload = new Payload();
            assertThat(mapper.writeValueAsString(payload)).isEqualTo("{}");
            payload.content = JsonNullable.of(null);
            assertThat(mapper.writeValueAsString(payload)).isEqualTo("{\"content\":null}");
            assertThat(mapper.readValue("{\"content\":null}", Payload.class).content)
                    .isEqualTo(JsonNullable.of(null));
            payload.content = JsonNullable.of(List.of("synthetic-value"));
            assertThat(mapper.writeValueAsString(payload))
                    .isEqualTo("{\"content\":[\"synthetic-value\"]}");
            assertThat(mapper.readValue("{}", Payload.class).content.isPresent()).isFalse();
            assertThat(mapper.readValue("{\"content\":[\"synthetic-value\"]}", Payload.class).content.get())
                    .containsExactly("synthetic-value");
        });
    }

    @Test
    void unrelatedModuleDoesNotDisableNullableSupport() {
        runner().withUserConfiguration(UnrelatedModule.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(JsonNullableModule.class);
            assertThat(context.getBeansOfType(SimpleModule.class).values())
                    .contains(context.getBean("projectOtherModule", SimpleModule.class));
        });
    }

    @Test
    void plainProjectModuleWinsInEitherUserConfigurationOrder() {
        for (boolean reverse : new boolean[] {false, true}) {
            var classes = reverse
                    ? new Class<?>[] {ProjectModule.class, UnrelatedModule.class}
                    : new Class<?>[] {UnrelatedModule.class, ProjectModule.class};
            runner().withUserConfiguration(classes).run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(JsonNullableModule.class);
                assertThat(context.getBean(JsonNullableModule.class)).isSameAs(ProjectModule.MODULE);
                assertThat(context.containsBean("jsonNullableModule")).isFalse();
            });
        }
    }

    @Test
    void conventionallyNamedProjectModuleMayDeclareTheGenericModuleReturnType() {
        for (boolean reverse : new boolean[] {false, true}) {
            var classes = reverse
                    ? new Class<?>[] {GenericNamedProjectModule.class, UnrelatedModule.class}
                    : new Class<?>[] {UnrelatedModule.class, GenericNamedProjectModule.class};
            runner().withUserConfiguration(classes).run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(JsonNullableModule.class);
                assertThat(context.getBean("jsonNullableModule"))
                        .isSameAs(GenericNamedProjectModule.MODULE);
            });
        }
    }

    @Test
    void existingApplicationMapperIsNeverReplacedOrMutated() {
        runner().withUserConfiguration(ProjectMapper.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(JsonNullableModule.class)
                    .hasSingleBean(ObjectMapper.class);
            assertThat(context.getBean(ObjectMapper.class)).isSameAs(ProjectMapper.MAPPER);
            assertThat(ProjectMapper.MAPPER.getRegisteredModuleIds()).isEmpty();
        });
    }

    @Test
    void absentOptionalNullableLibraryDoesNotCreateTheModule() {
        runner().withClassLoader(new FilteredClassLoader("org.openapitools.jackson.nullable"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.containsBean("jsonNullableModule")).isFalse();
                });
    }

    @Test
    void absentJackson2DoesNotLoadTheIntegration() {
        runner().withClassLoader(new FilteredClassLoader("com.fasterxml.jackson"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.containsBean("jsonNullableModule")).isFalse();
                });
    }

    public static class Payload {
        public JsonNullable<List<String>> content = JsonNullable.undefined();
    }

    @Configuration(proxyBeanMethods = false)
    static class ProjectModule {
        static final JsonNullableModule MODULE = new JsonNullableModule();
        @Bean JsonNullableModule projectNullableModule() { return MODULE; }
    }

    @Configuration(proxyBeanMethods = false)
    static class UnrelatedModule {
        @Bean SimpleModule projectOtherModule() { return new SimpleModule("project-other"); }
    }

    @Configuration(proxyBeanMethods = false)
    static class GenericNamedProjectModule {
        static final JsonNullableModule MODULE = new JsonNullableModule();
        @Bean com.fasterxml.jackson.databind.Module jsonNullableModule() { return MODULE; }
    }

    @Configuration(proxyBeanMethods = false)
    static class ProjectMapper {
        static final ObjectMapper MAPPER = new ObjectMapper();
        @Bean ObjectMapper projectMapper() { return MAPPER; }
    }
}
