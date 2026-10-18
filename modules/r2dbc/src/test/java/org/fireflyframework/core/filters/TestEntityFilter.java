package org.fireflyframework.core.filters;

import org.fireflyframework.utils.annotations.FilterableId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Filter class for TestEntity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestEntityFilter {
    private UUID id;

    @FilterableId
    private UUID filterableId;

    private String name;

    private Integer count;

    private Boolean active;

    private LocalDateTime createdDate;

    private List<String> tags;

    private Set<Long> relatedIds;

    private String[] stringArray;
}