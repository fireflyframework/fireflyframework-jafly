![Jafly. ECM Storage - AWS (S3)](../../docs/site/assets/modules/ecm-storage-aws.svg)

# ECM Storage - AWS (S3)

Implement ECM document-content storage against Amazon S3.

Configure the bucket, region, credentials or environment access, and the ECM adapter selection. Review endpoint/path-style options for an S3-compatible test service and presigned URL behavior in the source tests.

[Module guide](../../docs/site/modules/ecm-storage-aws.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

Store an applicant's supporting document in Amazon S3 through the ECM content port. Provision the storage resource and credentials, and keep the application's document/access metadata under its own policy.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-ecm-storage-aws</artifactId>
    <version>26.10.22</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../../docs/site/maven.md#constructor-injection).

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

1. Add the dependency (see above) and select the S3 backend with a single property:

```yaml
firefly:
  ecm:
    enabled: true
    adapter-type: s3          # activates this adapter as the active DocumentContentPort
    adapter:
      s3:
        bucket-name: firefly-ecm-documents
        region: us-east-1
        # credentials omitted -> uses the AWS default credentials provider chain (IAM role, etc.)
```

That is all that is required. `S3AdapterAutoConfiguration` builds the `S3Client`, `S3Presigner` and credentials provider, and registers `S3DocumentContentAdapter` as the active `DocumentContentPort`.

2. Inject and use the port from your services (program against the SPI, not the S3 type):

```java
import lombok.RequiredArgsConstructor;
import org.fireflyframework.ecm.port.document.DocumentContentPort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentContentPort content;



    public Mono<String> upload(UUID documentId, byte[] bytes) {
        // returns the S3 object key where the content was stored
        return content.storeContent(documentId, bytes, "application/pdf");
    }

    public Mono<byte[]> download(UUID documentId) {
        return content.getContent(documentId);
    }

    public Mono<Boolean> verify(UUID documentId, String expectedSha256) {
        return content.verifyChecksum(documentId, expectedSha256, "SHA-256");
    }
}
```

3. (Optional) generate a pre-signed upload URL for direct browser-to-S3 uploads by injecting the concrete adapter:

```java
import org.fireflyframework.ecm.adapter.s3.S3DocumentContentAdapter;

// 15-minute pre-signed PUT URL
Mono<String> url = s3Adapter.generateUploadUrl(documentId, 15);
```

## Configuration

The `firefly.ecm.adapter.s3` namespace is bound by [S3AdapterProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-storage-aws/src/main/java/org/fireflyframework/ecm/adapter/s3/S3AdapterProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.ecm.adapter.s3.bucket-name` | `String` | `not initialized` |
| `firefly.ecm.adapter.s3.region` | `String` | `not initialized` |
| `firefly.ecm.adapter.s3.access-key` | `String` | `not initialized` |
| `firefly.ecm.adapter.s3.secret-key` | `String` | `not initialized` |
| `firefly.ecm.adapter.s3.endpoint` | `String` | `not initialized` |

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`S3DocumentContentAdapter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-storage-aws/src/main/java/org/fireflyframework/ecm/adapter/s3/S3DocumentContentAdapter.java) | Defines `S3DocumentContentAdapter`, `storeContent`, `storeContentStream`, `deleteContent` operations. |
| [`S3AdapterProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-storage-aws/src/main/java/org/fireflyframework/ecm/adapter/s3/S3AdapterProperties.java) | Configuration properties for the Amazon S3 ECM adapter. |
| [`S3AdapterAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-storage-aws/src/main/java/org/fireflyframework/ecm/adapter/s3/S3AdapterAutoConfiguration.java) | Defines `awsCredentialsProvider`, `s3Client` operations. |

## Continue the application

Use the [Enterprise Content Management guide](../../docs/site/modules/ecm.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-ecm-storage-aws -am verify
./mvnw -pl :fireflyframework-ecm-storage-aws -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/ecm-storage-aws.md).
