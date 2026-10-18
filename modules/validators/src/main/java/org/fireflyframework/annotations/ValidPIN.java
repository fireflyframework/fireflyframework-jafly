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

import org.fireflyframework.validators.PINValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated element must be a valid PIN (Personal Identification Number).
 * By default, validates that the PIN is numeric and has 4-6 digits.
 */
@Documented
@Constraint(validatedBy = PINValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPIN {
    String message() default "Invalid PIN";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Specifies the minimum length of the PIN.
     * Default is 4 digits.
     */
    int minLength() default 4;
    
    /**
     * Specifies the maximum length of the PIN.
     * Default is 6 digits.
     */
    int maxLength() default 6;
    
    /**
     * Specifies whether the PIN must be numeric only.
     * Default is true, meaning only digits are allowed.
     */
    boolean numericOnly() default true;
    
    /**
     * Specifies whether to check for common/weak PINs (e.g., 1234, 0000).
     * Default is true, meaning common PINs are rejected.
     */
    boolean rejectCommonPINs() default true;
}