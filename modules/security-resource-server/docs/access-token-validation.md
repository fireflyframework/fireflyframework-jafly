# Access-token claim validation

`AccessTokenValidators` provides reusable claim-validation profiles for JWT access tokens. Install a profile on a signature-verifying decoder; the helper itself does not select keys, verify signatures or configure the security filter chain.

```java
import org.fireflyframework.security.rs.AccessTokenValidators;

// Require exp and retain Spring's default timestamp checks and clock skew.
decoder.setJwtValidator(AccessTokenValidators.timestamps());

// Also require an exact issuer and membership of one exact audience.
decoder.setJwtValidator(AccessTokenValidators.forIssuerAndAudience(issuer, audience));
```

Both factories return `OAuth2TokenValidator<Jwt>`. Each composition includes a fresh Spring `JwtTimestampValidator`, preserving its default clock/skew checks for present `exp` and `nbf`, and adds an explicit required-exp check. A signed access token without expiration now fails with `invalid_token`. No new issued-at, not-before presence, token-age or maximum-lifetime requirement is introduced.

`forIssuerAndAudience` rejects null/blank issuer or audience configuration immediately. The existing Spring issuer validator compares the exact configured issuer. Audience matching is exact membership: a token may contain additional audiences, but must include the configured audience. Values are not trimmed, case-folded, normalized, wildcard-matched or replaced with defaults. Error descriptions do not include token values or claims.

The framework's default `ResourceServerAutoConfiguration` uses `timestamps()`. This intentionally closes the previous acceptance of tokens with missing `exp`. Its optional issuer setting and optional acceptable-audience list keep their existing behavior: an absent issuer setting does not create an issuer requirement, and a nonempty audience list accepts any configured audience. Remote-JWKS/in-memory decoder selection, signature algorithm allowlists, authority mapping, policy decisions and default security chain are unchanged.

Applications supplying their own decoder/validator must opt into the helper; overriding the decoder also overrides the framework's validation configuration. Four case-service applications adopt the composed helper in a separate consumer gate. Authentication still requires the deployment's trusted signing keys and issuer/audience configuration.

Qualification uses pure validator contracts plus the existing real RANDOM_PORT resource-server integration fixture with signed RSA tokens. The missing-exp test reproduces the previous200 response and now requires401; the existing valid signed token still returns200. Expired/future-not-before claims, default skew, issuer/audience membership and blank configuration are covered separately. This module's verification is not a claim that a later full-framework gate or external identity-provider acceptance has completed.
