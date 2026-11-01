package org.fireflyframework.ecm.adapter.docusign;

import com.docusign.esign.api.EnvelopesApi;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.fireflyframework.ecm.adapter.docusign.client.DocuSignClientSettings;
import org.fireflyframework.ecm.adapter.docusign.client.ManagedDocuSignApiClientImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DocuSignCancellationIsolationTest {
    @TempDir Path temporary;
    private DocuSignClientSettings settings() throws Exception {
        var p = new DocuSignAdapterProperties();
        p.setIntegrationKey("00000000-0000-4000-8000-000000000001");
        p.setUserId("00000000-0000-4000-8000-000000000002");
        p.setAccountId("00000000-0000-4000-8000-000000000003");
        p.setBaseUrl("https://owned.invalid/restapi"); p.setAuthServer("account.owned.invalid");
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        p.setPrivateKey("-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, new byte[]{10})
                .encodeToString(generator.generateKeyPair().getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n");
        return DocuSignClientSettings.from(p);
    }
    private ScopedSdkConnector.Scope scope() throws Exception {
        Path service = temporary.resolve("builder.service");
        Files.writeString(service, ScopedSdkConnector.Builder.class.getName() + "\n");
        return new ScopedSdkConnector.Scope(service);
    }
    private static void awaitDespiteInterrupt(CountDownLatch release, CountDownLatch interrupted) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (true) {
            long left = deadline - System.nanoTime();
            if (left <= 0) throw new AssertionError("Controlled auth exchange release expired");
            try { if (release.await(left, TimeUnit.NANOSECONDS)) return; }
            catch (InterruptedException consumed) { interrupted.countDown(); }
        }
    }
    @Test void cancellingUserInfoCannotPublishAndDoesNotPoisonTheNextSubscription() throws Exception {
        try (var transport = scope(); var client = new ManagedDocuSignApiClientImpl(settings())) {
            var entered = new CountDownLatch(1); var interrupted = new CountDownLatch(1);
            var release = new CountDownLatch(1); var finished = new CountDownLatch(1);
            var first = new AtomicBoolean(true); var emitted = new AtomicBoolean();
            transport.responder = wire -> {
                if (wire.uri().getPath().endsWith("/oauth/userinfo") && first.compareAndSet(true, false)) {
                    entered.countDown(); awaitDespiteInterrupt(release, interrupted);
                }
                return transport.defaultReply(wire);
            };
            var pending = client.execute(() -> {
                try { return new EnvelopesApi(client).getEnvelope("00000000-0000-4000-8000-000000000003", "controlled-envelope"); }
                finally { finished.countDown(); }
            }).subscribe(value -> emitted.set(true), error -> emitted.set(true));
            try {
                assertTrue(entered.await(1, TimeUnit.SECONDS)); pending.dispose();
                assertTrue(interrupted.await(1, TimeUnit.SECONDS)); release.countDown();
                assertTrue(finished.await(1, TimeUnit.SECONDS)); assertNull(client.getAccessToken()); assertFalse(emitted.get());
                assertEquals(0, transport.exchanges.stream().filter(wire -> wire.uri().getPath().contains("/envelopes/")).count());
                assertNotNull(client.execute(() -> new EnvelopesApi(client).getEnvelope("00000000-0000-4000-8000-000000000003", "controlled-envelope")).block(Duration.ofSeconds(2)));
                assertEquals("owned-test-token", client.getAccessToken());
                assertEquals(2, transport.exchanges.stream().filter(wire -> wire.uri().getPath().endsWith("/oauth/token")).count());
            } finally { release.countDown(); pending.dispose(); }
        }
    }
    @Test void cancellingARefreshOwnerDoesNotCancelAnotherSubscriptionsRefresh() throws Exception {
        try (var transport = scope(); var client = new ManagedDocuSignApiClientImpl(settings())) {
            var entered = new CountDownLatch(1); var interrupted = new CountDownLatch(1); var release = new CountDownLatch(1);
            var ownerFinished = new CountDownLatch(1); var waiterEntered = new CountDownLatch(1);
            var first = new AtomicBoolean(true); var ownerEmitted = new AtomicBoolean();
            transport.responder = wire -> {
                if (wire.uri().getPath().endsWith("/oauth/token") && first.compareAndSet(true, false)) {
                    entered.countDown(); awaitDespiteInterrupt(release, interrupted);
                }
                return transport.defaultReply(wire);
            };
            var owner = client.execute(() -> {
                try { return new EnvelopesApi(client).getEnvelope("00000000-0000-4000-8000-000000000003", "controlled-envelope"); }
                finally { ownerFinished.countDown(); }
            }).subscribe(value -> ownerEmitted.set(true), error -> ownerEmitted.set(true));
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            CompletableFuture<com.docusign.esign.model.Envelope> result = new CompletableFuture<>();
            var waiter = client.execute(() -> {
                waiterEntered.countDown();
                return new EnvelopesApi(client).getEnvelope("00000000-0000-4000-8000-000000000003", "controlled-envelope");
            }).subscribe(result::complete, result::completeExceptionally);
            try {
                assertTrue(entered.await(1, TimeUnit.SECONDS)); assertTrue(waiterEntered.await(1, TimeUnit.SECONDS));
                owner.dispose(); assertTrue(interrupted.await(1, TimeUnit.SECONDS)); release.countDown();
                assertTrue(ownerFinished.await(1, TimeUnit.SECONDS)); assertNotNull(result.get(2, TimeUnit.SECONDS));
                assertFalse(ownerEmitted.get()); assertEquals("owned-test-token", client.getAccessToken());
                assertEquals(2, transport.exchanges.stream().filter(wire -> wire.uri().getPath().endsWith("/oauth/token")).count());
                assertEquals(1, transport.exchanges.stream().filter(wire -> wire.uri().getPath().contains("/envelopes/")).count());
            } finally { release.countDown(); owner.dispose(); waiter.dispose(); }
        }
    }
}
