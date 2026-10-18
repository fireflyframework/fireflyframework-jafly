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

package org.fireflyframework.data.enrichment;

/**
 * Defines the conditions under which an enricher should fall back to an alternative provider.
 *
 * <p>Used with {@link EnricherFallback} to control when fallback chains are triggered.</p>
 *
 * @see EnricherFallback
 * @see FallbackEnrichmentExecutor
 */
public enum FallbackStrategy {

    /**
     * Fallback only when the primary enricher returns an error response.
     *
     * <p>Triggered when {@code response.isSuccess() == false}.</p>
     */
    ON_ERROR,

    /**
     * Fallback when the primary enricher succeeds but returns no enriched fields.
     *
     * <p>Triggered when {@code response.isSuccess() == true} and
     * {@code response.getFieldsEnriched() == 0}.</p>
     */
    ON_EMPTY,

    /**
     * Fallback on either an error response or an empty result.
     *
     * <p>This is the most permissive strategy, combining both {@link #ON_ERROR}
     * and {@link #ON_EMPTY} conditions.</p>
     */
    ON_ERROR_OR_EMPTY
}
