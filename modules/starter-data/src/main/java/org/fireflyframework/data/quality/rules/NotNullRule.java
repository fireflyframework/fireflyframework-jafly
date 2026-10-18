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
 * Built-in rule that validates a field is not null.
 *
 * @param <T> the type of data being validated
 */
public class NotNullRule<T> implements DataQualityRule<T> {

    private final String fieldName;
    private final Function<T, Object> extractor;
    private final QualitySeverity severity;

    public NotNullRule(String fieldName, Function<T, Object> extractor) {
        this(fieldName, extractor, QualitySeverity.WARNING);
    }

    public NotNullRule(String fieldName, Function<T, Object> extractor, QualitySeverity severity) {
        this.fieldName = fieldName;
        this.extractor = extractor;
        this.severity = severity;
    }

    @Override
    public QualityResult evaluate(T data) {
        Object value = extractor.apply(data);
        if (value != null) {
            return QualityResult.pass(getRuleName());
        }
        return QualityResult.builder()
                .ruleName(getRuleName())
                .passed(false)
                .severity(severity)
                .message(fieldName + " must not be null")
                .fieldName(fieldName)
                .actualValue(null)
                .build();
    }

    @Override
    public String getRuleName() {
        return "not-null:" + fieldName;
    }

    @Override
    public QualitySeverity getSeverity() {
        return severity;
    }
}
