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

package org.fireflyframework.callbacks.core.filters;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;

/**
 * Generic filter request for paginated and filtered queries.
 * 
 * @param <T> The DTO type being filtered
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilterRequest<T> {

    /**
     * Page number (0-indexed).
     */
    @Min(0)
    @Builder.Default
    private Integer page = 0;

    /**
     * Page size.
     */
    @Min(1)
    @Builder.Default
    private Integer size = 20;

    /**
     * Sort criteria (e.g., ["name,asc", "createdAt,desc"]).
     */
    private List<String> sort;

    /**
     * Filter criteria as key-value pairs.
     * Keys are field names, values are the filter values.
     */
    private Map<String, Object> filters;

    /**
     * Search term for full-text search (optional).
     */
    private String search;
}

