package org.fireflyframework.ecm.adapter.docusign;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.fireflyframework.ecm.adapter.docusign.client.DocuSignClientSettings;
import org.fireflyframework.ecm.adapter.docusign.client.ManagedDocuSignApiClient;
import org.glassfish.jersey.client.JerseyClient;
import org.glassfish.jersey.client.JerseyClientBuilder;

/** Supported builder SPI pauses allocation after the managed client's initial open check. */
public final class IndependentClientBuildCloseProbe {
    private static volatile boolean pause;
    private static final CountDownLatch entered = new CountDownLatch(1);
    private static final CountDownLatch release = new CountDownLatch(1);
    private IndependentClientBuildCloseProbe() { }
    public static final class Builder extends JerseyClientBuilder {
        public Builder() { }
        @Override public JerseyClient build() {
            if (pause) {
                entered.countDown();
                try {
                    if (!release.await(2, TimeUnit.SECONDS)) throw new AssertionError("Owned builder was not released");
                } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
            }
            return new ScopedSdkConnector.Builder().build();
        }
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
            var client = new ManagedDocuSignApiClient(DocuSignClientSettings.from(properties));
            var worker = Executors.newSingleThreadExecutor();
            try {
                pause = true;
                var response = worker.submit(() -> client.getUserInfo("owned-test-token"));
                if (!entered.await(1, TimeUnit.SECONDS)) throw new AssertionError("Actual builder preallocation point required");
                client.close(); release.countDown();
                boolean returned = false;
                try { returned = response.get(1, TimeUnit.SECONDS) != null; }
                catch (java.util.concurrent.ExecutionException rejected) {
                    if (!(rejected.getCause() instanceof DocuSignAdapterException)) throw rejected;
                }
                long liveOwnedClients = transport.clients.stream().filter(value -> !value.isClosed()).count();
                System.out.println("{\"lateUserInfoReturned\":" + returned + ",\"liveOwnedClientsAfterClose\":" + liveOwnedClients
                        + ",\"actualUserInfoExchanges\":" + transport.exchanges.size() + "}");
                if (returned || liveOwnedClients != 0)
                    throw new AssertionError("A client constructed across closure must be refused and closed");
            } finally {
                pause = false; release.countDown(); worker.shutdownNow();
                if (!worker.awaitTermination(2, TimeUnit.SECONDS)) throw new AssertionError("Owned worker cleanup required");
                client.close();
            }
        }
    }
}
