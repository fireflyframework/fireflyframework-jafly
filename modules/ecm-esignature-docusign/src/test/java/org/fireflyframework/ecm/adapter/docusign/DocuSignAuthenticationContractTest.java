package org.fireflyframework.ecm.adapter.docusign;

import com.auth0.jwt.JWT;
import com.docusign.esign.api.EnvelopesApi;
import com.docusign.esign.client.ApiClient;
import org.fireflyframework.ecm.adapter.docusign.client.DocuSignClientSettings;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;

class DocuSignAuthenticationContractTest {
    @TempDir Path temporary;

    private DocuSignAdapterProperties properties() throws Exception {
        DocuSignAdapterProperties value = new DocuSignAdapterProperties();
        value.setIntegrationKey("00000000-0000-4000-8000-000000000001");
        value.setUserId("00000000-0000-4000-8000-000000000002");
        value.setAccountId("00000000-0000-4000-8000-000000000003");
        value.setBaseUrl("https://owned.invalid/restapi");
        value.setAuthServer("https://account.owned.invalid");
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        value.setPrivateKey("-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, new byte[]{10})
                .encodeToString(generator.generateKeyPair().getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n");
        return value;
    }

    private ScopedSdkConnector.Scope scope() throws Exception {
        Path service = temporary.resolve("builder-service");
        Files.writeString(service, ScopedSdkConnector.Builder.class.getName() + "\n");
        return new ScopedSdkConnector.Scope(service);
    }

    private static void closeManaged(ApiClient client) throws Exception {
        if (client instanceof AutoCloseable managed) managed.close();
    }

    @Test void beanCreationPerformsNoTokenOrUserInfoExchange() throws Exception {
        try (var transport = scope()) {
            ApiClient client = new DocuSignAdapterAutoConfiguration().docuSignApiClient(properties());
            try { assertTrue(transport.exchanges.isEmpty(), "Bean creation must not authenticate or fetch user info"); }
            finally { closeManaged(client); }
        }
    }

    @Test void firstActualSdkOperationUsesCanonicalOAuthAudienceAndEndpoint() throws Exception {
        try (var transport = scope()) {
            var value = properties();
            ApiClient client = new DocuSignAdapterAutoConfiguration().docuSignApiClient(value);
            try {
                new EnvelopesApi(client).getEnvelope(value.getAccountId(), "controlled-envelope");
                assertEquals(3, transport.exchanges.size());
                var wire = transport.exchanges.getFirst();
                assertEquals("https://account.owned.invalid/oauth/token", wire.uri().toString());
                String assertion = null;
                for (String entry : new String(wire.bytes(), StandardCharsets.UTF_8).split("&")) {
                    String[] pair = entry.split("=", 2);
                    if (pair[0].equals("assertion")) assertion = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                }
                assertNotNull(assertion);
                assertEquals(List.of("account.owned.invalid"), JWT.decode(assertion).getAudience());
            } finally { closeManaged(client); }
        }
    }

    @Test void OAuthPathIsRefusedLocallyBeforeAnyExchange() throws Exception {
        try (var transport = scope()) {
            var value = properties(); value.setAuthServer("https://account.owned.invalid/unexpected-path");
            assertThrows(RuntimeException.class, () -> new DocuSignAdapterAutoConfiguration().docuSignApiClient(value));
            assertTrue(transport.exchanges.isEmpty());
        }
    }

    @Test void restUrlUserInfoIsRefusedLocallyBeforeAnyExchange() throws Exception {
        try (var transport = scope()) {
            var value = properties(); value.setBaseUrl("https://fixture-secret@owned.invalid/restapi");
            assertThrows(RuntimeException.class, () -> new DocuSignAdapterAutoConfiguration().docuSignApiClient(value));
            assertTrue(transport.exchanges.isEmpty());
        }
    }

    @Test void nonHttpsRestSchemeIsRefusedLocallyBeforeAnyExchange() throws Exception {
        try (var transport = scope()) {
            var value = properties(); value.setBaseUrl("ftp://owned.invalid/restapi");
            assertThrows(RuntimeException.class, () -> new DocuSignAdapterAutoConfiguration().docuSignApiClient(value));
            assertTrue(transport.exchanges.isEmpty());
        }
    }

    @Test void invalidKeyFailureContainsNoRawCauseOrCredential() throws Exception {
        try (var transport = scope()) {
            var value = properties(); value.setPrivateKey("owned-invalid-credential");
            RuntimeException error = assertThrows(RuntimeException.class,
                    () -> new DocuSignAdapterAutoConfiguration().docuSignApiClient(value));
            assertNull(error.getCause(), "Configuration failures must not export raw SDK causes");
            assertFalse(error.getMessage().contains(value.getPrivateKey()));
            assertTrue(transport.exchanges.isEmpty());
        }
    }

    @Test void timeoutOverflowIsRefusedBeforeAnyAuthentication() throws Exception {
        try (var transport = scope()) {
            var value = properties(); value.setConnectionTimeout(Duration.ofMillis((long) Integer.MAX_VALUE + 1));
            assertThrows(RuntimeException.class, () -> new DocuSignAdapterAutoConfiguration().docuSignApiClient(value));
            assertTrue(transport.exchanges.isEmpty());
        }
    }

    @Test void credentialDoesNotAppearInPropertiesRendering() throws Exception {
        var value = properties();
        assertFalse(value.toString().contains(value.getPrivateKey()), "Signing credential must not appear in property rendering");
    }

    @Test void nullOAuthAuthorityIsRefusedBeforeBuildingOrAuthenticatingAClient() throws Exception {
        try (var transport = scope()) {
            var value = properties(); value.setAuthServer(null);
            assertThrows(RuntimeException.class, () -> new DocuSignAdapterAutoConfiguration().docuSignApiClient(value));
            assertTrue(transport.exchanges.isEmpty());
        }
    }

    @Test void immutableSettingsCannotBypassSecureEndpointValidation() throws Exception {
        var valid = DocuSignClientSettings.from(properties());
        assertThrows(RuntimeException.class, () -> new DocuSignClientSettings(valid.integrationKey(), valid.userId(),
                valid.accountId(), valid.privateKey(), "ftp://owned.invalid/restapi", valid.oauthHost(),
                valid.connectMillis(), valid.readMillis(), valid.jwtExpiration(), valid.authenticationTimeout(),
                valid.authenticationWaitTimeout(), valid.refreshSkew(), valid.maxConcurrentRequests(), valid.maxQueuedRequestsPerThread()));
    }

    @Test void locallyValidatedCredentialsRetainTheRealSdkSupportedPkcs1KeyFormat() throws Exception {
        try (var transport = scope()) {
            var value = properties();
            var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
            var key = (java.security.interfaces.RSAPrivateCrtKey) generator.generateKeyPair().getPrivate();
            byte[] encoded = new org.bouncycastle.asn1.pkcs.RSAPrivateKey(key.getModulus(), key.getPublicExponent(),
                    key.getPrivateExponent(), key.getPrimeP(), key.getPrimeQ(), key.getPrimeExponentP(),
                    key.getPrimeExponentQ(), key.getCrtCoefficient()).getEncoded();
            value.setPrivateKey("-----BEGIN RSA PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, new byte[]{10})
                    .encodeToString(encoded) + "\n-----END RSA PRIVATE KEY-----\n");
            var reference = new ApiClient(); reference.setOAuthBasePath("account.owned.invalid");
            assertNotNull(reference.requestJWTUserToken(value.getIntegrationKey(), value.getUserId(),
                    List.of("signature", "impersonation"), value.getPrivateKey().getBytes(StandardCharsets.UTF_8), 120));
            assertEquals(1, transport.exchanges.size(), "The actual unchanged SDK must accept this same credential format");
            ApiClient client = assertDoesNotThrow(() -> new DocuSignAdapterAutoConfiguration().docuSignApiClient(value));
            try { assertEquals(1, transport.exchanges.size(), "Local validation must not add authentication"); }
            finally { closeManaged(client); }
        }
    }

    @Test void explicitCallerClientRemainsUnmodifiedAndCallerOwned() throws Exception {
        try (var transport = scope()) {
            var value = properties(); ApiClient manual = new ApiClient();
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DocuSignAdapterAutoConfiguration.class))
                    .withPropertyValues("firefly.ecm.esignature.provider=docusign",
                            "firefly.ecm.adapter.docusign.integration-key=" + value.getIntegrationKey(),
                            "firefly.ecm.adapter.docusign.user-id=" + value.getUserId(),
                            "firefly.ecm.adapter.docusign.account-id=" + value.getAccountId(),
                            "firefly.ecm.adapter.docusign.private-key=" + value.getPrivateKey())
                    .withBean(ApiClient.class, () -> manual)
                    .run(context -> assertSame(manual, context.getBean(ApiClient.class)));
            assertDoesNotThrow(() -> manual.getHttpClient().target("https://owned.invalid"));
            assertTrue(transport.exchanges.isEmpty());
        }
    }

    @Test void realSpringDefaultBeanIsNetworkFreeAndClosesOnlyItsOwnedClientAtContextShutdown() throws Exception {
        try (var transport = scope()) {
            var value = properties(); var owned = new java.util.concurrent.atomic.AtomicReference<ApiClient>();
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DocuSignAdapterAutoConfiguration.class))
                    .withPropertyValues("firefly.ecm.esignature.provider=docusign",
                            "firefly.ecm.adapter.docusign.integration-key=" + value.getIntegrationKey(),
                            "firefly.ecm.adapter.docusign.user-id=" + value.getUserId(),
                            "firefly.ecm.adapter.docusign.account-id=" + value.getAccountId(),
                            "firefly.ecm.adapter.docusign.private-key=" + value.getPrivateKey())
                    .run(context -> {
                        assertNull(context.getStartupFailure());
                        owned.set(context.getBean(ApiClient.class));
                        assertInstanceOf(org.fireflyframework.ecm.adapter.docusign.client.ManagedDocuSignApiClient.class, owned.get());
                        assertTrue(transport.exchanges.isEmpty());
                    });
            assertTrue(((org.glassfish.jersey.client.JerseyClient) owned.get().getHttpClient()).isClosed());
            assertTrue(transport.exchanges.isEmpty());
        }
    }

    @Test void otherProviderDoesNotCreateDocuSignClientOrAuthenticate() throws Exception {
        try (var transport = scope()) {
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DocuSignAdapterAutoConfiguration.class))
                    .withPropertyValues("firefly.ecm.esignature.provider=other")
                    .run(context -> assertEquals(0, context.getBeansOfType(ApiClient.class).size()));
            assertTrue(transport.exchanges.isEmpty());
        }
    }
}
