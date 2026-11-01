package org.fireflyframework.ecm.adapter.docusign;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.docusign.esign.api.EnvelopesApi;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.fireflyframework.ecm.adapter.docusign.client.DocuSignClientSettings;
import org.fireflyframework.ecm.adapter.docusign.client.ManagedDocuSignApiClientImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

class CancelledSdkCallTest {
    @TempDir Path temporary;

    @Test void cancellationNeverPublishesAResultOrLogsALateRawSdkFailure() throws Exception {
        var value = new DocuSignAdapterProperties();
        value.setIntegrationKey("00000000-0000-4000-8000-000000000001");
        value.setUserId("00000000-0000-4000-8000-000000000002");
        value.setAccountId("00000000-0000-4000-8000-000000000003");
        value.setBaseUrl("https://owned.invalid/restapi"); value.setAuthServer("account.owned.invalid");
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        value.setPrivateKey("-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, new byte[]{10})
                .encodeToString(generator.generateKeyPair().getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n");
        Path service = temporary.resolve("builder-service"); Files.writeString(service, ScopedSdkConnector.Builder.class.getName() + "\n");
        var entered = new CountDownLatch(1); var interrupted = new CountDownLatch(1); var release = new CountDownLatch(1);
        var dropped = new CountDownLatch(1); var emitted = new AtomicBoolean();
        var logger = (Logger) LoggerFactory.getLogger("reactor.core.publisher.Operators");
        var captured = new ListAppender<ILoggingEvent>() {
            @Override protected void append(ILoggingEvent event) { super.append(event); dropped.countDown(); }
        };
        captured.start(); logger.addAppender(captured);
        try (var transport = new ScopedSdkConnector.Scope(service); var client = new ManagedDocuSignApiClientImpl(DocuSignClientSettings.from(value))) {
            transport.responder = wire -> {
                if (wire.uri().getPath().endsWith("/envelopes/controlled-envelope")) {
                    entered.countDown(); boolean wasInterrupted = false; long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                    try {
                        while (true) {
                            long left = deadline - System.nanoTime();
                            if (left <= 0) throw new AssertionError("Owned late SDK response was not released");
                            try { if (release.await(left, TimeUnit.NANOSECONDS)) break; }
                            catch (InterruptedException error) { wasInterrupted = true; interrupted.countDown(); }
                        }
                    } finally { if (wasInterrupted) Thread.currentThread().interrupt(); }
                    return new ScopedSdkConnector.Reply(429, "{\"message\":\"private-provider-body\"}");
                }
                return transport.defaultReply(wire);
            };
            var subscription = client.execute(() -> new EnvelopesApi(client).getEnvelope(value.getAccountId(), "controlled-envelope"))
                    .subscribe(result -> emitted.set(true), error -> emitted.set(true));
            try {
                assertTrue(entered.await(1, TimeUnit.SECONDS)); subscription.dispose();
                assertTrue(interrupted.await(1, TimeUnit.SECONDS)); release.countDown();
                assertTrue(dropped.await(1, TimeUnit.SECONDS), "Actual late error path must be observed");
                assertFalse(emitted.get());
                for (var event : captured.list) {
                    String diagnostic = event.getFormattedMessage() + (event.getThrowableProxy() == null ? "" : ThrowableProxyUtil.asString(event.getThrowableProxy()));
                    assertFalse(diagnostic.contains("private-provider-body"), "Cancelled SDK failures must already be safe before Reactor drops them");
                    assertNotNull(event.getThrowableProxy());
                    assertEquals(DocuSignAdapterException.class.getName(), event.getThrowableProxy().getClassName());
                    assertTrue(event.getThrowableProxy().getMessage().contains("RATE_LIMITED"));
                    assertNull(event.getThrowableProxy().getCause());
                }
                assertEquals(3, transport.responsesClosed.get());
                transport.responder = transport::defaultReply;
                assertNotNull(client.execute(() -> new EnvelopesApi(client).getEnvelope(value.getAccountId(), "controlled-envelope")).block(Duration.ofSeconds(1)));
            } finally { release.countDown(); subscription.dispose(); }
        } finally { logger.detachAppender(captured); captured.stop(); }
    }
}
