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

import java.time.Instant;
import java.util.List;

/**
 * Aggregated report produced by the {@link DataQualityEngine} after evaluating
 * all applicable rules against a data object.
 */
@Data
@Builder
public class QualityReport {

    private final boolean passed;
    private final int totalRules;
    private final int passedRules;
    private final int failedRules;
    private final List<QualityResult> results;
    private final Instant timestamp;

    /**
     * Returns only the results that represent failures.
     *
     * @return list of failed {@link QualityResult} entries
     */
    public List<QualityResult> getFailures() {
        return results.stream()
                .filter(result -> !result.isPassed())
                .toList();
    }

    /**
     * Returns results filtered by the given severity.
     *
     * @param severity the severity to filter by
     * @return list of {@link QualityResult} entries matching the severity
     */
    public List<QualityResult> getBySeverity(QualitySeverity severity) {
        return results.stream()
                .filter(result -> result.getSeverity() == severity)
                .toList();
    }
}
