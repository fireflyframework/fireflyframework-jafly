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

package org.fireflyframework.cache.spi.providers;

import org.fireflyframework.cache.core.CacheType;
import org.fireflyframework.cache.spi.CacheProviderFactory;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the Hazelcast cache provider is discoverable via the JDK
 * {@link ServiceLoader} SPI and reports {@link CacheType#HAZELCAST}.
 */
class HazelcastProviderWiringTest {

    @Test
    void hazelcastProviderIsDiscoveredViaServiceLoader() {
        ServiceLoader<CacheProviderFactory> loader = ServiceLoader.load(CacheProviderFactory.class);

        var factories = StreamSupport.stream(loader.spliterator(), false)
                .collect(Collectors.toList());

        assertThat(factories)
                .as("ServiceLoader should discover at least one CacheProviderFactory")
                .isNotEmpty();

        assertThat(factories)
                .as("a Hazelcast provider with getType()==HAZELCAST must be present")
                .anySatisfy(factory ->
                        assertThat(factory.getType()).isEqualTo(CacheType.HAZELCAST));

        CacheProviderFactory hazelcast = factories.stream()
                .filter(f -> f.getType() == CacheType.HAZELCAST)
                .findFirst()
                .orElseThrow();

        assertThat(hazelcast).isInstanceOf(HazelcastProvider.class);
    }
}
