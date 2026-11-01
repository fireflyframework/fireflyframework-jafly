# Optional LIVE JWT admission

This source addition requires a matching qualified whole-framework release. It is not included in version 26.10.23.

LIVE adds optional current-state admission after local JWT verification. Confirm the provider response contract and qualify the application security chain before enabling this mode in a deployment.

`LOCAL_JWT` remains the general default. It retains signature, issuer, audience and time validation. An unexpired locally verified JWT does not by itself establish that its provider account or session is currently active.

Explicit `LIVE` first uses the existing JWT decoder and then calls `TokenValidationPort.validateLive` for the same credential on each subscription. Existing port implementations refuse this additive method by default. The owned implementation uses Spring Security's native remote introspector, standard JDK HTTPS transport and managed Jackson3 codecs. No IdP name, adapter class, client alias, cache health or local token parser implies LIVE support.

```yaml
firefly:
  security:
    resource-server:
      admission-mode: LIVE
      issuer: ${OIDC_EXPECTED_ISSUER}
      audiences: [${OIDC_RESOURCE_AUDIENCE}]
      live-state:
        introspection-uri: ${OIDC_INTROSPECTION_HTTPS_URI}
        introspection-client-id: ${OIDC_INTROSPECTION_CLIENT_ID}
        introspection-client-secret: ${OIDC_INTROSPECTION_CLIENT_SECRET}
        token-client-claim: ${OIDC_SIGNED_CLIENT_CLAIM}
        allowed-clients: [${OIDC_ALLOWED_CLIENT}]
        timeout: 5s
```

Use one explicitly configured signed client claim, such as the exact `azp` or `client_id` defined by the trusted issuer. There is no automatic alias or token-derived endpoint discovery. Configuration and credentials are snapshotted when the adapter/decorator is constructed. Rotate them through a newly constructed configured instance.

The active response must contain actual Boolean `active` and correctly typed issuer, opaque subject, client_id, audience and integral expiration. The verified issuer, subject, client, audience set and expiration must match exactly; a signed token ID must also match. Opaque identifiers are not UUIDs and are not trimmed or normalized. Missing, malformed, duplicate, ambiguous or mismatched metadata refuses. Active=false may omit identity metadata and still refuses. Identity metadata is used only for the check: the exact locally verified Jwt continues to the existing principal converter, without provider role, claim or tenant merging.

The original per-subscription authentication window includes local verification and live admission. It is at most 5 seconds and additionally clipped to the verified token's original expiration. Timeout, empty, unsupported or failed results cannot become local/stale success. There is no positive cache or retry. Response bytes, including error bodies, are limited to 16 KiB before native coercion; duplicate keys, invalid UTF-8 and wrong wire types refuse. Errors have fixed messages without raw causes. The native Jackson codec receives Spring's supported suppress-logging hint. The existing internal BearerToken carrier renders a constant redaction; its value/type/masked APIs remain available and must not be logged.

The owned client uses an explicit HTTPS endpoint with ordinary platform trust/hostname validation, redirects disabled and finite connect/read timeouts. It owns only the client it constructs. A supplied custom introspector remains caller-owned and must actually implement the uncached current-state contract. Close/cancel propagate through supported APIs; no guarantee of physical socket/thread termination or provider revocation follows from this mode.

## Custom security chains

The default Jafly chain wraps its injected decoder in LIVE mode. An application that completely replaces SecurityWebFilterChain must explicitly use `new LiveActiveJwtDecoderImpl(localDecoder, livePort, properties)` in its own native Spring JWT DSL and qualify actual protected routes. A property or port bean alone cannot prove arbitrary custom-chain adoption. The retained direct five-argument default-chain factory supports LOCAL_JWT; it refuses a LIVE request without the explicit port.

## Deployment verification

Verify actual protected routes with the configured issuer, audience, signed client claim and current-state response. Provider acceptance must prove the required disable, logout and permanent-account-block behavior with old unexpired credentials and new session attempts. RFC 7662 active-state semantics alone do not establish that behavior. Account locking, SCA recovery, delegation and token exchange require their own application and identity-service contracts.
