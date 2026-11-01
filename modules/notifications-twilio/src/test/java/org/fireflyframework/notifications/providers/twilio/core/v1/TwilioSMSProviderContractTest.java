/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.notifications.providers.twilio.core.v1;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.twilio.Twilio;
import com.twilio.http.*;
import com.twilio.exception.ApiConnectionException;
import org.fireflyframework.notifications.core.services.sms.v1.SMSServiceImpl;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import reactor.test.StepVerifier;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.fireflyframework.notifications.interfaces.dtos.sms.v1.SMSRequestDTO;
import org.fireflyframework.notifications.interfaces.dtos.sms.v1.SMSResponseDTO;
import org.fireflyframework.notifications.interfaces.providers.sms.v1.SMSProvider;
import org.fireflyframework.notifications.providers.twilio.config.v1.TwilioAutoConfiguration;
import org.fireflyframework.notifications.providers.twilio.properties.v1.TwilioProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import static org.assertj.core.api.Assertions.assertThat;

class TwilioSMSProviderContractTest {
    static final String ACCOUNT_A = "AC" + "a".repeat(32);
    static final String ACCOUNT_B = "AC" + "b".repeat(32);
    static final String SID_A = "SM" + "1".repeat(32);
    static final String SID_B = "SM" + "2".repeat(32);
    static final String TOKEN_A = "controlled-token-alpha";
    static final String TOKEN_B = "controlled-token-beta";
    static final Duration WAIT = Duration.ofSeconds(5);

    static class Transport extends HttpClient {
        final List<Request> requests = new CopyOnWriteArrayList<>();
        final String body;
        final int code;
        Transport(String sid) { this("{\"sid\":\"" + sid + "\",\"status\":\"queued\"}", 201); }
        Transport(String body, int code) { this.body = body; this.code = code; }
        @Override public Response makeRequest(Request request) {
            requests.add(request);
            return new Response(body, code);
        }
    }

    static TwilioRestClient client(String account, String token, Transport transport) {
        return new TwilioRestClient.Builder(account, token).httpClient(transport).build();
    }
    static TwilioProperties properties() {
        var properties = new TwilioProperties();
        properties.setAccountSid(ACCOUNT_A); properties.setAuthToken(TOKEN_A); properties.setPhoneNumber(" +15555550101 ");
        return properties;
    }
    ApplicationContextRunner runner(String account, String token, TwilioRestClient client) {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(TwilioAutoConfiguration.class))
                .withBean(TwilioRestClient.class, () -> client)
                .withPropertyValues("firefly.notifications.sms.provider=twilio", "firefly.notifications.twilio.account-sid=" + account,
                        "firefly.notifications.twilio.auth-token=" + token, "firefly.notifications.twilio.phone-number= +15555550101 ");
    }
    static SMSRequestDTO request() { return new SMSRequestDTO(" +15555550100 ", "  hello & 🦋  "); }
    @AfterEach void resetGlobalTestControl() { Twilio.setRestClient(null); }

    @Test void unselectedUnconfiguredModuleDoesNotCreateAProviderOrChangeTheGlobalClient() {
        var transport = new Transport(SID_B); var global = client(ACCOUNT_B, TOKEN_B, transport); Twilio.setRestClient(global);
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(TwilioAutoConfiguration.class)).run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(SMSProvider.class).doesNotHaveBean(TwilioRestClient.class);
            assertThat(Twilio.getRestClient()).isSameAs(global); assertThat(transport.requests).isEmpty();
        });
    }
    @Test void selectedContextDoesNotInitializeTheSdkGlobalClient() {
        var transport = new Transport(SID_A); var global = client(ACCOUNT_B, TOKEN_B, new Transport(SID_B)); Twilio.init(ACCOUNT_B, TOKEN_B); Twilio.setRestClient(global);
        runner(ACCOUNT_A, TOKEN_A, client(ACCOUNT_A, TOKEN_A, transport)).run(context -> {
            assertThat(context).hasNotFailed(); assertThat(Twilio.getRestClient()).isSameAs(global); assertThat(transport.requests).isEmpty();
        });
    }
    @Test void actualSdkRequestUsesTheInjectedInstanceAndPreservesTrimmedFormAndExactSid() {
        var transport = new Transport(SID_A); var trap = new Transport(SID_B);
        runner(ACCOUNT_A, TOKEN_A, client(ACCOUNT_A, TOKEN_A, transport)).run(context -> {
            assertThat(context).hasNotFailed(); Twilio.setRestClient(client(ACCOUNT_B, TOKEN_B, trap));
            var result = context.getBean(SMSProvider.class).sendSMS(request()).block(WAIT);
            assertThat(result.getStatus()).isEqualTo("SENT"); assertThat(result.getMessageId()).isEqualTo(SID_A); assertThat(result.getErrorMessage()).isNull();
            assertThat(transport.requests).hasSize(1); assertThat(trap.requests).isEmpty(); var sent = transport.requests.getFirst();
            assertThat(sent.getMethod()).isEqualTo(HttpMethod.POST); assertThat(sent.getUrl()).endsWith("/2010-04-01/Accounts/" + ACCOUNT_A + "/Messages.json");
            assertThat(sent.getUsername()).isEqualTo(ACCOUNT_A); assertThat(sent.getPassword()).isEqualTo(TOKEN_A);
            assertThat(sent.getPostParams()).containsEntry("To", List.of("+15555550100")).containsEntry("From", List.of("+15555550101")).containsEntry("Body", List.of("hello & 🦋"));
        });
    }
    @Test void twoApplicationContextsKeepTheirClientsAndCredentialsIsolatedDuringInterleavedSends() {
        var alpha = new Transport(SID_A); var beta = new Transport(SID_B); var trap = new Transport("SM" + "3".repeat(32));
        runner(ACCOUNT_A, TOKEN_A, client(ACCOUNT_A, TOKEN_A, alpha)).run(first ->
                runner(ACCOUNT_B, TOKEN_B, client(ACCOUNT_B, TOKEN_B, beta)).run(second -> {
                    assertThat(first).hasNotFailed(); assertThat(second).hasNotFailed(); Twilio.setRestClient(client("AC" + "c".repeat(32), "controlled-trap", trap));
                    var a = first.getBean(SMSProvider.class); var b = second.getBean(SMSProvider.class);
                    var results = Flux.range(0, 16).flatMap(index -> (index % 2 == 0 ? a : b).sendSMS(request())
                            .doOnNext(value -> assertThat(value.getMessageId()).isEqualTo(index % 2 == 0 ? SID_A : SID_B)), 4).collectList().block(WAIT);
                    assertThat(results).hasSize(16); assertThat(alpha.requests).hasSize(8); assertThat(beta.requests).hasSize(8); assertThat(trap.requests).isEmpty();
                    assertThat(alpha.requests).allMatch(value -> value.getUsername().equals(ACCOUNT_A) && value.getPassword().equals(TOKEN_A));
                    assertThat(beta.requests).allMatch(value -> value.getUsername().equals(ACCOUNT_B) && value.getPassword().equals(TOKEN_B));
                }));
    }
    @Test void initializationDoesNotLogAccountCredentialsOrSender() {
        var logger = (Logger) LoggerFactory.getLogger(TwilioAutoConfiguration.class); var appender = new ListAppender<ILoggingEvent>(); appender.start(); logger.addAppender(appender);
        try {
            runner(ACCOUNT_A, TOKEN_A, client(ACCOUNT_A, TOKEN_A, new Transport(SID_A))).run(context -> assertThat(context).hasNotFailed());
            assertThat(appender.list).allSatisfy(event -> assertThat(event.getFormattedMessage()).doesNotContain(ACCOUNT_A, TOKEN_A, "+15555550101"));
        } finally { logger.detachAppender(appender); appender.stop(); }
    }
    @Test void selectedUnconfiguredContextFailsWithoutIssuingARequest() {
        var trap = new Transport(SID_B); Twilio.setRestClient(client(ACCOUNT_B, TOKEN_B, trap));
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(TwilioAutoConfiguration.class))
                .withPropertyValues("firefly.notifications.sms.provider=twilio").run(context -> assertThat(context).hasFailed());
        assertThat(trap.requests).isEmpty();
    }
    @Test void legacyConstructorStillValidatesANullRequestWithoutInitializingAClient() {
        var result = new TwilioSMSProviderImpl(new TwilioProperties()).sendSMS(null).block(WAIT);
        assertThat(result.getStatus()).isEqualTo("FAILED"); assertThat(result.getErrorMessage()).isEqualTo("Request cannot be null");
    }
    @ParameterizedTest @ValueSource(strings={"RECIPIENT", "BODY", "SENDER"})
    void originalValidationFailuresNeverIssueSdkRequests(String field) {
        var transport = new Transport(SID_A); Twilio.setRestClient(client(ACCOUNT_A, TOKEN_A, transport)); var properties = properties(); var request = request();
        if (field.equals("RECIPIENT")) request.setPhoneNumber(" "); if (field.equals("BODY")) request.setMessage(" "); if (field.equals("SENDER")) properties.setPhoneNumber(" ");
        var result = new TwilioSMSProviderImpl(properties).sendSMS(request).block(WAIT); assertThat(result.getStatus()).isEqualTo("FAILED"); assertThat(result.getErrorMessage()).isNotBlank(); assertThat(transport.requests).isEmpty();
    }
    void exercise(Transport transport, java.util.function.Consumer<SMSProvider> action) {
        var instance = client(ACCOUNT_A, TOKEN_A, transport);
        runner(ACCOUNT_A, TOKEN_A, instance).run(context -> {
            assertThat(context).hasNotFailed();
            Twilio.setRestClient(instance);
            action.accept(context.getBean(SMSProvider.class));
        });
    }
    @ParameterizedTest @ValueSource(strings={"queued", "sending", "sent", "delivered", "accepted", "scheduled", "read"})
    void everyKnownAcceptedOutboundStatusMapsOnlyToApiAcceptance(String status) {
        var transport = new Transport("{\"sid\":\"" + SID_A + "\",\"status\":\"" + status + "\"}", 201);
        exercise(transport, provider -> {
            var result = provider.sendSMS(request()).block(WAIT);
            assertThat(result.getStatus()).isEqualTo("SENT"); assertThat(result.getMessageId()).isEqualTo(SID_A); assertThat(result.getErrorMessage()).isNull();
        });
    }
    @ParameterizedTest @ValueSource(strings={"failed", "undelivered", "canceled", "partially_delivered"})
    void knownFailedOrPartialStatusesPreserveSidWithoutClaimingSuccess(String status) {
        var transport = new Transport("{\"sid\":\"" + SID_A + "\",\"status\":\"" + status + "\",\"error_message\":\"" + TOKEN_A + "\"}", 201);
        exercise(transport, provider -> {
            var result = provider.sendSMS(request()).block(WAIT);
            assertThat(result.getStatus()).isEqualTo("FAILED"); assertThat(result.getMessageId()).isEqualTo(SID_A);
            assertThat(result.getErrorMessage()).isEqualTo("Twilio SMS provider reported a failure");
        });
    }
    @ParameterizedTest @ValueSource(strings={"receiving", "received"})
    void inboundStatusesCannotMasqueradeAsAcceptedOutboundMessages(String status) {
        var transport = new Transport("{\"sid\":\"" + SID_A + "\",\"status\":\"" + status + "\"}", 201);
        exercise(transport, provider -> {
            var result = provider.sendSMS(request()).block(WAIT);
            assertThat(result.getStatus()).isEqualTo("FAILED"); assertThat(result.getMessageId()).isEqualTo(SID_A);
            assertThat(result.getErrorMessage()).isEqualTo("Twilio SMS provider response is invalid");
        });
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"future-unrecognized-status"})
    void missingOrUnrecognizedStatusDoesNotProduceSent(String status) {
        var transport = new Transport("{\"sid\":\"" + SID_A + "\",\"status\":" + (status == null ? "null" : "\"" + status + "\"") + "}", 201);
        exercise(transport, provider -> {
            var result = provider.sendSMS(request()).block(WAIT);
            assertThat(result.getStatus()).isEqualTo("FAILED"); assertThat(result.getMessageId()).isEqualTo(SID_A);
            assertThat(result.getErrorMessage()).isEqualTo("Twilio SMS provider response is invalid");
        });
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" "})
    void missingOrBlankSidCannotProduceSentOrAnInventedMessageId(String sid) {
        var transport = new Transport("{\"sid\":" + (sid == null ? "null" : "\"" + sid + "\"") + ",\"status\":\"queued\"}", 201);
        exercise(transport, provider -> {
            var result = provider.sendSMS(request()).block(WAIT);
            assertThat(result.getStatus()).isEqualTo("FAILED"); assertThat(result.getMessageId()).isNull();
            assertThat(result.getErrorMessage()).isEqualTo("Twilio SMS provider response is invalid");
        });
    }
    @ParameterizedTest @ValueSource(strings={"API", "NULL_RESPONSE", "MALFORMED", "TRANSPORT"})
    void genuineSdkErrorsStayReactiveButDoNotExposeProviderTextOrRawCauses(String failure) {
        String marker = TOKEN_A + " +15555550100 body-private-marker";
        Transport transport = switch (failure) {
            case "API" -> new Transport("{\"code\":21610,\"status\":400,\"message\":\"" + marker + "\",\"more_info\":\"https://example.invalid/\"}", 400);
            case "MALFORMED" -> new Transport("not-json-" + marker, 201);
            case "NULL_RESPONSE" -> new Transport(SID_A) { @Override public Response makeRequest(Request value) { requests.add(value); return null; } };
            default -> new Transport(SID_A) { @Override public Response makeRequest(Request value) { requests.add(value); throw new ApiConnectionException(marker); } };
        };
        exercise(transport, provider -> {
            StepVerifier.create(provider.sendSMS(request())).expectErrorSatisfies(error ->
                    assertThat(error).isInstanceOf(IllegalStateException.class).hasMessage("Twilio SMS request failed").hasNoCause()).verify(WAIT);
            var result = new SMSServiceImpl(provider).sendSMS(request()).block(WAIT);
            assertThat(result.getStatus()).isEqualTo("FAILED"); assertThat(result.getErrorMessage()).isEqualTo("Twilio SMS request failed");
            assertThat(result.getMessageId()).isNull();
        });
    }
    @Test void explicitSmsProviderOverrideDoesNotRequireUnusedTwilioCredentials() {
        SMSProvider custom = request -> Mono.just(SMSResponseDTO.error("controlled-custom-provider"));
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(TwilioAutoConfiguration.class))
                .withBean(SMSProvider.class, () -> custom).withPropertyValues("firefly.notifications.sms.provider=twilio")
                .run(context -> { assertThat(context).hasNotFailed(); assertThat(context.getBean(SMSProvider.class)).isSameAs(custom); });
    }
    @Test void defaultInstanceClientUsesBoundPropertiesWithoutChangingTheSdkGlobal() {
        var global = client(ACCOUNT_B, TOKEN_B, new Transport(SID_B)); Twilio.setRestClient(global);
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(TwilioAutoConfiguration.class))
                .withPropertyValues("firefly.notifications.sms.provider=twilio", "firefly.notifications.twilio.account-sid=" + ACCOUNT_A,
                        "firefly.notifications.twilio.auth-token=" + TOKEN_A, "firefly.notifications.twilio.phone-number=+15555550101")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(TwilioRestClient.class);
                    assertThat(context.getBean(TwilioProperties.class).getAccountSid()).isEqualTo(ACCOUNT_A);
                    assertThat(context.getBean(TwilioRestClient.class).getAccountSid()).isEqualTo(ACCOUNT_A);
                    assertThat(Twilio.getRestClient()).isSameAs(global);
                });
    }

}
