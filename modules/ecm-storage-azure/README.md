![Jafly. ECM Storage - Azure Blob](../../docs/site/assets/modules/ecm-storage-azure.svg)

# ECM Storage - Azure Blob

Implement ECM document-content storage against Azure Blob Storage.

Configure the container and one of the adapter credential modes, then select the Azure ECM adapter. Ensure the account/container exists and validate permissions for both reads and writes.

[Module guide](../../docs/site/modules/ecm-storage-azure.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

Store an applicant's supporting document in Azure Blob Storage through the ECM content port. Provision the storage resource and credentials, and keep the application's document/access metadata under its own policy.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-ecm-storage-azure</artifactId>
    <version>26.10.17-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

1. Add the dependency (see [Installation](#installation)) and select this adapter via the `firefly.ecm.adapter-type` property:

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

## Configuration

The `firefly.ecm.adapter.azure-blob` namespace is bound by [AzureBlobAdapterProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-storage-azure/src/main/java/org/fireflyframework/ecm/adapter/azureblob/AzureBlobAdapterProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.ecm.adapter.azure-blob.account-name` | `String` | `not initialized` |
| `firefly.ecm.adapter.azure-blob.container-name` | `String` | `not initialized` |
| `firefly.ecm.adapter.azure-blob.account-key` | `String` | `not initialized` |
| `firefly.ecm.adapter.azure-blob.connection-string` | `String` | `not initialized` |
| `firefly.ecm.adapter.azure-blob.sas-token` | `String` | `not initialized` |

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`AzureBlobDocumentContentAdapter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-storage-azure/src/main/java/org/fireflyframework/ecm/adapter/azureblob/AzureBlobDocumentContentAdapter.java) | Defines `AzureBlobDocumentContentAdapter`, `storeContent`, `storeContentStream`, `deleteContent` operations. |
| [`AzureBlobAdapterProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-storage-azure/src/main/java/org/fireflyframework/ecm/adapter/azureblob/AzureBlobAdapterProperties.java) | Configuration properties for the Microsoft Azure Blob Storage ECM adapter. |
| [`AzureBlobAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/ecm-storage-azure/src/main/java/org/fireflyframework/ecm/adapter/azureblob/AzureBlobAutoConfiguration.java) | Defines `blobServiceClient`, `blobContainerClient`, `objectMapper`, `azureBlobCircuitBreaker` operations. |

## Continue the application

Use the [Enterprise Content Management guide](../../docs/site/modules/ecm.md) for the next part of this integration. ## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-ecm-storage-azure -am verify
./mvnw -pl :fireflyframework-ecm-storage-azure -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/ecm-storage-azure.md).
