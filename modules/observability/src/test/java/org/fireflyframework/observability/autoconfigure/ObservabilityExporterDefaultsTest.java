package org.fireflyframework.observability.autoconfigure;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.fireflyframework.observability.FireflyObservabilityEnvironmentPostProcessorImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityExporterDefaultsTest {
    private static final Set<String> CONFIGURATIONS = Set.of(
            "org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration",
            "org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration",
            "org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.OpenTelemetryTracingAutoConfiguration",
            "org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingAutoConfiguration",
            "org.springframework.boot.micrometer.tracing.brave.autoconfigure.BraveAutoConfiguration",
            "org.springframework.boot.micrometer.tracing.brave.autoconfigure.zipkin.ZipkinWithBraveTracingAutoConfiguration",
            "org.springframework.boot.zipkin.autoconfigure.ZipkinAutoConfiguration",
            "org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration",
            "org.springframework.boot.opentelemetry.autoconfigure.logging.OpenTelemetryLoggingAutoConfiguration",
            "org.springframework.boot.opentelemetry.autoconfigure.logging.otlp.OtlpLoggingAutoConfiguration",
            "org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration",
            "org.springframework.boot.micrometer.metrics.autoconfigure.export.prometheus.PrometheusMetricsExportAutoConfiguration");

    private ApplicationContextRunner runner(String bridge, String... properties) {
        List<String> excluded = new ArrayList<>();
        for (String candidate : ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())) {
            if (!CONFIGURATIONS.contains(candidate)) excluded.add(candidate);
        }
        return new ApplicationContextRunner().withUserConfiguration(TestApplication.class)
                .withPropertyValues("spring.autoconfigure.exclude=" + String.join(",", excluded),
                        "firefly.observability.tracing.bridge=" + bridge)
                .withPropertyValues(properties)
                .withInitializer(context -> new FireflyObservabilityEnvironmentPostProcessorImpl()
                        .postProcessEnvironment(context.getEnvironment(), new SpringApplication()));
    }

    @ParameterizedTest @ValueSource(strings = {"OTEL", "BRAVE"})
    void defaultsKeepGenuineLocalSpansW3cAndPrometheusWithoutExporters(String bridge) {
        runner(bridge).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("management.tracing.export.enabled")).isNull();
            assertThat(context.getEnvironment().getProperty("management.tracing.export.otlp.enabled")).isEqualTo("false");
            assertThat(context.getEnvironment().getProperty("management.tracing.export.zipkin.enabled")).isEqualTo("false");
            assertThat(context.getEnvironment().getProperty("management.logging.export.otlp.enabled")).isEqualTo("false");
            assertThat(context).doesNotHaveBean(SpanExporter.class).doesNotHaveBean(LogRecordExporter.class);
            Object logWorker = ReflectionTestUtils.getField(context.getBean(BatchLogRecordProcessor.class), "worker");
            assertThat(ReflectionTestUtils.getField(logWorker, "logRecordExporter").getClass().getSimpleName())
                    .isEqualTo("NoopLogRecordExporter");
            if (bridge.equals("OTEL")) {
                Object spanWorker = ReflectionTestUtils.getField(context.getBean(BatchSpanProcessor.class), "worker");
                Object exporter = ReflectionTestUtils.getField(spanWorker, "spanExporter");
                assertThat((Collection<?>) ReflectionTestUtils.getField(exporter, "exporters")).isEmpty();
            }
            assertThat(context.getBeansOfType(Tracer.class)).hasSize(1);
            Tracer tracer = context.getBean(Tracer.class);
            assertThat(tracer.getClass().getSimpleName()).isEqualTo(bridge.equals("OTEL") ? "OtelTracer" : "BraveTracer");
            var span = tracer.nextSpan().name("local-only").start();
            try (var scope = tracer.withSpan(span)) {
                String traceId = span.context().traceId();
                String spanId = span.context().spanId();
                assertThat(traceId).matches("[0-9a-f]{32}").isNotEqualTo("0".repeat(32));
                assertThat(spanId).matches("[0-9a-f]{16}").isNotEqualTo("0".repeat(16));
                assertThat(tracer.currentSpan().context().traceId()).isEqualTo(traceId);
                Map<String, String> carrier = new LinkedHashMap<>();
                context.getBean(Propagator.class).inject(span.context(), carrier, Map::put);
                assertThat(carrier.get("traceparent")).as("W3C header in carrier keys %s", carrier.keySet())
                        .startsWith("00-" + traceId + "-" + spanId + "-");
            } finally { span.end(); }
            var registry = context.getBean(PrometheusMeterRegistry.class);
            registry.counter("local.export.defaults").increment();
            assertThat(registry.scrape()).containsPattern("(?m)^local_export_defaults_total(?:\\{[^\\n]*\\})? 1\\.0$");
        });
    }

    @Test void exporterEndpointAloneDoesNotEnableRemoteExport() {
        runner("OTEL", "management.opentelemetry.tracing.export.otlp.endpoint=http://collector.invalid:4318/v1/traces",
                "management.opentelemetry.logging.export.otlp.endpoint=http://collector.invalid:4318/v1/logs")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(SpanExporter.class).doesNotHaveBean(LogRecordExporter.class));
    }

    @Test void globalTraceExportFlagDoesNotOverrideSpecificDefaults() {
        runner("OTEL", "management.tracing.export.enabled=true", "management.opentelemetry.tracing.export.otlp.transport=http",
                "management.opentelemetry.tracing.export.otlp.endpoint=http://collector.invalid:4318/v1/traces")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(SpanExporter.class));
    }

    @Test void specificTraceOptInCreatesRealExporter() {
        runner("OTEL", "management.tracing.export.otlp.enabled=true", "management.opentelemetry.tracing.export.otlp.transport=http",
                "management.opentelemetry.tracing.export.otlp.endpoint=http://collector.invalid:4318/v1/traces")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(SpanExporter.class);
                    assertThat(context.getEnvironment().getProperty("management.tracing.export.enabled")).isNull();
                });
    }

    @Test void specificFalseOverridesGlobalTraceOptIn() {
        runner("OTEL", "management.tracing.export.enabled=true", "management.tracing.export.otlp.enabled=false")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(SpanExporter.class));
    }

    @ParameterizedTest @ValueSource(strings = {"OTEL", "BRAVE"})
    void explicitGlobalFalsePreservesBootNoPropagationSemantics(String bridge) {
        runner(bridge, "management.tracing.export.enabled=false").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(SpanExporter.class);
            Tracer tracer = context.getBean(Tracer.class);
            var span = tracer.nextSpan().start();
            try {
                Map<String, String> carrier = new LinkedHashMap<>();
                context.getBean(Propagator.class).inject(span.context(), carrier, Map::put);
                assertThat(carrier).isEmpty();
                assertThat(context.getEnvironment().getProperty("management.tracing.export.enabled")).isEqualTo("false");
            } finally { span.end(); }
        });
    }

    @Test void explicitLoggingOptInCreatesRealExporter() {
        runner("OTEL", "management.logging.export.otlp.enabled=true",
                "management.opentelemetry.logging.export.otlp.endpoint=http://collector.invalid:4318/v1/logs")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(LogRecordExporter.class).hasSingleBean(BatchLogRecordProcessor.class));
    }

    @Test void explicitLoggingFalseWinsWithEndpoint() {
        runner("OTEL", "management.logging.export.otlp.enabled=false",
                "management.opentelemetry.logging.export.otlp.endpoint=http://collector.invalid:4318/v1/logs")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(LogRecordExporter.class));
    }

    @Test void explicitProtocolAndEndpointAliasesRemainAvailableWithoutEnablingExport() {
        runner("OTEL", "OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf", "OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://alias.invalid:4318/v1/traces")
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(SpanExporter.class);
                    assertThat(context.getEnvironment().getProperty("management.opentelemetry.tracing.export.otlp.transport")).isEqualTo("http");
                    assertThat(context.getEnvironment().getProperty("management.opentelemetry.tracing.export.otlp.endpoint")).isEqualTo("http://alias.invalid:4318/v1/traces");
                });
    }

    @Test void perExporterOptInRetainsProtocolAndEndpointAliases() {
        runner("OTEL", "management.tracing.export.otlp.enabled=true", "OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf",
                "OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://alias.invalid:4318/v1/traces").run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(SpanExporter.class);
                    var details = context.getBean(org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingConnectionDetails.class);
                    assertThat(details.getUrl(org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.Transport.HTTP))
                            .isEqualTo("http://alias.invalid:4318/v1/traces");
                });
    }

    @ParameterizedTest @ValueSource(strings = {"export-opt-in", "export-opt-out"})
    void genuineConfigDataProfileOverridesLowPrecedenceDefaults(String profile) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        environment.setProperty("spring.config.location", "classpath:exporter-defaults-profiles.yml");
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        new FireflyObservabilityEnvironmentPostProcessorImpl().postProcessEnvironment(environment, new SpringApplication());
        assertThat(environment.getProperty("management.tracing.export.otlp.enabled")).isEqualTo(profile.equals("export-opt-in") ? "true" : "false");
        assertThat(environment.getProperty("management.logging.export.otlp.enabled")).isEqualTo(profile.equals("export-opt-in") ? "true" : "false");
    }

    @EnableAutoConfiguration
    static class TestApplication { }
}
