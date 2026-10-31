/*
 * Copyright 2024-2026 Firefly Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fireflyframework.ecm.adapter.docusign.client;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.fireflyframework.ecm.adapter.docusign.DocuSignAdapterException;
import org.fireflyframework.ecm.adapter.docusign.DocuSignAdapterProperties;

/** Immutable locally validated settings; rendering deliberately excludes all configured values. */
public record DocuSignClientSettings(String integrationKey, String userId, String accountId, String privateKey,
                                    String baseUrl, String oauthHost, int connectMillis, int readMillis,
                                    long jwtExpiration, Duration authenticationTimeout,
                                    Duration authenticationWaitTimeout, Duration refreshSkew,
                                    int maxConcurrentRequests, int maxQueuedRequestsPerThread) {
    public DocuSignClientSettings {
        try {
            integrationKey = identifier(integrationKey);
            userId = identifier(userId);
            accountId = identifier(accountId);
            privateKey = privateKey(privateKey);
            baseUrl = secureUrl(baseUrl);
            oauthHost = oauthHost(oauthHost);
            if (connectMillis < 1 || readMillis < 1 || jwtExpiration < 1 || jwtExpiration > 3600) throw invalid();
            authenticationTimeout = boundedDuration(authenticationTimeout, Duration.ofMinutes(10));
            authenticationWaitTimeout = boundedDuration(authenticationWaitTimeout, authenticationTimeout);
            if (refreshSkew == null || refreshSkew.isNegative() || refreshSkew.compareTo(Duration.ofMinutes(5)) > 0) throw invalid();
            bound(maxConcurrentRequests, 64);
            bound(maxQueuedRequestsPerThread, 1024);
        } catch (Exception error) { throw invalid(); }
    }

    public static DocuSignClientSettings from(DocuSignAdapterProperties properties) {
        try {
            if (properties == null || properties.getSandboxMode() == null) throw invalid();
            String key = properties.getIntegrationKey();
            String user = properties.getUserId();
            String account = properties.getAccountId();
            String pem = properties.getPrivateKey();
            String base = properties.getSandboxMode() ? "https://demo.docusign.net/restapi" : properties.getBaseUrl();
            String oauth = properties.getSandboxMode() ? "account-d.docusign.com" : properties.getAuthServer();
            int connect = milliseconds(properties.getConnectionTimeout());
            int read = milliseconds(properties.getReadTimeout());
            Long expiration = properties.getJwtExpiration();
            if (expiration == null || expiration < 1 || expiration > 3600) throw invalid();
            Duration timeout = boundedDuration(properties.getAuthenticationTimeout(), Duration.ofMinutes(10));
            Duration wait = boundedDuration(properties.getAuthenticationWaitTimeout(), timeout);
            Duration skew = properties.getAuthenticationRefreshSkew();
            if (skew == null || skew.isNegative() || skew.compareTo(Duration.ofMinutes(5)) > 0) throw invalid();
            int concurrency = bound(properties.getMaxConcurrentRequests(), 64);
            int queued = bound(properties.getMaxQueuedRequestsPerThread(), 1024);
            return new DocuSignClientSettings(key, user, account, pem, base, oauth, connect, read,
                    expiration, timeout, wait, skew, concurrency, queued);
        } catch (Exception error) {
            throw invalid();
        }
    }

    private static String identifier(String value) {
        if (value == null || value.isBlank() || value.length() > 256 || !value.equals(value.strip())
                || value.codePoints().anyMatch(Character::isISOControl)) throw invalid();
        return value;
    }

    private static String privateKey(String value) throws Exception {
        if (value == null || value.length() > 16384) throw invalid();
        String kind = value.startsWith("-----BEGIN RSA PRIVATE KEY-----") ? "RSA PRIVATE KEY" : "PRIVATE KEY";
        String begin = "-----BEGIN " + kind + "-----", end = "-----END " + kind + "-----";
        if (!value.startsWith(begin) || !value.stripTrailing().endsWith(end)) throw invalid();
        String encoded = value.substring(begin.length(), value.lastIndexOf(end)).replaceAll("\\s", "");
        byte[] bytes = Base64.getDecoder().decode(encoded);
        try {
            // A provider instance preserves the SDK's PKCS#1/PKCS#8 support without registering global state.
            var key = KeyFactory.getInstance("RSA", new BouncyCastleProvider()).generatePrivate(new PKCS8EncodedKeySpec(bytes));
            if (!(key instanceof RSAPrivateKey rsa) || rsa.getModulus().bitLength() < 2048) throw invalid();
        } finally { java.util.Arrays.fill(bytes, (byte) 0); }
        return value;
    }

    private static URI https(String value) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) throw invalid();
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || uri.getPort() == 0 || uri.getPort() > 65535) throw invalid();
        return uri;
    }

    private static String secureUrl(String value) {
        URI uri = https(value);
        return "https" + uri.toASCIIString().substring(uri.getScheme().length());
    }

    private static String oauthHost(String value) {
        if (value == null || value.isBlank()) throw invalid();
        URI uri = https(value.contains("://") ? value : "https://" + value);
        if (uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !uri.getRawPath().equals("/")) throw invalid();
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return host + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
    }

    private static int milliseconds(Duration value) {
        if (value == null || value.isNegative() || value.isZero()) throw invalid();
        long millis = value.toMillis();
        if (millis < 1 || millis > Integer.MAX_VALUE) throw invalid();
        return (int) millis;
    }

    private static Duration boundedDuration(Duration value, Duration maximum) {
        if (value == null || value.compareTo(Duration.ofMillis(2)) < 0 || value.compareTo(maximum) > 0) throw invalid();
        return value;
    }

    private static int bound(Integer value, int maximum) {
        if (value == null || value < 1 || value > maximum) throw invalid();
        return value;
    }

    public byte[] privateKeyBytes() { return privateKey.getBytes(StandardCharsets.UTF_8); }
    @Override public String toString() { return "DocuSignClientSettings[redacted]"; }
    private static DocuSignAdapterException invalid() {
        return new DocuSignAdapterException(DocuSignAdapterException.Code.CONFIGURATION,
                DocuSignAdapterException.Operation.CONFIGURATION);
    }
}
