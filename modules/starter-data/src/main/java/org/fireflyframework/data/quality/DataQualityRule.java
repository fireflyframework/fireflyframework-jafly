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

/**
 * Port interface for data quality validation rules.
 *
 * <p>Implementations define a single validation concern that can be evaluated
 * against a data object of type {@code T}. Rules are composed and executed
 * by the {@link DataQualityEngine}.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * public class EmailFormatRule implements DataQualityRule<Customer> {
 *
 *     @Override
 *     public QualityResult evaluate(Customer customer) {
 *         String email = customer.getEmail();
 *         if (email != null && email.contains("@")) {
 *             return QualityResult.pass(getRuleName());
 *         }
 *         return QualityResult.fail(getRuleName(), getSeverity(), "Invalid email format");
 *     }
 *
 *     @Override
 *     public String getRuleName() {
 *         return "email-format";
 *     }
 * }
 * }</pre>
 *
 * @param <T> the type of data this rule validates
 */
public interface DataQualityRule<T> {

    /**
     * Evaluates this rule against the given data.
     *
     * @param data the data to validate
     * @return the result of the evaluation
     */
    QualityResult evaluate(T data);

    /**
     * Returns the unique name of this rule.
     *
     * @return the rule name
     */
    String getRuleName();

    /**
     * Returns the severity level for violations of this rule.
     * Defaults to {@link QualitySeverity#WARNING}.
     *
     * @return the severity level
     */
    default QualitySeverity getSeverity() {
        return QualitySeverity.WARNING;
    }
}
