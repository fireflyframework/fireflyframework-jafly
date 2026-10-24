package org.fireflyframework.web.error.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.fireflyframework.web.error.config.ErrorHandlingProperties;
import org.fireflyframework.web.error.converter.DataAccessExceptionConverter;
import org.fireflyframework.web.error.converter.ExceptionConverterService;
import org.fireflyframework.web.error.service.ErrorResponseNegotiator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class DataAccessResponsePrivacyTest {
    private static final String DRIVER_MESSAGE =
            "executeMany; bad SQL grammar [INSERT INTO private_table (private_column)"
                    + " VALUES ('synthetic-private-database-value')]";

    static Stream<Arguments> responses() {
        return Stream.of("default", "development", "prod", "production")
                .flatMap(profile -> Stream.of("application/json", "application/problem+json")
                        .flatMap(accept -> Stream.of(
                                Arguments.of(profile, accept,
                                        new InvalidDataAccessResourceUsageException(DRIVER_MESSAGE),
                                        "DATABASE_ERROR", "A database error occurred."),
                                Arguments.of(profile, accept,
                                        new TransientDataAccessResourceException(DRIVER_MESSAGE),
                                        "TRANSIENT_DATABASE_ERROR", "A temporary database error occurred."))));
    }

    @ParameterizedTest
    @MethodSource("responses")
    void genericDatabaseResponsesKeepTheirStatusAndCodeWithoutDriverDetails(
            String profile, String accept, DataAccessException exception,
            String code, String message) throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var properties = new ErrorHandlingProperties();
        properties.setIncludeDebugInfo(true);
        properties.setIncludeExceptionCause(true);
        var environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        var converters = new ExceptionConverterService(List.of(new DataAccessExceptionConverter()));
        var handler = new GlobalExceptionHandler(converters, Optional.empty(), properties,
                Optional.empty(), Optional.empty(), environment, mapper,
                new ErrorResponseNegotiator(mapper), Optional.empty());
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/assets")
                .header("Accept", accept).build());

        handler.handle(exchange, exception).block();

        String body = exchange.getResponse().getBodyAsString().block();
        var json = mapper.readTree(body);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(body).contains(code).doesNotContain(
                "private_table", "private_column", "synthetic-private-database-value", "INSERT INTO");
        assertThat(json.path(accept.equals("application/json") ? "message" : "detail").asText())
                .isEqualTo(message);
        assertThat(exception.getMessage()).isEqualTo(DRIVER_MESSAGE);
    }
}
