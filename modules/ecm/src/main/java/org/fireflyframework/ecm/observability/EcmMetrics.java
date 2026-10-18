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

package org.fireflyframework.ecm.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.fireflyframework.observability.metrics.FireflyMetricsSupport;
import reactor.core.publisher.Mono;

/**
 * Shared observability instrumentation for the ECM module and all its adapters
 * (storage: S3/Azure Blob/local; e-signature: DocuSign/Adobe Sign/Logalty).
 * <p>
 * Records:
 * <ul>
 *     <li>{@code firefly.ecm.documents.operations} — counter tagged by {@code operation}
 *         (upload/download/delete/move/copy/sign), {@code provider}, {@code status}</li>
 *     <li>{@code firefly.ecm.operation.duration} — timer tagged by {@code operation}, {@code provider}</li>
 *     <li>{@code firefly.ecm.bytes.transferred} — distribution summary of payload bytes,
 *         tagged by {@code direction} (in/out), {@code provider}</li>
 *     <li>{@code firefly.ecm.errors} — failed operations, tagged by {@code operation},
 *         {@code provider}, {@code error.type}</li>
 *     <li>{@code firefly.ecm.signatures.completed} — successful e-signature workflows by {@code provider}</li>
 * </ul>
 */
public class EcmMetrics extends FireflyMetricsSupport {

    private static final String TAG_OPERATION = "operation";
    private static final String TAG_PROVIDER = "provider";
    private static final String TAG_DIRECTION = "direction";

    public EcmMetrics(MeterRegistry meterRegistry) {
        super(meterRegistry, "ecm");
    }

    /**
     * Wraps a document-storage or e-signature operation with a timer and success/failure counters.
     */
    public <T> Mono<T> timedOperation(String operation, String provider, Mono<T> op) {
        return timed("operation.duration", op, TAG_OPERATION, operation, TAG_PROVIDER, provider)
                .doOnSuccess(v -> recordSuccess("documents.operations",
                        TAG_OPERATION, operation, TAG_PROVIDER, provider))
                .doOnError(e -> {
                    recordFailure("documents.operations", e,
                            TAG_OPERATION, operation, TAG_PROVIDER, provider);
                    recordFailure("errors", e,
                            TAG_OPERATION, operation, TAG_PROVIDER, provider);
                });
    }

    public void recordBytesTransferred(String direction, String provider, long bytes) {
        distributionSummary("bytes.transferred", TAG_DIRECTION, direction, TAG_PROVIDER, provider)
                .record(bytes);
    }

    public void recordSignatureCompleted(String provider) {
        counter("signatures.completed", TAG_PROVIDER, provider).increment();
    }
}
