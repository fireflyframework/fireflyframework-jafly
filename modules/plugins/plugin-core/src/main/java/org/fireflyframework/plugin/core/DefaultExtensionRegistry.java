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

import org.fireflyframework.plugin.api.ExtensionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe implementation of ExtensionRegistry.
 * Uses concurrent data structures for safe concurrent access.
 */
public class DefaultExtensionRegistry implements ExtensionRegistry {
    
    private static final Logger log = LoggerFactory.getLogger(DefaultExtensionRegistry.class);
    
    private final Map<String, Class<?>> extensionPoints = new ConcurrentHashMap<>();
    private final Map<String, List<ExtensionEntry<?>>> extensions = new ConcurrentHashMap<>();
    
    @Override
    public <T> void registerExtensionPoint(String extensionPointId, Class<T> extensionPointClass) {
        Objects.requireNonNull(extensionPointId, "Extension point ID cannot be null");
        Objects.requireNonNull(extensionPointClass, "Extension point class cannot be null");
        
        log.info("Registering extension point: {} -> {}", extensionPointId, extensionPointClass.getName());
        extensionPoints.put(extensionPointId, extensionPointClass);
        extensions.putIfAbsent(extensionPointId, new CopyOnWriteArrayList<>());
    }
    
    @Override
    public <T> void registerExtension(String extensionPointId, T extension, int priority) {
        Objects.requireNonNull(extensionPointId, "Extension point ID cannot be null");
        Objects.requireNonNull(extension, "Extension cannot be null");
        
        Class<?> extensionPointClass = extensionPoints.get(extensionPointId);
        if (extensionPointClass == null) {
            throw new IllegalArgumentException("Extension point not registered: " + extensionPointId);
        }
        
        if (!extensionPointClass.isInstance(extension)) {
            throw new IllegalArgumentException(
                String.format("Extension %s does not implement extension point %s", 
                    extension.getClass().getName(), extensionPointClass.getName())
            );
        }
        
        log.info("Registering extension {} for extension point {} with priority {}", 
            extension.getClass().getSimpleName(), extensionPointId, priority);
        
        List<ExtensionEntry<?>> extensionList = extensions.computeIfAbsent(
            extensionPointId, k -> new CopyOnWriteArrayList<>()
        );
        
        @SuppressWarnings("unchecked")
        ExtensionEntry<T> entry = new ExtensionEntry<>(extension, priority);
        extensionList.add(entry);
        
        // Sort by priority (highest first)
        extensionList.sort((a, b) -> Integer.compare(b.priority, a.priority));
    }
    
    @Override
    public <T> void unregisterExtension(String extensionPointId, T extension) {
        Objects.requireNonNull(extensionPointId, "Extension point ID cannot be null");
        Objects.requireNonNull(extension, "Extension cannot be null");
        
        log.info("Unregistering extension {} from extension point {}", 
            extension.getClass().getSimpleName(), extensionPointId);
        
        List<ExtensionEntry<?>> extensionList = extensions.get(extensionPointId);
        if (extensionList != null) {
            extensionList.removeIf(entry -> entry.extension.equals(extension));
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> getExtensions(String extensionPointId) {
        List<ExtensionEntry<?>> extensionList = extensions.get(extensionPointId);
        if (extensionList == null || extensionList.isEmpty()) {
            return Collections.emptyList();
        }
        
        return extensionList.stream()
            .map(entry -> (T) entry.extension)
            .toList();
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getExtension(String extensionPointId) {
        List<ExtensionEntry<?>> extensionList = extensions.get(extensionPointId);
        if (extensionList == null || extensionList.isEmpty()) {
            return Optional.empty();
        }
        
        return Optional.of((T) extensionList.get(0).extension);
    }
    
    @Override
    public List<String> getExtensionPointIds() {
        return new ArrayList<>(extensionPoints.keySet());
    }
    
    @Override
    public boolean hasExtensionPoint(String extensionPointId) {
        return extensionPoints.containsKey(extensionPointId);
    }
    
    /**
     * Internal record to store extension with its priority.
     */
    private static class ExtensionEntry<T> {
        final T extension;
        final int priority;
        
        ExtensionEntry(T extension, int priority) {
            this.extension = extension;
            this.priority = priority;
        }
    }
}
