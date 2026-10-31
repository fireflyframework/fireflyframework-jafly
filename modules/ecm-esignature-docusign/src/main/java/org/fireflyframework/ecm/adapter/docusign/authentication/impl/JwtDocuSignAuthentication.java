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
package org.fireflyframework.ecm.adapter.docusign.authentication.impl;

import com.docusign.esign.client.auth.OAuth;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import org.fireflyframework.ecm.adapter.docusign.DocuSignAdapterException;
import org.fireflyframework.ecm.adapter.docusign.authentication.DocuSignAuthentication;
import org.fireflyframework.ecm.adapter.docusign.client.DocuSignClientSettings;
import org.fireflyframework.ecm.adapter.docusign.client.ManagedDocuSignApiClient;

/** One bounded refresh owner per configured client; no failed or unvalidated credential is published. */
public final class JwtDocuSignAuthentication implements DocuSignAuthentication {
    private final ManagedDocuSignApiClient client;
    private final DocuSignClientSettings settings;
    private final Clock clock;
    private final LongSupplier ticker;
    private final ReentrantLock refresh = new ReentrantLock();
    private volatile Session session;

    public JwtDocuSignAuthentication(ManagedDocuSignApiClient client, DocuSignClientSettings settings,
                                    Clock clock, LongSupplier ticker) {
        this.client = java.util.Objects.requireNonNull(client);
        this.settings = java.util.Objects.requireNonNull(settings);
        this.clock = java.util.Objects.requireNonNull(clock);
        this.ticker = java.util.Objects.requireNonNull(ticker);
    }

    @Override public String accessToken() {
        client.ensureOperationActive();
        Session current = session;
        if (usable(current)) return current.token();
        boolean locked = false;
        try {
            locked = refresh.tryLock(settings.authenticationWaitTimeout().toNanos(), TimeUnit.NANOSECONDS);
            if (!locked) throw new DocuSignAdapterException(DocuSignAdapterException.Code.BUSY,
                    DocuSignAdapterException.Operation.AUTHENTICATION);
            client.ensureOperationActive();
            current = session;
            if (usable(current)) return current.token();
            return client.authenticate(this::acquire, acquired -> {
                client.ensureOperationActive();
                if (!usable(acquired)) throw invalidResponse();
                return client.publishToken(acquired.token(), acquired.expiresIn(), () -> session = acquired);
            });
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new DocuSignAdapterException(DocuSignAdapterException.Code.CLOSED,
                    DocuSignAdapterException.Operation.AUTHENTICATION);
        } catch (Exception error) {
            throw DocuSignAdapterException.safe(error, DocuSignAdapterException.Operation.AUTHENTICATION);
        } finally { if (locked) refresh.unlock(); }
    }

    private Session acquire() throws Exception {
        Instant requestedAt = clock.instant();
        long requestedNanos = ticker.getAsLong();
        byte[] key = settings.privateKeyBytes();
        OAuth.OAuthToken token;
        try {
            token = client.requestJWTUserToken(settings.integrationKey(), settings.userId(),
                    List.of("signature", "impersonation"), key, settings.jwtExpiration());
        } finally { java.util.Arrays.fill(key, (byte) 0); }
        if (token == null || !validToken(token.getAccessToken())
                || token.getExpiresIn() == null || token.getExpiresIn() < 1 || token.getExpiresIn() > 3600
                || token.getTokenType() == null || !token.getTokenType().equalsIgnoreCase("Bearer")) throw invalidResponse();
        OAuth.UserInfo user = client.getUserInfo(token.getAccessToken());
        if (user == null || user.getSub() == null || !settings.userId().equals(user.getSub())
                || user.getAccounts() == null || user.getAccounts().isEmpty()
                || user.getAccounts().stream().anyMatch(java.util.Objects::isNull)) throw invalidResponse();
        if (user.getAccounts().stream().noneMatch(account -> settings.accountId().equals(account.getAccountId())))
            throw new DocuSignAdapterException(DocuSignAdapterException.Code.AUTHORIZATION,
                    DocuSignAdapterException.Operation.AUTHENTICATION);
        long lifetime = TimeUnit.SECONDS.toNanos(token.getExpiresIn());
        long skew = Math.min(settings.refreshSkew().toNanos(), lifetime / 10);
        return new Session(token.getAccessToken(), token.getExpiresIn(),
                requestedAt.plusSeconds(token.getExpiresIn()).minusNanos(skew), requestedNanos + lifetime - skew);
    }

    private boolean usable(Session value) {
        return value != null && clock.instant().isBefore(value.readyUntil())
                && value.readyUntilNanos() - ticker.getAsLong() > 0;
    }

    private static boolean validToken(String value) {
        return value != null && !value.isEmpty() && value.length() <= 16384
                && value.matches("[A-Za-z0-9\\-._~+/]+=*");
    }

    private static DocuSignAdapterException invalidResponse() {
        return new DocuSignAdapterException(DocuSignAdapterException.Code.PROVIDER_RESPONSE,
                DocuSignAdapterException.Operation.AUTHENTICATION);
    }

    private record Session(String token, long expiresIn, Instant readyUntil, long readyUntilNanos) {
        @Override public String toString() { return "DocuSignSession[redacted]"; }
    }
}
