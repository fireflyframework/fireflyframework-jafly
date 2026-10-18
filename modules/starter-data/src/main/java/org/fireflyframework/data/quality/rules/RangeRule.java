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

package org.fireflyframework.data.quality.rules;

import org.fireflyframework.data.quality.DataQualityRule;
import org.fireflyframework.data.quality.QualityResult;
import org.fireflyframework.data.quality.QualitySeverity;

import java.util.function.Function;

/**
 * Built-in rule that validates a comparable field falls within a specified range.
 *
 * @param <T> the type of data being validated
 */
public class RangeRule<T> implements DataQualityRule<T> {

    private final String fieldName;
    private final Comparable<?> min;
    private final Comparable<?> max;
    private final Function<T, Comparable<?>> extractor;
    private final QualitySeverity severity;

    public RangeRule(String fieldName, Comparable<?> min, Comparable<?> max,
                     Function<T, Comparable<?>> extractor) {
        this(fieldName, min, max, extractor, QualitySeverity.WARNING);
    }

    public RangeRule(String fieldName, Comparable<?> min, Comparable<?> max,
                     Function<T, Comparable<?>> extractor, QualitySeverity severity) {
        this.fieldName = fieldName;
        this.min = min;
        this.max = max;
        this.extractor = extractor;
        this.severity = severity;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public QualityResult evaluate(T data) {
        Comparable value = extractor.apply(data);
        if (value == null) {
            return QualityResult.builder()
                    .ruleName(getRuleName())
                    .passed(false)
                    .severity(severity)
                    .message(fieldName + " is null, expected range [" + min + ", " + max + "]")
                    .fieldName(fieldName)
                    .actualValue(null)
                    .build();
        }

        boolean belowMin = min != null && value.compareTo(min) < 0;
        boolean aboveMax = max != null && value.compareTo(max) > 0;

        if (!belowMin && !aboveMax) {
            return QualityResult.pass(getRuleName());
        }

        return QualityResult.builder()
                .ruleName(getRuleName())
                .passed(false)
                .severity(severity)
                .message(fieldName + " value " + value + " is outside range [" + min + ", " + max + "]")
                .fieldName(fieldName)
                .actualValue(value)
                .build();
    }

    @Override
    public String getRuleName() {
        return "range:" + fieldName;
    }

    @Override
    public QualitySeverity getSeverity() {
        return severity;
    }
}
