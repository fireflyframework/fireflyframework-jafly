# Security and identity

![Security and identity](../assets/banners/capability-security-and-identity.svg)

The onboarding service accepts customer details and exposes application records. Before opening those routes to applicants or reviewers, decide who can submit an application, who can read it, and who can approve it.

## Choose the enforcement boundary

| Requirement | Start with |
| --- | --- |
| Validate a signed bearer JWT at WebFlux routes | [Resource server](../modules/security-resource-server.md) |
| Authorize a protected application method | [Method policy](../modules/security-method-policy.md), using the [security API](../modules/security-api.md) |
| Ask an external policy or relationship service | One [Cerbos](../modules/security-adapter-cerbos.md), [OPA](../modules/security-adapter-opa.md), or [OpenFGA](../modules/security-adapter-openfga.md) adapter |
| Log in browser users and retain tokens server-side | [BFF](../modules/security-bff.md) |
| Manage users with an identity provider | [Identity-provider contracts](../modules/security-idp.md), then the selected provider adapter |

Start with the route or method you need to protect. Shared contracts alone do not install an enforcement boundary. An application that only validates external JWTs does not also need to host an authorization server or manage user accounts.

## Protect the onboarding flow

1. Run the [first service](../getting-started.md) and identify its submission and read routes. That application has no authentication policy.
2. Follow the resource-server guide to configure the expected issuer and signing-key source. For an external provider, set both `issuer` and `jwk-set-uri`; the issuer value alone does not discover signing keys.
3. Define application permissions, including ownership of an application and the reviewer role. The [method-policy guide](../modules/security-method-policy.md) shows how a protected method reaches the policy evaluator.
4. Choose a policy adapter only if the decision belongs to that external service. Keep trusted tenant and reviewer identity outside caller-controlled request fields.

This resource-server binding configures JWT validation. Do not assume it creates an opaque-token chain because introspection ports exist in the wider security API.

## Check access with your configuration

The [resource-server integration tests](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-resource-server/src/test/java/org/fireflyframework/security/rs/ResourceServerIntegrationTest.java) and [method-security tests](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-method-policy/src/test/java/org/fireflyframework/security/method/MethodSecurityIntegrationTest.java) show the library's test fixtures and enforcement paths.

For your onboarding deployment, verify that missing or invalid tokens fail, one applicant cannot read another applicant's data, and an unauthorized reviewer cannot approve it. Test those outcomes against your selected issuer, keys, and policy configuration. The runnable first-service tests do not prove that provider setup.

If your service already has Spring Security policies, keep the working boundary and adopt only the Jafly contracts or adapters you need.

Next, connect the protected operation to [business operations](business-operations.md), where a handler owns the application decision.
