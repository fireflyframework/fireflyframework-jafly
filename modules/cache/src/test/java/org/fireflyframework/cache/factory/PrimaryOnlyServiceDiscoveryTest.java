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

package org.fireflyframework.cache.factory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.cache.core.CacheAdapter;
import org.fireflyframework.cache.core.CacheOperationPolicy;
import org.fireflyframework.cache.core.CacheType;
import org.fireflyframework.cache.manager.FireflyCacheManagerImpl;
import org.fireflyframework.cache.properties.CacheProperties;
import org.fireflyframework.cache.spi.CacheProviderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Execution(ExecutionMode.SAME_THREAD)
public class PrimaryOnlyServiceDiscoveryTest {
    private static final String SERVICE = "META-INF/services/" + CacheProviderFactory.class.getName();
    private static final String MISSING = "missing.fixture.ProviderFactory";
    private static final AtomicInteger STRICT_CREATES = new AtomicInteger();
    private static final AtomicInteger LEGACY_CREATES = new AtomicInteger();
    private static final AtomicInteger RESOLVERS = new AtomicInteger();
    private static final AtomicInteger SECOND_CONSTRUCTORS = new AtomicInteger();
    @TempDir Path temporary;
    private int resourceNumber;

    @BeforeEach void reset() {
        STRICT_CREATES.set(0); LEGACY_CREATES.set(0); RESOLVERS.set(0); SECOND_CONSTRUCTORS.set(0);
    }

    @Test void completeSingleProviderConstructsStrictOnce() throws Exception {
        withServices(List.of(FirstProvider.class.getName()), factory -> {
            var manager = strict(factory);
            try { assertEquals(CacheOperationPolicy.PRIMARY_ONLY_PROPAGATE_ERRORS, manager.operationPolicy()); }
            finally { manager.close(); }
            assertEquals(1, STRICT_CREATES.get()); assertEquals(1, RESOLVERS.get());
        });
    }

    @Test void matchingThenMissingThenSecondMatchingRefusesBeforeAdapterOrFactoryResolution() throws Exception {
        withServices(List.of(FirstProvider.class.getName(), MISSING, SecondProvider.class.getName()), this::incomplete);
        assertEquals(0, SECOND_CONSTRUCTORS.get());
    }

    @Test void matchingThenThrowingConstructorThenSecondMatchingRefusesBeforeCreate() throws Exception {
        withServices(List.of(FirstProvider.class.getName(), ThrowingConstructor.class.getName(), SecondProvider.class.getName()), this::incomplete);
        assertEquals(0, SECOND_CONSTRUCTORS.get());
    }

    @Test void missingFirstEntryRefusesWithoutCreatingAnyAdapter() throws Exception {
        withServices(List.of(MISSING, FirstProvider.class.getName()), this::incomplete);
    }

    @Test void trailingMissingEntryCannotBeIgnoredAfterAUsableProvider() throws Exception {
        withServices(List.of(FirstProvider.class.getName(), MISSING), this::incomplete);
    }

    @Test void completeDuplicateBackendProvidersStillRefuseAsAmbiguous() throws Exception {
        withServices(List.of(FirstProvider.class.getName(), SecondProvider.class.getName()), factory -> {
            assertThrows(IllegalStateException.class, () -> strict(factory));
            assertEquals(0, STRICT_CREATES.get()); assertEquals(0, RESOLVERS.get());
            assertEquals(1, SECOND_CONSTRUCTORS.get());
        });
    }

    @Test void completeNonmatchingProviderDoesNotSatisfyRequestedBackend() throws Exception {
        withServices(List.of(OtherBackend.class.getName()), factory -> {
            var error = assertThrows(IllegalStateException.class, () -> strict(factory));
            assertTrue(error.getMessage().contains("Exactly one"));
            assertEquals(0, STRICT_CREATES.get()); assertEquals(0, RESOLVERS.get());
        });
    }

    @Test void completeEmptyDiscoveryRefusesAsMissingInsteadOfPretendingIncomplete() throws Exception {
        withServices(List.of(), factory -> {
            var error = assertThrows(IllegalStateException.class, () -> strict(factory));
            assertTrue(error.getMessage().contains("Exactly one"));
            assertEquals(0, STRICT_CREATES.get()); assertEquals(0, RESOLVERS.get());
        });
    }

    @Test void failedPrioritySortCannotLeaveAnApparentlyUniqueStrictProvider() throws Exception {
        withServices(List.of(FirstProvider.class.getName(), ThrowingPriority.class.getName()), this::incomplete);
    }

    @Test void legacyStillUsesItsPreviouslyRetainedPartialProviderList() throws Exception {
        withServices(List.of(FirstProvider.class.getName(), MISSING, SecondProvider.class.getName()), factory -> {
            var manager = factory.createCacheManager("fixture", CacheType.POSTGRES, "fixture:", Duration.ofMinutes(1));
            try { assertEquals(CacheOperationPolicy.LEGACY, manager.operationPolicy()); }
            finally { manager.close(); }
            assertEquals(1, LEGACY_CREATES.get()); assertEquals(0, STRICT_CREATES.get());
        });
    }

    @Test void aNewCompleteFactoryIsNotPoisonedByAnotherFactorysFailedDiscovery() throws Exception {
        withServices(List.of(FirstProvider.class.getName(), MISSING), this::incomplete);
        withServices(List.of(FirstProvider.class.getName()), factory -> {
            var manager = strict(factory); manager.close();
            assertEquals(1, STRICT_CREATES.get());
        });
    }

    @Test void repeatedSameServiceClassUsesRealServiceLoaderDeduplication() throws Exception {
        withServices(List.of(FirstProvider.class.getName(), FirstProvider.class.getName()), factory -> {
            var manager = strict(factory); manager.close();
            assertEquals(1, STRICT_CREATES.get());
        });
    }

    private void incomplete(CacheManagerFactory factory) {
        var error = assertThrows(IllegalStateException.class, () -> strict(factory));
        assertEquals("Cache provider discovery did not complete", error.getMessage());
        assertNull(error.getCause());
        assertEquals(0, STRICT_CREATES.get()); assertEquals(0, LEGACY_CREATES.get()); assertEquals(0, RESOLVERS.get());
    }

    private FireflyCacheManagerImpl strict(CacheManagerFactory factory) {
        return factory.createCacheManager("fixture", CacheType.POSTGRES, "fixture:", Duration.ofMinutes(1),
                "ServiceLoader completion fixture", "test", CacheOperationPolicy.PRIMARY_ONLY_PROPAGATE_ERRORS);
    }

    private void withServices(List<String> names, Consumer<CacheManagerFactory> exercise) throws IOException {
        Path root = temporary.resolve("services-" + resourceNumber++);
        Path file = root.resolve(SERVICE); Files.createDirectories(file.getParent());
        Files.writeString(file, String.join("\n", names) + "\n", StandardCharsets.UTF_8);
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (var loader = new URLClassLoader(new URL[]{root.toUri().toURL()}, previous) {
            @Override public Enumeration<URL> getResources(String resource) throws IOException {
                return resource.equals(SERVICE) ? findResources(resource) : super.getResources(resource);
            }
        }) {
            Thread.currentThread().setContextClassLoader(loader);
            var properties = new CacheProperties(); properties.getPostgres().setEnabled(true);
            properties.getSmart().setEnabled(false); properties.getCaffeine().setEnabled(false);
            var factory = new CacheManagerFactory(properties, new ObjectMapper(), null, null, null, null,
                    () -> { RESOLVERS.incrementAndGet(); return new Object(); });
            exercise.accept(factory);
        } finally { Thread.currentThread().setContextClassLoader(previous); }
    }

    // ServiceLoader requires publicly constructible provider classes; these are controlled SPI fixtures.
    public abstract static class Provider implements CacheProviderFactory {
        public CacheType getType() { return CacheType.POSTGRES; }
        public int priority() { return 10; }
        public boolean isAvailable(ProviderContext context) { return true; }
        public CacheAdapter create(String name, String prefix, Duration ttl, ProviderContext context) {
            LEGACY_CREATES.incrementAndGet(); return adapter(CacheOperationPolicy.LEGACY);
        }
        public CacheAdapter create(String name, String prefix, Duration ttl, ProviderContext context, CacheOperationPolicy policy) {
            STRICT_CREATES.incrementAndGet(); return adapter(policy);
        }
        private CacheAdapter adapter(CacheOperationPolicy policy) {
            var adapter = mock(CacheAdapter.class); when(adapter.getCacheType()).thenReturn(getType());
            when(adapter.operationPolicy()).thenReturn(policy); return adapter;
        }
    }
    public static final class FirstProvider extends Provider { public FirstProvider() { } }
    public static final class SecondProvider extends Provider { public SecondProvider() { SECOND_CONSTRUCTORS.incrementAndGet(); } }
    public static final class ThrowingConstructor extends Provider {
        public ThrowingConstructor() { throw new IllegalStateException("PRIVATE_PROVIDER_CONSTRUCTOR"); }
    }
    public static class OtherBackend extends Provider { public OtherBackend() { } @Override public CacheType getType() { return CacheType.CAFFEINE; } }
    public static final class ThrowingPriority extends OtherBackend {
        public ThrowingPriority() { } @Override public int priority() { throw new IllegalStateException("PRIVATE_PRIORITY_FAILURE"); }
    }
}
