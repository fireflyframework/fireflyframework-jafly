![Jafly. ECM Storage - AWS (S3)](../assets/modules/ecm-storage-aws.svg)

# ECM Storage - AWS (S3)

Implement ECM document-content storage against Amazon S3.

Configure the bucket, region, credentials or environment access, and the ECM adapter selection. Review endpoint/path-style options for an S3-compatible test service and presigned URL behavior in the source tests.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-ecm-storage-aws` | `jar` | [modules/ecm-storage-aws](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/ecm-storage-aws) |

Part of [Domain and data](index.md#domain-and-data).

## In the onboarding service

Store an applicant's supporting document in Amazon S3 through the ECM content port. Provision the storage resource and credentials, and keep the application's document/access metadata under its own policy.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-ecm-storage-aws</artifactId>
    <version>26.10.21-SNAPSHOT</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../maven.md#constructor-injection).

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

## Configuration and wiring

The `firefly.ecm.adapter.s3` namespace is bound by [S3AdapterProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-storage-aws/src/main/java/org/fireflyframework/ecm/adapter/s3/S3AdapterProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.ecm.adapter.s3.bucket-name` | `String` | `not initialized` |
| `firefly.ecm.adapter.s3.region` | `String` | `not initialized` |
| `firefly.ecm.adapter.s3.access-key` | `String` | `not initialized` |
| `firefly.ecm.adapter.s3.secret-key` | `String` | `not initialized` |
| `firefly.ecm.adapter.s3.endpoint` | `String` | `not initialized` |
| `firefly.ecm.adapter.s3.path-prefix` | `String` | `"documents/"` |
| `firefly.ecm.adapter.s3.enable-versioning` | `Boolean` | `true` |
| `firefly.ecm.adapter.s3.path-style-access` | `Boolean` | `false` |
| `firefly.ecm.adapter.s3.connection-timeout` | `Duration` | `Duration.ofSeconds(30)` |
| `firefly.ecm.adapter.s3.socket-timeout` | `Duration` | `Duration.ofSeconds(30)` |
| `firefly.ecm.adapter.s3.max-retries` | `Integer` | `3` |
| `firefly.ecm.adapter.s3.enable-encryption` | `Boolean` | `true` |
| `firefly.ecm.adapter.s3.kms-key-id` | `String` | `not initialized` |
| `firefly.ecm.adapter.s3.storage-class` | `String` | `"STANDARD"` |

Property-based wiring conditions found in this module:

- `name = "firefly.ecm.adapter-type", havingValue = "s3"`. [S3AdapterAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-storage-aws/src/main/java/org/fireflyframework/ecm/adapter/s3/S3AdapterAutoConfiguration.java).
- `name = "firefly.ecm.adapter-type", havingValue = "s3"`. [S3Beans.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-storage-aws/src/main/java/org/fireflyframework/ecm/adapter/s3/S3Beans.java).
- `name = "firefly.ecm.adapter-type", havingValue = "s3"`. [S3DocumentContentAdapter.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-storage-aws/src/main/java/org/fireflyframework/ecm/adapter/s3/S3DocumentContentAdapter.java).

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`S3DocumentContentAdapter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-storage-aws/src/main/java/org/fireflyframework/ecm/adapter/s3/S3DocumentContentAdapter.java) | Defines `S3DocumentContentAdapter`, `storeContent`, `storeContentStream`, `deleteContent` operations. |
| [`S3AdapterProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-storage-aws/src/main/java/org/fireflyframework/ecm/adapter/s3/S3AdapterProperties.java) | Configuration properties for the Amazon S3 ECM adapter. |
| [`S3AdapterAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-storage-aws/src/main/java/org/fireflyframework/ecm/adapter/s3/S3AdapterAutoConfiguration.java) | Defines `awsCredentialsProvider`, `s3Client` operations. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Enterprise Content Management Library](ecm.md) | Define content-management ports, adapter selection, document contracts, and resilience/observability support. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-ecm-storage-aws -am verify
./mvnw -pl :fireflyframework-ecm-storage-aws -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [S3AdapterAutoConfigurationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-storage-aws/src/test/java/org/fireflyframework/ecm/adapter/s3/S3AdapterAutoConfigurationTest.java).
- [S3DocumentContentAdapterTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-storage-aws/src/test/java/org/fireflyframework/ecm/adapter/s3/S3DocumentContentAdapterTest.java).
- [S3PresignTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-storage-aws/src/test/java/org/fireflyframework/ecm/adapter/s3/S3PresignTest.java).

Mock, local-container, and application-context tests do not establish acceptance against a live provider account. Validate the intended provider deployment and credentials separately.

## Test fixture

The `getContentRange_readsRequestedRange_andReturnsBytes` test demonstrates one concrete behavior. Excerpt from [S3DocumentContentAdapterTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-storage-aws/src/test/java/org/fireflyframework/ecm/adapter/s3/S3DocumentContentAdapterTest.java). The test class supplies its fixtures and imports.

```java
@Test
    void getContentRange_readsRequestedRange_andReturnsBytes() throws Exception {
        byte[] rangeBytes = "bcd".getBytes();
        GetObjectResponse getResp = GetObjectResponse.builder().contentLength((long) rangeBytes.length).build();
ResponseInputStream<GetObjectResponse> ris = new ResponseInputStream<>(
                getResp,
                AbortableInputStream.create(new ByteArrayInputStream(rangeBytes))
        );
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(ris);

        byte[] result = ((S3DocumentContentAdapter) contentPort).getContentRange(documentId, 1L, 3L).block();
        assertThat(new String(result)).isEqualTo("bcd");

        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(captor.capture());
        assertThat(captor.getValue().range()).isEqualTo("bytes=1-3");
    }
```

## Detailed reference

### Configuration

All properties live under the `firefly.ecm.adapter.s3` prefix and are bound by `S3AdapterProperties` (validated with Bean Validation). The example below shows every property with its real default:

```yaml
firefly:
  ecm:
    enabled: true
    adapter-type: s3                 # must be "s3" to activate this adapter
    adapter:
      s3:
        # --- Required ---
        bucket-name: firefly-ecm-documents   # target S3 bucket (required, not blank)
        region: us-east-1                    # AWS region (required, not blank)

        # --- Credentials (optional; omit to use the default provider chain / IAM roles) ---
        access-key:                          # static AWS access key
        secret-key:                          # static AWS secret key

        # --- Endpoint & addressing (optional; for MinIO / LocalStack / S3-compatible stores) ---
        endpoint:                            # custom S3 endpoint override
        path-style-access: false             # force path-style URLs (true for MinIO/LocalStack)
        path-prefix: documents/              # key prefix for every stored object

        # --- Reliability ---
        connection-timeout: PT30S            # API call timeout (Duration)
        socket-timeout: PT30S                # API call attempt timeout (Duration)
        max-retries: 3                       # SDK retry attempts

        # --- Object behaviour ---
        enable-versioning: true              # mark objects as versioned
        enable-encryption: true              # request server-side encryption
        kms-key-id:                          # SSE-KMS key id (when using KMS)
        storage-class: STANDARD              # S3 storage class

        # --- Large files ---
        enable-multipart: true               # use multipart upload above the threshold
        multipart-threshold: 5242880         # 5 MB — switch to multipart above this size
        multipart-part-size: 5242880         # 5 MB — part size for multipart uploads
```

#### Key properties

| Property | Default | Description |
| -------- | ------- | ----------- |
| `firefly.ecm.adapter-type` |. | Set to `s3` to activate this adapter (ECM core property). |
| `firefly.ecm.adapter.s3.bucket-name` |. | Required. Target S3 bucket. |
| `firefly.ecm.adapter.s3.region` |. | Required. AWS region, e.g. `eu-west-1`. |
| `firefly.ecm.adapter.s3.access-key` / `secret-key` | _unset_ | Static credentials. When omitted, the AWS `DefaultCredentialsProvider` chain is used (recommended: IAM roles). |
| `firefly.ecm.adapter.s3.endpoint` | _unset_ | Custom endpoint for S3-compatible stores (MinIO, LocalStack). |
| `firefly.ecm.adapter.s3.path-style-access` | `false` | Force path-style addressing. set `true` for MinIO / LocalStack. |
| `firefly.ecm.adapter.s3.path-prefix` | `documents/` | Key prefix applied to every object: keys become `<prefix><documentId>`. |
| `firefly.ecm.adapter.s3.connection-timeout` | `PT30S` | Overall API call timeout. |
| `firefly.ecm.adapter.s3.socket-timeout` | `PT30S` | Per-attempt API call timeout. |
| `firefly.ecm.adapter.s3.max-retries` | `3` | Maximum SDK retry attempts. |
| `firefly.ecm.adapter.s3.enable-multipart` | `true` | Enable multipart upload for large objects. |
| `firefly.ecm.adapter.s3.multipart-threshold` | `5242880` (5 MB) | Size above which multipart upload is used. |
| `firefly.ecm.adapter.s3.kms-key-id` | _unset_ | KMS key id for SSE-KMS encryption. |
| `firefly.ecm.adapter.s3.storage-class` | `STANDARD` | S3 storage class (e.g. `STANDARD_IA`, `INTELLIGENT_TIERING`). |

> A ready-to-use sample profile is bundled at `src/main/resources/application-s3.yml`, with every value sourced from environment variables.

## Continue the application

Use the [Enterprise Content Management guide](ecm.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-storage-aws/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/ecm-storage-aws/src/main/java).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-storage-aws/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [application-s3.yml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-storage-aws/src/main/resources/application-s3.yml).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
