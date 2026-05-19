/*
 * Copyright 2024-2026 Firefly Software Foundation
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

package org.fireflyframework.idp.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.fireflyframework.observability.metrics.FireflyMetricsSupport;
import reactor.core.publisher.Mono;

/**
 * Observability instrumentation shared by all Firefly IDP adapter implementations.
 * <p>
 * Records:
 * <ul>
 *     <li>{@code firefly.idp.authentications} — total auth attempts, tagged by {@code provider}, {@code status=success|failure}</li>
 *     <li>{@code firefly.idp.authentication.duration} — auth latency timer, tagged by {@code provider}</li>
 *     <li>{@code firefly.idp.token.issued} — total tokens issued, tagged by {@code provider}, {@code token.type}</li>
 *     <li>{@code firefly.idp.token.refreshed} — token refresh count, tagged by {@code provider}</li>
 *     <li>{@code firefly.idp.errors} — failed IDP operations, tagged by {@code operation}, {@code error.type}</li>
 * </ul>
 */
public class IdpMetrics extends FireflyMetricsSupport {

    private static final String TAG_PROVIDER = "provider";
    private static final String TAG_OPERATION = "operation";
    private static final String TAG_TOKEN_TYPE = "token.type";

    public IdpMetrics(MeterRegistry meterRegistry) {
        super(meterRegistry, "idp");
    }

    /**
     * Wraps an authentication call (login, validate-token, etc.) with a timer and success/failure counters.
     */
    public <T> Mono<T> timedAuthentication(String provider, Mono<T> operation) {
        return timed("authentication.duration", operation, TAG_PROVIDER, provider)
                .doOnSuccess(v -> recordSuccess("authentications", TAG_PROVIDER, provider))
                .doOnError(e -> {
                    recordFailure("authentications", e, TAG_PROVIDER, provider);
                    recordFailure("errors", e, TAG_OPERATION, "authenticate", TAG_PROVIDER, provider);
                });
    }

    public void recordTokenIssued(String provider, String tokenType) {
        counter("token.issued", TAG_PROVIDER, provider, TAG_TOKEN_TYPE, tokenType).increment();
    }

    public void recordTokenRefreshed(String provider) {
        counter("token.refreshed", TAG_PROVIDER, provider).increment();
    }

    public void recordError(String provider, String operation, Throwable error) {
        recordFailure("errors", error, TAG_PROVIDER, provider, TAG_OPERATION, operation);
    }
}
