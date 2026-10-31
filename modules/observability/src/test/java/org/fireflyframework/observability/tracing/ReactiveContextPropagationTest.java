package org.fireflyframework.observability.tracing;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import io.micrometer.tracing.Tracer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.fireflyframework.observability.FireflyObservabilityEnvironmentPostProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import static org.assertj.core.api.Assertions.assertThat;

@Isolated("Reactor Hooks and the actual Micrometer accessor registry are process-wide")
class ReactiveContextPropagationTest {
    private static final Set<String> CONFIGURATIONS = Set.of(
            "org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration",
            "org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration",
            "org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.OpenTelemetryTracingAutoConfiguration",
            "org.springframework.boot.micrometer.tracing.brave.autoconfigure.BraveAutoConfiguration",
            "org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration",
            "org.springframework.boot.reactor.autoconfigure.ReactorAutoConfiguration",
            "org.fireflyframework.observability.tracing.ReactiveContextPropagationAutoConfiguration");

    private boolean previousAutomaticPropagation;
    private List<ThreadLocalAccessor<?>> previousAccessors;

    @BeforeEach
    void isolateOwnedGlobalChanges() {
        previousAutomaticPropagation = Hooks.isAutomaticContextPropagationEnabled();
        previousAccessors = List.copyOf(ContextRegistry.getInstance().getThreadLocalAccessors());
        assertThat(previousAccessors).anyMatch(accessor -> accessor.getClass() == ObservationThreadLocalAccessor.class);
        Hooks.disableAutomaticContextPropagation();
    }

    @AfterEach
    void restoreExactPreviousGlobalState() {
        var registry = ContextRegistry.getInstance();
        for (var accessor : List.copyOf(registry.getThreadLocalAccessors())) registry.removeThreadLocalAccessor(accessor.key());
        for (var accessor : previousAccessors) registry.registerThreadLocalAccessor(accessor);
        if (previousAutomaticPropagation) Hooks.enableAutomaticContextPropagation();
        else Hooks.disableAutomaticContextPropagation();
        assertThat(registry.getThreadLocalAccessors()).containsExactlyElementsOf(previousAccessors);
        assertThat(Hooks.isAutomaticContextPropagationEnabled()).isEqualTo(previousAutomaticPropagation);
    }

    private ApplicationContextRunner runner(String bridge, String... properties) {
        List<String> excluded = new ArrayList<>();
        for (String candidate : ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())) {
            if (!CONFIGURATIONS.contains(candidate)) excluded.add(candidate);
        }
        return new ApplicationContextRunner().withUserConfiguration(TestApplication.class)
                .withPropertyValues("spring.autoconfigure.exclude=" + String.join(",", excluded),
                        "firefly.observability.tracing.bridge=" + bridge)
                .withPropertyValues(properties)
                .withInitializer(context -> new FireflyObservabilityEnvironmentPostProcessor()
                        .postProcessEnvironment(context.getEnvironment(), new SpringApplication()));
    }

    static Stream<Arguments> nativePolicies() {
        return Stream.of("OTEL", "BRAVE").flatMap(bridge -> Stream.of(
                Arguments.of(bridge, new String[]{}, "auto", true),
                Arguments.of(bridge, new String[]{"firefly.observability.context-propagation.reactor-hooks-enabled=false"}, "limited", false),
                Arguments.of(bridge, new String[]{"spring.reactor.context-propagation=limited"}, "limited", false),
                Arguments.of(bridge, new String[]{"spring.reactor.context-propagation=auto",
                        "firefly.observability.context-propagation.reactor-hooks-enabled=false"}, "auto", true)));
    }

    @ParameterizedTest @MethodSource("nativePolicies")
    void nativeModeOwnsActualSpanPropagation(String bridge, String[] properties, String mode, boolean automatic) {
        runner(bridge, properties).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("spring.reactor.context-propagation")).isEqualTo(mode);
            assertThat(Hooks.isAutomaticContextPropagationEnabled()).isEqualTo(automatic);
            Tracer tracer = context.getBean(Tracer.class);
            try (var observed = start(context.getBean(ObservationRegistry.class), tracer)) {
                TraceSample actual = acrossSchedulers(observed.observation(), tracer).block(Duration.ofSeconds(2));
                if (automatic) assertSameTrace(actual, observed.expected());
                else assertThat(actual.traceId()).isNull();
                assertThat(tracer.currentSpan()).isNull();
            }
        });
    }

    @ParameterizedTest @ValueSource(strings = {"OTEL", "BRAVE"})
    void concurrentRealObservationsKeepTheirOwnTraceAcrossSchedulers(String bridge) {
        runner(bridge).run(context -> {
            assertThat(context).hasNotFailed();
            Tracer tracer = context.getBean(Tracer.class);
            try (var first = start(context.getBean(ObservationRegistry.class), tracer);
                 var second = start(context.getBean(ObservationRegistry.class), tracer)) {
                assertThat(first.expected().traceId()).isNotEqualTo(second.expected().traceId());
                var actual = Flux.merge(acrossSchedulers(first.observation(), tracer),
                        acrossSchedulers(second.observation(), tracer)).collectList().block(Duration.ofSeconds(2));
                assertThat(actual).extracting(TraceSample::traceId)
                        .containsExactlyInAnyOrder(first.expected().traceId(), second.expected().traceId());
                assertThat(actual).allSatisfy(sample -> {
                    assertThat(sample.traceId()).matches("[0-9a-f]{32}").isNotEqualTo("0".repeat(32));
                    assertThat(sample.spanId()).matches("[0-9a-f]{16}").isNotEqualTo("0".repeat(16));
                });
                assertThat(tracer.currentSpan()).isNull();
            }
        });
    }

    @ParameterizedTest @ValueSource(strings = {"OTEL", "BRAVE"})
    void nativeAutoWithoutTheActualObservationAccessorCannotInventPropagation(String bridge) {
        runner(bridge, "spring.reactor.context-propagation=auto").run(context -> {
            assertThat(context).hasNotFailed();
            Tracer tracer = context.getBean(Tracer.class);
            try (var observed = start(context.getBean(ObservationRegistry.class), tracer)) {
                assertThat(ContextRegistry.getInstance().removeThreadLocalAccessor(ObservationThreadLocalAccessor.KEY)).isTrue();
                assertThat(acrossSchedulers(observed.observation(), tracer).block(Duration.ofSeconds(2)).traceId()).isNull();
            }
        });
    }

    private static Observed start(ObservationRegistry registry, Tracer tracer) {
        Observation observation = Observation.start("native.reactor.context", registry);
        try (var scope = observation.openScope()) {
            TraceSample sample = TraceSample.capture(tracer);
            assertThat(sample.traceId()).matches("[0-9a-f]{32}").isNotEqualTo("0".repeat(32));
            assertThat(sample.spanId()).matches("[0-9a-f]{16}").isNotEqualTo("0".repeat(16));
            return new Observed(observation, sample);
        } catch (RuntimeException | Error failure) {
            observation.stop();
            throw failure;
        }
    }

    private static Mono<TraceSample> acrossSchedulers(Observation observation, Tracer tracer) {
        return Mono.using(() -> Schedulers.newSingle("native-context-first"), first ->
                Mono.using(() -> Schedulers.newSingle("native-context-second"), second ->
                        Mono.fromSupplier(() -> TraceSample.capture(tracer)).subscribeOn(first)
                                .publishOn(second).map(before -> {
                                    TraceSample after = TraceSample.capture(tracer);
                                    assertThat(after.traceId()).isEqualTo(before.traceId());
                                    assertThat(Thread.currentThread().getName()).startsWith("native-context-second");
                                    return after;
                                }), scheduler -> scheduler.dispose()), scheduler -> scheduler.dispose())
                .contextWrite(context -> context.put(ObservationThreadLocalAccessor.KEY, observation));
    }

    private static void assertSameTrace(TraceSample actual, TraceSample expected) {
        assertThat(actual.traceId()).isEqualTo(expected.traceId());
        assertThat(actual.spanId()).isEqualTo(expected.spanId());
    }

    private record TraceSample(String traceId, String spanId) {
        static TraceSample capture(Tracer tracer) {
            var span = tracer.currentSpan();
            return span == null ? new TraceSample(null, null) : new TraceSample(span.context().traceId(), span.context().spanId());
        }
    }

    private record Observed(Observation observation, TraceSample expected) implements AutoCloseable {
        @Override public void close() { observation.stop(); }
    }

    @EnableAutoConfiguration
    static class TestApplication { }
}
