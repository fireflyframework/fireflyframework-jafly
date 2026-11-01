package org.fireflyframework.ecm.adapter.docusign;

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

/** Real SDK/Jersey fixture; a cancelled token response finishes after consuming interruption. */
public final class IndependentAuthCancellationProbe {
    private IndependentAuthCancellationProbe() { }
    public static void main(String[] args) throws Exception {
        Path service = Path.of(args[0]);
        Files.writeString(service, ScopedSdkConnector.Builder.class.getName() + "\n");
        var properties = new DocuSignAdapterProperties();
        properties.setIntegrationKey("00000000-0000-4000-8000-000000000001");
        properties.setUserId("00000000-0000-4000-8000-000000000002");
        properties.setAccountId("00000000-0000-4000-8000-000000000003");
        properties.setBaseUrl("https://owned.invalid/restapi");
        properties.setAuthServer("account.owned.invalid");
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        properties.setPrivateKey("-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, new byte[]{10})
                .encodeToString(generator.generateKeyPair().getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n");
        var entered = new CountDownLatch(1); var interrupted = new CountDownLatch(1);
        var release = new CountDownLatch(1); var completed = new CountDownLatch(1);
        var emitted = new AtomicBoolean();
        try (var transport = new ScopedSdkConnector.Scope(service);
             var client = new ManagedDocuSignApiClientImpl(DocuSignClientSettings.from(properties))) {
            transport.responder = wire -> {
                if (wire.uri().getPath().endsWith("/oauth/token")) {
                    entered.countDown(); long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                    while (true) {
                        long left = deadline - System.nanoTime();
                        if (left <= 0) throw new AssertionError("Owned authentication response release expired");
                        try { if (release.await(left, TimeUnit.NANOSECONDS)) break; }
                        catch (InterruptedException ignored) { interrupted.countDown(); }
                    }
                    Thread.interrupted();
                }
                return transport.defaultReply(wire);
            };
            var pending = client.execute(() -> {
                try { return new EnvelopesApi(client).getEnvelope(properties.getAccountId(), "controlled-envelope"); }
                finally { completed.countDown(); }
            }).subscribe(value -> emitted.set(true), error -> emitted.set(true));
            try {
                if (!entered.await(1, TimeUnit.SECONDS)) throw new AssertionError("Actual token exchange was not entered");
                pending.dispose();
                if (!interrupted.await(1, TimeUnit.SECONDS)) throw new AssertionError("Actual cancellation interruption was not observed");
                release.countDown();
                if (!completed.await(1, TimeUnit.SECONDS)) throw new AssertionError("Late owned call did not finish");
                boolean published = client.getAccessToken() != null;
                long envelopeCalls = transport.exchanges.stream().filter(wire -> wire.uri().getPath().contains("/envelopes/")).count();
                System.out.println("{\"cancelObserved\":true,\"tokenPublishedAfterCancellation\":" + published
                        + ",\"envelopeCallsAfterCancellation\":" + envelopeCalls + ",\"subscriberEmitted\":" + emitted.get() + "}");
                if (published) throw new AssertionError("Cancelled authentication must not publish a late credential");
            } finally { release.countDown(); pending.dispose(); }
        }
    }
}
