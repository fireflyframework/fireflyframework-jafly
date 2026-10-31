/* Copyright 2024-2026 Firefly Software Foundation. */
package org.fireflyframework.ecm.port.esignature;

import java.util.UUID;
import org.fireflyframework.ecm.domain.enums.esignature.SignatureProvider;
import reactor.core.publisher.Mono;

/** Caller-owned authorized persistent reference lookup; adapters do not store business identities. */
@FunctionalInterface
public interface SignatureEnvelopeReferenceResolver {
    /** Empty means no authorized reference in the caller's scope. */
    Mono<String> resolveExternalId(UUID envelopeId, SignatureProvider provider);
}
