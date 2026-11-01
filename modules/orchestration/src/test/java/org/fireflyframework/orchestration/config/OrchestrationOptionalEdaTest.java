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

package org.fireflyframework.orchestration.config;

import org.fireflyframework.eda.properties.EdaProperties;
import org.fireflyframework.eda.publisher.EventPublisherFactory;
import org.fireflyframework.eda.resilience.ResilientEventPublisherFactory;
import org.fireflyframework.orchestration.core.event.EdaOrchestrationEventPublisherImpl;
import org.fireflyframework.orchestration.core.event.NoOpEventPublisherImpl;
import org.fireflyframework.orchestration.core.event.OrchestrationEventPublisher;
import org.fireflyframework.orchestration.saga.engine.SagaEngine;
import org.fireflyframework.orchestration.tcc.engine.TccEngine;
import org.fireflyframework.orchestration.workflow.engine.WorkflowEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrchestrationOptionalEdaTest {
    @Test
    void startsAllEnginesWithoutOptionalEdaAndRedisModules() throws Exception {
        // Reload configuration behind the filter; parent-loaded classes can still resolve EDA signatures.
        try (var isolated = new URLClassLoader(
                new URL[] {OrchestrationAutoConfiguration.class.getProtectionDomain().getCodeSource().getLocation()},
                new FilteredClassLoader("org.fireflyframework.eda", "org.springframework.boot.data.redis",
                        "org.springframework.data.redis")) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("org.fireflyframework.orchestration.config.")) {
                    var loaded = findLoadedClass(name);
                    if (loaded == null) loaded = findClass(name);
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
                return super.loadClass(name, resolve);
            }
        }) {
            new ApplicationContextRunner()
                    .withClassLoader(isolated)
                    .withConfiguration(AutoConfigurations.of(
                            isolated.loadClass(OrchestrationAutoConfiguration.class.getName()),
                            isolated.loadClass(OrchestrationPersistenceAutoConfiguration.class.getName()),
                            isolated.loadClass(SagaAutoConfiguration.class.getName()),
                            isolated.loadClass(TccAutoConfiguration.class.getName()),
                            isolated.loadClass(WorkflowAutoConfiguration.class.getName())))
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasSingleBean(SagaEngine.class).hasSingleBean(TccEngine.class)
                                .hasSingleBean(WorkflowEngine.class);
                        assertThat(context.getBean(OrchestrationEventPublisher.class)).isInstanceOf(NoOpEventPublisherImpl.class);
                    });
        }
    }

    @Test
    void enablesTheEdaBridgeWhenTheFactoryAndOptInArePresent() {
        var beanFactory = new DefaultListableBeanFactory();
        var factory = new EventPublisherFactory(List.of(), new EdaProperties(),
                beanFactory.getBeanProvider(ResilientEventPublisherFactory.class));
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OrchestrationAutoConfiguration.class))
                .withBean(EventPublisherFactory.class, () -> factory)
                .withPropertyValues("firefly.orchestration.events.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(OrchestrationEventPublisher.class);
                    assertThat(context.getBean(OrchestrationEventPublisher.class))
                            .isInstanceOf(EdaOrchestrationEventPublisherImpl.class);
                });
    }
}
