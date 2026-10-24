![Jafly. Enterprise Content Management Library](../../docs/site/assets/modules/ecm.svg)

# Enterprise Content Management Library

Define content-management ports, adapter selection, document contracts, and resilience/observability support.

Select the concrete storage or signature adapter required by the application and call the ECM ports. Review adapter availability and graceful-degradation behavior before assuming an optional capability is backed by a live provider.

[Module guide](../../docs/site/modules/ecm.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

An applicant may upload supporting documents or sign an agreement after submission. Use the content/signature ports with a real provider adapter; OCR and classification ports need an application-supplied implementation.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-ecm</artifactId>
    <version>26.10.22</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../../docs/site/maven.md#constructor-injection).

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

1. Add the dependency (above) and a provider adapter. Select it via configuration:

```yaml
firefly:
  ecm:
    enabled: true
    adapter-type: s3          # selects the storage/core adapter (e.g. AWS S3)
    properties:               # adapter-specific settings (free-form map)
      bucket-name: my-documents
      region: us-east-1
    features:
      document-management: true
      content-storage: true
      esignature: true        # off by default — opt in
    esignature:
      provider: docusign      # selects the e-signature adapter independently
```

2. Inject the reactive ports you need. Your code is provider-agnostic:

```java
import lombok.RequiredArgsConstructor;
import org.fireflyframework.ecm.port.document.DocumentContentPort;
import org.fireflyframework.ecm.port.esignature.SignatureEnvelopePort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentContentPort content;
    private final SignatureEnvelopePort envelopes;



    /** Store binary content for a document and return its storage path. */
    public Mono<String> upload(UUID documentId, byte[] bytes, String mimeType) {
        return content.storeContent(documentId, bytes, mimeType);
    }
}
```

3. (Optional) Use the built-in in-memory adapters for local development/tests:

```yaml
firefly:
  ecm:
    search:
      enabled: true       # registers LocalDocumentSearchAdapter
    permissions:
      enabled: true       # registers LocalPermissionAdapter
```

To write your own adapter, annotate a port implementation with `@EcmAdapter` and the registry will discover it:

```java
import org.fireflyframework.ecm.adapter.EcmAdapter;
import org.fireflyframework.ecm.adapter.AdapterFeature;
import org.fireflyframework.ecm.port.document.DocumentContentPort;

@EcmAdapter(
    type = "s3",
    description = "AWS S3 content storage adapter",
    supportedFeatures = { AdapterFeature.CONTENT_STORAGE, AdapterFeature.STREAMING, AdapterFeature.CLOUD_STORAGE }
)
public class S3DocumentContentAdapter implements DocumentContentPort {
    // ... reactive implementation ...
}
```

## Configuration

The `firefly.ecm` namespace is bound by [EcmProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm/src/main/java/org/fireflyframework/ecm/config/EcmProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.ecm.enabled` | `Boolean` | `true` |
| `firefly.ecm.adapter-type` | `String` | `not initialized` |
| `firefly.ecm.properties` | `Map<String, Object>` | `not initialized` |
| `firefly.ecm.connection` | `Connection` | `new Connection()` |
| `firefly.ecm.features` | `Features` | `new Features()` |

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`DocumentContentPort`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm/src/main/java/org/fireflyframework/ecm/port/document/DocumentContentPort.java) | Port interface for document binary content storage and retrieval operations. |
| [`SignatureEnvelopePort`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm/src/main/java/org/fireflyframework/ecm/port/esignature/SignatureEnvelopePort.java) | Port interface for signature envelope management operations in the Firefly ECM system. |
| [`AdapterRegistry`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm/src/main/java/org/fireflyframework/ecm/adapter/AdapterRegistry.java) | Central registry for ECM adapter discovery, registration, and management. |
| [`AdapterSelector`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm/src/main/java/org/fireflyframework/ecm/adapter/AdapterSelector.java) | Service responsible for selecting appropriate ECM adapters based on configuration and requirements. |
| [`EcmProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm/src/main/java/org/fireflyframework/ecm/config/EcmProperties.java) | Configuration properties for the Firefly ECM (Enterprise Content Management) system. |

## Continue the application

Use the [ECM Storage - AWS (S3) guide](../../docs/site/modules/ecm-storage-aws.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-ecm -am verify
./mvnw -pl :fireflyframework-ecm -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/ecm.md).
