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

import org.fireflyframework.plugin.model.PluginMetadata;

/**
 * Core interface that all plugins must implement.
 * Defines the lifecycle methods for a plugin.
 */
public interface Plugin {
    
    /**
     * Gets the metadata for this plugin.
     * 
     * @return the plugin metadata
     */
    PluginMetadata getMetadata();
    
    /**
     * Initializes the plugin.
     * Called once when the plugin is first loaded.
     * 
     * @throws PluginException if initialization fails
     */
    void initialize() throws PluginException;
    
    /**
     * Starts the plugin.
     * Called when the plugin should begin its work.
     * 
     * @throws PluginException if start fails
     */
    void start() throws PluginException;
    
    /**
     * Stops the plugin.
     * Called when the plugin should cease its work.
     * 
     * @throws PluginException if stop fails
     */
    void stop() throws PluginException;
    
    /**
     * Destroys the plugin and releases all resources.
     * Called when the plugin is being uninstalled.
     * 
     * @throws PluginException if destruction fails
     */
    void destroy() throws PluginException;
}
