# Header bearer APIs and browser CSRF

The default resource-server chain authenticates protected routes from an explicit `Authorization: Bearer <token>` header. It does not load or save authentication through a WebSession. HTTP Basic, form login, query/form access tokens and token cookies are not alternative authentication transports. Supplying a session cookie cannot supply API authority, including when another application component previously stored an authenticated Spring Security context in that session.

JWT signature, lifetime, issuer/audience settings and current URL policy still apply. A valid header token must satisfy policy on each protected request. An invalid header token does not fall back to a stored session, and a valid token takes its identity from that token when a conflicting authenticated session cookie is present.

## Native CSRF behavior

The chain retains Spring Security's native CSRF filter and the resource server's native bearer exemption. An explicit header bearer request can call POST, PUT, PATCH or DELETE without a CSRF token; the JWT decoder and policy still decide whether that request is authorized. No custom CSRF matcher or universal CSRF-disable switch is installed.

Unsafe requests without explicit bearer authentication require a valid native CSRF token, even on paths listed in `permit-matchers`. `permitAll` is an authorization decision and does not disable CSRF protection. A browser application must supply its own permitted view/token-delivery route and include the native CSRF token in the supported header/form field. This module adds no token-delivery or browser-login endpoint. Spring's default CSRF token repository may use WebSession to store CSRF state; that storage does not make a session an authentication authority.

| Request | Default-chain result |
| --- | --- |
| Protected safe request, no valid bearer | 401 |
| Protected unsafe request, no bearer and missing/invalid CSRF token | 403 before authentication |
| Protected unsafe request, valid CSRF/session but no bearer | 401 |
| Valid header bearer, current policy permits | Controller response; CSRF token not required |
| Valid header bearer, current policy denies | 403 |
| Invalid header bearer plus an authenticated session | 401 |
| Allowlisted unsafe browser request, no bearer and missing CSRF token | 403 |
| Allowlisted unsafe browser request with valid CSRF | Controller response, subject to its application-owned behavior |

The non-bearer unsafe 403 result is an intentional correction from the earlier chain, which disabled CSRF globally and could load session authentication. Clients should send their legitimate header bearer token rather than infer authority from cookies or change HTTP methods to bypass a protection. Public browser/webhook routes must review their own explicit authentication and CSRF contract; an allowlist is not a business permission model.

## Application-owned browser chains

Applications that require session login supply their own `SecurityWebFilterChain` through the existing bean-backoff seam and retain appropriate CSRF controls. The default API chain is not a session-login implementation. Supplying another chain also transfers responsibility for matching routes, authentication, CSRF, policy and headers to that application configuration.

The maintained [real HTTP boundary tests](../src/test/java/org/fireflyframework/security/rs/BearerApiCsrfBoundaryIntegrationTest.java) use runtime-signed synthetic JWTs, a deliberately server-seeded test session, native CSRF tokens and current policy decisions. They qualify this default chain; they do not establish a deployed identity provider, browser exploit, customer consent or financial authority.

Primary reference: [Spring Security WebFlux CSRF](https://docs.spring.io/spring-security/reference/reactive/exploits/csrf.html). The qualified dependency is Spring Security 7.1.1; later documentation may describe a newer release.
