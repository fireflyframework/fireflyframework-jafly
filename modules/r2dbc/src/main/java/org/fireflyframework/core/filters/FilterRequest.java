package org.fireflyframework.core.filters;

import org.fireflyframework.core.queries.PaginationRequest;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Generic filter request that includes both filter criteria and pagination")
public class FilterRequest<T> {
    /**
     * Special marker object to indicate a field should be filtered for NULL values.
     * Usage: FilterRequest.setNullFilter(filters, "fieldName");
     */
    public static final Object NULL_VALUE = new Object();

    /**
     * Special marker object to indicate a field should be filtered for NOT NULL values.
     * Usage: FilterRequest.setNotNullFilter(filters, "fieldName");
     */
    public static final Object NOT_NULL_VALUE = new Object();

    /**
     * Map to store special filter values (NULL, NOT_NULL) for fields
     */
    private static final Map<Object, Map<String, Object>> SPECIAL_FILTERS = new ConcurrentHashMap<>();

    /**
     * Sets a field to be filtered for NULL values
     * @param filter The filter object
     * @param fieldName The name of the field to filter for NULL
     */
    public static void setNullFilter(Object filter, String fieldName) {
        SPECIAL_FILTERS.computeIfAbsent(filter, k -> new HashMap<>())
                .put(fieldName, NULL_VALUE);
    }

    /**
     * Sets a field to be filtered for NOT NULL values
     * @param filter The filter object
     * @param fieldName The name of the field to filter for NOT NULL
     */
    public static void setNotNullFilter(Object filter, String fieldName) {
        SPECIAL_FILTERS.computeIfAbsent(filter, k -> new HashMap<>())
                .put(fieldName, NOT_NULL_VALUE);
    }

    /**
     * Gets the special filter value for a field if it exists
     * @param filter The filter object
     * @param fieldName The name of the field
     * @return The special filter value or null if none exists
     */
    public static Object getSpecialFilter(Object filter, String fieldName) {
        Map<String, Object> specialFilters = SPECIAL_FILTERS.get(filter);
        return specialFilters != null ? specialFilters.get(fieldName) : null;
    }

    @Schema(description = "Filter criteria")
    private T filters;

    @Schema(description = "Range filters for numeric fields")
    private RangeFilter rangeFilters;

    @Schema(description = "Pagination and sorting parameters", requiredMode = Schema.RequiredMode.REQUIRED)
    private PaginationRequest pagination;

    @Schema(description = "Filter options for customizing filter behavior")
    private FilterUtils.FilterOptions options;
}
