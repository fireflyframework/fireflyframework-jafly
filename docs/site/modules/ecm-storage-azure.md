![Jafly. ECM Storage - Azure Blob](../assets/modules/ecm-storage-azure.svg)

# ECM Storage - Azure Blob

Implement ECM document-content storage against Azure Blob Storage.

Configure the container and one of the adapter credential modes, then select the Azure ECM adapter. Ensure the account/container exists and validate permissions for both reads and writes.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-ecm-storage-azure` | `jar` | [modules/ecm-storage-azure](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/ecm-storage-azure) |

Part of [Domain and data](index.md#domain-and-data).

## In the onboarding service

Store an applicant's supporting document in Azure Blob Storage through the ECM content port. Provision the storage resource and credentials, and keep the application's document/access metadata under its own policy.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-ecm-storage-azure</artifactId>
    <version>26.10.17</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

1. Add the dependency (see [Installation](#add-the-component)) and select this adapter via the `firefly.ecm.adapter-type` property:

```yaml
firefly:
  ecm:
    enabled: true
    adapter-type: azure-blob          # selects this module
    adapter:
      azure-blob:
        account-name: ${AZURE_ACCOUNT_NAME}
        container-name: documents
        connection-string: ${AZURE_CONNECTION_STRING}   # or account-key / sas-token / managed-identity
```

2. Inject the ECM `DocumentContentPort`. The Azure adapter is wired in automatically; your code depends only on the core port:

```java
import org.fireflyframework.ecm.port.document.DocumentContentPort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class DocumentService {

    private final DocumentContentPort content;

    public DocumentService(DocumentContentPort content) {
        this.content = content; // AzureBlobDocumentContentAdapter at runtime
    }

    public Mono<String> upload(UUID documentId, byte[] bytes, String mimeType) {
        // returns the storage path of the stored blob
        return content.storeContent(documentId, bytes, mimeType);
    }

    public Mono<byte[]> download(UUID documentId) {
        return content.getContent(documentId);
    }
}
```

That is all. Switching to a different backend (e.g. Amazon S3) is a matter of swapping the dependency and setting `firefly.ecm.adapter-type` accordingly, with no change to `DocumentService`.

## Configuration and wiring

The `firefly.ecm.adapter.azure-blob` namespace is bound by [AzureBlobAdapterProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-storage-azure/src/main/java/org/fireflyframework/ecm/adapter/azureblob/AzureBlobAdapterProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.ecm.adapter.azure-blob.account-name` | `String` | `not initialized` |
| `firefly.ecm.adapter.azure-blob.container-name` | `String` | `not initialized` |
| `firefly.ecm.adapter.azure-blob.account-key` | `String` | `not initialized` |
| `firefly.ecm.adapter.azure-blob.connection-string` | `String` | `not initialized` |
| `firefly.ecm.adapter.azure-blob.sas-token` | `String` | `not initialized` |
| `firefly.ecm.adapter.azure-blob.managed-identity` | `Boolean` | `false` |
| `firefly.ecm.adapter.azure-blob.endpoint` | `String` | `not initialized` |
| `firefly.ecm.adapter.azure-blob.path-prefix` | `String` | `"documents/"` |
| `firefly.ecm.adapter.azure-blob.enable-versioning` | `Boolean` | `true` |
| `firefly.ecm.adapter.azure-blob.access-tier` | `String` | `"Hot"` |
| `firefly.ecm.adapter.azure-blob.max-retries` | `Integer` | `3` |
| `firefly.ecm.adapter.azure-blob.timeout-seconds` | `Integer` | `30` |
| `firefly.ecm.adapter.azure-blob.block-size` | `Long` | `4194304L` |
| `firefly.ecm.adapter.azure-blob.block-upload-threshold` | `Long` | `268435456L` |

Property-based wiring conditions found in this module:

- `name = "firefly.ecm.adapter-type", havingValue = "azure-blob"`. [AzureBlobAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-storage-azure/src/main/java/org/fireflyframework/ecm/adapter/azureblob/AzureBlobAutoConfiguration.java).
- `name = "firefly.ecm.adapter-type", havingValue = "azure-blob"`. [AzureBlobDocumentContentAdapter.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-storage-azure/src/main/java/org/fireflyframework/ecm/adapter/azureblob/AzureBlobDocumentContentAdapter.java).

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`AzureBlobDocumentContentAdapter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-storage-azure/src/main/java/org/fireflyframework/ecm/adapter/azureblob/AzureBlobDocumentContentAdapter.java) | Defines `AzureBlobDocumentContentAdapter`, `storeContent`, `storeContentStream`, `deleteContent` operations. |
| [`AzureBlobAdapterProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-storage-azure/src/main/java/org/fireflyframework/ecm/adapter/azureblob/AzureBlobAdapterProperties.java) | Configuration properties for the Microsoft Azure Blob Storage ECM adapter. |
| [`AzureBlobAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-storage-azure/src/main/java/org/fireflyframework/ecm/adapter/azureblob/AzureBlobAutoConfiguration.java) | Defines `blobServiceClient`, `blobContainerClient`, `objectMapper`, `azureBlobCircuitBreaker` operations. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Enterprise Content Management Library](ecm.md) | Define content-management ports, adapter selection, document contracts, and resilience/observability support. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-ecm-storage-azure -am verify
./mvnw -pl :fireflyframework-ecm-storage-azure -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [AzureBlobAdapterTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-storage-azure/src/test/java/org/fireflyframework/ecm/adapter/azureblob/AzureBlobAdapterTest.java).

Mock, local-container, and application-context tests do not establish acceptance against a live provider account. Validate the intended provider deployment and credentials separately.

## API reference excerpt

Contract excerpt from [AzureBlobDocumentContentAdapter.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-storage-azure/src/main/java/org/fireflyframework/ecm/adapter/azureblob/AzureBlobDocumentContentAdapter.java). This fragment needs the full type and its imports.

```java
public class AzureBlobDocumentContentAdapter implements DocumentContentPort {
    private final BlobContainerClient containerClient;
    private final AzureBlobAdapterProperties properties;
    private final DataBufferFactory dataBufferFactory;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    public AzureBlobDocumentContentAdapter(BlobContainerClient containerClient,
                                          AzureBlobAdapterProperties properties,
        this.containerClient = containerClient;
        this.properties = properties;
        this.dataBufferFactory = new DefaultDataBufferFactory();
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
        log.info("AzureBlobDocumentContentAdapter initialized with container: {}, prefix: {}, resilience enabled",
                properties.getContainerName(), properties.getPathPrefix());
    }
    public Flux<DataBuffer> getContentStream(UUID documentId) {
        return Mono.fromCallable(() -> {
            String blobName = buildContentBlobName(documentId);
            BlobClient blobClient = containerClient.getBlobClient(blobName);
            if (!blobClient.exists()) {
                throw new RuntimeException("Content not found for document: " + documentId);
            }
            return blobClient.openInputStream();
```

## Detailed reference

### Configuration

All properties live under the `firefly.ecm.adapter.azure-blob` prefix and are bound by `AzureBlobAdapterProperties`. The adapter activates only when `firefly.ecm.adapter-type=azure-blob`.

```yaml
firefly:
  ecm:
    enabled: true
    adapter-type: azure-blob
    adapter:
      azure-blob:
        account-name: ${AZURE_ACCOUNT_NAME}          # required
        container-name: documents                    # required
        # --- one authentication option ---
        account-key: ${AZURE_ACCOUNT_KEY}             # account name + key
        connection-string: ${AZURE_CONNECTION_STRING} # full connection string
        sas-token: ${AZURE_SAS_TOKEN}                 # shared access signature
        managed-identity: false                       # use Azure AD / DefaultAzureCredential
        # --- endpoint & layout ---
        endpoint:                                     # custom/sovereign cloud endpoint (default: https://<account>.blob.core.windows.net)
        path-prefix: documents/                       # blob key prefix
        # --- upload tuning ---
        block-size: 4194304                           # 4 MB stream buffer / block size
        block-upload-threshold: 268435456             # 256 MB: switch to block-blob upload above this
        # --- resilience ---
        max-retries: 3                                # 0..10, drives the Resilience4j Retry
        timeout-seconds: 30                           # 1..300
        # --- storage policy (account/container-level intent) ---
        access-tier: Hot                              # Hot | Cool | Archive
        enable-versioning: true
        enable-encryption: true
        encryption-key-url:                           # customer-managed key (Key Vault) URL
        enable-soft-delete: true
        soft-delete-retention-days: 7                 # 1..365
```

Key properties:

| Property | Default | Description |
| --- | --- | --- |
| `account-name` | _(required)_ | Azure Storage account name. |
| `container-name` | _(required)_ | Blob container for document content; auto-created if missing. |
| `connection-string` / `account-key` / `sas-token` / `managed-identity` |, /, /, / `false` | Authentication options, tried in this order; at least one must be supplied. |
| `endpoint` | `https://<account>.blob.core.windows.net` | Override for sovereign/custom endpoints. |
| `path-prefix` | `documents/` | Prefix for blob names; final key is `{path-prefix}{documentId}.content`. |
| `block-size` | `4194304` (4 MB) | Buffer/block size used for streaming reads and writes. Min 1024. |
| `block-upload-threshold` | `268435456` (256 MB) | Content larger than this is uploaded via block blobs. Min 1024. |
| `max-retries` | `3` | Retry attempts for the `azureBlobRetry` Resilience4j instance. Range 0-10. |
| `timeout-seconds` | `30` | Operation timeout hint. Range 1-300. |
| `access-tier` | `Hot` | Intended blob access tier. |
| `enable-versioning` / `enable-soft-delete` / `soft-delete-retention-days` | `true` / `true` / `7` | Data-protection intent; soft-delete retention range 1-365 days. |
| `enable-encryption` / `encryption-key-url` | `true` /, | Encryption-at-rest intent and optional customer-managed key URL. |

A ready-to-copy profile is bundled at `src/main/resources/application-azure-blob.yml`.

## Continue the application

Use the [Enterprise Content Management guide](ecm.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-storage-azure/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/ecm-storage-azure/src/main/java).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-storage-azure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [application-azure-blob.yml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-storage-azure/src/main/resources/application-azure-blob.yml).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
