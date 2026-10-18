# Firefly Framework - Enterprise Content Management (ECM)

[![CI](https://github.com/fireflyframework/fireflyframework-ecm/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-ecm/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> Reactive Enterprise Content Management abstraction for Spring Boot — provider-agnostic ports for document storage, e-signature, intelligent document processing, folders, security and audit, with pluggable adapters selected by configuration.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Architecture](#architecture)
- [Observability](#observability)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

Firefly Framework ECM provides Enterprise Content Management capabilities to Spring Boot applications through a **hexagonal (port/adapter) architecture**. Your application code depends only on a stable set of reactive **port interfaces** — it never talks to a vendor SDK directly. Concrete behaviour is supplied by **adapters** that are discovered on the classpath and selected by configuration, so you can switch from a cloud provider to an on-premise system (or to a no-op test double) without touching business logic.

The library is fully **reactive** (Spring WebFlux + Project Reactor): every port returns `Mono`/`Flux`, including streaming binary content via `DataBuffer`. It groups its contracts into six functional families — **document management**, **e-signature**, **intelligent document processing (IDP)**, **folder management**, **document security**, and **audit** — each guarded by an independent feature flag so you only enable (and pay the startup cost of) what you use.

Adapter discovery and routing are handled by the built-in **adapter registry/selector** (`AdapterRegistry`, `AdapterSelector`) together with the `@EcmAdapter` annotation SPI. When a feature is enabled but no real adapter is present, the auto-configuration installs a **no-op fallback** (`NoOpAdapterFactory`) so the application still starts — and logs a loud warning for security-sensitive ports (permissions and document security default to *deny*). The module ships two ready-to-use built-in adapters for development and testing: an in-memory `LocalDocumentSearchAdapter` and `LocalPermissionAdapter`.

This is the **ECM core** library. Provider integrations — AWS S3 and Azure Blob (storage), DocuSign and Adobe Sign (e-signature), and IDP backends such as AWS Textract, Azure Form Recognizer and Google Document AI — plug in as adapters and are selected via `firefly.ecm.adapter-type` (storage/core) and `firefly.ecm.esignature.provider` (e-signature). The client SDK versions for those providers are managed in this module's `dependencyManagement` so adapter modules stay version-aligned. It sits alongside sibling Firefly capability cores such as `fireflyframework-idp`, `fireflyframework-notifications` and the cache/EDA cores, and builds on `fireflyframework-kernel` (foundational types) and `fireflyframework-observability` (metrics, tracing, health).

## Features

- **Hexagonal port/adapter design** — application code depends on reactive ports; vendors plug in behind them.
- **18 reactive port interfaces across 6 families:**
  - *Document* — `DocumentPort` (CRUD/metadata), `DocumentContentPort` (binary store/stream), `DocumentVersionPort` (versioning), `DocumentSearchPort` (search).
  - *E-signature* — `SignatureEnvelopePort`, `SignatureRequestPort`, `SignatureValidationPort`, `SignatureProofPort`.
  - *IDP* — `DocumentExtractionPort` (OCR/text), `DocumentClassificationPort`, `DocumentValidationPort`, `DataExtractionPort` (structured/forms/tables).
  - *Folder* — `FolderPort`, `FolderHierarchyPort`.
  - *Security* — `PermissionPort`, `DocumentSecurityPort`.
  - *Audit* — `AuditPort`.
- **Annotation-driven adapter SPI** — declare a provider with `@EcmAdapter(type=…, supportedFeatures={…})`; the `AdapterRegistry` auto-discovers it and `AdapterSelector` routes by type/interface with fallback.
- **Capability model** — the `AdapterFeature` enum (40+ values: `DOCUMENT_CRUD`, `CONTENT_STORAGE`, `VERSIONING`, `ESIGNATURE_ENVELOPES`, `OCR`, `TABLE_EXTRACTION`, `COMPLIANCE`, `LEGAL_HOLD`, …) plus `AdapterProfile` (`BASIC`/`STANDARD`/`ADVANCED`) describe what each adapter supports.
- **Safe no-op fallbacks** — `NoOpAdapterFactory` keeps the app booting when an enabled feature has no real adapter; permission and security ports *deny by default* and warn.
- **Built-in local adapters** — in-memory `LocalDocumentSearchAdapter` and `LocalPermissionAdapter` for dev/test (opt-in via properties).
- **Fine-grained feature flags** — independently toggle document management, content storage, versioning, folders, hierarchy, permissions, security, search, auditing, e-signature and IDP.
- **Streaming-first content** — upload/download via `byte[]` or backpressure-aware `Flux<DataBuffer>` for large files.
- **Resilience built in** — Resilience4j circuit breaker, retry and time limiter wired for adapter calls (`ResilienceConfiguration`), plus connection timeout/pool/retry settings.
- **Validation guardrails** — global max file size, allowed/blocked extension lists, and checksum algorithm enforced at the configuration level.
- **First-class observability** — `EcmMetrics` publishes Micrometer counters, timers and distribution summaries (see [Observability](#observability)).
- **Zero-config auto-configuration** — `EcmAutoConfiguration` registers everything; enabled by default via `firefly.ecm.enabled`.

## Requirements

- Java 21+ (Java 25 recommended)
- Spring Boot 3.x
- Maven 3.9+
- A provider adapter on the classpath for production use (e.g. AWS S3 / Azure Blob for storage, DocuSign / Adobe Sign for e-signature). Without one, enabled features fall back to no-op behaviour.

## Installation

Add the dependency. The version is managed by the Firefly BOM/parent, so you normally omit `<version>`:

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-ecm</artifactId>
    <!-- version managed by the Firefly BOM / fireflyframework-parent -->
</dependency>
```

If your project does not inherit the Firefly parent, import the BOM in `dependencyManagement` (or pin the version explicitly):

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.fireflyframework</groupId>
            <artifactId>fireflyframework-bom</artifactId>
            <version>${firefly.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Then add the matching provider adapter module(s) for your storage / e-signature backend.

## Quick Start

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

2. Inject the reactive ports you need — your code is provider-agnostic:

```java
import org.fireflyframework.ecm.port.document.DocumentContentPort;
import org.fireflyframework.ecm.port.esignature.SignatureEnvelopePort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class DocumentService {

    private final DocumentContentPort content;
    private final SignatureEnvelopePort envelopes;

    public DocumentService(DocumentContentPort content, SignatureEnvelopePort envelopes) {
        this.content = content;
        this.envelopes = envelopes;
    }

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

All properties are bound under the `firefly.ecm` prefix (`EcmProperties`). The block below shows the real keys and their defaults:

```yaml
firefly:
  ecm:
    enabled: true                 # master switch for the ECM auto-configuration
    adapter-type:                 # storage/core adapter to select (e.g. "s3"); none by default
    properties:                   # free-form, adapter-specific settings (Map<String,Object>)
      # bucket-name: my-documents
      # region: us-east-1

    connection:
      connect-timeout: PT30S      # connection establishment timeout
      read-timeout: PT5M          # read/transfer timeout
      max-connections: 100        # max pooled connections
      retry-attempts: 3           # retries for transient failures

    features:                     # independent capability toggles
      document-management: true
      content-storage: true
      versioning: true
      folder-management: true
      folder-hierarchy: true
      permissions: true
      security: true
      search: true
      auditing: true
      esignature: false           # opt-in
      virus-scanning: false       # opt-in
      content-extraction: false   # opt-in
      idp: false                  # opt-in (Intelligent Document Processing)

    defaults:
      max-file-size-mb: 100
      allowed-extensions: [pdf, doc, docx, txt, jpg, png]
      blocked-extensions: [exe, bat, cmd, scr]
      checksum-algorithm: SHA-256
      default-folder: "/"

    performance:
      batch-size: 100
      cache-enabled: true
      cache-expiration: PT30M
      compression-enabled: true

    esignature:
      provider:                   # e-signature adapter (e.g. "docusign", "adobe-sign")

    # The two built-in local adapters are opt-in and live outside the blocks above:
    search:
      enabled: false              # registers LocalDocumentSearchAdapter when true
    permissions:
      enabled: false              # registers LocalPermissionAdapter when true
```

Key properties:

| Property | Default | Purpose |
| --- | --- | --- |
| `firefly.ecm.enabled` | `true` | Master switch. When `false`, no ECM beans are created. |
| `firefly.ecm.adapter-type` | *(none)* | Selects the storage/core adapter by its `@EcmAdapter(type=…)`. |
| `firefly.ecm.properties` | *(empty)* | Adapter-specific key/value settings (bucket, region, credentials, …). |
| `firefly.ecm.esignature.provider` | *(none)* | Selects the e-signature adapter independently of `adapter-type`. |
| `firefly.ecm.features.*` | see above | Enable/disable each capability family. `esignature` and `idp` are **off** by default; most others **on**. |
| `firefly.ecm.defaults.*` | see above | Upload validation: size limit, allowed/blocked extensions, checksum algorithm, default folder. |
| `firefly.ecm.connection.*` | see above | Timeouts, pool size and retry attempts for adapter connections. |
| `firefly.ecm.performance.*` | see above | Batch size, caching and compression toggles. |
| `firefly.ecm.search.enabled` | `false` | Registers the in-memory `LocalDocumentSearchAdapter`. |
| `firefly.ecm.permissions.enabled` | `false` | Registers the in-memory `LocalPermissionAdapter`. |

> Security note: if `permissions` or `security` is enabled but no real adapter is found, the no-op fallback **denies by default** and logs a warning. Always configure a real adapter for production.

## Architecture

```
your application
        │  depends only on ports (reactive interfaces)
        ▼
┌─────────────────────────────────────────────────────────────┐
│ ECM core (this module)                                       │
│                                                              │
│  Ports:  document · esignature · idp · folder · security ·   │
│          audit   (Mono / Flux)                               │
│                                                              │
│  Routing:  @EcmAdapter → AdapterRegistry → AdapterSelector   │
│            (by adapter-type / esignature.provider)           │
│                                                              │
│  Fallback: NoOpAdapterFactory (deny-by-default for security) │
│  Built-in: LocalDocumentSearchAdapter, LocalPermissionAdapter│
└─────────────────────────────────────────────────────────────┘
        ▲                         ▲
        │ classpath adapters      │
   storage adapters          e-signature / IDP adapters
   (S3, Azure Blob, …)       (DocuSign, Adobe Sign, Textract, …)
```

`EcmAutoConfiguration` registers the registry, selector, no-op factory and `EcmPortProvider`, then exposes one bean per port — each gated by its `firefly.ecm.features.*` flag and resolved through the selector with a no-op fallback.

## Observability

`EcmMetrics` (auto-configured via `EcmObservabilityAutoConfiguration`, built on `fireflyframework-observability`) publishes Micrometer meters:

- `firefly.ecm.documents.operations` — counter, tagged by `operation`
- `firefly.ecm.operation.duration` — timer, tagged by `operation`, `provider`
- `firefly.ecm.bytes.transferred` — distribution summary of payload bytes (by `direction`, `provider`)
- `firefly.ecm.errors` — failed operations, tagged by `operation`, `provider`
- `firefly.ecm.signatures.completed` — successful e-signature workflows, tagged by `provider`

## Documentation

- Framework module catalog and docs hub: [fireflyframework on GitHub](https://github.com/fireflyframework)
- In-repo docs ([`docs/`](docs/)):
  - [Architecture](docs/architecture.md)
  - [Configuration](docs/configuration.md) · [Configuration Reference](docs/configuration-reference.md)
  - [Testing](docs/testing.md)
  - Adapter integration guides ([`docs/adapters/`](docs/adapters)) — S3, DocuSign, Adobe Sign
  - IDP integration guides ([`docs/idp/`](docs/idp)) — AWS Textract, Azure Form Recognizer, Google Document AI
  - Usage examples ([`docs/examples/`](docs/examples)) — basic usage, document versioning, folder management

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
