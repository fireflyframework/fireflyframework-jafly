package org.fireflyframework.core.filters;

import org.fireflyframework.utils.annotations.FilterableId;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.annotations.ParameterObject;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.data.annotation.Id;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code FilterParameterCustomizer} customizes the OpenAPI documentation for endpoints
 * that accept a {@code FilterRequest<T>} parameter (via {@literal @ParameterObject} or
 * {@literal @ModelAttribute}).
 *
 * <p>It generates nested query parameters such as:</p>
 *
 * <ul>
 *   <li>{@code pagination.pageNumber}, {@code pagination.pageSize}, {@code pagination.sortBy}, {@code pagination.sortDirection}</li>
 *   <li>{@code filters.<fieldName>} for exact filtering</li>
 *   <li>{@code rangeFilters.ranges[<fieldName>].from/to} for range filtering of numeric/date fields</li>
 *   <li>{@code options.caseInsensitiveStrings=true} for case-insensitive string filtering</li>
 *   <li>{@code options.includeInheritedFields=true} to include fields from parent classes</li>
 * </ul>
 *
 * <p><strong>ID-like fields</strong> (e.g., ending with {@code "Id"} or exactly named {@code "id"})
 * never get range filters, even if they are annotated with {@code @FilterableId}. When annotated with
 * {@code @FilterableId}, they appear only as exact filters. Otherwise, ID fields are skipped entirely.</p>
 *
 * <p>Example URL:</p>
 * <pre>{@code
 * GET /accounts?
 *     pagination.pageNumber=0&
 *     pagination.pageSize=10&
 *     filters.status=ACTIVE&
 *     filters.tags=tag1,tag2&
 *     rangeFilters.ranges[balance].from=1000&
 *     rangeFilters.ranges[balance].to=5000&
 *     options.caseInsensitiveStrings=true
 * }</pre>
 *
 * <p>Special filtering capabilities:</p>
 * <ul>
 *   <li><strong>NULL/NOT NULL filtering</strong>: Use {@code FilterRequest.setNullFilter(filter, "fieldName")} or
 *       {@code FilterRequest.setNotNullFilter(filter, "fieldName")} in your code</li>
 *   <li><strong>Collection/Array filtering</strong>: Fields of type Collection or array will be filtered using the IN operator</li>
 * </ul>
 */
@Component
public class FilterParameterCustomizer implements OperationCustomizer {

    /**
     * Default constructor.
     * <p>Avoids Javadoc warnings about missing comments.</p>
     */
    public FilterParameterCustomizer() {
        // Default constructor intentionally left blank
    }

    /**
     * Customizes the OpenAPI {@code Operation} by adding parameters for filtering and pagination.
     *
     * @param operation     The OpenAPI {@code Operation} to customize
     * @param handlerMethod The Spring {@code HandlerMethod} handling the request
     * @return The updated {@code Operation} with new query parameters
     */
    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        // Preserve any existing parameters that are not query-type (e.g., path, header)
        List<Parameter> existingParameters = operation.getParameters() != null
                ? operation.getParameters()
                : new ArrayList<>();

        List<Parameter> preservedParameters = existingParameters.stream()
                .filter(param -> !"query".equals(param.getIn()))
                .collect(Collectors.toList());

        // Check if this method has a FilterRequest<T> parameter with @ParameterObject or @ModelAttribute
        Arrays.stream(handlerMethod.getMethodParameters())
                .filter(param ->
                        FilterRequest.class.isAssignableFrom(param.getParameterType()) &&
                                (param.hasParameterAnnotation(ParameterObject.class)
                                        || param.hasParameterAnnotation(ModelAttribute.class)))
                .findFirst()
                .ifPresent(param -> {
                    // Extract the <T> from FilterRequest<T>, e.g., AccountDTO
                    Class<?> dtoClass = extractDtoClass(param.getGenericParameterType());
                    if (dtoClass != null) {
                        // Copy preserved parameters and add filters/pagination
                        List<Parameter> filterParameters = new ArrayList<>(preservedParameters);
                        addFilterParameters(filterParameters, dtoClass);
                        operation.setParameters(filterParameters);
                    }
                });

        return operation;
    }

    /**
     * Extracts the DTO class <T> from a {@code FilterRequest<T>} type.
     *
     * @param type The generic parameter type
     * @return The Class of <T> if found, or null otherwise
     */
    private Class<?> extractDtoClass(java.lang.reflect.Type type) {
        if (type instanceof ParameterizedType) {
            ParameterizedType paramType = (ParameterizedType) type;
            return (Class<?>) paramType.getActualTypeArguments()[0];
        }
        return null;
    }

    /**
     * Adds pagination parameters and generates filter parameters (exact and range) for each field.
     *
     * @param parameters The list of parameters to which new ones will be appended
     * @param dtoClass   The DTO class that contains fields to filter on
     */
    private void addFilterParameters(List<Parameter> parameters, Class<?> dtoClass) {
        // --- Pagination (nested under "pagination.*") ---
        parameters.add(createParameter("pagination.pageNumber", integerSchema(),
                "Page number (0-based)", "0"));
        parameters.add(createParameter("pagination.pageSize", integerSchema(),
                "Number of items per page", "10"));
        parameters.add(createParameter("pagination.sortBy", stringSchema(),
                "Field to sort by", null));
        parameters.add(createParameter("pagination.sortDirection", stringSchema(),
                "Sort direction (ASC or DESC)", "DESC"));

        // --- Filter Options ---
        parameters.add(createParameter("options.caseInsensitiveStrings", new BooleanSchema(),
                "Enable case-insensitive string filtering", "false"));
        parameters.add(createParameter("options.includeInheritedFields", new BooleanSchema(),
                "Include fields from parent classes", "false"));

        // --- Filters (exact) and Range Filters ---
        // Get fields from the class and its superclasses if needed
        List<Field> allFields = new ArrayList<>();
        getAllFields(dtoClass, allFields);

        for (Field field : allFields) {
            if (!shouldIncludeField(field)) {
                continue;
            }

            if (isIdField(field)) {
                // It's recognized as an ID field (annotation @Id, or named "id", or ends with "Id" without @FilterableId)
                // => Exclude entirely UNLESS it has @FilterableId
                if (field.isAnnotationPresent(FilterableId.class)) {
                    // Add an exact filter parameter
                    parameters.add(createParameter(
                            "filters." + field.getName(),
                            createSchemaForType(field.getType()),
                            "Exact filter for " + camelCaseToWords(field.getName()),
                            null
                    ));
                }
                // No range filters for ID fields (filterable or not)
            } else {
                // Normal field => add an exact filter
                parameters.add(createParameter(
                        "filters." + field.getName(),
                        createSchemaForType(field.getType()),
                        "Exact filter for " + camelCaseToWords(field.getName()),
                        null
                ));

                // If it's rangeable AND not ID-like, add range parameters
                // Some fields end with "Id" but are also annotated with @FilterableId -> skip range
                if (isRangeableField(field) && !isIdLikeField(field)) {
                    parameters.add(createRangeFromParameter(field));
                    parameters.add(createRangeToParameter(field));
                }
            }
        }
    }

    /**
     * Determines whether to include a field based on modifiers (static, transient, etc.).
     *
     * @param field The Field to check
     * @return true if we should include it; false otherwise
     */
    private boolean shouldIncludeField(Field field) {
        int modifiers = field.getModifiers();
        return !Modifier.isStatic(modifiers)
                && !Modifier.isTransient(modifiers)
                && !"serialVersionUID".equals(field.getName());
    }

    /**
     * Checks if a field is considered an ID field that should be excluded from range filters
     * unless annotated with {@code @FilterableId}.
     *
     * <p>Conditions for being an ID field:</p>
     * <ul>
     *   <li>Annotated with {@code @Id}</li>
     *   <li>Named exactly "id"</li>
     *   <li>Ends with "Id" and not annotated with {@code @FilterableId}</li>
     * </ul>
     *
     * @param field The Field to evaluate
     * @return true if it is an ID field; false otherwise
     */
    private boolean isIdField(Field field) {
        String fieldName = field.getName();
        boolean isAnnotatedId = field.isAnnotationPresent(Id.class);
        boolean isExactNameId = "id".equals(fieldName);
        boolean endsWithId = fieldName.endsWith("Id");
        boolean hasFilterableId = field.isAnnotationPresent(FilterableId.class);

        // Return true if it's an explicit @Id, or "id", or ends with "Id" without @FilterableId
        if (isAnnotatedId || isExactNameId) {
            return true;
        }
        // If ends with "Id" but does NOT have @FilterableId, it's also considered an ID field
        if (endsWithId && !hasFilterableId) {
            return true;
        }
        return false;
    }

    /**
     * Checks if a field name is ID-like (e.g., "id", "contractId", "branchId").
     * This helps ensure no range filters are created for ID-like fields, even if they're @FilterableId.
     *
     * @param field The Field to evaluate
     * @return true if it's named "id" or ends with "Id"; false otherwise
     */
    private boolean isIdLikeField(Field field) {
        String fieldName = field.getName();
        return "id".equals(fieldName) || fieldName.endsWith("Id");
    }

    /**
     * Determines if a field is eligible for range-based filtering.
     *
     * <p>Rangeable types include numeric types, {@code LocalDateTime}, or {@code java.util.Date}.</p>
     *
     * @param field The Field to evaluate
     * @return true if it is rangeable; false otherwise
     */
    private boolean isRangeableField(Field field) {
        Class<?> type = field.getType();
        return Number.class.isAssignableFrom(type)
                || type.equals(LocalDateTime.class)
                || java.util.Date.class.isAssignableFrom(type);
    }

    /**
     * Creates a range start parameter (e.g., "rangeFilters.ranges[someField].from").
     *
     * @param field The Field for which the range start parameter is generated
     * @return The created Parameter
     */
    private Parameter createRangeFromParameter(Field field) {
        return createParameter(
                "rangeFilters.ranges[" + field.getName() + "].from",
                createSchemaForType(field.getType()),
                "Filter " + camelCaseToWords(field.getName()) + " from value",
                null
        );
    }

    /**
     * Creates a range end parameter (e.g., "rangeFilters.ranges[someField].to").
     *
     * @param field The Field for which the range end parameter is generated
     * @return The created Parameter
     */
    private Parameter createRangeToParameter(Field field) {
        return createParameter(
                "rangeFilters.ranges[" + field.getName() + "].to",
                createSchemaForType(field.getType()),
                "Filter " + camelCaseToWords(field.getName()) + " to value",
                null
        );
    }

    /**
     * Creates an OpenAPI query Parameter with the given name, schema, description, and optional default value.
     */
    private Parameter createParameter(String name,
                                      io.swagger.v3.oas.models.media.Schema<?> schema,
                                      String description,
                                      String defaultValue) {
        if (defaultValue != null) {
            schema.setDefault(defaultValue);
        }
        return new Parameter()
                .name(name)
                .in("query")
                .description(description)
                .required(false)
                .schema(schema);
    }

    /**
     * Creates a StringSchema for OpenAPI.
     */
    private StringSchema stringSchema() {
        return new StringSchema();
    }

    /**
     * Creates an IntegerSchema for OpenAPI.
     */
    private IntegerSchema integerSchema() {
        return new IntegerSchema();
    }

    /**
     * Creates a NumberSchema for OpenAPI (floats, doubles, etc.).
     */
    private NumberSchema numberSchema() {
        return new NumberSchema();
    }

    /**
     * Creates a BooleanSchema for OpenAPI.
     */
    private BooleanSchema booleanSchema() {
        return new BooleanSchema();
    }

    /**
     * Recursively gets all fields from a class and its superclasses.
     *
     * @param clazz     The class to get fields from
     * @param allFields The list to add fields to
     */
    private void getAllFields(Class<?> clazz, List<Field> allFields) {
        if (clazz == null || clazz == Object.class) {
            return;
        }

        // Add declared fields
        allFields.addAll(Arrays.asList(clazz.getDeclaredFields()));

        // Recursively add fields from superclass
        getAllFields(clazz.getSuperclass(), allFields);
    }

    /**
     * Chooses an appropriate OpenAPI schema based on the Java type.
     *
     * <ul>
     *   <li>Enums: A StringSchema with enum values</li>
     *   <li>String, Integer, Long, Double, Float, Boolean</li>
     *   <li>LocalDateTime and java.util.Date: StringSchema with "date-time" format</li>
     *   <li>Collection/Array: StringSchema with description for comma-separated values</li>
     *   <li>Default for unrecognized types: StringSchema</li>
     * </ul>
     *
     * @param type The Java type to create a schema for
     * @return An appropriate OpenAPI schema
     */
    private io.swagger.v3.oas.models.media.Schema<?> createSchemaForType(Class<?> type) {
        if (type.isEnum()) {
            StringSchema enumSchema = new StringSchema();
            for (Object constant : type.getEnumConstants()) {
                enumSchema.addEnumItem(constant.toString());
            }
            return enumSchema;
        } else if (type == String.class) {
            return stringSchema();
        } else if (type == Integer.class || type == int.class
                || type == Long.class || type == long.class) {
            return integerSchema();
        } else if (type == Double.class || type == double.class
                || type == Float.class || type == float.class) {
            return numberSchema();
        } else if (type == Boolean.class || type == boolean.class) {
            return booleanSchema();
        } else if (type == LocalDateTime.class) {
            return stringSchema().format("date-time");
        } else if (java.util.Date.class.isAssignableFrom(type)) {
            return stringSchema().format("date-time");
        } else if (Collection.class.isAssignableFrom(type) || type.isArray()) {
            // For collections and arrays, we use a string schema with comma-separated values
            return stringSchema().description("Comma-separated values for collection/array filtering");
        } else {
            // Default: String
            return stringSchema();
        }
    }

    /**
     * Converts camelCase to a space-separated lowercase phrase. E.g. "firstName" -> "first name".
     */
    private String camelCaseToWords(String camelCase) {
        String[] words = camelCase.split("(?=[A-Z])");
        return String.join(" ", words).toLowerCase();
    }
}