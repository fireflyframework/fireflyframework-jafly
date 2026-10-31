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

package org.fireflyframework.common.application.metadata;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationMetadataProviderDiscoveryTest {

    @Test
    void fallsBackWithoutInstantiatingUnrelatedLazyBeans() {
        AtomicInteger creations = new AtomicInteger();
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("unrelated", Object.class, () -> {
                creations.incrementAndGet();
                return new Object();
            }, definition -> definition.setLazyInit(true));
            context.refresh();

            var provider = new ApplicationMetadataProvider(context,
                    new MockEnvironment().withProperty("spring.application.name", "fallback-service"));

            assertThat(provider.appMetadata().getName()).isEqualTo("fallback-service");
            assertThat(creations.get()).isZero();
        }
    }

    @Test
    void readsApplicationMetadataWithoutInvokingItsLazyConstructor() {
        AtomicInteger creations = new AtomicInteger();
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("application", OnboardingApplication.class, () -> {
                creations.incrementAndGet();
                return new OnboardingApplication();
            }, definition -> definition.setLazyInit(true));
            context.refresh();

            var metadata = new ApplicationMetadataProvider(context, new MockEnvironment()).appMetadata();

            assertThat(metadata.getName()).isEqualTo("onboarding");
            assertThat(metadata.getDomain()).isEqualTo("customer");
            assertThat(metadata.getTeam()).isEqualTo("onboarding-team");
            assertThat(creations.get()).isZero();
        }
    }

    @Test
    void doesNotInitializeFactoryBeansOrTheirProductsToInspectAnnotations() {
        AtomicInteger factories = new AtomicInteger();
        AtomicInteger products = new AtomicInteger();
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("unrelatedFactory", UnrelatedFactory.class, () -> {
                factories.incrementAndGet();
                return new UnrelatedFactory(products);
            }, definition -> definition.setLazyInit(true));
            context.refresh();

            var provider = new ApplicationMetadataProvider(context,
                    new MockEnvironment().withProperty("spring.application.name", "fallback-service"));

            assertThat(provider.appMetadata().getName()).isEqualTo("fallback-service");
            assertThat(factories.get()).isZero();
            assertThat(products.get()).isZero();
        }
    }

    @Test
    void findsSuperclassMetadataWithoutInstantiatingTheSubclass() {
        AtomicInteger creations = new AtomicInteger();
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("application", ProxiedApplication.class, () -> {
                creations.incrementAndGet();
                return new ProxiedApplication();
            }, definition -> definition.setLazyInit(true));
            context.refresh();

            var metadata = new ApplicationMetadataProvider(context, new MockEnvironment()).appMetadata();

            assertThat(metadata.getName()).isEqualTo("onboarding");
            assertThat(creations.get()).isZero();
        }
    }

    @FireflyApplication(name = "onboarding", description = "Accept customer applications",
            domain = "customer", team = "onboarding-team", owners = {"owner@example.test"})
    static class OnboardingApplication {
    }

    static class ProxiedApplication extends OnboardingApplication {
    }

    static class UnrelatedFactory implements FactoryBean<Object> {
        private final AtomicInteger products;

        UnrelatedFactory(AtomicInteger products) {
            this.products = products;
        }

        @Override
        public Object getObject() {
            products.incrementAndGet();
            return new Object();
        }

        @Override
        public Class<?> getObjectType() {
            return Object.class;
        }
    }

    @Test
    void findsMetadataTwoSuperclassesAwayWithoutInstantiatingBean() {
        AtomicInteger creations = new AtomicInteger();
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("application", GrandchildApplication.class, () -> {
                creations.incrementAndGet();
                return new GrandchildApplication();
            }, definition -> definition.setLazyInit(true));
            context.refresh();

            var metadata = new ApplicationMetadataProvider(context, new MockEnvironment()).appMetadata();

            assertThat(creations.get()).isZero();
            assertThat(metadata.getName()).isEqualTo("onboarding");
        }
    }

    @Test
    void findsInterfaceMetadataWithoutInstantiatingBean() {
        AtomicInteger creations = new AtomicInteger();
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("application", InterfaceApplication.class, () -> {
                creations.incrementAndGet();
                return new InterfaceApplication();
            }, definition -> definition.setLazyInit(true));
            context.refresh();

            var metadata = new ApplicationMetadataProvider(context, new MockEnvironment()).appMetadata();

            assertThat(creations.get()).isZero();
            assertThat(metadata.getName()).isEqualTo("interface-application");
        }
    }

    static class GrandchildApplication extends ProxiedApplication {
    }

    @FireflyApplication(name = "interface-application", description = "Application contract",
            domain = "customer", team = "onboarding-team", owners = {"owner@example.test"})
    interface ApplicationContract {
    }

    static class InterfaceApplication implements ApplicationContract {
    }
}
