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

import java.util.List;
import java.util.Optional;

/**
 * Registry for managing extension points and their implementations.
 */
public interface ExtensionRegistry {
    
    /**
     * Registers an extension point.
     * 
     * @param <T> the extension point type
     * @param extensionPointId the unique ID of the extension point
     * @param extensionPointClass the extension point interface/class
     */
    <T> void registerExtensionPoint(String extensionPointId, Class<T> extensionPointClass);
    
    /**
     * Registers an extension implementation.
     * 
     * @param <T> the extension type
     * @param extensionPointId the extension point ID
     * @param extension the extension implementation
     * @param priority the priority (higher values = higher priority)
     */
    <T> void registerExtension(String extensionPointId, T extension, int priority);
    
    /**
     * Registers an extension with default priority (0).
     * 
     * @param <T> the extension type
     * @param extensionPointId the extension point ID
     * @param extension the extension implementation
     */
    default <T> void registerExtension(String extensionPointId, T extension) {
        registerExtension(extensionPointId, extension, 0);
    }
    
    /**
     * Unregisters an extension.
     * 
     * @param <T> the extension type
     * @param extensionPointId the extension point ID
     * @param extension the extension to remove
     */
    <T> void unregisterExtension(String extensionPointId, T extension);
    
    /**
     * Gets all extensions for an extension point, ordered by priority (highest first).
     * 
     * @param <T> the extension type
     * @param extensionPointId the extension point ID
     * @return list of extensions, or empty list if none found
     */
    <T> List<T> getExtensions(String extensionPointId);
    
    /**
     * Gets the highest priority extension for an extension point.
     * 
     * @param <T> the extension type
     * @param extensionPointId the extension point ID
     * @return the highest priority extension, or empty if none found
     */
    <T> Optional<T> getExtension(String extensionPointId);
    
    /**
     * Gets all registered extension point IDs.
     * 
     * @return list of extension point IDs
     */
    List<String> getExtensionPointIds();
    
    /**
     * Checks if an extension point is registered.
     * 
     * @param extensionPointId the extension point ID
     * @return true if registered, false otherwise
     */
    boolean hasExtensionPoint(String extensionPointId);
}
