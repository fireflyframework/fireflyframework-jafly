package org.fireflyframework.common.application.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.http.codec.CodecCustomizer;
import org.springframework.boot.http.codec.autoconfigure.CodecsAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WebFluxJacksonCodecAutoConfigurationTest {

    @Test
    void codecsUseJackson2PropertiesWhenJackson3IsAlsoPresent() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        Jackson2AutoConfiguration.class, WebFluxJacksonCodecAutoConfiguration.class))
                .withPropertyValues(
                        "spring.jackson2.serialization.write-dates-as-timestamps=false",
                        "spring.jackson2.property-naming-strategy=SNAKE_CASE")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ServerCodecConfigurer codecs = ServerCodecConfigurer.create();
                    context.getBean(WebFluxJacksonCodecAutoConfiguration.class)
                            .configureHttpMessageCodecs(codecs);
                    ResolvableType type = ResolvableType.forClass(Payload.class);
                    @SuppressWarnings("unchecked")
                    HttpMessageWriter<Object> writer = (HttpMessageWriter<Object>) codecs.getWriters().stream()
                            .filter(candidate -> candidate.canWrite(type, MediaType.APPLICATION_JSON))
                            .findFirst().orElseThrow();
                    MockServerHttpResponse response = new MockServerHttpResponse();
                    writer.write(Mono.just(new Payload(Instant.parse("2026-10-17T12:00:00Z"))),
                            type, MediaType.APPLICATION_JSON, response, Map.of()).block();

                    assertThat(response.getBodyAsString().block())
                            .isEqualTo("{\"created_at\":\"2026-10-17T12:00:00Z\"}");
                });
    }


    @Test
    void bootCodecsHonorTheJackson2Preference() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
                        Jackson2AutoConfiguration.class, CodecsAutoConfiguration.class))
                .withPropertyValues("spring.http.codecs.preferred-json-mapper=jackson2",
                        "spring.jackson2.serialization.write-dates-as-timestamps=false",
                        "spring.jackson2.property-naming-strategy=SNAKE_CASE")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ServerCodecConfigurer codecs = ServerCodecConfigurer.create();
                    context.getBeansOfType(CodecCustomizer.class).values()
                            .forEach(customizer -> customizer.customize(codecs));
                    ResolvableType type = ResolvableType.forClass(Payload.class);
                    @SuppressWarnings("unchecked")
                    HttpMessageWriter<Object> writer = (HttpMessageWriter<Object>) codecs.getWriters().stream()
                            .filter(candidate -> candidate.canWrite(type, MediaType.APPLICATION_JSON))
                            .findFirst().orElseThrow();
                    MockServerHttpResponse response = new MockServerHttpResponse();
                    writer.write(Mono.just(new Payload(Instant.parse("2026-10-17T12:00:00Z"))),
                            type, MediaType.APPLICATION_JSON, response, Map.of()).block();

                    assertThat(response.getBodyAsString().block())
                            .isEqualTo("{\"created_at\":\"2026-10-17T12:00:00Z\"}");
                });
    }

    record Payload(Instant createdAt) {
    }
}
