package org.fireflyframework.core.queries;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Represents a pagination request used for retrieving paginated results.
 * This class provides the page number and page size parameters necessary
 * for defining the segments of data to be requested.
 * <p>
 * By default, the {@code pageNumber} is set to 0 and the {@code pageSize} is set to 10.
 * These defaults can be overridden by explicitly setting the values.
 * <p>
 * This class includes a method to convert the pagination request into a {@link Pageable} object,
 * which is commonly used in Spring Data repositories to handle paging.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "Represents a pagination request for retrieving paginated results, including page number, size, sort field, and direction.")
public class PaginationRequest {

    @Schema(description = "The zero-based page number to retrieve.", example = "0", defaultValue = "0")
    private int pageNumber = 0;

    @Schema(description = "The number of items per page.", example = "10", defaultValue = "10")
    private int pageSize = 10;

    @Schema(description = "The field to sort the results by.", example = "name")
    private String sortBy;

    @Schema(description = "The direction of sorting, either ASC or DESC.", example = "DESC", defaultValue = "DESC")
    private String sortDirection = "DESC";

    public Pageable toPageable() {
        if (sortBy == null || sortBy.isEmpty()) {
            return PageRequest.of(pageNumber, pageSize);
        }
        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortBy);
        return PageRequest.of(pageNumber, pageSize, sort);
    }
}