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

package org.fireflyframework.data.quality;

import lombok.Builder;
import lombok.Data;

/**
 * Result of evaluating a single {@link DataQualityRule}.
 */
@Data
@Builder
public class QualityResult {

    private final String ruleName;
    private final boolean passed;
    private final QualitySeverity severity;
    private final String message;
    private final String fieldName;
    private final Object actualValue;

    /**
     * Creates a passing result for the given rule.
     *
     * @param ruleName the name of the rule that passed
     * @return a passing {@link QualityResult}
     */
    public static QualityResult pass(String ruleName) {
        return QualityResult.builder()
                .ruleName(ruleName)
                .passed(true)
                .severity(QualitySeverity.INFO)
                .build();
    }

    /**
     * Creates a failing result for the given rule.
     *
     * @param ruleName the name of the rule that failed
     * @param severity the severity of the failure
     * @param message  a human-readable description of the failure
     * @return a failing {@link QualityResult}
     */
    public static QualityResult fail(String ruleName, QualitySeverity severity, String message) {
        return QualityResult.builder()
                .ruleName(ruleName)
                .passed(false)
                .severity(severity)
                .message(message)
                .build();
    }
}
