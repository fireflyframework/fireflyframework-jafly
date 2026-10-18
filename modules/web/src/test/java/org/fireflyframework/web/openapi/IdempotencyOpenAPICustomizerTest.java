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

import org.fireflyframework.web.idempotency.config.IdempotencyProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the IdempotencyOpenAPICustomizer class.
 */
class IdempotencyOpenAPICustomizerTest {

    private IdempotencyOpenAPICustomizer customizer;
    private OpenAPI openAPI;
    private Paths paths;
    private PathItem pathItem;
    private Operation postOperation;
    private Operation putOperation;
    private Operation patchOperation;
    private Operation getOperation;
    private Operation deleteOperation;

    @BeforeEach
    void setUp() {
        IdempotencyProperties properties = new IdempotencyProperties();
        properties.setHeaderName("X-Idempotency-Key");
        customizer = new IdempotencyOpenAPICustomizer(properties);

        // Set up OpenAPI structure
        openAPI = new OpenAPI();
        paths = new Paths();
        openAPI.setPaths(paths);

        pathItem = new PathItem();
        paths.addPathItem("/test", pathItem);

        postOperation = new Operation().operationId("testPost");
        putOperation = new Operation().operationId("testPut");
        patchOperation = new Operation().operationId("testPatch");
        getOperation = new Operation().operationId("testGet");
        deleteOperation = new Operation().operationId("testDelete");

        pathItem.setPost(postOperation);
        pathItem.setPut(putOperation);
        pathItem.setPatch(patchOperation);
        pathItem.setGet(getOperation);
        pathItem.setDelete(deleteOperation);
    }

    @Test
    void shouldAddIdempotencyKeyHeaderToAllOperations() {
        // Act
        customizer.customise(openAPI);

        // Assert
        // POST operation should have X-Idempotency-Key header
        List<Parameter> postParams = postOperation.getParameters();
        assertNotNull(postParams);
        assertTrue(postParams.stream().anyMatch(p -> "X-Idempotency-Key".equals(p.getName()) && "header".equals(p.getIn())));

        // PUT operation should have X-Idempotency-Key header
        List<Parameter> putParams = putOperation.getParameters();
        assertNotNull(putParams);
        assertTrue(putParams.stream().anyMatch(p -> "X-Idempotency-Key".equals(p.getName()) && "header".equals(p.getIn())));

        // PATCH operation should have X-Idempotency-Key header
        List<Parameter> patchParams = patchOperation.getParameters();
        assertNotNull(patchParams);
        assertTrue(patchParams.stream().anyMatch(p -> "X-Idempotency-Key".equals(p.getName()) && "header".equals(p.getIn())));

        // GET operation should also have X-Idempotency-Key header (now supports all methods)
        List<Parameter> getParams = getOperation.getParameters();
        assertNotNull(getParams);
        assertTrue(getParams.stream().anyMatch(p -> "X-Idempotency-Key".equals(p.getName()) && "header".equals(p.getIn())));

        // DELETE operation should have X-Idempotency-Key header
        List<Parameter> deleteParams = deleteOperation.getParameters();
        assertNotNull(deleteParams);
        assertTrue(deleteParams.stream().anyMatch(p -> "X-Idempotency-Key".equals(p.getName()) && "header".equals(p.getIn())));
    }

    @Test
    void shouldNotAddIdempotencyKeyHeaderToOperationsWithDisableIdempotencyExtension() {
        // Arrange
        Map<String, Object> extensions = new HashMap<>();
        extensions.put(IdempotencyOperationCustomizer.DISABLE_IDEMPOTENCY_EXTENSION, true);
        postOperation.setExtensions(extensions);

        // Act
        customizer.customise(openAPI);

        // Assert
        // POST operation should NOT have X-Idempotency-Key header because it has the disable extension
        List<Parameter> postParams = postOperation.getParameters();
        if (postParams != null) {
            assertFalse(postParams.stream().anyMatch(p -> "X-Idempotency-Key".equals(p.getName()) && "header".equals(p.getIn())));
        }

        // PUT operation should still have X-Idempotency-Key header
        List<Parameter> putParams = putOperation.getParameters();
        assertNotNull(putParams);
        assertTrue(putParams.stream().anyMatch(p -> "X-Idempotency-Key".equals(p.getName()) && "header".equals(p.getIn())));

        // GET operation should have X-Idempotency-Key header (no disable extension)
        List<Parameter> getParams = getOperation.getParameters();
        assertNotNull(getParams);
        assertTrue(getParams.stream().anyMatch(p -> "X-Idempotency-Key".equals(p.getName()) && "header".equals(p.getIn())));
    }

    @Test
    void shouldNotDuplicateIdempotencyKeyHeaderIfAlreadyPresent() {
        // Arrange
        Parameter existingParam = new Parameter()
                .name("X-Idempotency-Key")
                .in("header")
                .description("Existing description");
        postOperation.setParameters(Collections.singletonList(existingParam));

        // Act
        customizer.customise(openAPI);

        // Assert
        // POST operation should still have only one X-Idempotency-Key header
        List<Parameter> postParams = postOperation.getParameters();
        assertNotNull(postParams);
        assertEquals(1, postParams.size());
        assertEquals("X-Idempotency-Key", postParams.get(0).getName());
        assertEquals("header", postParams.get(0).getIn());
        assertEquals("Existing description", postParams.get(0).getDescription());
    }
}
