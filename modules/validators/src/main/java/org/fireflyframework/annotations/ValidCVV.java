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

import org.fireflyframework.validators.CVVValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated element must be a valid CVV (Card Verification Value).
 * By default, validates that the CVV is 3 or 4 digits.
 */
@Documented
@Constraint(validatedBy = CVVValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCVV {
    String message() default "Invalid CVV";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Specifies the card type to validate against.
     * Different card types have different CVV formats.
     * If not specified, accepts both 3 and 4 digit CVVs.
     */
    CardType cardType() default CardType.ANY;
    
    /**
     * Card types with their corresponding CVV formats.
     */
    enum CardType {
        /**
         * Any card type (accepts 3 or 4 digit CVVs).
         */
        ANY,
        
        /**
         * Visa, MasterCard, Discover (3 digit CVV).
         */
        STANDARD,
        
        /**
         * American Express (4 digit CVV).
         */
        AMEX
    }
}