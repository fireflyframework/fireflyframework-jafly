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

import java.time.Instant;
import java.util.Objects;

/**
 * Complete descriptor of a plugin including its metadata and current state.
 */
public record PluginDescriptor(
    PluginMetadata metadata,
    PluginState state,
    Instant loadedAt,
    Instant lastStateChange
) {
    public PluginDescriptor {
        Objects.requireNonNull(metadata, "Plugin metadata cannot be null");
        Objects.requireNonNull(state, "Plugin state cannot be null");
        Objects.requireNonNull(loadedAt, "Loaded timestamp cannot be null");
        Objects.requireNonNull(lastStateChange, "Last state change timestamp cannot be null");
    }
    
    public String getId() {
        return metadata.id();
    }
    
    public String getName() {
        return metadata.name();
    }
    
    public String getVersion() {
        return metadata.version();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private PluginMetadata metadata;
        private PluginState state = PluginState.LOADED;
        private Instant loadedAt = Instant.now();
        private Instant lastStateChange = Instant.now();
        
        public Builder metadata(PluginMetadata metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public Builder state(PluginState state) {
            this.state = state;
            return this;
        }
        
        public Builder loadedAt(Instant loadedAt) {
            this.loadedAt = loadedAt;
            return this;
        }
        
        public Builder lastStateChange(Instant lastStateChange) {
            this.lastStateChange = lastStateChange;
            return this;
        }
        
        public PluginDescriptor build() {
            return new PluginDescriptor(metadata, state, loadedAt, lastStateChange);
        }
    }
    
    /**
     * Creates a new descriptor with an updated state.
     */
    public PluginDescriptor withState(PluginState newState) {
        return new PluginDescriptor(metadata, newState, loadedAt, Instant.now());
    }
}
