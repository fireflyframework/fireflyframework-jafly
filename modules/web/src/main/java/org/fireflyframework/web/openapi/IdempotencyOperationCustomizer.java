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


package org.fireflyframework.web.openapi;

import org.fireflyframework.web.idempotency.annotation.DisableIdempotency;
import io.swagger.v3.oas.models.Operation;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.method.HandlerMethod;

/**
 * Operation customizer that marks operations with the DisableIdempotency annotation.
 * This customizer adds an extension to the OpenAPI operation that can be used by
 * the IdempotencyOpenAPICustomizer to exclude these operations from idempotency.
 *
 * <p>This customizer is automatically configured by {@link org.fireflyframework.web.idempotency.config.IdempotencyAutoConfiguration}
 * when fireflyframework-cache is available on the classpath. It will not be created if the required
 * cache dependencies are not available, preventing application startup failures.</p>
 */
public class IdempotencyOperationCustomizer implements OperationCustomizer {

    public static final String DISABLE_IDEMPOTENCY_EXTENSION = "x-disable-idempotency";

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        if (handlerMethod.hasMethodAnnotation(DisableIdempotency.class)) {
            // Mark this operation as having the DisableIdempotency annotation
            operation.addExtension(DISABLE_IDEMPOTENCY_EXTENSION, true);
        }
        return operation;
    }
}