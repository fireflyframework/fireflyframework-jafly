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

package org.fireflyframework.client.plugin;

import org.fireflyframework.client.ServiceClient;
import org.fireflyframework.client.plugin.PluginManager.PluginStatistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for PluginManager.
 */
class PluginManagerTest {

    @Mock
    private ServiceClient serviceClient;

    private PluginManager pluginManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        pluginManager = new PluginManager(serviceClient);
    }

    @Test
    void shouldRegisterPlugin() {
        // Given
        TestPlugin plugin = new TestPlugin();

        // When
        pluginManager.registerPlugin(plugin);

        // Then
        Optional<ServiceClientPlugin> registered = pluginManager.getPlugin("test-plugin");
        assertThat(registered).isPresent();
        assertThat(registered.get()).isEqualTo(plugin);
    }

    @Test
    void shouldUnregisterPlugin() {
        // Given
        TestPlugin plugin = new TestPlugin();
        pluginManager.registerPlugin(plugin);

        // When
        pluginManager.unregisterPlugin("test-plugin");

        // Then
        Optional<ServiceClientPlugin> registered = pluginManager.getPlugin("test-plugin");
        assertThat(registered).isEmpty();
    }

    @Test
    void shouldGetAllPlugins() {
        // Given
        TestPlugin plugin1 = new TestPlugin();
        TestPlugin plugin2 = new TestPlugin("test-plugin-2");
        pluginManager.registerPlugin(plugin1);
        pluginManager.registerPlugin(plugin2);

        // When
        List<ServiceClientPlugin> plugins = pluginManager.getAllPlugins();

        // Then
        assertThat(plugins).hasSize(2);
    }

    @Test
    void shouldGetStatistics() {
        // Given
        TestPlugin plugin = new TestPlugin();
        pluginManager.registerPlugin(plugin);

        // When
        PluginStatistics stats = pluginManager.getStatistics();

        // Then
        assertThat(stats.totalPlugins()).isEqualTo(1);
        assertThat(stats.enabledPlugins()).isEqualTo(1);
    }

    @Test
    void shouldShutdownAllPlugins() {
        // Given
        TestPlugin plugin = new TestPlugin();
        pluginManager.registerPlugin(plugin);

        // When
        pluginManager.shutdownAll();

        // Then
        assertThat(plugin.isShutdown()).isTrue();
    }

    /**
     * Test plugin implementation.
     */
    static class TestPlugin implements ServiceClientPlugin {
        private final String name;
        private boolean shutdown = false;

        TestPlugin() {
            this("test-plugin");
        }

        TestPlugin(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getVersion() {
            return "1.0.0";
        }

        @Override
        public void initialize(ServiceClient client) {
            // Test implementation
        }

        @Override
        public Mono<Void> beforeRequest(RequestContext context) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> afterResponse(ResponseContext context) {
            return Mono.empty();
        }

        @Override
        public void shutdown() {
            this.shutdown = true;
        }

        public boolean isShutdown() {
            return shutdown;
        }
    }
}

