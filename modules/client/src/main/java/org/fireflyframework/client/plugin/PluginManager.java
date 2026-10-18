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
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Plugin manager for discovering, loading, and managing ServiceClient plugins.
 * 
 * <p>Uses Java's ServiceLoader mechanism to automatically discover plugins
 * on the classpath.
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 */
@Slf4j
public class PluginManager {

    private final Map<String, ServiceClientPlugin> plugins = new ConcurrentHashMap<>();
    private final ServiceClient client;

    public PluginManager(ServiceClient client) {
        this.client = client;
        discoverPlugins();
    }

    /**
     * Discovers and loads plugins using ServiceLoader.
     */
    private void discoverPlugins() {
        ServiceLoader<ServiceClientPlugin> loader = ServiceLoader.load(ServiceClientPlugin.class);
        
        for (ServiceClientPlugin plugin : loader) {
            try {
                if (plugin.isEnabled()) {
                    registerPlugin(plugin);
                } else {
                    log.debug("Plugin {} is disabled, skipping", plugin.getName());
                }
            } catch (Exception e) {
                log.error("Error loading plugin: {}", plugin.getName(), e);
            }
        }

        log.info("Discovered {} plugins", plugins.size());
    }

    /**
     * Registers a plugin.
     */
    public void registerPlugin(ServiceClientPlugin plugin) {
        String name = plugin.getName();
        
        if (plugins.containsKey(name)) {
            log.warn("Plugin {} is already registered, skipping", name);
            return;
        }

        try {
            plugin.initialize(client);
            plugins.put(name, plugin);
            log.info("Registered plugin: {} v{} - {}", 
                name, plugin.getVersion(), plugin.getDescription());
        } catch (Exception e) {
            log.error("Error initializing plugin: {}", name, e);
            throw new RuntimeException("Failed to initialize plugin: " + name, e);
        }
    }

    /**
     * Unregisters a plugin.
     */
    public void unregisterPlugin(String name) {
        ServiceClientPlugin plugin = plugins.remove(name);
        if (plugin != null) {
            try {
                plugin.shutdown();
                log.info("Unregistered plugin: {}", name);
            } catch (Exception e) {
                log.error("Error shutting down plugin: {}", name, e);
            }
        }
    }

    /**
     * Gets a plugin by name.
     */
    public Optional<ServiceClientPlugin> getPlugin(String name) {
        return Optional.ofNullable(plugins.get(name));
    }

    /**
     * Gets all registered plugins.
     */
    public List<ServiceClientPlugin> getAllPlugins() {
        return new ArrayList<>(plugins.values());
    }

    /**
     * Gets all plugins sorted by priority.
     */
    public List<ServiceClientPlugin> getPluginsByPriority() {
        return plugins.values().stream()
            .sorted(Comparator.comparingInt(ServiceClientPlugin::getPriority))
            .collect(Collectors.toList());
    }

    /**
     * Shuts down all plugins.
     */
    public void shutdownAll() {
        log.info("Shutting down {} plugins", plugins.size());
        
        plugins.values().forEach(plugin -> {
            try {
                plugin.shutdown();
                log.debug("Shut down plugin: {}", plugin.getName());
            } catch (Exception e) {
                log.error("Error shutting down plugin: {}", plugin.getName(), e);
            }
        });
        
        plugins.clear();
    }

    /**
     * Gets plugin statistics.
     */
    public PluginStatistics getStatistics() {
        long enabledCount = plugins.values().stream()
            .filter(ServiceClientPlugin::isEnabled)
            .count();

        return new PluginStatistics(
            plugins.size(),
            (int) enabledCount,
            plugins.size() - (int) enabledCount
        );
    }

    /**
     * Plugin statistics.
     */
    public record PluginStatistics(
        int totalPlugins,
        int enabledPlugins,
        int disabledPlugins
    ) {}
}

