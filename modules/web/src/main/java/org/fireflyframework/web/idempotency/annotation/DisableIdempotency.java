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


package org.fireflyframework.web.idempotency.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to disable idempotency for specific endpoints.
 * When applied to a controller method, the idempotency filter will not
 * process requests to that endpoint, and the X-Idempotency-Key header
 * will not be included in the OpenAPI documentation for that operation.
 * 
 * Example usage:
 * <pre>
 * {@code
 * @PostMapping("/api/resource")
 * @DisableIdempotency
 * public Mono<ResponseEntity<Resource>> createResource(@RequestBody Resource resource) {
 *     // Method implementation
 * }
 * }
 * </pre>
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface DisableIdempotency {
}
