package org.fireflyframework.core.filters;

import org.fireflyframework.core.queries.PaginationRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import org.fireflyframework.core.queries.PaginationUtils;
import org.fireflyframework.utils.annotations.FilterableId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Slf4j
public class FilterUtils {
    private static R2dbcEntityTemplate entityTemplate;

    // Cache for field reflection to improve performance
    private static final ConcurrentHashMap<Class<?>, List<Field>> FIELDS_CACHE = new ConcurrentHashMap<>();

    /**
     * Configuration options for filtering behavior
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilterOptions {
        /**
         * Whether string comparisons should be case-insensitive
         */
        @Builder.Default
        private boolean caseInsensitiveStrings = false;

        /**
         * Whether to include inherited fields in filtering
         */
        @Builder.Default
        private boolean includeInheritedFields = false;

        /**
         * Default filter options with default values
         */
        public static FilterOptions defaults() {
            return FilterOptions.builder().build();
        }
    }

    // Initializes the static R2dbcEntityTemplate instance
    public static void initializeTemplate(R2dbcEntityTemplate template) {
        entityTemplate = template;
    }

    // Creates a new GenericFilter object for handling entity filtering
    public static <F, E, D> GenericFilter<F, E, D> createFilter(Class<E> entityClass, Function<E, D> mapper) {
        // Ensures the R2dbcEntityTemplate has been initialized before use
        if (entityTemplate == null) {
            throw new IllegalStateException("R2dbcEntityTemplate not initialized. Call FilterUtils.initializeTemplate first.");
        }
        return new GenericFilter<>(entityClass, mapper, FilterOptions.defaults());
    }

    // Creates a new GenericFilter object with custom filter options
    public static <F, E, D> GenericFilter<F, E, D> createFilter(Class<E> entityClass, Function<E, D> mapper, FilterOptions options) {
        // Ensures the R2dbcEntityTemplate has been initialized before use
        if (entityTemplate == null) {
            throw new IllegalStateException("R2dbcEntityTemplate not initialized. Call FilterUtils.initializeTemplate first.");
        }
        return new GenericFilter<>(entityClass, mapper, options);
    }

    // Generic filter class for handling filtering for a specified entity type
    public static class GenericFilter<F, E, D> {
        private final Class<E> entityClass; // The entity class type
        private final Function<E, D> mapper; // Mapper to transform entity to another type
        private final FilterOptions options; // Options for filtering behavior

        // Constructs the filter with entity class, mapper function, and options
        private GenericFilter(Class<E> entityClass, Function<E, D> mapper, FilterOptions options) {
            this.entityClass = entityClass;
            this.mapper = mapper;
            this.options = options;
        }

        // Main filtering method to handle filter requests
        public Mono<PaginationResponse<D>> filter(FilterRequest<?> filterRequest) {
            Criteria criteria = buildCriteria(filterRequest); // Build Criteria from the filter request

            log.info("pagination request is {}", filterRequest.getPagination());
            // Use default pagination if no pagination provided in request
            PaginationRequest paginationRequest = filterRequest.getPagination() != null ?
                    filterRequest.getPagination() :
                    new PaginationRequest();

            // Leverage PaginationUtils to handle query paging and mapping
            return PaginationUtils.paginateQuery(
                    paginationRequest,
                    mapper,
                    pageable -> fetchPagedData(criteria, pageable), // Fetch data for the current page
                    () -> countTotalElements(criteria) // Count the total number of elements
            );
        }

        /**
         * Returns the raw criteria without executing the query.
         * This is useful for custom query building.
         */
        public Criteria getCriteria(FilterRequest<?> filterRequest) {
            return buildCriteria(filterRequest);
        }

        // Fetches paged data based on provided criteria and pagination details
        private Flux<E> fetchPagedData(Criteria criteria, Pageable pageable) {
            Query query = Query.query(criteria)
                    .with(pageable); // Build the query with pagination

            return entityTemplate.select(entityClass)
                    .matching(query) // Execute the query
                    .all(); // Return all matching results
        }

        // Counts the total number of elements matching the criteria
        private Mono<Long> countTotalElements(Criteria criteria) {
            return entityTemplate.select(entityClass)
                    .matching(Query.query(criteria)) // Execute a count query on the criteria
                    .count(); // Return the count
        }

        // Builds a Criteria object based on the filters and range filters from the request
        private Criteria buildCriteria(FilterRequest<?> filterRequest) {
            List<Criteria> criteriaList = new ArrayList<>();

            // Process regular filters if present
            if (filterRequest.getFilters() != null) {
                criteriaList.addAll(processRegularFilters(filterRequest.getFilters()));
            }

            // Process range filters if present
            if (filterRequest.getRangeFilters() != null) {
                criteriaList.addAll(processRangeFilters(filterRequest.getRangeFilters()));
            }

            // Combine all criteria into a single Criteria or return empty if no filters
            return criteriaList.isEmpty() ?
                    Criteria.empty() :
                    Criteria.from(criteriaList);
        }

        // Processes regular filters into a list of Criteria objects
        private List<Criteria> processRegularFilters(Object filters) {
            List<Criteria> criteriaList = new ArrayList<>();

            // Get all fields (including inherited if option is enabled)
            List<Field> fields = getAllFields(filters.getClass());

            for (Field field : fields) {
                field.setAccessible(true); // Make field accessible for reflection
                try {
                    Object value = field.get(filters); // Get the value of the field
                    String fieldName = field.getName();

                    // Check for special filter values (NULL, NOT_NULL)
                    Object specialFilter = FilterRequest.getSpecialFilter(filters, fieldName);

                    // Handle null values - if field is marked with NULL_VALUE
                    if (specialFilter == FilterRequest.NULL_VALUE) {
                        criteriaList.add(Criteria.where(fieldName).isNull());
                        continue;
                    }

                    // Handle not-null values - if field is marked with NOT_NULL_VALUE
                    if (specialFilter == FilterRequest.NOT_NULL_VALUE) {
                        criteriaList.add(Criteria.where(fieldName).isNotNull());
                        continue;
                    }

                    if (value != null) { // Process only non-null values
                        // Skip excluded ID fields
                        if (isExcludableIdField(field)) {
                            continue;
                        }

                        // Exact match for ID fields
                        if (isAnyIdField(field)) {
                            criteriaList.add(Criteria.where(field.getName()).is(value));
                        }
                        // Handle Collection/Array fields
                        else if (value instanceof Collection) {
                            Collection<?> collection = (Collection<?>) value;
                            if (!collection.isEmpty()) {
                                criteriaList.add(Criteria.where(field.getName()).in(collection));
                            }
                        }
                        // Handle array fields
                        else if (value.getClass().isArray()) {
                            Object[] array = (Object[]) value;
                            if (array.length > 0) {
                                criteriaList.add(Criteria.where(field.getName()).in(array));
                            }
                        }
                        // Perform LIKE query for non-empty String fields
                        else if (value instanceof String && !((String) value).isEmpty()) {
                            String stringValue = (String) value;
                            if (options.isCaseInsensitiveStrings()) {
                                // Case-insensitive LIKE query
                                criteriaList.add(Criteria.where(field.getName()).like("%" + stringValue.toLowerCase() + "%"));
                            } else {
                                // Case-sensitive LIKE query (default behavior)
                                criteriaList.add(Criteria.where(field.getName()).like("%" + stringValue + "%"));
                            }
                        }
                        // Handle Enum types
                        else if (field.getType().isEnum() || value.getClass().isEnum()) {
                            // For Enum types, we use the exact match with the Enum's name or the Enum itself
                            // depending on how the database stores the enum (as string or as ordinal)
                            if (value instanceof String) {
                                // If the value is already a String, use it directly
                                criteriaList.add(Criteria.where(field.getName()).is(value));
                            } else {
                                // If the value is an Enum, use its name for matching
                                criteriaList.add(Criteria.where(field.getName()).is(((Enum<?>) value).name()));
                            }
                        }
                        // Exact match for other types of fields
                        else if (!(value instanceof String)) {
                            criteriaList.add(Criteria.where(field.getName()).is(value));
                        }
                    }
                } catch (IllegalAccessException e) {
                    log.error("Error accessing field: {}", field.getName(), e); // Log error for inaccessible fields
                } catch (Exception e) {
                    log.error("Unexpected error processing filter field {}: {}", field.getName(), e.getMessage(), e);
                }
            }

            return criteriaList;
        }

        /**
         * Gets all fields for a class, using cache for performance.
         * If includeInheritedFields option is enabled, includes fields from superclasses.
         */
        private List<Field> getAllFields(Class<?> clazz) {
            // Check cache first
            return FIELDS_CACHE.computeIfAbsent(clazz, cls -> {
                List<Field> allFields = new ArrayList<>();

                // Add declared fields
                allFields.addAll(Arrays.asList(cls.getDeclaredFields()));

                // Add inherited fields if option is enabled
                if (options.isIncludeInheritedFields()) {
                    Class<?> superClass = cls.getSuperclass();
                    while (superClass != null && superClass != Object.class) {
                        allFields.addAll(Arrays.asList(superClass.getDeclaredFields()));
                        superClass = superClass.getSuperclass();
                    }
                }

                return allFields;
            });
        }

        // Processes range filters into a list of Criteria objects
        private List<Criteria> processRangeFilters(RangeFilter rangeFilters) {
            List<Criteria> criteriaList = new ArrayList<>();

            rangeFilters.getRanges().forEach((fieldName, range) -> {
                // Skip ID fields for range filters
                if (isAnyIdField(fieldName, entityClass)) {
                    log.debug("Skipping range filter for ID field: {}", fieldName);
                    return;
                }

                // Add BETWEEN criteria if both bounds are present
                if (range.getFrom() != null && range.getTo() != null) {
                    criteriaList.add(
                            Criteria.where(fieldName)
                                    .between(range.getFrom(), range.getTo())
                    );
                }
                // Add GREATER-THAN-OR-EQUAL criteria if only the lower bound is present
                else if (range.getFrom() != null) {
                    criteriaList.add(
                            Criteria.where(fieldName)
                                    .greaterThanOrEquals(range.getFrom())
                    );
                }
                // Add LESS-THAN-OR-EQUAL criteria if only the upper bound is present
                else if (range.getTo() != null) {
                    criteriaList.add(
                            Criteria.where(fieldName)
                                    .lessThanOrEquals(range.getTo())
                    );
                }
            });

            return criteriaList;
        }

        // Checks if a field is an ID field (by annotation, convention, or name)
        private boolean isAnyIdField(Field field) {
            return field.isAnnotationPresent(Id.class) || // Annotation-based ID
                    field.getName().endsWith("Id") || // Named convention (ends with "Id")
                    "id".equals(field.getName()); // Explicit name "id"
        }

        // Checks if a String field name corresponds to an ID field in the entity class
        private boolean isAnyIdField(String fieldName, Class<?> entityClass) {
            try {
                Field field = entityClass.getDeclaredField(fieldName); // Look up the field by name
                return isAnyIdField(field); // Check if it is an ID field
            } catch (NoSuchFieldException e) {
                return false; // Return false if field does not exist
            }
        }

        // Checks if an ID field is excluded from filtering
        private boolean isExcludableIdField(Field field) {
            return isAnyIdField(field) && !field.isAnnotationPresent(FilterableId.class); // Allow only if annotated as filterable
        }
    }
}
