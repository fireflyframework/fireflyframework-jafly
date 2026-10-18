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
import java.util.regex.Pattern;

/**
 * Built-in rule that validates a string field matches a regular expression pattern.
 *
 * @param <T> the type of data being validated
 */
public class PatternRule<T> implements DataQualityRule<T> {

    private final String fieldName;
    private final Pattern pattern;
    private final Function<T, String> extractor;
    private final QualitySeverity severity;

    public PatternRule(String fieldName, Pattern pattern, Function<T, String> extractor) {
        this(fieldName, pattern, extractor, QualitySeverity.WARNING);
    }

    public PatternRule(String fieldName, Pattern pattern, Function<T, String> extractor,
                       QualitySeverity severity) {
        this.fieldName = fieldName;
        this.pattern = pattern;
        this.extractor = extractor;
        this.severity = severity;
    }

    @Override
    public QualityResult evaluate(T data) {
        String value = extractor.apply(data);
        if (value != null && pattern.matcher(value).matches()) {
            return QualityResult.pass(getRuleName());
        }
        return QualityResult.builder()
                .ruleName(getRuleName())
                .passed(false)
                .severity(severity)
                .message(fieldName + " does not match pattern: " + pattern.pattern())
                .fieldName(fieldName)
                .actualValue(value)
                .build();
    }

    @Override
    public String getRuleName() {
        return "pattern:" + fieldName;
    }

    @Override
    public QualitySeverity getSeverity() {
        return severity;
    }
}
