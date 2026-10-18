package org.fireflyframework.observability.autoconfigure;

import io.micrometer.tracing.Tracer;
import org.fireflyframework.observability.tracing.FireflyTracingSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class TracingBridgeStartupTest {

    @Test
    void defaultBridgeCreatesAnOpenTelemetryTracer() {
        verifyBridge("OTEL", "OtelTracer");
    }

    @Test
    void braveSelectionCreatesOnlyABraveTracer() {
        verifyBridge("BRAVE", "BraveTracer");
    }

    private void verifyBridge(String bridge, String tracerType) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TestApplication.class)
                .web(WebApplicationType.NONE)
                .run("--spring.main.banner-mode=off", "--logging.level.root=OFF",
                        "--firefly.observability.tracing.bridge=" + bridge,
                        "--management.tracing.export.otlp.enabled=false",
                        "--management.tracing.export.zipkin.enabled=false")) {
            assertThat(context.getBeansOfType(Tracer.class)).hasSize(1);
            assertThat(context.getBean(Tracer.class).getClass().getSimpleName()).isEqualTo(tracerType);
            assertThat(context.getBean(FireflyTracingSupport.class)).isNotNull();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
