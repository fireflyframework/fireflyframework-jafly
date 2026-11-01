package org.fireflyframework.ecm.adapter.docusign;

import com.docusign.esign.api.EnvelopesApi;
import com.docusign.esign.client.ApiClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;
import org.fireflyframework.ecm.domain.model.esignature.SignatureEnvelope;
import org.fireflyframework.ecm.domain.enums.esignature.EnvelopeStatus;
import org.fireflyframework.ecm.port.document.DocumentContentPort;
import org.fireflyframework.ecm.port.document.DocumentPort;
import org.fireflyframework.ecm.adapter.docusign.authentication.impl.JwtDocuSignAuthenticationImpl;
import org.fireflyframework.ecm.adapter.docusign.client.DocuSignClientSettings;
import org.fireflyframework.ecm.adapter.docusign.client.ManagedDocuSignApiClientImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class DocuSignAuthenticationLifecycleTest {
    @TempDir Path temporary;

    private DocuSignAdapterProperties properties() throws Exception {
        var value = new DocuSignAdapterProperties();
        value.setIntegrationKey("00000000-0000-4000-8000-000000000001");
        value.setUserId("00000000-0000-4000-8000-000000000002");
        value.setAccountId("00000000-0000-4000-8000-000000000003");
        value.setBaseUrl("https://owned.invalid/restapi"); value.setAuthServer("account.owned.invalid");
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        value.setPrivateKey("-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, new byte[]{10})
                .encodeToString(generator.generateKeyPair().getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n");
        return value;
    }

    private ScopedSdkConnector.Scope scope() throws Exception {
        Path service = temporary.resolve("builder-service");
        Files.writeString(service, ScopedSdkConnector.Builder.class.getName() + "\n");
        return new ScopedSdkConnector.Scope(service);
    }

    private static void get(ApiClient client) throws Exception {
        new EnvelopesApi(client).getEnvelope("00000000-0000-4000-8000-000000000003", "controlled-envelope");
    }

    private static long tokens(ScopedSdkConnector.Scope scope) {
        return scope.exchanges.stream().filter(wire -> wire.uri().getPath().endsWith("/oauth/token")).count();
    }

    private static ScopedSdkConnector.Reply token(int ordinal) {
        return new ScopedSdkConnector.Reply(200, "{\"access_token\":\"owned-token-" + ordinal
                + "\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.now();
        void advance(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test void successfulAuthenticationClosesItsTwoClientsAndCachesOnlyTheValidatedToken() throws Exception {
        try (var transport = scope(); var client = new ManagedDocuSignApiClientImpl(DocuSignClientSettings.from(properties()))) {
            assertEquals(1, transport.clients.size()); assertNull(client.getAccessToken());
            get(client);
            assertEquals(3, transport.clients.size());
            assertEquals(2, transport.clients.stream().filter(org.glassfish.jersey.client.JerseyClient::isClosed).count());
            assertEquals(3, transport.responsesClosed.get()); assertEquals("owned-test-token", client.getAccessToken());
            get(client); assertEquals(1, tokens(transport)); assertEquals(3, transport.clients.size());
            assertEquals("Bearer owned-test-token", transport.exchanges.getLast().authorization());
        }
    }

    @Test void newlyBuiltAuthenticationClientsReceiveFiniteConfiguredTimeouts() throws Exception {
        try (var transport = scope()) {
            var value = properties(); value.setConnectionTimeout(Duration.ofMillis(150)); value.setReadTimeout(Duration.ofMillis(250));
            value.setAuthenticationTimeout(Duration.ofSeconds(1)); value.setAuthenticationWaitTimeout(Duration.ofMillis(100));
            try (var client = new ManagedDocuSignApiClientImpl(DocuSignClientSettings.from(value))) {
                get(client);
                for (var wire : transport.exchanges) {
                    assertTrue(wire.connectTimeout() instanceof Integer && (Integer) wire.connectTimeout() > 0 && (Integer) wire.connectTimeout() <= 150);
                    assertTrue(wire.readTimeout() instanceof Integer && (Integer) wire.readTimeout() > 0 && (Integer) wire.readTimeout() <= 250);
                }
            }
        }
    }

    @Test void aCompletedAuthenticationResponsePastItsDeadlineCannotPublishAToken() throws Exception {
        try (var transport = scope()) {
            var value = properties(); value.setAuthenticationTimeout(Duration.ofMillis(200));
            value.setAuthenticationWaitTimeout(Duration.ofMillis(20));
            transport.responder = wire -> {
                if (wire.uri().getPath().endsWith("/oauth/token")) {
                    try { new CountDownLatch(1).await(250, TimeUnit.MILLISECONDS); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
                }
                return transport.defaultReply(wire);
            };
            try (var client = new ManagedDocuSignApiClientImpl(DocuSignClientSettings.from(value))) {
                assertThrows(DocuSignAdapterException.class, () -> get(client));
                assertEquals(1, transport.exchanges.size()); assertNull(client.getAccessToken());
                assertEquals(1, transport.clients.stream().filter(org.glassfish.jersey.client.JerseyClient::isClosed).count());
            }
        }
    }

    @Test void expiryRefreshesOnTheNextOperationWithoutAnAutomaticWriteRetry() throws Exception {
        try (var transport = scope()) {
            var count = new AtomicInteger();
            transport.responder = wire -> wire.uri().getPath().endsWith("/oauth/token") ? token(count.incrementAndGet()) : transport.defaultReply(wire);
            var clock = new MutableClock();
            try (var client = new ManagedDocuSignApiClientImpl(DocuSignClientSettings.from(properties()), clock)) {
                get(client); get(client); assertEquals(1, count.get());
                clock.advance(4000); get(client); assertEquals(2, count.get());
                assertEquals("Bearer owned-token-2", transport.exchanges.getLast().authorization());
            }
        }
    }

    @Test void backwardsWallClockCannotExtendTheMonotonicTokenLifetime() throws Exception {
        try (var transport = scope()) {
            var count = new AtomicInteger();
            transport.responder = wire -> wire.uri().getPath().endsWith("/oauth/token") ? token(count.incrementAndGet()) : transport.defaultReply(wire);
            var settings = DocuSignClientSettings.from(properties()); var clock = new MutableClock(); var ticker = new AtomicLong();
            try (var client = new ManagedDocuSignApiClientImpl(settings)) {
                var authentication = new JwtDocuSignAuthenticationImpl(client, settings, clock, ticker::get);
                assertEquals("owned-token-1", authentication.accessToken());
                clock.advance(-1000); ticker.set(TimeUnit.SECONDS.toNanos(4000));
                assertEquals("owned-token-2", authentication.accessToken()); assertEquals(2, count.get());
            }
        }
    }

    @Test void separateClientsNeverShareAnAuthenticationSession() throws Exception {
        try (var transport = scope()) {
            var count = new AtomicInteger();
            transport.responder = wire -> wire.uri().getPath().endsWith("/oauth/token") ? token(count.incrementAndGet()) : transport.defaultReply(wire);
            try (var first = new ManagedDocuSignApiClientImpl(DocuSignClientSettings.from(properties()));
                 var second = new ManagedDocuSignApiClientImpl(DocuSignClientSettings.from(properties()))) {
                get(first); assertEquals("owned-token-1", first.getAccessToken());
                get(second); assertEquals("owned-token-2", second.getAccessToken()); assertEquals(2, count.get());
            }
        }
    }

    @Test void concurrentRequestsHaveExactlyOneRefreshOwner() throws Exception {
        try (var transport = scope(); var client = new ManagedDocuSignApiClientImpl(DocuSignClientSettings.from(properties()))) {
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            transport.responder = wire -> {
                if (wire.uri().getPath().endsWith("/oauth/token")) {
                    entered.countDown();
                    try { if (!release.await(2, TimeUnit.SECONDS)) throw new AssertionError("Controlled authentication was not released"); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
                }
                return transport.defaultReply(wire);
            };
            var workers = Executors.newFixedThreadPool(2);
            try {
                var first = workers.submit(() -> { get(client); return true; });
                assertTrue(entered.await(1, TimeUnit.SECONDS));
                var second = workers.submit(() -> { get(client); return true; });
                release.countDown(); assertTrue(first.get(2, TimeUnit.SECONDS)); assertTrue(second.get(2, TimeUnit.SECONDS));
                assertEquals(1, tokens(transport)); assertEquals(4, transport.exchanges.size());
            } finally { release.countDown(); workers.shutdownNow(); assertTrue(workers.awaitTermination(2, TimeUnit.SECONDS)); }
        }
    }

    @Test void waitingRefreshIsBoundedAndDoesNotStartASecondAuthentication() throws Exception {
        try (var transport = scope()) {
            var value = properties(); value.setAuthenticationWaitTimeout(Duration.ofMillis(20));
            try (var client = new ManagedDocuSignApiClientImpl(DocuSignClientSettings.from(value))) {
                var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
                transport.responder = wire -> {
                    if (wire.uri().getPath().endsWith("/oauth/token")) {
                        entered.countDown();
                        try { if (!release.await(2, TimeUnit.SECONDS)) throw new AssertionError("Controlled authentication was not released"); }
                        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
                    }
                    return transport.defaultReply(wire);
                };
                var workers = Executors.newFixedThreadPool(2);
                try {
                    var first = workers.submit(() -> { get(client); return true; }); assertTrue(entered.await(1, TimeUnit.SECONDS));
                    var second = workers.submit(() -> { get(client); return true; });
                    var failure = assertThrows(java.util.concurrent.ExecutionException.class, () -> second.get(1, TimeUnit.SECONDS));
                    assertInstanceOf(DocuSignAdapterException.class, failure.getCause());
                    assertEquals(DocuSignAdapterException.Code.BUSY, ((DocuSignAdapterException) failure.getCause()).getCode());
                    assertFalse(first.isDone()); assertEquals(1, tokens(transport)); release.countDown(); assertTrue(first.get(2, TimeUnit.SECONDS));
                } finally { release.countDown(); workers.shutdownNow(); assertTrue(workers.awaitTermination(2, TimeUnit.SECONDS)); }
            }
        }
    }

    @Test void accountMismatchNeverPublishesOrUsesAToken() throws Exception {
        try (var transport = scope(); var client = new ManagedDocuSignApiClientImpl(DocuSignClientSettings.from(properties()))) {
            transport.responder = wire -> wire.uri().getPath().endsWith("/oauth/userinfo")
                    ? new ScopedSdkConnector.Reply(200, "{\"sub\":\"00000000-0000-4000-8000-000000000002\",\"accounts\":[{\"account_id\":\"other-owned-account\"}]}") : transport.defaultReply(wire);
            var failure = assertThrows(DocuSignAdapterException.class, () -> get(client));
            assertEquals(DocuSignAdapterException.Code.AUTHORIZATION, failure.getCode()); assertNull(client.getAccessToken());
            assertEquals(2, transport.exchanges.size()); assertEquals(2, transport.clients.stream().filter(org.glassfish.jersey.client.JerseyClient::isClosed).count());
        }
    }

    @Test void actualSdkAuthenticationErrorIsSafeAndIsNotCachedOrAutomaticallyRetried() throws Exception {
        try (var transport = scope(); var client = new ManagedDocuSignApiClientImpl(DocuSignClientSettings.from(properties()))) {
            transport.responder = wire -> new ScopedSdkConnector.Reply(401, "{\"error\":\"private-provider-body\"}");
            var failure = assertThrows(DocuSignAdapterException.class, () -> get(client));
            assertEquals(DocuSignAdapterException.Code.AUTHENTICATION, failure.getCode()); assertEquals(401, failure.getHttpStatus());
            assertNull(failure.getCause()); assertFalse(failure.getMessage().contains("private-provider-body"));
            assertNull(client.getAccessToken()); assertEquals(1, tokens(transport)); assertEquals(1, transport.responsesClosed.get());
            transport.responder = transport::defaultReply; get(client); assertEquals(2, tokens(transport));
        }
    }

    @Test void wrongOrMissingUserInfoSubjectCannotPublishACredentialForTheConfiguredAccount() throws Exception {
        for (String subject : new String[]{"\"sub\":\"other-owned-user\",", ""}) {
            try (var transport = scope(); var client = new ManagedDocuSignApiClientImpl(DocuSignClientSettings.from(properties()))) {
                transport.responder = wire -> wire.uri().getPath().endsWith("/oauth/userinfo")
                        ? new ScopedSdkConnector.Reply(200, "{" + subject + "\"accounts\":[{\"account_id\":\"00000000-0000-4000-8000-000000000003\"}]}") : transport.defaultReply(wire);
                var failure = assertThrows(DocuSignAdapterException.class, () -> get(client));
                assertNull(failure.getCause()); assertNull(client.getAccessToken()); assertEquals(2, transport.exchanges.size());
            }
        }
    }

    @Test void clientClosureImmediatelyBeforeTokenPublicationCannotReturnACredential() throws Exception {
        try (var transport = scope()) {
            var client = new ManagedDocuSignApiClientImpl(DocuSignClientSettings.from(properties()));
            try {
                var reads = new AtomicInteger();
                Clock closingClock = new Clock() {
                    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
                    @Override public Clock withZone(ZoneId zone) { return this; }
                    @Override public Instant instant() {
                        if (reads.incrementAndGet() == 2) client.close();
                        return Instant.now();
                    }
                };
                var authentication = new JwtDocuSignAuthenticationImpl(client, DocuSignClientSettings.from(properties()),
                        closingClock, System::nanoTime);
                assertThrows(DocuSignAdapterException.class, authentication::accessToken);
                assertNull(client.getAccessToken()); assertEquals(2, transport.exchanges.size());
            } finally { client.close(); }
        }
    }

    @Test void malformedOrInvalidTokenResponsesNeverReachUserInfoOrAnEnvelope() throws Exception {
        for (String reply : new String[]{"{}", "{\"access_token\":\"\",\"expires_in\":3600,\"token_type\":\"Bearer\"}",
                "{\"access_token\":\"owned\",\"expires_in\":0,\"token_type\":\"Bearer\"}",
                "{\"access_token\":\"owned\",\"expires_in\":9223372036854775807,\"token_type\":\"Bearer\"}",
                "{\"access_token\":\"owned\",\"expires_in\":3600,\"token_type\":\"Basic\"}", "{broken",
                "{\"access_token\":\"owned\\r\\ninjected\",\"expires_in\":3600,\"token_type\":\"Bearer\"}",
                "{\"access_token\":\"" + "x".repeat(16385) + "\",\"expires_in\":3600,\"token_type\":\"Bearer\"}"}) {
            try (var transport = scope(); var client = new ManagedDocuSignApiClientImpl(DocuSignClientSettings.from(properties()))) {
                transport.responder = wire -> new ScopedSdkConnector.Reply(200, reply);
                var failure = assertThrows(DocuSignAdapterException.class, () -> get(client));
                assertNull(failure.getCause()); assertNull(client.getAccessToken()); assertEquals(1, transport.exchanges.size());
            }
        }
    }

    @Test void closingTheManagedClientDoesNotDisposeACallerReplacementClient() throws Exception {
        try (var transport = scope()) {
            var client = new ManagedDocuSignApiClientImpl(DocuSignClientSettings.from(properties()));
            var original = (org.glassfish.jersey.client.JerseyClient) client.getHttpClient();
            ApiClient borrowed = new ApiClient(); client.setHttpClient(borrowed.getHttpClient()); client.close();
            assertTrue(original.isClosed());
            assertDoesNotThrow(() -> borrowed.getHttpClient().target("https://owned.invalid"));
            assertThrows(DocuSignAdapterException.class, () -> client.execute(() -> true).block(Duration.ofSeconds(1)));
            assertDoesNotThrow(client::close);
            assertEquals(2, transport.clients.size()); assertTrue(transport.exchanges.isEmpty());
        }
    }

    @Test void closedClientRefusesDirectSdkAuthenticationBeforeCreatingAnotherHttpClient() throws Exception {
        try (var transport = scope()) {
            var client = new ManagedDocuSignApiClientImpl(DocuSignClientSettings.from(properties())); client.close();
            assertThrows(DocuSignAdapterException.class, () -> client.getUserInfo("owned-test-token"));
            assertEquals(1, transport.clients.size()); assertTrue(transport.exchanges.isEmpty());
        }
    }

    @Test void invalidConstructorDependenciesDoNotAllocateAnSdkClient() throws Exception {
        try (var transport = scope()) {
            assertThrows(RuntimeException.class, () -> new ManagedDocuSignApiClientImpl(null));
            assertTrue(transport.clients.isEmpty(), "Invalid settings must fail before SDK construction");
            var settings = DocuSignClientSettings.from(properties());
            assertThrows(RuntimeException.class, () -> new ManagedDocuSignApiClientImpl(settings, null));
            assertTrue(transport.clients.isEmpty(), "Invalid clock must fail before SDK construction");
        }
    }

    @Test void concurrentClientClosureCannotPublishCredentialsOrBuildAnotherAuthenticationClient() throws Exception {
        try (var transport = scope()) {
            var client = new ManagedDocuSignApiClientImpl(DocuSignClientSettings.from(properties()));
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            transport.responder = wire -> {
                if (wire.uri().getPath().endsWith("/oauth/token")) {
                    entered.countDown();
                    try { if (!release.await(2, TimeUnit.SECONDS)) throw new AssertionError("Owned token response was not released"); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
                }
                return transport.defaultReply(wire);
            };
            var worker = Executors.newSingleThreadExecutor();
            try {
                var pending = worker.submit(() -> { get(client); return true; });
                assertTrue(entered.await(1, TimeUnit.SECONDS)); client.close(); release.countDown();
                var failure = assertThrows(java.util.concurrent.ExecutionException.class, () -> pending.get(2, TimeUnit.SECONDS));
                assertInstanceOf(DocuSignAdapterException.class, failure.getCause()); assertNull(client.getAccessToken());
                assertEquals(2, transport.clients.size()); assertEquals(1, transport.exchanges.size());
            } finally { release.countDown(); worker.shutdownNow(); assertTrue(worker.awaitTermination(2, TimeUnit.SECONDS)); client.close(); }
        }
    }

    @Test void failedAuthenticationCleanupPreventsPublicationAndRemainsAReportedFailure() throws Exception {
        try (var transport = scope()) {
            var client = new ManagedDocuSignApiClientImpl(DocuSignClientSettings.from(properties()));
            transport.failConnectorClose = true;
            try {
                var failure = assertThrows(DocuSignAdapterException.class, () -> get(client));
                assertEquals(DocuSignAdapterException.Code.CLEANUP_FAILURE, failure.getCode()); assertNull(client.getAccessToken());
            } finally { transport.failConnectorClose = false; }
            assertThrows(DocuSignAdapterException.class, client::close, "Earlier owned cleanup failure must not become successful cleanup");
        }
    }

    @Test void reportedCleanupFailureKeepsTheClientClosedToFurtherAuthentication() throws Exception {
        try (var transport = scope()) {
            var client = new ManagedDocuSignApiClientImpl(DocuSignClientSettings.from(properties()));
            transport.failConnectorClose = true;
            assertThrows(DocuSignAdapterException.class, () -> get(client));
            transport.failConnectorClose = false;
            int before = transport.exchanges.size();
            assertThrows(DocuSignAdapterException.class, () -> get(client));
            assertEquals(before, transport.exchanges.size(), "Failed owned cleanup cannot be reset by another authentication");
            assertNull(client.getAccessToken());
            assertThrows(DocuSignAdapterException.class, client::close);
        }
    }

    private static DocuSignSignatureEnvelopeAdapterImpl adapter(ApiClient client, DocuSignAdapterProperties properties) {
        var content = org.mockito.Mockito.mock(DocumentContentPort.class);
        var documents = org.mockito.Mockito.mock(DocumentPort.class);
        org.mockito.Mockito.when(content.getContent(DocuSignEnvelopeMappingTest.DOCUMENT)).thenReturn(reactor.core.publisher.Mono.just(DocuSignEnvelopeMappingTest.PDF.clone()));
        org.mockito.Mockito.when(documents.getDocument(DocuSignEnvelopeMappingTest.DOCUMENT)).thenReturn(reactor.core.publisher.Mono.just(
                org.fireflyframework.ecm.domain.model.document.Document.builder().id(DocuSignEnvelopeMappingTest.DOCUMENT)
                        .name("agreement.pdf").mimeType("application/pdf").build()));
        return new DocuSignSignatureEnvelopeAdapterImpl(client, properties, content, documents,
                (id, provider) -> reactor.core.publisher.Mono.just("controlled-envelope"));
    }

    @Test void actualAdapterUsesTheOwnedLaneAndExportsOnlySafeSdkFailures() throws Exception {
        try (var transport = scope()) {
            var value = properties();
            try (var client = new ManagedDocuSignApiClientImpl(DocuSignClientSettings.from(value))) {
                var thread = new AtomicReference<Thread>(); var caller = Thread.currentThread();
                transport.responder = wire -> {
                    if (wire.method().equals("POST") && wire.uri().getPath().endsWith("/envelopes")) {
                        thread.set(Thread.currentThread());
                        return new ScopedSdkConnector.Reply(429, "{\"message\":\"private-provider-body\"}");
                    }
                    return transport.defaultReply(wire);
                };
                var failure = assertThrows(DocuSignAdapterException.class,
                        () -> adapter(client, value).createEnvelope(DocuSignEnvelopeMappingTest.envelope()).block(Duration.ofSeconds(2)));
                assertEquals(DocuSignAdapterException.Code.RATE_LIMITED, failure.getCode());
                assertNull(failure.getCause()); assertFalse(failure.getMessage().contains("private-provider-body"));
                assertNotNull(thread.get()); assertNotSame(caller, thread.get());
            }
        }
    }

    @Test void actualExistsDoesNotTurnProviderAuthorizationFailureIntoFalse() throws Exception {
        try (var transport = scope()) {
            var value = properties();
            try (var client = new ManagedDocuSignApiClientImpl(DocuSignClientSettings.from(value))) {
                transport.responder = wire -> {
                    if (wire.method().equals("POST") && wire.uri().getPath().endsWith("/envelopes"))
                        return new ScopedSdkConnector.Reply(201, "{\"envelopeId\":\"controlled-envelope\",\"status\":\"created\"}");
                    if (wire.uri().getPath().endsWith("/envelopes/controlled-envelope"))
                        return new ScopedSdkConnector.Reply(401, "{\"message\":\"private-provider-body\"}");
                    return transport.defaultReply(wire);
                };
                var port = adapter(client, value);
                var created = port.createEnvelope(DocuSignEnvelopeMappingTest.envelope().toBuilder().id(UUID.randomUUID()).build()).block(Duration.ofSeconds(2));
                assertNotNull(created);
                var failure = assertThrows(DocuSignAdapterException.class, () -> port.existsEnvelope(created.getId()).block(Duration.ofSeconds(2)));
                assertEquals(DocuSignAdapterException.Code.AUTHENTICATION, failure.getCode());
            }
        }
    }

    @Test void actualListDoesNotTurnProviderFailureIntoAnEmptySuccessfulFlux() throws Exception {
        try (var transport = scope()) {
            var value = properties();
            try (var client = new ManagedDocuSignApiClientImpl(DocuSignClientSettings.from(value))) {
                transport.responder = wire -> wire.method().equals("GET") && wire.uri().getPath().endsWith("/envelopes")
                        ? new ScopedSdkConnector.Reply(503, "{\"message\":\"private-provider-body\"}") : transport.defaultReply(wire);
                var failure = assertThrows(DocuSignAdapterException.class,
                        () -> adapter(client, value).getEnvelopesByStatus(EnvelopeStatus.SENT, Instant.parse("2026-10-29T00:00:00Z"), Instant.parse("2026-10-30T00:00:00Z"), 10).collectList().block(Duration.ofSeconds(2)));
                assertEquals(DocuSignAdapterException.Code.PROVIDER_UNAVAILABLE, failure.getCode());
            }
        }
    }

    @Test void ownedExecutionLaneReportsActualSdkErrorsWithoutExportingProviderBodies() throws Exception {
        try (var transport = scope(); var client = new ManagedDocuSignApiClientImpl(DocuSignClientSettings.from(properties()))) {
            transport.responder = wire -> wire.uri().getPath().endsWith("/envelopes/controlled-envelope")
                    ? new ScopedSdkConnector.Reply(429, "{\"errorCode\":\"CONTROLLED_LIMIT\",\"message\":\"private-provider-body\"}") : transport.defaultReply(wire);
            var failure = assertThrows(DocuSignAdapterException.class,
                    () -> client.execute(() -> { get(client); return true; }).block(Duration.ofSeconds(2)));
            assertEquals(DocuSignAdapterException.Code.RATE_LIMITED, failure.getCode());
            assertNull(failure.getCause()); assertFalse(failure.getMessage().contains("private-provider-body"));
        }
    }

    @Test void finiteExecutionQueueRejectsOverflowWithoutRunningAnotherTask() throws Exception {
        try (var transport = scope()) {
            var value = properties(); value.setMaxConcurrentRequests(1); value.setMaxQueuedRequestsPerThread(1);
            try (var client = new ManagedDocuSignApiClientImpl(DocuSignClientSettings.from(value))) {
                var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var calls = new AtomicInteger();
                var first = client.execute(() -> { entered.countDown(); if (!release.await(2, TimeUnit.SECONDS)) throw new AssertionError("Owned task not released"); return true; }).toFuture();
                try {
                    assertTrue(entered.await(1, TimeUnit.SECONDS));
                    var second = client.execute(() -> calls.incrementAndGet()).toFuture();
                    var rejected = client.execute(() -> calls.incrementAndGet()).toFuture();
                    var failure = assertThrows(java.util.concurrent.ExecutionException.class, () -> rejected.get(1, TimeUnit.SECONDS));
                    assertInstanceOf(DocuSignAdapterException.class, failure.getCause());
                    assertEquals(DocuSignAdapterException.Code.BUSY, ((DocuSignAdapterException) failure.getCause()).getCode());
                    assertEquals(0, calls.get()); release.countDown(); assertTrue(first.get(1, TimeUnit.SECONDS)); assertEquals(1, second.get(1, TimeUnit.SECONDS));
                } finally { release.countDown(); }
            }
            assertTrue(transport.exchanges.isEmpty());
        }
    }
}
