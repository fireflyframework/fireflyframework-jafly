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

package org.fireflyframework.plugin.api;

import org.fireflyframework.plugin.model.PluginDescriptor;
import java.util.List;
import java.util.Optional;

/**
 * Main interface for managing plugins.
 * Provides high-level operations for plugin lifecycle management.
 */
public interface PluginManager {
    
    /**
     * Loads and registers a plugin from the classpath.
     * 
     * @param pluginClass the plugin class to load
     * @return the plugin descriptor
     * @throws PluginException if loading fails
     */
    PluginDescriptor loadPlugin(Class<? extends Plugin> pluginClass) throws PluginException;
    
    /**
     * Starts a plugin by its ID.
     * 
     * @param pluginId the plugin ID
     * @throws PluginException if the plugin doesn't exist or start fails
     */
    void startPlugin(String pluginId) throws PluginException;
    
    /**
     * Stops a plugin by its ID.
     * 
     * @param pluginId the plugin ID
     * @throws PluginException if the plugin doesn't exist or stop fails
     */
    void stopPlugin(String pluginId) throws PluginException;
    
    /**
     * Unloads and destroys a plugin.
     * 
     * @param pluginId the plugin ID
     * @throws PluginException if the plugin doesn't exist or destruction fails
     */
    void unloadPlugin(String pluginId) throws PluginException;
    
    /**
     * Gets a plugin by its ID.
     * 
     * @param pluginId the plugin ID
     * @return the plugin, or empty if not found
     */
    Optional<Plugin> getPlugin(String pluginId);
    
    /**
     * Gets a plugin descriptor by plugin ID.
     * 
     * @param pluginId the plugin ID
     * @return the plugin descriptor, or empty if not found
     */
    Optional<PluginDescriptor> getPluginDescriptor(String pluginId);
    
    /**
     * Gets all loaded plugins.
     * 
     * @return list of all plugin descriptors
     */
    List<PluginDescriptor> getAllPlugins();
    
    /**
     * Gets the extension registry.
     * 
     * @return the extension registry
     */
    ExtensionRegistry getExtensionRegistry();
    
    /**
     * Initializes the plugin manager.
     * 
     * @throws PluginException if initialization fails
     */
    void initialize() throws PluginException;
    
    /**
     * Shuts down the plugin manager and all plugins.
     * 
     * @throws PluginException if shutdown fails
     */
    void shutdown() throws PluginException;
}
