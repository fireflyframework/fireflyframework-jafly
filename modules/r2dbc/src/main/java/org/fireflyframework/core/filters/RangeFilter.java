package org.fireflyframework.core.filters;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "Represents range filters for numeric fields")
public class RangeFilter {
    @Schema(description = "Map of field names to their range criteria")
    private Map<String, Range<?>> ranges = new HashMap<>();

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Schema(description = "Represents a range with from and to values")
    public static class Range<R> {
        @Schema(description = "Start value of the range")
        private R from;

        @Schema(description = "End value of the range")
        private R to;
    }
}