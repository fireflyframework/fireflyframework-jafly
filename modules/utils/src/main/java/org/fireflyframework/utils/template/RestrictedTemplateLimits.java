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


package org.fireflyframework.utils.template;

/** Parsing/output/work limits; cooperative time checks are not hard CPU interruption. */
public record RestrictedTemplateLimits(int templateBytes, int outputBytes, int modelNodes, int depth,
                                       int stringChars, int elementSteps, long renderMillis) {
    public RestrictedTemplateLimits {
        if (templateBytes < 1 || outputBytes < 1 || modelNodes < 1 || depth < 1 || depth > 64
                || stringChars < 1 || elementSteps < 1 || renderMillis < 1 || renderMillis > 60_000) {
            throw new IllegalArgumentException("Restricted template limits must be positive and bounded");
        }
    }
    public static RestrictedTemplateLimits defaults() {
        return new RestrictedTemplateLimits(262_144, 1_048_576, 5_000, 32, 65_536, 10_000, 5_000);
    }
}
