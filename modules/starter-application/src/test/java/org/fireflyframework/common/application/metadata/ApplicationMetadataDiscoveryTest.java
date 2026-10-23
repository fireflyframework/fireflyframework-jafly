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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.support.GenericApplicationContext;

import java.util.concurrent.atomic.AtomicInteger;

class ApplicationMetadataDiscoveryTest {
    private static final AtomicInteger MAIN_CONSTRUCTIONS = new AtomicInteger();
    private static final AtomicInteger UNRELATED_CONSTRUCTIONS = new AtomicInteger();
    private static final AtomicInteger FACTORY_CONSTRUCTIONS = new AtomicInteger();
    private static final AtomicInteger PRODUCT_CONSTRUCTIONS = new AtomicInteger();

    @BeforeEach
    void resetCounters() {
        MAIN_CONSTRUCTIONS.set(0);
        UNRELATED_CONSTRUCTIONS.set(0);
        FACTORY_CONSTRUCTIONS.set(0);
        PRODUCT_CONSTRUCTIONS.set(0);
    }

    @Test
    void findsDeclaredApplicationWithoutCreatingItOrUnrelatedLazyBeans() {
        try (var context = new GenericApplicationContext()) {
            context.registerBean(
                    "unrelated",
                    Object.class,
                    () -> {
                        UNRELATED_CONSTRUCTIONS.incrementAndGet();
                        return new Object();
                    },
                    definition -> definition.setLazyInit(true));
            context.registerBean(
                    "main", DeclaredApplication.class, definition -> definition.setLazyInit(true));
            context.refresh();
            var metadata =
                    new ApplicationMetadataProvider(context, context.getEnvironment())
                            .appMetadata();
            assertThat(metadata.getName()).isEqualTo("declared-application");
            assertThat(metadata.getDomain()).isEqualTo("synthetic-domain");
            assertThat(MAIN_CONSTRUCTIONS.get()).isZero();
            assertThat(UNRELATED_CONSTRUCTIONS.get()).isZero();
        }
    }

    @Test
    void predictsTypedFactoryProductWithoutInitializingFactoryOrProduct() {
        try (var context = new GenericApplicationContext()) {
            context.registerBean(
                    "typed", TypedFactory.class, definition -> definition.setLazyInit(true));
            context.refresh();
            var metadata =
                    new ApplicationMetadataProvider(context, context.getEnvironment())
                            .appMetadata();
            assertThat(metadata.getName()).isEqualTo("declared-application");
            assertThat(FACTORY_CONSTRUCTIONS.get()).isZero();
            assertThat(PRODUCT_CONSTRUCTIONS.get()).isZero();
            assertThat(MAIN_CONSTRUCTIONS.get()).isZero();
        }
    }

    @Test
    void leavesUnknownFactoryUntouchedAndRetainsDefaultMetadata() {
        try (var context = new GenericApplicationContext()) {
            context.registerBean(
                    "unknown", UnknownFactory.class, definition -> definition.setLazyInit(true));
            context.refresh();
            var metadata =
                    new ApplicationMetadataProvider(context, context.getEnvironment())
                            .appMetadata();
            assertThat(metadata.getName()).isEqualTo("unknown-application");
            assertThat(FACTORY_CONSTRUCTIONS.get()).isZero();
            assertThat(PRODUCT_CONSTRUCTIONS.get()).isZero();
        }
    }

    @Test
    void preservesImmediateSuperclassAnnotationAndItsFields() {
        try (var context = new GenericApplicationContext()) {
            context.registerBean(
                    "derived",
                    DerivedApplication.class,
                    definition -> definition.setLazyInit(true));
            context.refresh();
            var metadata =
                    new ApplicationMetadataProvider(context, context.getEnvironment())
                            .appMetadata();
            assertThat(metadata.getName()).isEqualTo("declared-application");
            assertThat(metadata.getDescription())
                    .isEqualTo("Predictable synthetic application metadata");
            assertThat(metadata.getTeam()).isEqualTo("synthetic-team");
            assertThat(metadata.getDomain()).isEqualTo("synthetic-domain");
        }
    }

    @FireflyApplication(
            name = "declared-application",
            description = "Predictable synthetic application metadata",
            domain = "synthetic-domain",
            team = "synthetic-team",
            owners = {"synthetic-owner"})
    static class DeclaredApplication {
        DeclaredApplication() {
            MAIN_CONSTRUCTIONS.incrementAndGet();
        }
    }

    static class DerivedApplication extends DeclaredApplication {}

    static class TypedFactory implements FactoryBean<DeclaredApplication> {
        TypedFactory() {
            FACTORY_CONSTRUCTIONS.incrementAndGet();
        }

        public DeclaredApplication getObject() {
            PRODUCT_CONSTRUCTIONS.incrementAndGet();
            return new DeclaredApplication();
        }

        public Class<?> getObjectType() {
            return DeclaredApplication.class;
        }
    }

    static class UnknownFactory implements FactoryBean<Object> {
        UnknownFactory() {
            FACTORY_CONSTRUCTIONS.incrementAndGet();
        }

        public Object getObject() {
            PRODUCT_CONSTRUCTIONS.incrementAndGet();
            return new Object();
        }

        public Class<?> getObjectType() {
            return null;
        }
    }
}
