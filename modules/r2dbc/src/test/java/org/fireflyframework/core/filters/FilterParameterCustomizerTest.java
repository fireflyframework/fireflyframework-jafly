package org.fireflyframework.core.filters;

import org.fireflyframework.utils.annotations.FilterableId;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import java.util.UUID;

/**
 * Tests for {@link FilterParameterCustomizer} to ensure it correctly enhances
 * OpenAPI documentation with filter parameters.
 */
class FilterParameterCustomizerTest {

    private FilterParameterCustomizer customizer;

    @Mock
    private HandlerMethod handlerMethod;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        customizer = new FilterParameterCustomizer();
    }

    @Test
    void shouldAddFilterParametersWhenMethodHasFilterRequestWithParameterObject() throws Exception {
        // Given
        Method method = TestController.class.getMethod("filterWithParameterObject", FilterRequest.class);
        MethodParameter methodParameter = new MethodParameter(method, 0);
        methodParameter.initParameterNameDiscovery(null);
        when(handlerMethod.getMethodParameters()).thenReturn(new MethodParameter[]{methodParameter});
        Operation operation = new Operation();

        // When
        Operation customizedOperation = customizer.customize(operation, handlerMethod);

        // Then
        assertThat(customizedOperation.getParameters()).isNotNull();
        verifyParameters(customizedOperation.getParameters());
    }

    @Test
    void shouldAddFilterParametersWhenMethodHasFilterRequestWithModelAttribute() throws Exception {
        // Given
        Method method = TestController.class.getMethod("filterWithModelAttribute", FilterRequest.class);
        MethodParameter methodParameter = new MethodParameter(method, 0);
        methodParameter.initParameterNameDiscovery(null);
        when(handlerMethod.getMethodParameters()).thenReturn(new MethodParameter[]{methodParameter});
        Operation operation = new Operation();

        // When
        Operation customizedOperation = customizer.customize(operation, handlerMethod);

        // Then
        assertThat(customizedOperation.getParameters()).isNotNull();
        verifyParameters(customizedOperation.getParameters());
    }

    @Test
    void shouldNotAddFilterParametersWhenMethodHasNoFilterRequest() throws Exception {
        // Given
        Method method = TestController.class.getMethod("noFilter");
        when(handlerMethod.getMethodParameters()).thenReturn(new MethodParameter[0]);
        Operation operation = new Operation();

        // When
        Operation customizedOperation = customizer.customize(operation, handlerMethod);

        // Then
        assertThat(customizedOperation.getParameters()).isNull();
    }

    @Test
    void shouldPreserveExistingNonQueryParameters() throws Exception {
        // Given
        Method method = TestController.class.getMethod("filterWithParameterObject", FilterRequest.class);
        MethodParameter methodParameter = new MethodParameter(method, 0);
        methodParameter.initParameterNameDiscovery(null);
        when(handlerMethod.getMethodParameters()).thenReturn(new MethodParameter[]{methodParameter});

        Operation operation = new Operation();
        Parameter pathParam = new Parameter().name("id").in("path");
        operation.addParametersItem(pathParam);

        // When
        Operation customizedOperation = customizer.customize(operation, handlerMethod);

        // Then
        assertThat(customizedOperation.getParameters()).isNotNull();
        assertThat(customizedOperation.getParameters()).anyMatch(p -> "id".equals(p.getName()) && "path".equals(p.getIn()));
        verifyParameters(customizedOperation.getParameters());
    }

    private void verifyParameters(List<Parameter> parameters) {
        // Verify pagination parameters
        assertThat(parameters).anyMatch(p -> "pagination.pageNumber".equals(p.getName()));
        assertThat(parameters).anyMatch(p -> "pagination.pageSize".equals(p.getName()));
        assertThat(parameters).anyMatch(p -> "pagination.sortBy".equals(p.getName()));
        assertThat(parameters).anyMatch(p -> "pagination.sortDirection".equals(p.getName()));

        // Verify filter options
        assertThat(parameters).anyMatch(p -> "options.caseInsensitiveStrings".equals(p.getName()));
        assertThat(parameters).anyMatch(p -> "options.includeInheritedFields".equals(p.getName()));

        // Verify filter fields
        assertThat(parameters).anyMatch(p -> "filters.name".equals(p.getName()));
        assertThat(parameters).anyMatch(p -> "filters.count".equals(p.getName()));
        assertThat(parameters).anyMatch(p -> "filters.active".equals(p.getName()));
        assertThat(parameters).anyMatch(p -> "filters.createdDate".equals(p.getName()));
        assertThat(parameters).anyMatch(p -> "filters.tags".equals(p.getName()));
        assertThat(parameters).anyMatch(p -> "filters.relatedIds".equals(p.getName()));
        assertThat(parameters).anyMatch(p -> "filters.stringArray".equals(p.getName()));

        // Verify filterable ID field
        assertThat(parameters).anyMatch(p -> "filters.filterableId".equals(p.getName()));

        // Verify range filter parameters for numeric and date fields
        assertThat(parameters).anyMatch(p -> "rangeFilters.ranges[count].from".equals(p.getName()));
        assertThat(parameters).anyMatch(p -> "rangeFilters.ranges[count].to".equals(p.getName()));
        assertThat(parameters).anyMatch(p -> "rangeFilters.ranges[createdDate].from".equals(p.getName()));
        assertThat(parameters).anyMatch(p -> "rangeFilters.ranges[createdDate].to".equals(p.getName()));

        // Verify ID fields are not included in range filters
        assertThat(parameters).noneMatch(p -> "rangeFilters.ranges[id].from".equals(p.getName()));
        assertThat(parameters).noneMatch(p -> "rangeFilters.ranges[filterableId].from".equals(p.getName()));
    }

    /**
     * Test controller class with methods that use FilterRequest.
     */
    static class TestController {
        @GetMapping("/filter1")
        public void filterWithParameterObject(@ParameterObject FilterRequest<TestFilter> request) {
            // Method for testing
        }

        @GetMapping("/filter2")
        public void filterWithModelAttribute(@ModelAttribute FilterRequest<TestFilter> request) {
            // Method for testing
        }

        @GetMapping("/no-filter")
        public void noFilter() {
            // Method for testing
        }
    }

    /**
     * Test filter class with various field types.
     */
    static class TestFilter {
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

        // Getters and setters omitted for brevity
    }
}
