package org.fireflyframework.web.error.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.fireflyframework.web.error.config.ErrorHandlingProperties;
import org.fireflyframework.web.error.service.ErrorResponseNegotiator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.support.WebExchangeBindException;

class ValidationRejectedValuePrivacyTest {
    private static final String PRIVATE_INPUT = "synthetic-private-credential-must-not-be-echoed";

    @ParameterizedTest
    @CsvSource({
        "default,application/json", "development,application/json",
        "prod,application/json", "production,application/json",
        "default,application/problem+json", "development,application/problem+json",
        "prod,application/problem+json", "production,application/problem+json"
    })
    void validationKeepsActionableDiagnosticsWithoutEchoingInputOrLoggingTheBindingException(
            String profile, String accept) throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var properties = new ErrorHandlingProperties();
        properties.setIncludeDebugInfo(true);
        properties.setIncludeExceptionCause(true);
        var environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        var handler = new GlobalExceptionHandler(null, Optional.empty(), properties,
                Optional.empty(), Optional.empty(), environment, mapper,
                new ErrorResponseNegotiator(mapper), Optional.empty());

        var binding = new BeanPropertyBindingResult(new Input(PRIVATE_INPUT), "card");
        binding.addError(new FieldError("card", "credential", PRIVATE_INPUT, true,
                new String[] {"NotAccepted"}, null, "Use the processor credential operation"));
        binding.addError(new ObjectError("card", "The card request is invalid"));
        var parameter = new MethodParameter(
                ValidationRejectedValuePrivacyTest.class.getDeclaredMethod("submit", Input.class), 0);
        var exception = new WebExchangeBindException(parameter, binding);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/cards")
                .header("Accept", accept).build());

        var logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        var logs = new ListAppender<ILoggingEvent>();
        logs.start();
        logger.addAppender(logs);
        try {
            handler.handle(exchange, exception).block();
        } finally {
            logger.detachAppender(logs);
            logs.stop();
        }
        String body = exchange.getResponse().getBodyAsString().block();
        var json = mapper.readTree(body);
        var errors = accept.equals("application/json") ? json.path("errors") : json.path("extensions").path("errors");
        String diagnostic = logs.list.stream().map(event -> event.getFormattedMessage()
                + (event.getThrowableProxy() == null ? "" : ThrowableProxyUtil.asString(event.getThrowableProxy())))
                .reduce("", (a, b) -> a + b);

        assertAll(
                () -> assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(body).doesNotContain(PRIVATE_INPUT, "rejectedValue"),
                () -> assertThat(diagnostic).doesNotContain(PRIVATE_INPUT),
                () -> assertThat(errors).hasSize(2),
                () -> assertThat(errors.get(0).path("field").asText()).isEqualTo("credential"),
                () -> assertThat(errors.get(0).path("code").asText()).isEqualTo("NotAccepted"),
                () -> assertThat(errors.get(0).path("message").asText()).isEqualTo("Use the processor credential operation"),
                () -> assertThat(errors.get(0).path("metadata").path("bindingFailure").asBoolean()).isTrue());
        assertThat(binding.getFieldError().getRejectedValue()).isEqualTo(PRIVATE_INPUT);
    }

    static void submit(Input input) {}

    record Input(String credential) {}
}
