![Jafly. ECM eSignature - DocuSign](../../docs/site/assets/modules/ecm-esignature-docusign.svg)

# ECM eSignature - DocuSign

Implement the ECM signature-envelope port with DocuSign.

Configure integration/account/user identifiers and the signing credential through environment-backed configuration. Select the adapter and verify the account, auth server, and base URL for your DocuSign environment.

[Module guide](../../docs/site/modules/ecm-esignature-docusign.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

If onboarding includes a signed agreement, use the DocuSign adapter for the configured envelope workflow. The application supplies the agreement, recipients, and account configuration.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-ecm-esignature-docusign</artifactId>
    <version>26.10.22</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../../docs/site/maven.md#constructor-injection).

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

1. Add the dependency (see [Installation](#installation)) to a service that already builds on `fireflyframework-ecm`.

2. Select DocuSign and provide JWT credentials in `application.yml`:

```yaml
firefly:
  ecm:
    features:
      esignature: true
    esignature:
      provider: docusign          # selects this adapter
    adapter:
      docusign:
        integration-key: ${DOCUSIGN_INTEGRATION_KEY}
        user-id: ${DOCUSIGN_USER_ID}
        account-id: ${DOCUSIGN_ACCOUNT_ID}
        private-key: ${DOCUSIGN_PRIVATE_KEY}   # RSA private key (PEM)
        sandbox-mode: ${DOCUSIGN_SANDBOX:false}
```

3. Inject the ECM port. Your code depends only on the provider-neutral `SignatureEnvelopePort`, never on DocuSign types:

```java
import java.util.List;
import java.util.UUID;
import org.fireflyframework.ecm.domain.enums.esignature.SignatureRequestType;
import org.fireflyframework.ecm.domain.model.esignature.SignatureEnvelope;
import org.fireflyframework.ecm.domain.model.esignature.SignatureField;
import org.fireflyframework.ecm.domain.model.esignature.SignatureRequest;
import org.fireflyframework.ecm.port.esignature.SignatureEnvelopePort;
import reactor.core.publisher.Mono;

// The application supplies the port, accessible document, signer and actual document position.
Mono<SignatureEnvelope> prepareAgreement(SignatureEnvelopePort envelopes, UUID documentId,
                                       String signerEmail, String signerName, SignatureField position) {
    SignatureRequest signer = SignatureRequest.builder()
            .signerEmail(signerEmail).signerName(signerName)
            .requestType(SignatureRequestType.SIGNATURE)
            .signatureFields(List.of(position)).build();
    return envelopes.createEnvelope(SignatureEnvelope.builder()
            .title("Please review and sign")
            .documentIds(List.of(documentId))
            .signatureRequests(List.of(signer)).build());
}
```

Creation returns a draft with its caller-supplied or newly assigned local UUID and the actual external envelope ID. The application Core persists that reference and applies its existing authorization rules. Later UUID operations require an application Core bean implementing `SignatureEnvelopeReferenceResolver.resolveExternalId(UUID, SignatureProvider)`; an empty result means no authorized reference. There is no adapter-owned memory map or framework business table. `getEnvelopeByExternalId` queries the configured provider account directly and leaves the internal UUID unknown. A four-argument direct adapter constructor remains available; without a resolver, UUID-only lookups/sends/voids fail explicitly as unsupported.

The Core signing journey currently uses `SignatureRequestPort`. This module implements the envelope port; that Core binding/bridge remains a separate integration requirement. Selecting another provider does not establish identical supported capabilities.

## Configuration

The framework-managed client validates its configuration locally at startup. It accepts unencrypted RSA private keys in PKCS#1 or PKCS#8 PEM format, with at least 2048 bits. Token exchange and user-info requests occur when an SDK operation first needs authentication. Successful authentication validates the configured user and account, and subsequent operations reuse the token until its refresh window. There are no background authentication requests or automatic retries of envelope writes.

`auth-server` accepts a bare host or an HTTPS authority; it is converted to the host expected by the SDK. REST and OAuth endpoints reject HTTP, URL credentials and query/fragment components. OAuth paths must be empty or `/`. Configure the correct account REST endpoint explicitly; a user-info response does not replace it.

| Client setting | Default | Meaning |
| --- | --- | --- |
| `connection-timeout` | `30s` | Maximum configured connection wait. |
| `read-timeout` | `60s` | Maximum configured response read wait. |
| `authentication-timeout` | `90s` | Deadline for accepting a completed authentication round; fresh auth clients receive IO timeouts bounded by its remaining time. |
| `authentication-wait-timeout` | `30s` | Bound for waiting on another refresh or the short client lifecycle transition. |
| `authentication-refresh-skew` | `60s` | Refresh margin, capped at 10% of the returned token lifetime. |
| `max-concurrent-requests` | `4` | Threads in this client's owned SDK execution lane. |
| `max-queued-requests-per-thread` | `16` | Pending task bound per lane thread; overflow is a typed busy failure. |

Blocking SDK calls execute on that owned lane after subscription. The Spring bean closes the HTTP clients it creates and disposes its lane. SDK authentication clients close before a validated credential is published. An observed owned connector cleanup failure refuses publication and keeps the client failed. Connector lifecycle observations do not establish physical socket closure.

Each subscription to the framework-managed execution lane owns its cancellation fence. Cancellation remains recorded even if a transport consumes the thread interrupt. A cancelled authentication round cannot publish a token or continue into a later user-info/envelope exchange; cancellation does not clear another subscription's validated cache. The exact owned connector checks this fence before delegating a new exchange. This is admission control, not proof that an already admitted HTTP exchange was physically aborted.

Successful authentication-client cleanup and the wait to publish a token consume the original authentication deadline. Completion past that deadline is refused before publication. The client closes and refuses a newly allocated HTTP client if allocation crossed shutdown. These checks do not establish a hard duration limit for vendor cleanup callbacks or physical socket closure.

An explicitly supplied `ApiClient` remains caller-owned: this autoconfiguration neither replaces it nor installs authentication on it. The caller supplies its authentication, timeout and HTTP-client lifecycle policies. Provider errors surface as `DocuSignAdapterException` categories and optional HTTP status, without raw SDK response bodies or causes. Provider existence checks return false for an actual 404 or an empty authorized resolver result; absence of a resolver is unsupported. Provider failures do not become empty successful lists.

This source targets DocuSign SDK **6.9.0 only**. SDK4.3 lacks the per-connection authentication constructor and is unsupported. Both dependency version properties must select6.9 before publication or a full Maven build. Framework public constructors and port methods are preserved; this does not imply support for the older vendor runtime. Authentication and provider workflow status do not establish cryptographic signature validity or legal acceptance.

## Envelope contract

| Input or operation | Supported behavior |
| --- | --- |
| Documents | Ordered document UUIDs resolve through `DocumentPort` and `DocumentContentPort.getContent`. Metadata must describe PDF content; names, optional extension and size are checked. Actual bytes are base64-encoded into SDK documents with stable1-based IDs. No URL fetch, PDF parsing/decryption or storage-encryption inference occurs. |
| Draft creation | Subject/description, local UUID, creator metadata, document IDs and supported requests; status is `created`/DRAFT. Missing content or unsupported settings fail before provider IO. |
| Recipients | SIGNATURE, INITIAL and FORM_FILL use SDK signers; CARBON_COPY uses carbon copies. Name, email, role and routing order map explicitly. Per-signer message/language use SDK email-notification fields; language codes use the SDK's declared set. |
| Routing | PARALLEL requires order1; SEQUENTIAL requires explicit distinct positive orders; HYBRID permits explicit shared groups. The default honors supplied routing orders, otherwise order1. |
| Tabs | SIGNATURE, INITIAL and TEXT. Coordinates are finite integral points at72DPI from the top-left, pages start at1, x/y are nonnegative. Supplied dimensions must be finite positive values and are serialized without rounding or unit conversion. Text required/locked/value/default/validation, labels, tooltips and tab order map to the supported SDK fields. FORM_FILL cannot contain signature tabs. |
| Metadata update | A patch containing local/external reference, optional DOCUSIGN selector, subject and description. Lifecycle/settings/document/recipient changes are refused; use the dedicated send/void operations. |
| Send/void | UUID lookup uses the caller-owned resolver. Send records the exact UUID argument in additive `sentByActorId`; legacy `Long sentBy` remains separate. Void requires the supplied nonblank reason and actor. Delete is not reinterpreted as void. The actor is caller provenance, not authentication evidence. |
| Read status | `created`→DRAFT, `sent`→SENT, `delivered`/`signed`→IN_PROGRESS, `completed`→COMPLETED, `declined`→DECLINED, `voided`→VOIDED. Other/unrepresentable states and malformed provider dates are typed response failures. Only actual provider timestamps are returned. |
| Query | The additive status/from/to/limit overload uses the explicit provider date window; no default lookback. It returns up to the requested bound from the provider response, with no whole-history or pagination-completeness claim. DRAFT/SENT/COMPLETED/DECLINED/VOIDED filters are supported. |
| Unsupported | Date-less/global creator/sender/provider queries, unbounded completed/expiring queries, delete/archive/resend/embedded URL, per-request expiry/time-zone/authentication schemes, reminders/expiration/callback/priority/custom metadata, partial-signing policies, advanced fonts/conditions and other tab/request types. These return typed UNSUPPORTED, never empty successful placeholders. |

Admission limits:10 documents,10MiB per document,20MiB total bytes,100 recipients,1,000 tabs,100-character subject,10,000-character description/signer message/text value and1,000-character text validation pattern. The content port's byte-array method already materializes its input; these limits do not certify upstream storage streaming or PDF correctness. At-rest encryption metadata does not mean the returned bytes are an encrypted PDF.

Provider update summaries with error details, malformed success responses and a post-write404 fail explicitly. Writes are not automatically retried, and a failure after a provider effect does not imply provider rollback. Persist returned references in Core before later UUID operations. Actual provider authentication/envelope/signature acceptance and the Core signing journey require separate qualification.

The `firefly.ecm.adapter.docusign` namespace is bound by [DocuSignAdapterProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-docusign/src/main/java/org/fireflyframework/ecm/adapter/docusign/DocuSignAdapterProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.ecm.adapter.docusign.integration-key` | `String` | `not initialized` |
| `firefly.ecm.adapter.docusign.user-id` | `String` | `not initialized` |
| `firefly.ecm.adapter.docusign.account-id` | `String` | `not initialized` |
| `firefly.ecm.adapter.docusign.private-key` | `String` | `not initialized` |
| `firefly.ecm.adapter.docusign.base-url` | `String` | `"https://na3.docusign.net/restapi"` |

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`DocuSignSignatureEnvelopeAdapter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-docusign/src/main/java/org/fireflyframework/ecm/adapter/docusign/DocuSignSignatureEnvelopeAdapter.java) | Defines `DocuSignSignatureEnvelopeAdapter`, `createEnvelope`, `updateEnvelope`, `deleteEnvelope` operations. |
| [`DocuSignAdapterProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-docusign/src/main/java/org/fireflyframework/ecm/adapter/docusign/DocuSignAdapterProperties.java) | Carries `integrationKey`, `userId`, `accountId`, `privateKey`, `baseUrl` data; field constraints and defaults are declared in the linked source. |
| [`DocuSignAdapterAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-esignature-docusign/src/main/java/org/fireflyframework/ecm/adapter/docusign/DocuSignAdapterAutoConfiguration.java) | Auto-configuration for the DocuSign ECM adapter. |

## Continue the application

Use the [Enterprise Content Management guide](../../docs/site/modules/ecm.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-ecm-esignature-docusign -am verify
./mvnw -pl :fireflyframework-ecm-esignature-docusign -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/ecm-esignature-docusign.md).

## Immutable account binding

`SignatureEnvelopePort.accountBinding()` lazily returns immutable, nonsecret provider/account/HTTPS endpoint configuration. Reading it does not authenticate, allocate an HTTP client, or contact DocuSign. It is not proof of caller authorization or of permission at the provider; the application Core must apply its signed tenant/actor and policy checks before using persisted references.

The framework-managed authentication client and envelope adapter share the same settings-derived binding object. All envelope reads and writes use that frozen account. Pairing the managed client with properties for another account fails locally. Its REST endpoint cannot be changed after construction. The existing direct adapter constructors snapshot a caller-owned client's account and actual endpoint once; subsequent properties changes do not redirect operations, and observed endpoint drift fails before an SDK call or binding lookup. Callers must not concurrently mutate their custom SDK client's configuration. This contract does not certify arbitrary custom clients or vendor account authorization. Other adapters retain an explicit unsupported default lookup until they implement an equivalent contract.

The binding contains no credentials and introduces no business persistence. The Core signing bridge, immutable dispatched content/version binding, and actual provider acceptance remain separate requirements.
