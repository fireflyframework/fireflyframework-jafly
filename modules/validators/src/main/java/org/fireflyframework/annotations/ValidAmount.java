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

import org.fireflyframework.validators.AmountValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated element must be a valid monetary amount.
 * By default, validates that the amount is a positive decimal number
 * with the appropriate number of decimal places for the specified currency.
 */
@Documented
@Constraint(validatedBy = AmountValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidAmount {
    String message() default "Invalid monetary amount";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Specifies the currency code (ISO 4217) for currency-specific validation.
     * This determines the maximum number of decimal places allowed.
     */
    String currency() default "EUR";
    
    /**
     * Specifies the minimum value (inclusive) that the amount can have.
     * Default is 0, meaning the amount must be non-negative.
     */
    double min() default 0.0;
    
    /**
     * Specifies the maximum value (inclusive) that the amount can have.
     * Default is Double.MAX_VALUE, meaning there is no upper limit.
     */
    double max() default Double.MAX_VALUE;
    
    /**
     * Specifies whether zero is allowed as a valid amount.
     * Default is true, meaning zero is a valid amount.
     */
    boolean allowZero() default true;
}