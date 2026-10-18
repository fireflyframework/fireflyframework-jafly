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


package org.fireflyframework.utils.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.UUID;

/**
 * The FilterableId annotation is used to mark ID fields that should be included
 * in filtering operations. By default, fields that end with "Id" are excluded
 * from filter parameters, but when a field is annotated with @FilterableId, it will
 * be included in the filters with the following behavior:
 *
 * - In OpenAPI/Swagger documentation (FilterParameterCustomizer):
 *   - The field will appear as a query parameter
 *   - No range parameters will be generated for the field
 *
 * - In runtime filtering (FilterUtils):
 *   - The field will be included in filtering operations
 *   - Only exact matching will be used (no LIKE or range operations)
 *   - Range filters will be ignored for this field
 *
 * Example usage:
 * {@code
 *     public class UserFilterDTO {
 *         @FilterableId
 *         private UUID customerId;  // Will be included in filters with exact matching
 *
 *         private UUID accountId;   // Will be excluded from filters
 *
 *         private String name;      // Regular field with full filtering capabilities
 *     }
 * }
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface FilterableId {
}