package org.fireflyframework.core.queries;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * A generic class representing a paginated response, typically used for returning
 * a portion of data along with pagination details in API responses.
 *
 * @param <T> the type of elements contained within the paginated content
 *            <p>
 *            This class contains the following attributes:
 *            - A list of elements (`content`) representing the data for the current page.
 *            - The total number of elements across all pages (`totalElements`).
 *            - The total number of pages (`totalPages`) based on the data size and page size.
 *            - The current page number (`currentPage`), typically zero-based.
 *            <p>
 *            This structure is useful for encapsulating paginated responses and providing
 *            necessary metadata for navigation through paginated data.
 */
@Data
@AllArgsConstructor
@Builder
@Schema(description = "Represents a paginated response containing a list of items and pagination metadata.")
public class PaginationResponse<T> {

    @Schema(description = "The list of items for the current page.")
    private List<T> content;

    @Schema(description = "The total number of elements across all pages.")
    private long totalElements;

    @Schema(description = "The total number of pages based on the data size and page size.")
    private int totalPages;

    @Schema(description = "The current page number, typically zero-based.")
    private int currentPage;
}