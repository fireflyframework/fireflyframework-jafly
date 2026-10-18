# ECM API reference

Use ECM ports to store applicant documents or manage a signature workflow. An adapter implements a set of ports; selecting it does not supply every ECM capability. The application still owns document access policy and its onboarding records.

[The ECM component guide](../../../../docs/site/modules/ecm.md) explains dependencies, provider selection, and configuration. Use the selected storage/signature adapter guide for its actual supported operations.

## Ports

| Area | Contract | Use |
| --- | --- | --- |
| audit | [AuditPort](../../src/main/java/org/fireflyframework/ecm/port/audit/AuditPort.java) | Record and query content audit events. |
| document | [DocumentContentPort](../../src/main/java/org/fireflyframework/ecm/port/document/DocumentContentPort.java) | Store and retrieve binary document content. |
| document | [DocumentPort](../../src/main/java/org/fireflyframework/ecm/port/document/DocumentPort.java) | Create, read, update, move, copy, and delete document records. |
| document | [DocumentSearchPort](../../src/main/java/org/fireflyframework/ecm/port/document/DocumentSearchPort.java) | Search document records through the chosen implementation. |
| document | [DocumentVersionPort](../../src/main/java/org/fireflyframework/ecm/port/document/DocumentVersionPort.java) | Manage the document version contract. |
| esignature | [SignatureEnvelopePort](../../src/main/java/org/fireflyframework/ecm/port/esignature/SignatureEnvelopePort.java) | Create, send, and inspect signature envelopes. |
| esignature | [SignatureProofPort](../../src/main/java/org/fireflyframework/ecm/port/esignature/SignatureProofPort.java) | Retrieve signature proof and evidence. |
| esignature | [SignatureRequestPort](../../src/main/java/org/fireflyframework/ecm/port/esignature/SignatureRequestPort.java) | Manage individual signature requests. |
| esignature | [SignatureValidationPort](../../src/main/java/org/fireflyframework/ecm/port/esignature/SignatureValidationPort.java) | Validate signature information through the adapter. |
| folder | [FolderHierarchyPort](../../src/main/java/org/fireflyframework/ecm/port/folder/FolderHierarchyPort.java) | Work with folder ancestry and hierarchy. |
| folder | [FolderPort](../../src/main/java/org/fireflyframework/ecm/port/folder/FolderPort.java) | Manage folder records and contents. |
| idp | [DataExtractionPort](../../src/main/java/org/fireflyframework/ecm/port/idp/DataExtractionPort.java) | Extract structured data through the processing implementation. |
| idp | [DocumentClassificationPort](../../src/main/java/org/fireflyframework/ecm/port/idp/DocumentClassificationPort.java) | Classify documents with an application-supplied implementation. |
| idp | [DocumentExtractionPort](../../src/main/java/org/fireflyframework/ecm/port/idp/DocumentExtractionPort.java) | Extract document content with an application-supplied implementation. |
| idp | [DocumentValidationPort](../../src/main/java/org/fireflyframework/ecm/port/idp/DocumentValidationPort.java) | Validate document processing results through the port. |
| security | [DocumentSecurityPort](../../src/main/java/org/fireflyframework/ecm/port/security/DocumentSecurityPort.java) | Apply the document security contract. |
| security | [PermissionPort](../../src/main/java/org/fireflyframework/ecm/port/security/PermissionPort.java) | Manage document/folder permission records. |

OCR/classification interfaces are extension points. The repository does not ship AWS Textract, Azure Form Recognizer, or Google Document AI implementations.

## Select an available port

[EcmPortProvider](../../src/main/java/org/fireflyframework/ecm/service/EcmPortProvider.java) exposes typed getters returning `Optional`, including `getDocumentPort()` and `getDocumentContentPort()`. There is no generic `getPort(Class)` method. Handle an unavailable capability explicitly.

This API class needs an `EcmPortProvider` configured with the selected adapter. It does not authorize the applicant or expose an HTTP route. Lombok setup is described in [Constructor injection](../../../../docs/site/maven.md#constructor-injection).

```java
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.fireflyframework.ecm.service.EcmPortProvider;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class ApplicationDocumentStore {
    private final EcmPortProvider ports;

    public Mono<byte[]> content(UUID documentId) {
        return Mono.defer(() -> ports.getDocumentContentPort()
                .map(port -> port.getContent(documentId))
                .orElseGet(() -> Mono.error(
                        new IllegalStateException("No document content port configured"))));
    }
}
```

## Models and configuration

- [Document](../../src/main/java/org/fireflyframework/ecm/domain/model/document/Document.java).
- [DocumentVersion](../../src/main/java/org/fireflyframework/ecm/domain/model/document/DocumentVersion.java).
- [Folder](../../src/main/java/org/fireflyframework/ecm/domain/model/folder/Folder.java).
- [Permission](../../src/main/java/org/fireflyframework/ecm/domain/model/security/Permission.java).
- [SignatureEnvelope](../../src/main/java/org/fireflyframework/ecm/domain/model/esignature/SignatureEnvelope.java).
- [SignatureRequest](../../src/main/java/org/fireflyframework/ecm/domain/model/esignature/SignatureRequest.java).
- [EcmProperties](../../src/main/java/org/fireflyframework/ecm/config/EcmProperties.java).

## Provider choices

- [Amazon S3](../../../../docs/site/modules/ecm-storage-aws.md).
- [Azure Blob Storage](../../../../docs/site/modules/ecm-storage-azure.md).
- [Adobe Sign](../../../../docs/site/modules/ecm-esignature-adobe-sign.md).
- [DocuSign](../../../../docs/site/modules/ecm-esignature-docusign.md).
- [Logalty](../../../../docs/site/modules/ecm-esignature-logalty.md).
