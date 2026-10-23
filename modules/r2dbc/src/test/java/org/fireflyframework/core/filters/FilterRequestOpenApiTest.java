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
package org.fireflyframework.core.filters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springdoc.core.extractor.MethodParameterPojoExtractor;
import org.springdoc.core.utils.SchemaUtils;
import org.springframework.core.MethodParameter;
import org.springframework.test.util.ReflectionTestUtils;

class FilterRequestOpenApiTest {
    public static final class Criteria {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static final class TypedRequest extends FilterRequest<Criteria> {}

    private static List<String> parameters(Class<?> type) {
        var extractor = new MethodParameterPojoExtractor(mock(SchemaUtils.class));
        Stream<MethodParameter> parameters = ReflectionTestUtils.invokeMethod(extractor, "extractFrom", type);
        try (parameters) {
            return parameters.map(MethodParameter::getParameterName).toList();
        }
    }

    @Test
    void typedParameterObjectKeepsPublicCriteriaAndPaginationWithoutInternalRegistryTraversal() {
        var names = assertDoesNotThrow(() -> parameters(TypedRequest.class));
        assertThat(names).contains("filters.name", "pagination.pageNumber", "pagination.pageSize");
        assertThat(names).noneMatch(name -> name.contains("SPECIAL_FILTERS") || name.contains("CLEARED_FILTERS"));
    }

    @Test
    void rawParameterObjectStillExposesPaginationWithoutInternalRegistryTraversal() {
        var names = assertDoesNotThrow(() -> parameters(FilterRequest.class));
        assertThat(names).contains("pagination.pageNumber", "pagination.pageSize", "pagination.sortBy");
        assertThat(names).noneMatch(name -> name.contains("SPECIAL_FILTERS") || name.contains("CLEARED_FILTERS"));
    }
}
