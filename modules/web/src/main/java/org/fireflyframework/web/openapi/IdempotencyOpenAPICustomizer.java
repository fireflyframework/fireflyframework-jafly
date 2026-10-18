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
import org.fireflyframework.web.idempotency.config.IdempotencyProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OpenApiCustomizer;

import java.util.List;
import java.util.Optional;

/**
 * OpenAPI customizer that adds the Idempotency header to all HTTP operations.
 * It excludes operations that have the {@link DisableIdempotency} annotation.
 *
 * <p>This customizer is automatically configured by {@link org.fireflyframework.web.idempotency.config.IdempotencyAutoConfiguration}
 * when fireflyframework-cache is available on the classpath. It will not be created if the required
 * cache dependencies are not available, preventing application startup failures.</p>
 */
public class IdempotencyOpenAPICustomizer implements OpenApiCustomizer {

    private final String headerName;

    /**
     * Creates a new IdempotencyOpenAPICustomizer with the specified properties.
     *
     * @param properties the idempotency configuration properties
     */
    public IdempotencyOpenAPICustomizer(IdempotencyProperties properties) {
        this.headerName = properties.getHeaderName();
    }

    @Override
    public void customise(OpenAPI openApi) {
        openApi.getPaths().forEach((path, pathItem) -> {
            // Process all HTTP methods
            processOperation(pathItem.getGet());
            processOperation(pathItem.getPost());
            processOperation(pathItem.getPut());
            processOperation(pathItem.getPatch());
            processOperation(pathItem.getDelete());
            processOperation(pathItem.getHead());
            processOperation(pathItem.getOptions());
            processOperation(pathItem.getTrace());
        });
    }

    private void processOperation(Operation operation) {
        if (operation == null) {
            return;
        }

        // Skip if the operation has the DisableIdempotency annotation
        if (hasDisableIdempotencyExtension(operation)) {
            return;
        }

        // Check if the idempotency header parameter already exists
        Optional<Parameter> existingParam = operation.getParameters() != null ?
                operation.getParameters().stream()
                        .filter(p -> headerName.equals(p.getName()) && "header".equals(p.getIn()))
                        .findFirst() :
                Optional.empty();

        // Add the parameter if it doesn't exist
        if (existingParam.isEmpty()) {
            Parameter idempotencyKeyParam = new Parameter()
                    .name(headerName)
                    .in("header")
                    .description("Unique key for idempotent requests. If provided, ensures that identical requests with the same key will only be processed once.")
                    .required(false)
                    .schema(new io.swagger.v3.oas.models.media.StringSchema());

            if (operation.getParameters() == null) {
                operation.setParameters(List.of(idempotencyKeyParam));
            } else {
                operation.addParametersItem(idempotencyKeyParam);
            }
        }
    }

    private boolean hasDisableIdempotencyExtension(Operation operation) {
        // Check if the operation has the x-disable-idempotency extension
        Object extension = operation.getExtensions() != null ?
                operation.getExtensions().get(IdempotencyOperationCustomizer.DISABLE_IDEMPOTENCY_EXTENSION) : null;
        return Boolean.TRUE.equals(extension);
    }
}
