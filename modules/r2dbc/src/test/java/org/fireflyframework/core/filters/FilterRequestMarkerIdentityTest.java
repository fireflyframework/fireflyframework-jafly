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

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class FilterRequestMarkerIdentityTest {
    static final class MutableFilter {
        String value;

        @Override
        public boolean equals(Object other) {
            return other instanceof MutableFilter filter && Objects.equals(value, filter.value);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }
    }

    @Test
    void equalInstancesDoNotShareNullMarkers() {
        var left = new MutableFilter();
        var right = new MutableFilter();
        assertThat(left).isEqualTo(right);
        FilterRequest.setNullFilter(left, "value");
        assertThat(FilterRequest.getSpecialFilter(left, "value")).isSameAs(FilterRequest.NULL_VALUE);
        assertThat(FilterRequest.getSpecialFilter(right, "value")).isNull();
    }

    @Test
    void equalInstancesRetainDifferentMarkersForTheSameField() {
        var left = new MutableFilter();
        var right = new MutableFilter();
        FilterRequest.setNullFilter(left, "value");
        FilterRequest.setNotNullFilter(right, "value");
        assertThat(FilterRequest.getSpecialFilter(left, "value")).isSameAs(FilterRequest.NULL_VALUE);
        assertThat(FilterRequest.getSpecialFilter(right, "value")).isSameAs(FilterRequest.NOT_NULL_VALUE);
    }

    @Test
    void markerSurvivesMutationOfItsOwnDtoHash() {
        var filter = new MutableFilter();
        FilterRequest.setNullFilter(filter, "value");
        filter.value = "changed";
        assertThat(FilterRequest.getSpecialFilter(filter, "value")).isSameAs(FilterRequest.NULL_VALUE);
    }

    @Test
    void registryDoesNotInvokeUserEqualityOrHashCode() {
        Object filter = new Object() {
            @Override
            public int hashCode() { throw new AssertionError("User hashCode must not run"); }
            @Override
            public boolean equals(Object other) { throw new AssertionError("User equals must not run"); }
        };
        FilterRequest.setNotNullFilter(filter, "field");
        assertThat(FilterRequest.getSpecialFilter(filter, "field")).isSameAs(FilterRequest.NOT_NULL_VALUE);
    }

    @Test
    void concurrentUpdatesKeepEveryFieldMarker() throws Exception {
        var filter = new MutableFilter();
        try (var executor = Executors.newFixedThreadPool(8)) {
            var work = new java.util.ArrayList<Callable<Void>>();
            for (int i = 0; i < 128; i++) {
                int index = i;
                work.add(() -> {
                    if (index % 2 == 0) FilterRequest.setNullFilter(filter, "field" + index);
                    else FilterRequest.setNotNullFilter(filter, "field" + index);
                    return null;
                });
            }
            for (var future : executor.invokeAll(work)) future.get();
        }
        for (int i = 0; i < 128; i++) {
            assertThat(FilterRequest.getSpecialFilter(filter, "field" + i))
                    .isSameAs(i % 2 == 0 ? FilterRequest.NULL_VALUE : FilterRequest.NOT_NULL_VALUE);
        }
    }

    @Test
    void replacingAMarkerOnlyAffectsItsExactObjectAndField() {
        var filter = new MutableFilter();
        FilterRequest.setNullFilter(filter, "first");
        FilterRequest.setNotNullFilter(filter, "second");
        FilterRequest.setNotNullFilter(filter, "first");
        assertThat(FilterRequest.getSpecialFilter(filter, "first")).isSameAs(FilterRequest.NOT_NULL_VALUE);
        assertThat(FilterRequest.getSpecialFilter(filter, "second")).isSameAs(FilterRequest.NOT_NULL_VALUE);
        assertThat(FilterRequest.getSpecialFilter(filter, "third")).isNull();
    }
}
