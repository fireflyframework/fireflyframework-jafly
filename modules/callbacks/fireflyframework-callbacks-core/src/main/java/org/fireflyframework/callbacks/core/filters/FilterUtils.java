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

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Utility class for creating filters and pagination.
 */
@Slf4j
public class FilterUtils {

    /**
     * Creates a filter that can be used to filter and paginate entities.
     *
     * @param <E>        Entity type
     * @param <D>        DTO type
     * @param entityClass Entity class
     * @param mapper     Function to map entity to DTO
     * @return A Filter instance
     */
    public static <E, D> Filter<E, D> createFilter(
            Class<E> entityClass,
            Function<E, D> mapper) {
        return new Filter<>(entityClass, mapper);
    }

    /**
     * Filter class that handles filtering and pagination logic.
     *
     * @param <E> Entity type
     * @param <D> DTO type
     */
    @Slf4j
    public static class Filter<E, D> {
        private final Class<E> entityClass;
        private final Function<E, D> mapper;
        private R2dbcRepository<E, ?> repository;

        public Filter(Class<E> entityClass, Function<E, D> mapper) {
            this.entityClass = entityClass;
            this.mapper = mapper;
        }

        /**
         * Sets the repository to use for querying.
         */
        public Filter<E, D> withRepository(R2dbcRepository<E, ?> repository) {
            this.repository = repository;
            return this;
        }

        /**
         * Executes the filter request and returns a paginated response.
         *
         * @param filterRequest The filter request
         * @return Mono of PaginationResponse
         */
        public Mono<PaginationResponse<D>> filter(FilterRequest<D> filterRequest) {
            if (repository == null) {
                return Mono.error(new IllegalStateException("Repository not set. Call withRepository() first."));
            }

            Pageable pageable = createPageable(filterRequest);

            return repository.count()
                    .flatMap(totalElements -> {
                        if (totalElements == 0) {
                            return Mono.just(createEmptyResponse(filterRequest));
                        }

                        return repository.findAll()
                                .skip((long) pageable.getPageNumber() * pageable.getPageSize())
                                .take(pageable.getPageSize())
                                .map(mapper)
                                .collectList()
                                .map(content -> createResponse(content, filterRequest, totalElements));
                    });
        }

        /**
         * Creates a Pageable from the filter request.
         */
        private Pageable createPageable(FilterRequest<D> filterRequest) {
            int page = filterRequest.getPage() != null ? filterRequest.getPage() : 0;
            int size = filterRequest.getSize() != null ? filterRequest.getSize() : 20;

            Sort sort = Sort.unsorted();
            if (filterRequest.getSort() != null && !filterRequest.getSort().isEmpty()) {
                List<Sort.Order> orders = new ArrayList<>();
                for (String sortStr : filterRequest.getSort()) {
                    String[] parts = sortStr.split(",");
                    if (parts.length == 2) {
                        String property = parts[0].trim();
                        String direction = parts[1].trim();
                        orders.add(new Sort.Order(
                                "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC,
                                property
                        ));
                    } else if (parts.length == 1) {
                        orders.add(new Sort.Order(Sort.Direction.ASC, parts[0].trim()));
                    }
                }
                if (!orders.isEmpty()) {
                    sort = Sort.by(orders);
                }
            }

            return PageRequest.of(page, size, sort);
        }

        /**
         * Creates a pagination response from the content and metadata.
         */
        private PaginationResponse<D> createResponse(
                List<D> content,
                FilterRequest<D> filterRequest,
                Long totalElements) {

            int page = filterRequest.getPage() != null ? filterRequest.getPage() : 0;
            int size = filterRequest.getSize() != null ? filterRequest.getSize() : 20;
            int totalPages = (int) Math.ceil((double) totalElements / size);

            return PaginationResponse.<D>builder()
                    .content(content)
                    .page(page)
                    .size(size)
                    .totalElements(totalElements)
                    .totalPages(totalPages)
                    .first(page == 0)
                    .last(page >= totalPages - 1)
                    .numberOfElements(content.size())
                    .empty(content.isEmpty())
                    .build();
        }

        /**
         * Creates an empty pagination response.
         */
        private PaginationResponse<D> createEmptyResponse(FilterRequest<D> filterRequest) {
            int page = filterRequest.getPage() != null ? filterRequest.getPage() : 0;
            int size = filterRequest.getSize() != null ? filterRequest.getSize() : 20;

            return PaginationResponse.<D>builder()
                    .content(List.of())
                    .page(page)
                    .size(size)
                    .totalElements(0L)
                    .totalPages(0)
                    .first(true)
                    .last(true)
                    .numberOfElements(0)
                    .empty(true)
                    .build();
        }
    }
}

