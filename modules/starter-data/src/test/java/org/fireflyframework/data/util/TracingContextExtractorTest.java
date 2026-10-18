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

package org.fireflyframework.data.util;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.*;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TracingContextExtractorTest {

    private ObservationRegistry observationRegistry;
    private OpenTelemetry openTelemetry;
    private Tracer tracer;

    @BeforeEach
    void setUp() {
        // Set up OpenTelemetry tracing
        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder().build();
        openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();

        tracer = new OtelTracer(
                openTelemetry.getTracer("test"),
                new OtelCurrentTraceContext(),
                event -> {}
        );

        // Set the tracer in the extractor
        TracingContextExtractor.setTracer(tracer);

        // Set up observation registry with tracing
        observationRegistry = ObservationRegistry.create();
        observationRegistry.observationConfig().observationHandler(
                new io.micrometer.tracing.handler.DefaultTracingObservationHandler(tracer)
        );
    }

    @AfterEach
    void tearDown() {
        // OpenTelemetry SDK cleanup if needed
    }

    @Test
    void shouldExtractTraceIdFromObservation() {
        // Given: An observation with tracing context
        Observation observation = Observation.start("test.operation", observationRegistry);

        try {
            // When: Extracting trace ID within the observation scope
            observation.scoped(() -> {
                String traceId = TracingContextExtractor.extractTraceId(observation);

                // Then: Trace ID should be extracted
                assertThat(traceId).isNotNull();
                assertThat(traceId).isNotEmpty();
                assertThat(traceId).hasSize(32); // OpenTelemetry trace IDs are 32 hex characters
            });
        } finally {
            observation.stop();
        }
    }

    @Test
    void shouldExtractSpanIdFromObservation() {
        // Given: An observation with tracing context
        Observation observation = Observation.start("test.operation", observationRegistry);

        try {
            // When: Extracting span ID within the observation scope
            observation.scoped(() -> {
                String spanId = TracingContextExtractor.extractSpanId(observation);

                // Then: Span ID should be extracted
                assertThat(spanId).isNotNull();
                assertThat(spanId).isNotEmpty();
                assertThat(spanId).hasSize(16); // OpenTelemetry span IDs are 16 hex characters
            });
        } finally {
            observation.stop();
        }
    }

    @Test
    void shouldReturnNullWhenObservationIsNull() {
        // When: Extracting from null observation
        String traceId = TracingContextExtractor.extractTraceId(null);
        String spanId = TracingContextExtractor.extractSpanId(null);

        // Then: Should return null
        assertThat(traceId).isNull();
        assertThat(spanId).isNull();
    }

    @Test
    void shouldReturnNullWhenNoTracingContext() {
        // Given: An observation without tracing context
        ObservationRegistry registryWithoutTracing = ObservationRegistry.create();
        Observation observation = Observation.start("test.operation", registryWithoutTracing);

        try {
            // When: Extracting trace and span IDs
            String traceId = TracingContextExtractor.extractTraceId(observation);
            String spanId = TracingContextExtractor.extractSpanId(observation);

            // Then: Should return null
            assertThat(traceId).isNull();
            assertThat(spanId).isNull();
        } finally {
            observation.stop();
        }
    }

    @Test
    void shouldDetectTracingContext() {
        // Given: An observation with tracing context
        Observation observationWithTracing = Observation.start("test.operation", observationRegistry);

        try {
            // When: Checking for tracing context within scope
            observationWithTracing.scoped(() -> {
                boolean hasContext = TracingContextExtractor.hasTracingContext(observationWithTracing);

                // Then: Should detect tracing context
                assertThat(hasContext).isTrue();
            });
        } finally {
            observationWithTracing.stop();
        }
    }

    @Test
    void shouldDetectNoTracingContext() {
        // Given: An observation without tracing context
        ObservationRegistry registryWithoutTracing = ObservationRegistry.create();
        Observation observationWithoutTracing = Observation.start("test.operation", registryWithoutTracing);

        try {
            // When: Checking for tracing context
            boolean hasContext = TracingContextExtractor.hasTracingContext(observationWithoutTracing);

            // Then: Should not detect tracing context
            assertThat(hasContext).isFalse();
        } finally {
            observationWithoutTracing.stop();
        }
    }

    @Test
    void shouldHandleNullObservationInHasTracingContext() {
        // When: Checking null observation
        boolean hasContext = TracingContextExtractor.hasTracingContext(null);

        // Then: Should return false
        assertThat(hasContext).isFalse();
    }

    @Test
    void shouldExtractConsistentIdsFromSameObservation() {
        // Given: An observation with tracing context
        Observation observation = Observation.start("test.operation", observationRegistry);

        try {
            // When: Extracting IDs multiple times
            String traceId1 = TracingContextExtractor.extractTraceId(observation);
            String traceId2 = TracingContextExtractor.extractTraceId(observation);
            String spanId1 = TracingContextExtractor.extractSpanId(observation);
            String spanId2 = TracingContextExtractor.extractSpanId(observation);

            // Then: IDs should be consistent
            assertThat(traceId1).isEqualTo(traceId2);
            assertThat(spanId1).isEqualTo(spanId2);
        } finally {
            observation.stop();
        }
    }

    @Test
    void shouldExtractDifferentSpanIdsForNestedObservations() {
        // Given: A parent observation
        Observation parentObservation = Observation.start("parent.operation", observationRegistry);

        try {
            parentObservation.scoped(() -> {
                String parentSpanId = TracingContextExtractor.extractSpanId(parentObservation);
                String parentTraceId = TracingContextExtractor.extractTraceId(parentObservation);

                // When: Creating a child observation
                Observation childObservation = Observation.createNotStarted("child.operation", observationRegistry)
                        .parentObservation(parentObservation)
                        .start();

                try {
                    childObservation.scoped(() -> {
                        String childSpanId = TracingContextExtractor.extractSpanId(childObservation);
                        String childTraceId = TracingContextExtractor.extractTraceId(childObservation);

                        // Then: Trace IDs should be the same, but span IDs should be different
                        assertThat(childTraceId).isEqualTo(parentTraceId);
                        assertThat(childSpanId).isNotEqualTo(parentSpanId);
                    });
                } finally {
                    childObservation.stop();
                }
            });
        } finally {
            parentObservation.stop();
        }
    }
}

