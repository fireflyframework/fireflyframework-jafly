# Firefly Framework - ECM Storage Azure Blob

[![CI](https://github.com/fireflyframework/fireflyframework-ecm-storage-azure/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-ecm-storage-azure/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> Microsoft Azure Blob Storage adapter for Firefly ECM — stores and streams document content reactively, with resilient block-blob uploads, range reads and checksums.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

`fireflyframework-ecm-storage-azure` is a pluggable storage adapter for the Firefly Enterprise Content Management (ECM) abstraction. It implements the ECM core `DocumentContentPort` (from `fireflyframework-ecm`) on top of **Microsoft Azure Blob Storage**, so applications can persist, stream and manage document binaries in Azure without coupling to the Azure SDK directly.

The ECM core defines storage as a hexagonal **port**; each backend ships as a separate adapter module that is selected at runtime by the `firefly.ecm.adapter-type` property. This module registers itself when `firefly.ecm.adapter-type=azure-blob`, providing the `AzureBlobDocumentContentAdapter` plus the Azure SDK clients (`BlobServiceClient`, `BlobContainerClient`) and a Resilience4j circuit breaker and retry wired around storage calls. Sibling adapters cover other backends — for example `fireflyframework-ecm-storage-aws` (`adapter-type=s3`) for Amazon S3 — so you swap object stores by switching one dependency and one property, with no application code changes.

Activation is zero-boilerplate: drop the dependency on the classpath alongside `fireflyframework-ecm`, set the selector property, and Spring Boot auto-configuration (`AzureBlobAutoConfiguration`) builds the clients and registers the adapter as a `DocumentContentPort` bean. The adapter is fully reactive (Project Reactor `Mono`/`Flux`) and integrates with WebFlux `DataBuffer` streaming for efficient handling of large files.

## Features

- **`DocumentContentPort` implementation** — `AzureBlobDocumentContentAdapter` covers the full ECM content contract: store, retrieve, stream, delete, existence checks and content size, addressable by `documentId` or raw storage path.
- **Reactive, non-blocking API** — all operations return `Mono`/`Flux`; streaming downloads and uploads use Spring `DataBuffer` for backpressure-aware transfer of large documents.
- **Large-file block uploads** — content above a configurable `block-upload-threshold` (default 256 MB) is uploaded via Azure `BlockBlobClient`; smaller payloads use single-shot uploads.
- **Range reads** — `getContentRange(documentId, offset, length)` fetches partial content for resumable downloads and previews.
- **Integrity helpers** — `calculateChecksum` and `verifyChecksum` compute and compare digests (e.g. `SHA-256`, `MD5`) over stored content.
- **Flexible authentication** — connection string, account name + account key, SAS token, or Managed Identity via `DefaultAzureCredentialBuilder` (Azure AD / workload identity).
- **Built-in resilience** — dedicated Resilience4j `CircuitBreaker` (`azureBlobCircuitBreaker`) and `Retry` (`azureBlobRetry`, attempts driven by `max-retries`) protect Azure calls.
- **Auto-configuration & auto-provisioning** — `AzureBlobAutoConfiguration` builds the service and container clients and creates the target container if it does not exist; everything is `@ConditionalOnMissingBean` so each bean can be overridden.
- **Self-describing adapter** — annotated with `@EcmAdapter` (features `CONTENT_STORAGE`, `STREAMING`, `CLOUD_STORAGE`) for discovery and capability reporting by the ECM core.
- **Content addressing** — blobs are named `{path-prefix}{documentId}.content`, keeping content layout predictable and namespaced.

## Requirements

- Java 21+ (Java 25 recommended)
- Spring Boot 3.x
- Maven 3.9+
- `fireflyframework-ecm` (ECM core) on the classpath
- A Microsoft Azure Storage account with a Blob container, plus credentials (connection string, account key, SAS token, or a Managed Identity)

## Installation

Add the adapter alongside the ECM core. The version is managed by the Firefly BOM / parent, so you normally omit `<version>`.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-ecm-storage-azure</artifactId>
    <!-- version managed by the Firefly BOM / fireflyframework-parent -->
</dependency>
```

Make sure the ECM core is also present (it is a transitive dependency of this module, but is shown here for clarity):

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-ecm</artifactId>
</dependency>
```

## Quick Start

**1. Add the dependency** (see [Installation](#installation)) and **select this adapter** via the `firefly.ecm.adapter-type` property:

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

**2. Inject the ECM `DocumentContentPort`** — the Azure adapter is wired in automatically; your code depends only on the core port:

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

That is all — switching to a different backend (e.g. Amazon S3) is a matter of swapping the dependency and setting `firefly.ecm.adapter-type` accordingly, with no change to `DocumentService`.

## Configuration

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
| `connection-string` / `account-key` / `sas-token` / `managed-identity` | – / – / – / `false` | Authentication options, tried in this order; at least one must be supplied. |
| `endpoint` | `https://<account>.blob.core.windows.net` | Override for sovereign/custom endpoints. |
| `path-prefix` | `documents/` | Prefix for blob names; final key is `{path-prefix}{documentId}.content`. |
| `block-size` | `4194304` (4 MB) | Buffer/block size used for streaming reads and writes. Min 1024. |
| `block-upload-threshold` | `268435456` (256 MB) | Content larger than this is uploaded via block blobs. Min 1024. |
| `max-retries` | `3` | Retry attempts for the `azureBlobRetry` Resilience4j instance. Range 0–10. |
| `timeout-seconds` | `30` | Operation timeout hint. Range 1–300. |
| `access-tier` | `Hot` | Intended blob access tier. |
| `enable-versioning` / `enable-soft-delete` / `soft-delete-retention-days` | `true` / `true` / `7` | Data-protection intent; soft-delete retention range 1–365 days. |
| `enable-encryption` / `encryption-key-url` | `true` / – | Encryption-at-rest intent and optional customer-managed key URL. |

A ready-to-copy profile is bundled at `src/main/resources/application-azure-blob.yml`.

## Documentation

- Firefly Framework documentation hub and module catalog: [github.com/fireflyframework](https://github.com/fireflyframework)
- ECM core abstraction and `DocumentContentPort`: [`fireflyframework-ecm`](https://github.com/fireflyframework/fireflyframework-ecm)
- Sample profile: [`src/main/resources/application-azure-blob.yml`](src/main/resources/application-azure-blob.yml)

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
