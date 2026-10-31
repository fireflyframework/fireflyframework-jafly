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

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.fireflyframework.ecm.adapter.docusign.DocuSignAdapterException;
import org.glassfish.jersey.client.ClientRequest;
import org.glassfish.jersey.client.ClientResponse;
import org.glassfish.jersey.client.spi.AsyncConnectorCallback;
import org.glassfish.jersey.client.spi.Connector;

/** Observes owned connector lifecycle callbacks; it does not attest physical socket closure. */
final class OwnedConnectorCloseObserver {
    private final AtomicInteger acquired = new AtomicInteger();
    private final AtomicInteger completed = new AtomicInteger();
    private final AtomicBoolean failed = new AtomicBoolean();
    private final Runnable beforeExchange;

    OwnedConnectorCloseObserver(Runnable beforeExchange) {
        this.beforeExchange = java.util.Objects.requireNonNull(beforeExchange);
    }

    Connector observe(Connector delegate) {
        if (delegate == null) throw failure();
        acquired.incrementAndGet();
        return new Connector() {
            private final AtomicBoolean closing = new AtomicBoolean();
            @Override public ClientResponse apply(ClientRequest request) {
                beforeExchange.run();
                return delegate.apply(request);
            }
            @Override public Future<?> apply(ClientRequest request, AsyncConnectorCallback callback) {
                beforeExchange.run();
                return delegate.apply(request, callback);
            }
            @Override public String getName() { return delegate.getName(); }
            @Override public void close() {
                if (!closing.compareAndSet(false, true)) { failed.set(true); throw failure(); }
                try { delegate.close(); completed.incrementAndGet(); }
                catch (RuntimeException error) { failed.set(true); throw failure(); }
            }
        };
    }

    void verify() {
        if (failed.get() || acquired.get() != completed.get()) throw failure();
    }

    private static DocuSignAdapterException failure() {
        return new DocuSignAdapterException(DocuSignAdapterException.Code.CLEANUP_FAILURE,
                DocuSignAdapterException.Operation.AUTHENTICATION);
    }
}
