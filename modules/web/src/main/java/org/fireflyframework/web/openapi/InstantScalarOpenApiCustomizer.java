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

import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Encoding;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;

import org.springdoc.core.customizers.OpenApiCustomizer;

import java.util.*;

/** Registers the reserved RFC3339 scalar only when source schemas explicitly reference it. */
public final class InstantScalarOpenApiCustomizer implements OpenApiCustomizer {
    public static final String SCHEMA_NAME = "FireflyInstant";
    public static final String SCHEMA_REF = "#/components/schemas/" + SCHEMA_NAME;

    @Override
    public void customise(OpenAPI api) {
        if (api == null || !references(api, Collections.newSetFromMap(new IdentityHashMap<>())))
            return;
        if (api.getComponents() == null) api.setComponents(new Components());
        var standard = new StringSchema().format("date-time");
        var existing =
                api.getComponents().getSchemas() == null
                        ? null
                        : api.getComponents().getSchemas().get(SCHEMA_NAME);
        if (existing == null) {
            api.getComponents().addSchemas(SCHEMA_NAME, standard);
            return;
        }
        JsonNode actual = Json.mapper().valueToTree(existing);
        JsonNode expected = Json.mapper().valueToTree(standard);
        if (!expected.equals(actual))
            throw new IllegalStateException("OPENAPI_INSTANT_SCHEMA_CONFLICT");
    }

    private boolean references(Object value, Set<Object> visited) {
        if (value == null || !visited.add(value)) return false;
        if (value instanceof Schema<?> schema) {
            return SCHEMA_REF.equals(schema.get$ref())
                    || SCHEMA_REF.equals(schema.get$dynamicRef())
                    || references(schema.getProperties(), visited)
                    || references(schema.getItems(), visited)
                    || references(schema.getAdditionalProperties(), visited)
                    || references(schema.getAllOf(), visited)
                    || references(schema.getAnyOf(), visited)
                    || references(schema.getOneOf(), visited)
                    || references(schema.getNot(), visited)
                    || references(schema.getContains(), visited)
                    || references(schema.getPatternProperties(), visited)
                    || references(schema.getPrefixItems(), visited)
                    || references(schema.getContentSchema(), visited)
                    || references(schema.getPropertyNames(), visited)
                    || references(schema.getUnevaluatedProperties(), visited)
                    || references(schema.getAdditionalItems(), visited)
                    || references(schema.getUnevaluatedItems(), visited)
                    || references(schema.getIf(), visited)
                    || references(schema.getThen(), visited)
                    || references(schema.getElse(), visited)
                    || references(schema.getDependentSchemas(), visited);
        }
        if (value instanceof OpenAPI api) {
            return references(api.getComponents(), visited)
                    || references(api.getPaths(), visited)
                    || references(api.getWebhooks(), visited);
        }
        if (value instanceof Components components) {
            return references(components.getSchemas(), visited)
                    || references(components.getParameters(), visited)
                    || references(components.getRequestBodies(), visited)
                    || references(components.getResponses(), visited)
                    || references(components.getHeaders(), visited)
                    || references(components.getCallbacks(), visited)
                    || references(components.getPathItems(), visited);
        }
        if (value instanceof PathItem path)
            return references(path.getParameters(), visited)
                    || references(path.readOperations(), visited);
        if (value instanceof Operation operation) {
            return references(operation.getParameters(), visited)
                    || references(operation.getRequestBody(), visited)
                    || references(operation.getResponses(), visited)
                    || references(operation.getCallbacks(), visited);
        }
        if (value instanceof Parameter parameter) {
            return references(parameter.getSchema(), visited)
                    || references(parameter.getContent(), visited);
        }
        if (value instanceof RequestBody body) return references(body.getContent(), visited);
        if (value instanceof ApiResponse response) {
            return references(response.getContent(), visited)
                    || references(response.getHeaders(), visited);
        }
        if (value instanceof Header header) {
            return references(header.getSchema(), visited)
                    || references(header.getContent(), visited);
        }
        if (value instanceof MediaType media)
            return references(media.getSchema(), visited)
                    || references(media.getEncoding(), visited);
        if (value instanceof Encoding encoding) return references(encoding.getHeaders(), visited);
        if (value instanceof Map<?, ?> map) {
            for (Object nested : map.values()) if (references(nested, visited)) return true;
        } else if (value instanceof Iterable<?> values) {
            for (Object nested : values) if (references(nested, visited)) return true;
        }
        return false;
    }
}
