package org.fireflyframework.ecm.adapter.docusign;

import com.docusign.esign.api.EnvelopesApi;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.fireflyframework.ecm.adapter.docusign.client.DocuSignClientSettings;
import org.fireflyframework.ecm.adapter.docusign.client.ManagedDocuSignApiClientImpl;
import org.glassfish.jersey.client.ClientRequest;
import org.glassfish.jersey.client.ClientResponse;
import org.glassfish.jersey.client.JerseyClient;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.glassfish.jersey.client.spi.AsyncConnectorCallback;
import org.glassfish.jersey.client.spi.Connector;

/** Real per-client Jersey SPI close completion crosses the configured authentication deadline. */
public final class IndependentAuthCloseDeadlineProbe {
    private static volatile boolean delayClose;
    private IndependentAuthCloseDeadlineProbe() { }

    public static final class Builder extends JerseyClientBuilder {
        public Builder() { }
        @Override public JerseyClient build() {
            JerseyClient client = new ScopedSdkConnector.Builder().build();
            var configuration = client.getConfiguration();
            var original = configuration.getConnectorProvider();
            configuration.connectorProvider((owner, settings) -> {
                Connector delegate = original.getConnector(owner, settings);
                return new Connector() {
                    @Override public ClientResponse apply(ClientRequest request) { return delegate.apply(request); }
                    @Override public Future<?> apply(ClientRequest request, AsyncConnectorCallback callback) {
                        return delegate.apply(request, callback);
                    }
                    @Override public String getName() { return delegate.getName(); }
                    @Override public void close() {
                        delegate.close();
                        if (delayClose) {
                            try { new CountDownLatch(1).await(300, TimeUnit.MILLISECONDS); }
                            catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
                        }
                    }
                };
            });
            return client;
        }
    }

    private static void get(ManagedDocuSignApiClientImpl client, DocuSignAdapterProperties properties) throws Exception {
        new EnvelopesApi(client).getEnvelope(properties.getAccountId(), "controlled-envelope");
    }

    public static void main(String[] args) throws Exception {
        Path service = Path.of(args[0]); Files.writeString(service, Builder.class.getName() + "\n");
        var properties = new DocuSignAdapterProperties();
        properties.setIntegrationKey("00000000-0000-4000-8000-000000000001");
        properties.setUserId("00000000-0000-4000-8000-000000000002");
        properties.setAccountId("00000000-0000-4000-8000-000000000003");
        properties.setBaseUrl("https://owned.invalid/restapi"); properties.setAuthServer("account.owned.invalid");
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        properties.setPrivateKey("-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, new byte[]{10})
                .encodeToString(generator.generateKeyPair().getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n");
        try (var transport = new ScopedSdkConnector.Scope(service)) {
            try (var warm = new ManagedDocuSignApiClientImpl(DocuSignClientSettings.from(properties))) { get(warm, properties); }
            if (transport.exchanges.size() != 3) throw new AssertionError("Genuine warmup exchanges required");
            properties.setAuthenticationTimeout(Duration.ofMillis(200));
            properties.setAuthenticationWaitTimeout(Duration.ofMillis(20));
            try (var client = new ManagedDocuSignApiClientImpl(DocuSignClientSettings.from(properties))) {
                long before = System.nanoTime(); delayClose = true;
                DocuSignAdapterException rejected = null;
                try { get(client, properties); }
                catch (DocuSignAdapterException error) { rejected = error; }
                finally { delayClose = false; }
                long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before);
                boolean published = client.getAccessToken() != null;
                System.out.println("{\"configuredAuthenticationMillis\":200,\"elapsedMillis\":" + elapsedMillis
                        + ",\"tokenPublishedAfterCloseDeadline\":" + published + "}");
                if (elapsedMillis >= 200 && published)
                    throw new AssertionError("Authentication client close completion past its deadline must not publish a credential");
                if (rejected == null || published) throw new AssertionError("Probe requires an observed late authentication close and refusal");
            }
        }
    }
}
