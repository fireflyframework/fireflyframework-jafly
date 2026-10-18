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


package org.fireflyframework.annotations;

import org.fireflyframework.validators.InterestRateValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated element must be a valid interest rate.
 * By default, validates that the interest rate is a percentage between 0 and 100.
 */
@Documented
@Constraint(validatedBy = InterestRateValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidInterestRate {
    String message() default "Invalid interest rate";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Specifies the minimum value (inclusive) that the interest rate can have.
     * Default is 0, meaning the interest rate must be non-negative.
     */
    double min() default 0.0;
    
    /**
     * Specifies the maximum value (inclusive) that the interest rate can have.
     * Default is 100, meaning the interest rate must be at most 100%.
     */
    double max() default 100.0;
    
    /**
     * Specifies the maximum number of decimal places allowed.
     * Default is 4, allowing rates like 5.2500%.
     */
    int decimalPlaces() default 4;
    
    /**
     * Specifies whether zero is allowed as a valid interest rate.
     * Default is true, meaning zero is a valid interest rate.
     */
    boolean allowZero() default true;
}