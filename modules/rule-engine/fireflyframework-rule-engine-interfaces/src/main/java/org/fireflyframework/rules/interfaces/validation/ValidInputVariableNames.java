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

package org.fireflyframework.rules.interfaces.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Custom validation annotation to ensure input variable names follow camelCase naming convention.
 * 
 * This annotation validates that all keys in a Map follow the camelCase pattern:
 * - Must start with lowercase letter
 * - Can contain letters and numbers
 * - No underscores or special characters
 * 
 * Valid examples: creditScore, annualIncome, employmentYears
 * Invalid examples: CREDIT_SCORE, credit_score, CreditScore
 */
@Documented
@Constraint(validatedBy = ValidInputVariableNamesValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidInputVariableNames {

    String message() default "Input variable names must follow camelCase naming convention (e.g., creditScore, annualIncome)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
