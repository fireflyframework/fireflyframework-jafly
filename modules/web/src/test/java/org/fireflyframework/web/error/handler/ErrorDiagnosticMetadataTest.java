package org.fireflyframework.web.error.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.web.error.config.ErrorHandlingProperties;
import org.fireflyframework.web.error.converter.ExceptionConverterService;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.fireflyframework.web.error.service.ErrorResponseNegotiator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ErrorDiagnosticMetadataTest {
    private static final Map<String, Object> METADATA = Map.of(
            "sourceClass", "internal.TemplateService", "sourceMethod", "publish",
            "sourceLine", 166, "sourceFile", "TemplateService.java", "exceptionType", "internal.Failure",
            "causeType", "internal.DatabaseFailure", "causeMessage", "private diagnostic",
            "operationId", "synthetic-operation", "retryAfter", 5);

    @ParameterizedTest
    @ValueSource(strings = {"test", "prod", "production"})
    void defaultResponsesOmitDiagnosticCoordinatesButKeepBusinessMetadata(String profile) throws Exception {
        verify(profile, false, false, false, false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "production"})
    void productionOverridesEnabledDiagnosticFlags(String profile) throws Exception {
        verify(profile, true, true, false, false);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nonProductionDiagnosticsAndCauseFollowSeparateExplicitFlags(boolean cause) throws Exception {
        verify("development", true, cause, true, cause);
    }

    private void verify(String profile, boolean debug, boolean cause, boolean expectDebug, boolean expectCause) throws Exception {
        var properties = new ErrorHandlingProperties();
        properties.setIncludeDebugInfo(debug);
        properties.setIncludeExceptionCause(cause);
        var environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        var converter = mock(ExceptionConverterService.class);
        var converted = new BusinessException(HttpStatus.BAD_REQUEST, "SYNTHETIC_INVALID", "Invalid request").withMetadata(METADATA);
        when(converter.convertException(any())).thenReturn(converted);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var handler = new GlobalExceptionHandler(converter, Optional.empty(), properties, Optional.empty(),
                Optional.empty(), environment, mapper, new ErrorResponseNegotiator(mapper), Optional.empty());
        for (Throwable error : java.util.List.of(converted, new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid request"))) {
            var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/synthetic-template").build());
            handler.handle(exchange, error).block();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            var metadata = mapper.readTree(exchange.getResponse().getBodyAsString().block()).path("metadata");
            assertThat(metadata.path("operationId").asText()).isEqualTo("synthetic-operation");
            assertThat(metadata.path("retryAfter").asInt()).isEqualTo(5);
            for (String key : java.util.List.of("sourceClass", "sourceMethod", "sourceLine", "sourceFile", "exceptionType")) {
                assertThat(metadata.has(key)).as(profile + ":" + error.getClass().getSimpleName() + ":" + key).isEqualTo(expectDebug);
            }
            assertThat(metadata.has("causeType")).isEqualTo(expectCause);
            assertThat(metadata.has("causeMessage")).isEqualTo(expectCause);
            assertThat(converted.getMetadata()).containsAllEntriesOf(METADATA);
        }
    }
}
