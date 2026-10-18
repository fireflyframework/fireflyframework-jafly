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

import java.util.List;

/**
 * Generic pagination response wrapper.
 * 
 * @param <T> The DTO type in the response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaginationResponse<T> {

    /**
     * The list of items in the current page.
     */
    private List<T> content;

    /**
     * Current page number (0-indexed).
     */
    private Integer page;

    /**
     * Page size.
     */
    private Integer size;

    /**
     * Total number of elements across all pages.
     */
    private Long totalElements;

    /**
     * Total number of pages.
     */
    private Integer totalPages;

    /**
     * Whether this is the first page.
     */
    private Boolean first;

    /**
     * Whether this is the last page.
     */
    private Boolean last;

    /**
     * Number of elements in the current page.
     */
    private Integer numberOfElements;

    /**
     * Whether the page is empty.
     */
    private Boolean empty;
}

