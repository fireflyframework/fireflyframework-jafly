package org.fireflyframework.observability.tracing;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.Kind;
import io.micrometer.observation.transport.RequestReplyReceiverContext;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.kafka.core.KafkaTemplateProbe;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DistributedTracingTest {

    private InMemorySpanExporter exporter;
    private SdkTracerProvider tracerProvider;
    private ObservationRegistry registry;
    private HttpServer server;
    private final AtomicReference<String> traceparent = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        exporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        io.opentelemetry.api.trace.Tracer otelTracer = openTelemetry.getTracer("firefly-test");
        OtelTracer tracer = new OtelTracer(otelTracer, new OtelCurrentTraceContext(), event -> {});
        OtelPropagator propagator = new OtelPropagator(openTelemetry.getPropagators(), otelTracer);

        registry = ObservationRegistry.create();
        ObservationHandler<Observation.Context> tracing = new ObservationHandler.FirstMatchingCompositeObservationHandler(
                new PropagatingSenderTracingObservationHandler<>(tracer, propagator),
                new PropagatingReceiverTracingObservationHandler<>(tracer, propagator),
                new DefaultTracingObservationHandler(tracer));
        registry.observationConfig()
                .observationHandler(tracing)
                .observationFilter(new HttpErrorStatusObservationFilter());
        TracingRegistry.install(registry);

        traceparent.set(null);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", exchange -> {
            traceparent.set(exchange.getRequestHeaders().getFirst("traceparent"));
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/fail", exchange -> {
            traceparent.set(exchange.getRequestHeaders().getFirst("traceparent"));
            byte[] body = "nope".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        TracingRegistry.reset();
        if (server != null) {
            server.stop(0);
        }
        if (tracerProvider != null) {
            tracerProvider.close();
        }
    }

    @Test
    void webClientBuiltBeforeTheRegistryStillPropagatesTraceparent() {
        TracingRegistry.reset();
        WebClient client = ObservedClients.instrument(WebClient.builder()
                        .clientConnector(new JdkClientHttpConnector())
                        .baseUrl(baseUrl()))
                .build();
        TracingRegistry.install(registry);

        String body = client.get().uri("/ok").retrieve().bodyToMono(String.class).block();

        assertThat(body).isEqualTo("ok");
        assertThat(traceparent.get()).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}");
        SpanData span = singleSpan();
        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(span.getTraceId()).isEqualTo(traceparent.get().split("-")[1]);
        assertThat(span.getStatus().getStatusCode()).isNotEqualTo(StatusCode.ERROR);
    }

    @Test
    void webClientFiveHundredSetsErrorStatusWithoutAnApplicationException() {
        WebClient client = ObservedClients.instrument(WebClient.builder()
                        .clientConnector(new JdkClientHttpConnector())
                        .baseUrl(baseUrl()))
                .build();

        Integer status = client.get().uri("/fail")
                .exchangeToMono(response -> response.releaseBody().thenReturn(response.statusCode().value()))
                .block();

        assertThat(status).isEqualTo(500);
        assertThat(traceparent.get()).startsWith("00-");
        SpanData span = singleSpan();
        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getEvents()).anyMatch(event -> "exception".equals(event.getName()));
    }

    @Test
    void restTemplateFiveHundredIsAnErrorSpan() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(response -> false);
        new ClientObservationBeanPostProcessor(new MockEnvironment())
                .postProcessAfterInitialization(restTemplate, "restTemplate");

        restTemplate.getForEntity(baseUrl() + "/fail", String.class);

        assertThat(traceparent.get()).startsWith("00-");
        SpanData span = singleSpan();
        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getEvents()).anyMatch(event -> "exception".equals(event.getName()));
    }

    @Test
    void serverFiveHundredWithoutThrowableSetsErrorStatus() {
        StatusCarrier carrier = new StatusCarrier(500);
        Observation.createNotStarted("http.server.requests", () -> carrier, registry).start().stop();

        SpanData span = singleSpan();
        assertThat(span.getKind()).isEqualTo(SpanKind.SERVER);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getEvents()).anyMatch(event ->
                "exception".equals(event.getName())
                        && event.getAttributes().asMap().values().stream()
                        .anyMatch(value -> String.valueOf(value).contains("HTTP 500")));
    }

    @Test
    void clientErrorStatusDoesNotMarkTheSpanAsError() {
        StatusCarrier carrier = new StatusCarrier(404);
        Observation.createNotStarted("http.server.requests", () -> carrier, registry).start().stop();

        assertThat(singleSpan().getStatus().getStatusCode()).isNotEqualTo(StatusCode.ERROR);
    }

    @Test
    void unhandledExceptionRecordsTheOriginalFailure() {
        Observation observation = Observation.start("order.process", registry);
        observation.error(new IllegalStateException("boom"));
        observation.stop();

        SpanData span = singleSpan();
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getEvents()).anyMatch(event ->
                "exception".equals(event.getName())
                        && event.getAttributes().asMap().values().stream()
                        .anyMatch(value -> String.valueOf(value).contains("boom")));
    }

    @Test
    void messagingInfrastructureBeansAreObservedByDefault() {
        KafkaTemplateProbe probe = new KafkaTemplateProbe();
        new ClientObservationBeanPostProcessor(new MockEnvironment())
                .postProcessAfterInitialization(probe, "kafka");

        assertThat(probe.enabled).isTrue();
    }

    private SpanData singleSpan() {
        List<SpanData> spans = List.of();
        for (int attempt = 0; attempt < 50; attempt++) {
            spans = List.copyOf(exporter.getFinishedSpanItems());
            if (!spans.isEmpty()) {
                break;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertThat(spans).hasSize(1);
        return spans.get(0);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static final class StatusCarrier extends RequestReplyReceiverContext<String, Object> {
        private StatusCarrier(int status) {
            super((carrier, key) -> null, Kind.SERVER);
            setResponse(new StatusResponse(status));
        }
    }

    private record StatusResponse(int code) {
        public HttpStatusCode getStatusCode() {
            return HttpStatusCode.valueOf(code);
        }
    }
}
