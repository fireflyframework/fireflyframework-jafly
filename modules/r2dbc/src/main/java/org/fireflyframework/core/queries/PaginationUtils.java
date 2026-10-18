package org.fireflyframework.core.queries;

import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
public class PaginationUtils {

    /**
     * Generic method to handle pagination for any entity type and DTO
     *
     * @param paginationRequest The pagination request containing page info
     * @param mapper The mapper function to convert from entity to DTO
     * @param findAllFunction The function to fetch all entities with pagination
     * @param countFunction The function to get total count of entities
     * @param <E> Entity type
     * @param <D> DTO type
     * @return A Mono of PaginationResponse containing the paginated DTOs
     */
    public static <E, D> Mono<PaginationResponse<D>> paginateQuery(
            PaginationRequest paginationRequest,
            Function<E, D> mapper,
            Function<Pageable, Flux<E>> findAllFunction,
            Supplier<Mono<Long>> countFunction
    ) {
        // Convert PaginationRequest to Pageable
        Pageable pageable = paginationRequest.toPageable();

        // Fetch paginated entities using the provided function
        Flux<E> entities = findAllFunction.apply(pageable);

        // Fetch total count using the provided function
        Mono<Long> count = countFunction.get();

        // Transform entities to DTOs and create pagination response
        return entities
                .map(mapper)
                .collectList()
                .zipWith(count)
                .map(tuple -> {
                    List<D> dtos = tuple.getT1();
                    long total = tuple.getT2();

                    return new PaginationResponse<>(
                            dtos,
                            total,
                            (int) Math.ceil((double) total / pageable.getPageSize()),
                            pageable.getPageNumber()
                    );
                });
    }

}