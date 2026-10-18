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

package org.fireflyframework.plugin.core;

import org.fireflyframework.plugin.annotation.Extension;
import org.fireflyframework.plugin.api.*;
import org.fireflyframework.plugin.model.PluginDescriptor;
import org.fireflyframework.plugin.model.PluginMetadata;
import org.fireflyframework.plugin.model.PluginState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of PluginManager.
 * Manages the complete lifecycle of plugins including loading, starting, stopping, and unloading.
 */
public class DefaultPluginManager implements PluginManager {
    
    private static final Logger log = LoggerFactory.getLogger(DefaultPluginManager.class);
    
    private final Map<String, Plugin> plugins = new ConcurrentHashMap<>();
    private final Map<String, PluginDescriptor> descriptors = new ConcurrentHashMap<>();
    private final ExtensionRegistry extensionRegistry;
    private final PluginDependencyResolver dependencyResolver;
    
    private boolean initialized = false;
    
    public DefaultPluginManager() {
        this.extensionRegistry = new DefaultExtensionRegistry();
        this.dependencyResolver = new PluginDependencyResolver();
    }
    
    public DefaultPluginManager(ExtensionRegistry extensionRegistry) {
        this.extensionRegistry = extensionRegistry;
        this.dependencyResolver = new PluginDependencyResolver();
    }
    
    @Override
    public synchronized void initialize() throws PluginException {
        if (initialized) {
            log.warn("Plugin manager already initialized");
            return;
        }
        
        log.info("Initializing plugin manager");
        initialized = true;
    }
    
    @Override
    public PluginDescriptor loadPlugin(Class<? extends Plugin> pluginClass) throws PluginException {
        Objects.requireNonNull(pluginClass, "Plugin class cannot be null");
        
        try {
            // Create plugin instance
            Constructor<? extends Plugin> constructor = pluginClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            Plugin plugin = constructor.newInstance();
            
            PluginMetadata metadata = plugin.getMetadata();
            String pluginId = metadata.id();
            
            // Check if already loaded
            if (plugins.containsKey(pluginId)) {
                throw new PluginException("Plugin already loaded: " + pluginId);
            }
            
            log.info("Loading plugin: {} ({})", metadata.name(), pluginId);
            
            // Create descriptor
            PluginDescriptor descriptor = PluginDescriptor.builder()
                .metadata(metadata)
                .state(PluginState.LOADED)
                .build();
            
            // Store plugin and descriptor
            plugins.put(pluginId, plugin);
            descriptors.put(pluginId, descriptor);
            
            // Initialize plugin
            try {
                plugin.initialize();
                descriptor = descriptor.withState(PluginState.INITIALIZED);
                descriptors.put(pluginId, descriptor);
                log.info("Plugin initialized: {}", pluginId);
            } catch (Exception e) {
                descriptor = descriptor.withState(PluginState.FAILED);
                descriptors.put(pluginId, descriptor);
                throw new PluginException("Failed to initialize plugin: " + pluginId, e);
            }
            
            // Register extensions
            registerPluginExtensions(plugin, pluginClass);
            
            return descriptor;
            
        } catch (PluginException e) {
            throw e;
        } catch (Exception e) {
            throw new PluginException("Failed to load plugin: " + pluginClass.getName(), e);
        }
    }
    
    @Override
    public void startPlugin(String pluginId) throws PluginException {
        Objects.requireNonNull(pluginId, "Plugin ID cannot be null");
        
        Plugin plugin = plugins.get(pluginId);
        if (plugin == null) {
            throw new PluginException("Plugin not found: " + pluginId);
        }
        
        PluginDescriptor descriptor = descriptors.get(pluginId);
        if (descriptor.state() == PluginState.STARTED) {
            log.warn("Plugin already started: {}", pluginId);
            return;
        }
        
        // Check and start dependencies first
        try {
            List<String> orderedPlugins = dependencyResolver.resolveDependencies(descriptors.values());
            for (String depId : orderedPlugins) {
                if (depId.equals(pluginId)) {
                    break;
                }
                PluginDescriptor depDescriptor = descriptors.get(depId);
                if (depDescriptor.state() != PluginState.STARTED) {
                    startPlugin(depId);
                }
            }
        } catch (PluginException e) {
            throw new PluginException("Failed to resolve dependencies for plugin: " + pluginId, e);
        }
        
        log.info("Starting plugin: {}", pluginId);
        
        try {
            plugin.start();
            descriptor = descriptor.withState(PluginState.STARTED);
            descriptors.put(pluginId, descriptor);
            log.info("Plugin started: {}", pluginId);
        } catch (Exception e) {
            descriptor = descriptor.withState(PluginState.FAILED);
            descriptors.put(pluginId, descriptor);
            throw new PluginException("Failed to start plugin: " + pluginId, e);
        }
    }
    
    @Override
    public void stopPlugin(String pluginId) throws PluginException {
        Objects.requireNonNull(pluginId, "Plugin ID cannot be null");
        
        Plugin plugin = plugins.get(pluginId);
        if (plugin == null) {
            throw new PluginException("Plugin not found: " + pluginId);
        }
        
        PluginDescriptor descriptor = descriptors.get(pluginId);
        if (descriptor.state() != PluginState.STARTED) {
            log.warn("Plugin not started: {}", pluginId);
            return;
        }
        
        log.info("Stopping plugin: {}", pluginId);
        
        try {
            plugin.stop();
            descriptor = descriptor.withState(PluginState.STOPPED);
            descriptors.put(pluginId, descriptor);
            log.info("Plugin stopped: {}", pluginId);
        } catch (Exception e) {
            descriptor = descriptor.withState(PluginState.FAILED);
            descriptors.put(pluginId, descriptor);
            throw new PluginException("Failed to stop plugin: " + pluginId, e);
        }
    }
    
    @Override
    public void unloadPlugin(String pluginId) throws PluginException {
        Objects.requireNonNull(pluginId, "Plugin ID cannot be null");
        
        Plugin plugin = plugins.get(pluginId);
        if (plugin == null) {
            throw new PluginException("Plugin not found: " + pluginId);
        }
        
        PluginDescriptor descriptor = descriptors.get(pluginId);
        
        // Stop plugin if running
        if (descriptor.state() == PluginState.STARTED) {
            stopPlugin(pluginId);
        }
        
        log.info("Unloading plugin: {}", pluginId);
        
        try {
            // Unregister extensions
            unregisterPluginExtensions(plugin, plugin.getClass());
            
            // Destroy plugin
            plugin.destroy();
            
            // Remove from maps
            plugins.remove(pluginId);
            descriptors.remove(pluginId);
            
            log.info("Plugin unloaded: {}", pluginId);
        } catch (Exception e) {
            throw new PluginException("Failed to unload plugin: " + pluginId, e);
        }
    }
    
    @Override
    public Optional<Plugin> getPlugin(String pluginId) {
        return Optional.ofNullable(plugins.get(pluginId));
    }
    
    @Override
    public Optional<PluginDescriptor> getPluginDescriptor(String pluginId) {
        return Optional.ofNullable(descriptors.get(pluginId));
    }
    
    @Override
    public List<PluginDescriptor> getAllPlugins() {
        return new ArrayList<>(descriptors.values());
    }
    
    @Override
    public ExtensionRegistry getExtensionRegistry() {
        return extensionRegistry;
    }
    
    @Override
    public synchronized void shutdown() throws PluginException {
        if (!initialized) {
            return;
        }
        
        log.info("Shutting down plugin manager");
        
        // Stop and unload all plugins
        List<String> pluginIds = new ArrayList<>(plugins.keySet());
        for (String pluginId : pluginIds) {
            try {
                unloadPlugin(pluginId);
            } catch (Exception e) {
                log.error("Error unloading plugin during shutdown: {}", pluginId, e);
            }
        }
        
        initialized = false;
    }
    
    /**
     * Registers extensions from a plugin.
     */
    private void registerPluginExtensions(Plugin plugin, Class<?> pluginClass) {
        // Scan for @Extension annotated classes within the plugin
        Class<?>[] innerClasses = pluginClass.getDeclaredClasses();
        for (Class<?> innerClass : innerClasses) {
            if (innerClass.isAnnotationPresent(Extension.class)) {
                Extension annotation = innerClass.getAnnotation(Extension.class);
                try {
                    Object extension = innerClass.getDeclaredConstructor().newInstance();
                    extensionRegistry.registerExtension(
                        annotation.extensionPointId(), 
                        extension, 
                        annotation.priority()
                    );
                    log.debug("Registered extension {} for plugin {}", 
                        innerClass.getSimpleName(), plugin.getMetadata().id());
                } catch (Exception e) {
                    log.error("Failed to register extension: {}", innerClass.getName(), e);
                }
            }
        }
    }
    
    /**
     * Unregisters extensions from a plugin.
     */
    private void unregisterPluginExtensions(Plugin plugin, Class<?> pluginClass) {
        Class<?>[] innerClasses = pluginClass.getDeclaredClasses();
        for (Class<?> innerClass : innerClasses) {
            if (innerClass.isAnnotationPresent(Extension.class)) {
                Extension annotation = innerClass.getAnnotation(Extension.class);
                try {
                    Object extension = innerClass.getDeclaredConstructor().newInstance();
                    extensionRegistry.unregisterExtension(
                        annotation.extensionPointId(), 
                        extension
                    );
                    log.debug("Unregistered extension {} for plugin {}", 
                        innerClass.getSimpleName(), plugin.getMetadata().id());
                } catch (Exception e) {
                    log.error("Failed to unregister extension: {}", innerClass.getName(), e);
                }
            }
        }
    }
}
