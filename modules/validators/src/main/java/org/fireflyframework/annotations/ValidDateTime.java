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

import org.fireflyframework.validators.DateTimeValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated element must be a valid date-time.
 * By default, validates date-times in ISO format (yyyy-MM-dd HH:mm:ss).
 */
@Documented
@Constraint(validatedBy = DateTimeValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidDateTime {
    String message() default "Invalid date-time";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Specifies the date-time format pattern.
     * Default is ISO format (yyyy-MM-dd HH:mm:ss).
     * See java.text.SimpleDateFormat for pattern syntax.
     */
    String pattern() default "yyyy-MM-dd HH:mm:ss";
    
    /**
     * Specifies the minimum date-time (inclusive) in the same format as the pattern.
     * Default is empty, meaning there is no minimum date-time.
     */
    String min() default "";
    
    /**
     * Specifies the maximum date-time (inclusive) in the same format as the pattern.
     * Default is empty, meaning there is no maximum date-time.
     */
    String max() default "";
    
    /**
     * Specifies whether future date-times are allowed.
     * Default is true, meaning future date-times are allowed.
     */
    boolean allowFuture() default true;
    
    /**
     * Specifies whether past date-times are allowed.
     * Default is true, meaning past date-times are allowed.
     */
    boolean allowPast() default true;
}