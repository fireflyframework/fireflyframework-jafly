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

package org.fireflyframework.plugin.model;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Metadata information about a plugin.
 */
public record PluginMetadata(
    String id,
    String name,
    String version,
    String description,
    String author,
    Set<String> dependencies
) {
    public PluginMetadata {
        Objects.requireNonNull(id, "Plugin ID cannot be null");
        Objects.requireNonNull(name, "Plugin name cannot be null");
        Objects.requireNonNull(version, "Plugin version cannot be null");
        
        // Make dependencies immutable
        dependencies = dependencies != null ? 
            Set.copyOf(dependencies) : Collections.emptySet();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String name;
        private String version;
        private String description = "";
        private String author = "";
        private Set<String> dependencies = Collections.emptySet();
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder version(String version) {
            this.version = version;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder author(String author) {
            this.author = author;
            return this;
        }
        
        public Builder dependencies(Set<String> dependencies) {
            this.dependencies = dependencies;
            return this;
        }
        
        public PluginMetadata build() {
            return new PluginMetadata(id, name, version, description, author, dependencies);
        }
    }
}
