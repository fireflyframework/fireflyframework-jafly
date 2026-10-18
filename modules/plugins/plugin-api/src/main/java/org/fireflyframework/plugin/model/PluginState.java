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

/**
 * Represents the lifecycle state of a plugin.
 */
public enum PluginState {
    /** Plugin is loaded but not yet initialized */
    LOADED,
    
    /** Plugin has been initialized */
    INITIALIZED,
    
    /** Plugin is running */
    STARTED,
    
    /** Plugin has been stopped */
    STOPPED,
    
    /** Plugin encountered an error */
    FAILED
}
