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

import org.fireflyframework.validators.TaxIdValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated element must be a valid tax identification number.
 * Supported tax ID types include:
 * - US TIN (Tax Identification Number)
 * - Mexico RFC (Registro Federal de Contribuyentes)
 * - Argentina CUIT (Código Único de Identificación Tributaria)
 * - Spain NIF (Número de Identificación Fiscal)
 * - And others depending on the country
 */
@Documented
@Constraint(validatedBy = TaxIdValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTaxId {
    String message() default "Invalid tax identification number";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Specifies the country code (ISO 3166-1 alpha-2) for country-specific validation.
     * If not specified, the validator will attempt to determine the country from the format.
     */
    String country() default "";
}