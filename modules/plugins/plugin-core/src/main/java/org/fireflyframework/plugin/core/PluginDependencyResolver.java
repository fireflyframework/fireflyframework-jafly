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

import org.fireflyframework.plugin.api.PluginException;
import org.fireflyframework.plugin.model.PluginDescriptor;

import java.util.*;

/**
 * Resolves plugin dependencies and determines the correct loading order.
 * Uses topological sorting to handle dependency chains.
 */
public class PluginDependencyResolver {
    
    /**
     * Resolves dependencies and returns plugins in the order they should be loaded.
     * Throws an exception if there are circular dependencies or missing dependencies.
     * 
     * @param plugins the plugins to resolve
     * @return ordered list of plugin IDs (dependencies first)
     * @throws PluginException if circular dependencies or missing dependencies are found
     */
    public List<String> resolveDependencies(Collection<PluginDescriptor> plugins) throws PluginException {
        // Build dependency graph
        Map<String, Set<String>> graph = new HashMap<>();
        Map<String, PluginDescriptor> pluginMap = new HashMap<>();
        
        for (PluginDescriptor plugin : plugins) {
            pluginMap.put(plugin.getId(), plugin);
            graph.put(plugin.getId(), new HashSet<>(plugin.metadata().dependencies()));
        }
        
        // Validate all dependencies exist
        for (PluginDescriptor plugin : plugins) {
            for (String depId : plugin.metadata().dependencies()) {
                if (!pluginMap.containsKey(depId)) {
                    throw new PluginException(
                        String.format("Plugin %s depends on %s which is not loaded", 
                            plugin.getId(), depId)
                    );
                }
            }
        }
        
        // Perform topological sort
        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        
        for (String pluginId : graph.keySet()) {
            if (!visited.contains(pluginId)) {
                topologicalSort(pluginId, graph, visited, visiting, result);
            }
        }
        
        return result;
    }
    
    private void topologicalSort(String pluginId, Map<String, Set<String>> graph, 
                                  Set<String> visited, Set<String> visiting, List<String> result) 
            throws PluginException {
        
        if (visiting.contains(pluginId)) {
            throw new PluginException(
                String.format("Circular dependency detected involving plugin: %s", pluginId)
            );
        }
        
        if (visited.contains(pluginId)) {
            return;
        }
        
        visiting.add(pluginId);
        
        // Visit dependencies first
        Set<String> dependencies = graph.get(pluginId);
        if (dependencies != null) {
            for (String depId : dependencies) {
                topologicalSort(depId, graph, visited, visiting, result);
            }
        }
        
        visiting.remove(pluginId);
        visited.add(pluginId);
        result.add(pluginId);
    }
}
