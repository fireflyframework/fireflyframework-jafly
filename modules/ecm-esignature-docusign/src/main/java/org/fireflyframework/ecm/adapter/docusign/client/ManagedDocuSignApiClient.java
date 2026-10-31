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

import com.docusign.esign.client.ApiClient;
import com.docusign.esign.client.Pair;
import com.docusign.esign.client.auth.OAuth;
import jakarta.ws.rs.client.Client;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.fireflyframework.ecm.adapter.docusign.DocuSignAdapterException;
import org.fireflyframework.ecm.adapter.docusign.authentication.DocuSignAuthentication;
import org.fireflyframework.ecm.adapter.docusign.authentication.impl.JwtDocuSignAuthentication;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.JerseyClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/** Owns only the HTTP clients it builds and its finite SDK execution lane. */
public final class ManagedDocuSignApiClient extends ApiClient implements AutoCloseable {
    private final Set<Client> ownedClients = ConcurrentHashMap.newKeySet();
    private final Map<Client, OwnedConnectorCloseObserver> closeObservers = new ConcurrentHashMap<>();
    private final AtomicBoolean cleanupFailed = new AtomicBoolean();
    private final ThreadLocal<OperationScope> operationScope = new ThreadLocal<>();
    private final ThreadLocal<AuthenticationScope> authenticationScope = new ThreadLocal<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ReentrantLock lifecycle = new ReentrantLock();
    private final DocuSignClientSettings settings;
    private final Scheduler scheduler;

    public ManagedDocuSignApiClient(DocuSignClientSettings settings) {
        this(settings, Clock.systemUTC());
    }

    public ManagedDocuSignApiClient(DocuSignClientSettings settings, Clock clock) {
        super(isolatedProxyAuthentication(settings, clock));
        this.settings = java.util.Objects.requireNonNull(settings);
        ownedClients.add(getHttpClient());
        observeOwnedClient(getHttpClient());
        setBasePath(settings.baseUrl());
        setOAuthBasePath(settings.oauthHost());
        setConnectTimeout(settings.connectMillis());
        setReadTimeout(settings.readMillis());
        setUserAgent("Firefly-ECM-Library/1.0.0");
        scheduler = Schedulers.newBoundedElastic(settings.maxConcurrentRequests(),
                settings.maxQueuedRequestsPerThread(), "firefly-docusign", 60, true);
        DocuSignAuthentication authentication = new JwtDocuSignAuthentication(this, settings,
                java.util.Objects.requireNonNull(clock), System::nanoTime);
        addAuthorization("docusignAccessCode", new ManagedOAuth(authentication));
    }

    @Override protected Client buildHttpClient(boolean debugging) {
        if (closed != null) ensureOperationActive();
        AuthenticationScope scope = authenticationScope == null ? null : authenticationScope.get();
        if (scope != null) scope.remainingMillis();
        Client client = super.buildHttpClient(debugging);
        // The SDK invokes this hook from its constructor, before subclass fields exist.
        if (ownedClients != null) {
            admitBuiltClient(client);
        }
        if (scope != null) {
            try {
                scope.add(client);
                long remaining = scope.remainingMillis();
                int connect = (int) Math.min(settings.connectMillis(), remaining / 2);
                int read = (int) Math.min(settings.readMillis(), remaining - connect);
                client.property(ClientProperties.CONNECT_TIMEOUT, connect);
                client.property(ClientProperties.READ_TIMEOUT, read);
            } catch (RuntimeException error) {
                closeOwned(client);
                throw error;
            }
        }
        return client;
    }

    private static boolean isolatedProxyAuthentication(DocuSignClientSettings settings, Clock clock) {
        if (settings == null || clock == null)
            throw new DocuSignAdapterException(DocuSignAdapterException.Code.CONFIGURATION,
                    DocuSignAdapterException.Operation.CONFIGURATION);
        return true;
    }

    public void ensureOpen() {
        if (cleanupFailed.get()) throw cleanupFailure();
        if (closed.get()) throw new DocuSignAdapterException(DocuSignAdapterException.Code.CLOSED,
                DocuSignAdapterException.Operation.ENVELOPE);
    }

    public <T> Mono<T> execute(Callable<T> operation) {
        return Mono.defer(() -> {
            ensureOpen();
            var scope = new OperationScope();
            return Mono.fromCallable(() -> {
                operationScope.set(scope);
                try {
                    ensureOperationActive();
                    T result = operation.call();
                    ensureOperationActive();
                    return result;
                } finally { operationScope.remove(); }
            }).subscribeOn(scheduler).doOnCancel(scope::cancel);
        }).onErrorMap(error -> DocuSignAdapterException.safe(error, DocuSignAdapterException.Operation.ENVELOPE));
    }

    public void ensureOperationActive() {
        ensureOpen();
        OperationScope operation = operationScope.get();
        if (operation != null) operation.check();
        AuthenticationScope authentication = authenticationScope.get();
        if (authentication != null) authentication.remainingMillis();
    }

    public <T> T authenticate(Callable<T> operation) throws Exception {
        return authenticate(operation, Function.identity());
    }

    public <T, R> R authenticate(Callable<T> operation, Function<T, R> publish) throws Exception {
        ensureOperationActive();
        if (authenticationScope.get() != null)
            throw new DocuSignAdapterException(DocuSignAdapterException.Code.AUTHENTICATION,
                    DocuSignAdapterException.Operation.AUTHENTICATION);
        AuthenticationScope scope = new AuthenticationScope();
        authenticationScope.set(scope);
        try {
            T result;
            try (scope) {
                result = operation.call();
                ensureOperationActive();
            }
            // Owned cleanup and publication consume the same original authentication deadline.
            ensureOperationActive();
            return publish.apply(result);
        } finally { authenticationScope.remove(); }
    }

    public String publishToken(String token, long expiresIn, Runnable remember) {
        boolean locked = false;
        try {
            ensureOperationActive();
            locked = lifecycle.tryLock(lifecycleWaitNanos(), TimeUnit.NANOSECONDS);
            if (!locked) throw new DocuSignAdapterException(DocuSignAdapterException.Code.BUSY,
                    DocuSignAdapterException.Operation.AUTHENTICATION);
            Runnable publication = () -> {
                ensureOperationActive();
                setAccessToken(token, expiresIn);
                remember.run();
            };
            OperationScope operation = operationScope.get();
            if (operation == null) publication.run();
            else operation.publish(publication);
            return token;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new DocuSignAdapterException(DocuSignAdapterException.Code.CLOSED,
                    DocuSignAdapterException.Operation.AUTHENTICATION);
        } finally { if (locked) lifecycle.unlock(); }
    }

    private void closeOwned(Client client) {
        if (!ownedClients.remove(client)) return;
        OwnedConnectorCloseObserver observer = closeObservers.remove(client);
        try {
            client.close();
            if (observer == null) throw cleanupFailure();
            observer.verify();
        } catch (RuntimeException error) { cleanupFailed.set(true); throw cleanupFailure(); }
    }

    private long lifecycleWaitNanos() {
        AuthenticationScope authentication = authenticationScope.get();
        return authentication == null ? settings.authenticationWaitTimeout().toNanos()
                : Math.min(settings.authenticationWaitTimeout().toNanos(), authentication.remainingNanos());
    }

    private void admitBuiltClient(Client client) {
        OwnedConnectorCloseObserver observer = null;
        boolean locked = false;
        boolean admitted = false;
        try {
            observer = observeClient(client);
            locked = lifecycle.tryLock(lifecycleWaitNanos(), TimeUnit.NANOSECONDS);
            if (!locked) throw new DocuSignAdapterException(DocuSignAdapterException.Code.BUSY,
                    DocuSignAdapterException.Operation.AUTHENTICATION);
            ensureOperationActive();
            ownedClients.add(client);
            closeObservers.put(client, observer);
            admitted = true;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new DocuSignAdapterException(DocuSignAdapterException.Code.CLOSED,
                    DocuSignAdapterException.Operation.AUTHENTICATION);
        } finally {
            if (locked) lifecycle.unlock();
            if (!admitted) {
                try {
                    client.close();
                    if (observer == null) throw cleanupFailure();
                    observer.verify();
                } catch (RuntimeException error) { cleanupFailed.set(true); throw cleanupFailure(); }
            }
        }
    }

    private void observeOwnedClient(Client client) {
        closeObservers.put(client, observeClient(client));
    }

    private OwnedConnectorCloseObserver observeClient(Client client) {
        if (!(client instanceof JerseyClient jersey))
            throw new DocuSignAdapterException(DocuSignAdapterException.Code.CONFIGURATION,
                    DocuSignAdapterException.Operation.CONFIGURATION);
        var configuration = jersey.getConfiguration();
        var original = configuration.getConnectorProvider();
        if (original == null) throw new DocuSignAdapterException(DocuSignAdapterException.Code.CONFIGURATION,
                DocuSignAdapterException.Operation.CONFIGURATION);
        var observer = new OwnedConnectorCloseObserver(this::ensureOperationActive);
        configuration.connectorProvider((owner, config) -> observer.observe(original.getConnector(owner, config)));
        return observer;
    }

    private static DocuSignAdapterException cleanupFailure() {
        return new DocuSignAdapterException(DocuSignAdapterException.Code.CLEANUP_FAILURE,
                DocuSignAdapterException.Operation.AUTHENTICATION);
    }

    @Override public void close() {
        boolean locked = false;
        try {
            locked = lifecycle.tryLock(settings.authenticationWaitTimeout().toNanos(), TimeUnit.NANOSECONDS);
            if (!locked) { cleanupFailed.set(true); throw cleanupFailure(); }
            if (!closed.compareAndSet(false, true)) {
                if (cleanupFailed.get()) throw cleanupFailure();
                return;
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); cleanupFailed.set(true); throw cleanupFailure();
        } finally { if (locked) lifecycle.unlock(); }
        scheduler.dispose();
        boolean failed = false;
        for (Client client : List.copyOf(ownedClients)) {
            try { closeOwned(client); } catch (RuntimeException error) { failed = true; }
        }
        if (failed || cleanupFailed.get()) throw new DocuSignAdapterException(DocuSignAdapterException.Code.CLEANUP_FAILURE,
                DocuSignAdapterException.Operation.ENVELOPE);
    }

    private final class AuthenticationScope implements AutoCloseable {
        private final List<Client> clients = new ArrayList<>(2);
        private final long deadline = System.nanoTime() + settings.authenticationTimeout().toNanos();
        void add(Client client) {
            if (clients.size() >= 2) throw new DocuSignAdapterException(DocuSignAdapterException.Code.AUTHENTICATION,
                    DocuSignAdapterException.Operation.AUTHENTICATION);
            clients.add(client);
        }
        long remainingNanos() {
            ensureOpen();
            OperationScope operation = operationScope.get();
            if (operation != null) operation.check();
            long remaining = deadline - System.nanoTime();
            if (remaining < 2_000_000) throw new DocuSignAdapterException(DocuSignAdapterException.Code.AUTHENTICATION,
                    DocuSignAdapterException.Operation.AUTHENTICATION);
            return remaining;
        }
        long remainingMillis() { return remainingNanos() / 1_000_000; }
        @Override public void close() {
            boolean failed = false;
            for (Client client : clients) {
                try { closeOwned(client); } catch (RuntimeException error) { failed = true; }
            }
            if (failed) throw new DocuSignAdapterException(DocuSignAdapterException.Code.CLEANUP_FAILURE,
                    DocuSignAdapterException.Operation.AUTHENTICATION);
        }
    }

    private static final class OperationScope {
        private boolean cancelled;
        synchronized void cancel() { cancelled = true; }
        synchronized void check() {
            if (cancelled) throw new DocuSignAdapterException(DocuSignAdapterException.Code.CLOSED,
                    DocuSignAdapterException.Operation.ENVELOPE);
        }
        synchronized void publish(Runnable publication) { check(); publication.run(); }
    }

    private static final class ManagedOAuth extends OAuth {
        private final DocuSignAuthentication authentication;
        ManagedOAuth(DocuSignAuthentication authentication) { this.authentication = authentication; }
        @Override public void applyToParams(List<Pair> queryParams, Map<String, String> headerParams) {
            // The SDK's default OAuth implementation swallows refresh failures; never delegate to it here.
            headerParams.put("Authorization", "Bearer " + authentication.accessToken());
        }
    }
}
