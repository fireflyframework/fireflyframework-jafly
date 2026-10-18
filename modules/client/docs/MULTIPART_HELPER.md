# File Upload Helper Guide

Complete guide for using the **Enterprise-Grade** Multipart Upload helper in the Firefly Common Client Library.

---

## Table of Contents

1. [Overview](#overview)
2. [When to Use File Upload Helper](#when-to-use-file-upload-helper)
3. [Quick Start](#quick-start)
4. [Advanced Configuration](#advanced-configuration)
5. [Single File Upload](#single-file-upload)
6. [Multiple File Upload](#multiple-file-upload)
7. [Progress Tracking](#progress-tracking)
8. [Chunked Uploads (Resumable)](#chunked-uploads-resumable)
9. [Parallel Uploads](#parallel-uploads)
10. [File Compression](#file-compression)
11. [File Validation](#file-validation)
12. [Upload Cancellation](#upload-cancellation)
13. [Automatic Retry](#automatic-retry)
14. [Best Practices](#best-practices)
15. [Complete Examples](#complete-examples)

---

## Overview

The `MultipartUploadHelper` provides an **enterprise-grade** API for uploading files using multipart/form-data encoding with Spring WebClient.

**Key Features**:
- ✅ Single and multiple file uploads
- ✅ Upload with additional metadata
- ✅ Stream-based uploads
- ✅ Custom headers per request
- ✅ Configurable timeouts
- ✅ Reactive programming with `Mono<T>` and `Flux<T>`
- ✅ Type-safe response handling
- ✅ **NEW: Real-time progress tracking**
- ✅ **NEW: Chunked uploads for large files (resumable)**
- ✅ **NEW: Parallel uploads for multiple files**
- ✅ **NEW: File validation (size, MIME type, extension)**
- ✅ **NEW: Automatic compression (GZIP)**
- ✅ **NEW: Upload cancellation support**
- ✅ **NEW: Automatic retry with exponential backoff**
- ✅ **NEW: Advanced configuration with builder pattern**

---

## When to Use File Upload Helper

### ✅ Use File Upload Helper When:

- You need to upload files to REST APIs
- You're implementing document management features
- You need to upload images, PDFs, or other binary files
- You want to send files with additional form data
- You're building file upload functionality in microservices
- **You need to upload large files (>100MB) with chunking**
- **You need resumable uploads**
- **You need progress tracking for user feedback**
- **You need to validate files before upload**

### ❌ Consider Alternatives When:

- You're uploading to cloud storage - use provider SDKs (S3, Azure Blob, etc.)
- You need specialized protocols (FTP, SFTP)

---

## Quick Start

### ✨ Production-Ready Setup (Recommended)

```java
import org.fireflyframework.client.multipart.MultipartUploadHelper;
import org.fireflyframework.client.multipart.MultipartUploadHelper.UploadConfig;
import org.fireflyframework.client.multipart.MultipartUploadHelper.UploadResponse;
import reactor.core.publisher.Mono;
import java.io.File;
import java.time.Duration;

// Production-ready configuration
UploadConfig config = UploadConfig.builder()
    .timeout(Duration.ofMinutes(10))
    .enableRetry(true)
    .maxRetries(3)
    .retryBackoff(Duration.ofSeconds(2))
    .enableCompression(false)  // Enable if bandwidth is limited
    .chunkSize(10 * 1024 * 1024)  // 10MB chunks
    .maxParallelUploads(3)
    .maxFileSize(500 * 1024 * 1024)  // 500MB max
    .allowedExtension("pdf")
    .allowedExtension("jpg")
    .allowedExtension("png")
    .allowedExtension("docx")
    .enableProgressTracking(true)
    .defaultHeader("X-Upload-Client", "MyApp/1.0")
    .build();

MultipartUploadHelper uploader = new MultipartUploadHelper(
    "https://upload.example.com",
    config
);

// Upload with progress tracking
File file = new File("/path/to/document.pdf");

Mono<UploadResponse> response = uploader.uploadFileWithProgress(
    "/api/upload",
    file,
    "document",
    progress -> {
        System.out.printf("Uploading %s: %.2f%% (%.2f KB/s)%n",
            progress.getFilename(),
            progress.getPercentage(),
            progress.getSpeedBytesPerSecond() / 1024);
    },
    UploadResponse.class
);

response.subscribe(
    result -> System.out.println("Upload complete: " + result.getFileId()),
    error -> System.err.println("Upload failed: " + error.getMessage())
);
```

### Basic File Upload

```java
// Simple configuration
MultipartUploadHelper uploader = new MultipartUploadHelper("http://localhost:8080");

// Upload a file
File file = new File("/path/to/document.pdf");

Mono<UploadResponse> response = uploader.uploadFile(
    "/api/upload",           // Upload endpoint
    file,                    // File to upload
    "document",              // Form field name
    UploadResponse.class     // Response type
);

response.subscribe(result -> {
    System.out.println("File uploaded: " + result.getFileId());
    System.out.println("URL: " + result.getUrl());
});
```

---

## Advanced Configuration

### UploadConfig Builder

The `UploadConfig` class provides comprehensive configuration options:

```java
UploadConfig config = UploadConfig.builder()
    // Timeout configuration
    .timeout(Duration.ofMinutes(10))

    // Retry configuration
    .enableRetry(true)
    .maxRetries(3)
    .retryBackoff(Duration.ofSeconds(2))

    // Compression
    .enableCompression(true)

    // Chunking for large files
    .chunkSize(10 * 1024 * 1024)  // 10MB chunks

    // Parallel uploads
    .maxParallelUploads(5)

    // File validation
    .maxFileSize(500 * 1024 * 1024)  // 500MB
    .allowedMimeType("application/pdf")
    .allowedMimeType("image/jpeg")
    .allowedMimeType("image/png")
    .allowedExtension("pdf")
    .allowedExtension("jpg")
    .allowedExtension("png")

    // Headers
    .defaultHeader("X-Upload-Client", "MyApp/1.0")
    .defaultHeader("X-API-Version", "2.0")

    // Progress tracking
    .enableProgressTracking(true)

    .build();
```

### Configuration Options Reference

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `timeout` | `Duration` | `5 minutes` | Request timeout for uploads |
| `enableRetry` | `boolean` | `false` | Enable automatic retry on failures |
| `maxRetries` | `int` | `3` | Maximum number of retry attempts |
| `retryBackoff` | `Duration` | `1 second` | Initial backoff duration (exponential) |
| `enableCompression` | `boolean` | `false` | Enable GZIP compression before upload |
| `chunkSize` | `long` | `5MB` | Chunk size for large file uploads |
| `maxParallelUploads` | `int` | `3` | Maximum parallel uploads |
| `maxFileSize` | `long` | `100MB` | Maximum allowed file size |
| `allowedMimeTypes` | `Set<String>` | Empty | Allowed MIME types (empty = all) |
| `allowedExtensions` | `Set<String>` | Empty | Allowed file extensions (empty = all) |
| `defaultHeaders` | `Map<String,String>` | Empty | Default headers for all requests |
| `enableProgressTracking` | `boolean` | `false` | Enable progress tracking |

---

## Single File Upload

### Upload from File

```java
File file = new File("/path/to/document.pdf");

Mono<UploadResponse> response = uploader.uploadFile(
    "/api/upload",
    file,
    "document",
    UploadResponse.class
);
```

### Upload from Path

```java
import java.nio.file.Path;
import java.nio.file.Paths;

Path filePath = Paths.get("/path/to/document.pdf");

Mono<UploadResponse> response = uploader.uploadFile(
    "/api/upload",
    filePath,
    "document",
    UploadResponse.class
);
```

### Upload from Resource

```java
import org.springframework.core.io.Resource;
import org.springframework.core.io.ClassPathResource;

Resource resource = new ClassPathResource("templates/invoice.pdf");

Mono<UploadResponse> response = uploader.uploadFile(
    "/api/upload",
    resource,
    "document",
    "invoice.pdf",  // Filename
    UploadResponse.class
);
```

### Upload with Custom Headers

```java
Map<String, String> headers = Map.of(
    "Authorization", "Bearer " + token,
    "X-Request-ID", UUID.randomUUID().toString()
);

Mono<UploadResponse> response = uploader.uploadFileWithHeaders(
    "/api/upload",
    file,
    "document",
    headers,
    UploadResponse.class
);
```

---

## Multiple File Upload

### Upload Multiple Files

```java
Map<String, File> files = Map.of(
    "invoice", new File("/path/to/invoice.pdf"),
    "receipt", new File("/path/to/receipt.jpg"),
    "contract", new File("/path/to/contract.pdf")
);

Mono<UploadResponse> response = uploader.uploadFiles(
    "/api/upload/batch",
    files,
    UploadResponse.class
);
```

### Upload with Metadata


### Progress Tracking with UI Update

```java
// Example with JavaFX ProgressBar
@FXML
private ProgressBar uploadProgressBar;

@FXML
private Label uploadStatusLabel;

public void uploadFile(File file) {
    uploader.uploadFileWithProgress(
        "/api/upload",
        file,
        "file",
        progress -> {
            // Update UI on JavaFX thread
            Platform.runLater(() -> {
                uploadProgressBar.setProgress(progress.getPercentage() / 100.0);
                uploadStatusLabel.setText(String.format(
                    "Uploading: %.2f%% (%.2f KB/s)",
                    progress.getPercentage(),
                    progress.getSpeedBytesPerSecond() / 1024
                ));
            });
        },
        UploadResponse.class
    ).subscribe(
        result -> Platform.runLater(() ->
            uploadStatusLabel.setText("Upload complete!")
        ),
        error -> Platform.runLater(() ->
            uploadStatusLabel.setText("Upload failed: " + error.getMessage())
        )
    );
}
```

---

## Chunked Uploads (Resumable)

For large files (>100MB), use chunked uploads to enable resumability and better progress tracking:

### Upload Large File in Chunks

```java
File largeFile = new File("/path/to/large-video.mp4");  // 2GB file

Mono<UploadResponse> response = uploader.uploadFileChunked(
    "/api/upload",
    largeFile,
    "video",
    progress -> {
        System.out.printf(
            "Chunk upload progress: %.2f%% | Speed: %.2f MB/s%n",
            progress.getPercentage(),
            progress.getSpeedBytesPerSecond() / (1024 * 1024)
        );
    },
    UploadResponse.class
);

response.subscribe(
    result -> System.out.println("Large file uploaded successfully"),
    error -> System.err.println("Upload failed: " + error.getMessage())
);
```

### How Chunked Upload Works

1. **File is split into chunks** (default 5MB, configurable)
2. **Each chunk is uploaded separately** with metadata (chunk index, session ID)
3. **Progress is tracked per chunk**
4. **Server reassembles chunks** using session ID
5. **Upload can be resumed** if interrupted

### Server-Side Requirements

Your server must support chunked uploads:

```java
// Spring Boot example
@PostMapping("/api/upload/chunk")
public ResponseEntity<String> uploadChunk(
    @RequestParam("file") MultipartFile file,
    @RequestParam("chunkIndex") int chunkIndex,
    @RequestParam("sessionId") String sessionId,
    @RequestParam("totalChunks") int totalChunks
) {
    // Save chunk to temporary storage
    chunkService.saveChunk(sessionId, chunkIndex, file);

    // If all chunks received, reassemble file
    if (chunkService.allChunksReceived(sessionId, totalChunks)) {
        File completeFile = chunkService.reassembleFile(sessionId);
        // Process complete file
    }

    return ResponseEntity.ok("Chunk received");
}
```

---

## Parallel Uploads

Upload multiple files in parallel for better performance:

### Upload Files in Parallel

```java
Map<String, File> files = Map.of(
    "doc1", new File("/path/to/doc1.pdf"),
    "doc2", new File("/path/to/doc2.pdf"),
    "doc3", new File("/path/to/doc3.pdf"),
    "doc4", new File("/path/to/doc4.pdf"),
    "doc5", new File("/path/to/doc5.pdf")
);

Flux<UploadResponse> responses = uploader.uploadFilesParallel(
    "/api/upload",
    files,
    progress -> {
        System.out.printf("Uploading %s: %.2f%%%n",
            progress.getFilename(),
            progress.getPercentage());
    },
    UploadResponse.class
);

responses.subscribe(
    result -> System.out.println("File uploaded: " + result.getFileId()),
    error -> System.err.println("Upload failed: " + error.getMessage()),
    () -> System.out.println("All uploads complete!")
);
```

### Control Parallelism

```java
UploadConfig config = UploadConfig.builder()
    .maxParallelUploads(5)  // Upload max 5 files simultaneously
    .build();
```

---

## File Compression

Compress files before upload to save bandwidth:

### Upload Compressed File

```java
File file = new File("/path/to/large-document.pdf");

Mono<UploadResponse> response = uploader.uploadFileCompressed(
    "/api/upload",
    file,
    "document",
    UploadResponse.class
);

response.subscribe(
    result -> System.out.println("Compressed file uploaded: " + result.getFileId()),
    error -> System.err.println("Upload failed: " + error.getMessage())
);
```

### Enable Compression Globally

```java
UploadConfig config = UploadConfig.builder()
    .enableCompression(true)  // All uploads will be compressed
    .build();
```

### How Compression Works

1. **File is compressed using GZIP** before upload
2. **Compressed file is uploaded** with metadata
3. **Server receives compressed file** and can decompress it
4. **Temporary compressed file is deleted** after upload

**Note**: Server must support decompression or store compressed files.

---

## File Validation

Validate files before upload to prevent errors:

### Validate File Manually

```java
try {
    uploader.validateFile(file);
    // File is valid, proceed with upload
} catch (UploadValidationException e) {
    System.err.println("Validation failed: " + e.getMessage());
}
```

### Configure Validation Rules

```java
UploadConfig config = UploadConfig.builder()
    // Size validation
    .maxFileSize(50 * 1024 * 1024)  // 50MB max

    // MIME type validation
    .allowedMimeType("application/pdf")
    .allowedMimeType("image/jpeg")
    .allowedMimeType("image/png")

    // Extension validation
    .allowedExtension("pdf")
    .allowedExtension("jpg")
    .allowedExtension("png")

    .build();
```

### Validation Errors

```java
// File too large
UploadValidationException: File size 104857600 bytes exceeds maximum allowed size 52428800 bytes

// Invalid extension
UploadValidationException: File extension 'exe' is not allowed. Allowed: [pdf, jpg, png]

// Invalid MIME type
UploadValidationException: MIME type 'application/x-msdownload' is not allowed. Allowed: [application/pdf, image/jpeg, image/png]

// File doesn't exist
UploadValidationException: File does not exist

// Not a file
UploadValidationException: Path is not a file

// Not readable
UploadValidationException: File is not readable
```

---

## Upload Cancellation

Cancel active uploads:

### Cancel Upload by Session ID

```java
String sessionId = "upload-session-123";

boolean cancelled = uploader.cancelUpload(sessionId);

if (cancelled) {
    System.out.println("Upload cancelled successfully");
} else {
    System.out.println("Upload session not found");
}
```

### Get Active Uploads

```java
Map<String, UploadProgress> activeUploads = uploader.getActiveUploads();

activeUploads.forEach((sessionId, progress) -> {
    System.out.printf("Session %s: %s - %.2f%%%n",
        sessionId,
        progress.getFilename(),
        progress.getPercentage());
});
```

### Get Upload Progress

```java
String sessionId = "upload-session-123";

UploadProgress progress = uploader.getUploadProgress(sessionId);

if (progress != null) {
    System.out.printf("Progress: %.2f%% | Speed: %.2f KB/s%n",
        progress.getPercentage(),
        progress.getSpeedBytesPerSecond() / 1024);
}
```

---

## Automatic Retry

Configure automatic retry for failed uploads:

### Enable Retry

```java
UploadConfig config = UploadConfig.builder()
    .enableRetry(true)
    .maxRetries(3)
    .retryBackoff(Duration.ofSeconds(2))
    .build();
```

### Retryable Errors

The helper automatically retries on:
- **Network errors** (IOException)
- **Timeouts**
- **Connection refused**

**Non-retryable errors** (fail immediately):
- **Validation errors** (file too large, invalid extension)
- **4xx client errors** (except timeouts)

### Retry Behavior

**Exponential Backoff**:
- Attempt 1: Immediate
- Attempt 2: Wait 2 seconds
- Attempt 3: Wait 4 seconds
- Attempt 4: Wait 8 seconds

---

## Best Practices

### Practice 1: Use Production-Ready Configuration

```java
// ✅ GOOD: Production-ready configuration
UploadConfig config = UploadConfig.builder()
    .timeout(Duration.ofMinutes(10))
    .enableRetry(true)
    .maxRetries(3)
    .enableProgressTracking(true)
    .maxFileSize(500 * 1024 * 1024)
    .allowedExtension("pdf")
    .allowedExtension("jpg")
    .build();

// ❌ BAD: Using defaults without validation
MultipartUploadHelper uploader = new MultipartUploadHelper("http://localhost:8080");
```

### Practice 2: Always Validate Files

```java
// ✅ GOOD: Validate before upload
try {
    uploader.validateFile(file);
    uploader.uploadFile(...).subscribe(...);
} catch (UploadValidationException e) {
    // Handle validation error
}

// ❌ BAD: Upload without validation
uploader.uploadFile(...).subscribe(...);  // May fail with unclear error
```

### Practice 3: Use Chunked Uploads for Large Files

```java
// ✅ GOOD: Use chunked upload for large files
if (file.length() > 100 * 1024 * 1024) {  // >100MB
    uploader.uploadFileChunked(...).subscribe(...);
} else {
    uploader.uploadFile(...).subscribe(...);
}

// ❌ BAD: Upload 2GB file in one request
uploader.uploadFile(twoGBFile, ...).subscribe(...);  // Will likely timeout
```

### Practice 4: Provide User Feedback with Progress Tracking

```java
// ✅ GOOD: Show progress to user
uploader.uploadFileWithProgress(
    "/api/upload",
    file,
    "file",
    progress -> updateUI(progress),  // Update UI
    UploadResponse.class
);

// ❌ BAD: No feedback for long uploads
uploader.uploadFile(...).subscribe(...);  // User has no idea what's happening
```

### Practice 5: Handle Errors Gracefully

```java
// ✅ GOOD: Comprehensive error handling
uploader.uploadFile(...).subscribe(
    result -> handleSuccess(result),
    error -> {
        if (error instanceof UploadValidationException) {
            showValidationError(error.getMessage());
        } else if (error.getMessage().contains("timeout")) {
            showTimeoutError();
        } else {
            showGenericError(error);
        }
    }
);

// ❌ BAD: No error handling
uploader.uploadFile(...).subscribe(result -> handleSuccess(result));
```

### Practice 6: Use Compression for Large Text Files

```java
// ✅ GOOD: Compress large text files
if (file.getName().endsWith(".txt") || file.getName().endsWith(".json")) {
    uploader.uploadFileCompressed(...).subscribe(...);
}

// ❌ BAD: Upload large uncompressed text file
uploader.uploadFile(largeLogFile, ...).subscribe(...);
```

### Practice 7: Limit Parallel Uploads

```java
// ✅ GOOD: Reasonable parallelism
UploadConfig config = UploadConfig.builder()
    .maxParallelUploads(3)  // 3-5 is reasonable
    .build();

// ❌ BAD: Too many parallel uploads
UploadConfig config = UploadConfig.builder()
    .maxParallelUploads(50)  // Will overwhelm server and network
    .build();
```

### Practice 8: Clean Up Resources

```java
// ✅ GOOD: Proper resource management
uploader.uploadFile(...).subscribe(
    result -> handleSuccess(result),
    error -> handleError(error),
    () -> cleanupResources()  // Cleanup on completion
);

// ❌ BAD: No cleanup
uploader.uploadFile(...).subscribe(result -> handleSuccess(result));
```

---

## Complete Examples

### Example 1: Document Management System

```java
import org.fireflyframework.client.multipart.MultipartUploadHelper;
import org.fireflyframework.client.multipart.MultipartUploadHelper.UploadConfig;
import org.fireflyframework.client.multipart.MultipartUploadHelper.UploadResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.io.File;
import java.time.Duration;

@Service
public class DocumentService {

    private final MultipartUploadHelper uploader;

    public DocumentService() {
        UploadConfig config = UploadConfig.builder()
            .timeout(Duration.ofMinutes(10))
            .enableRetry(true)
            .maxRetries(3)
            .maxFileSize(50 * 1024 * 1024)  // 50MB
            .allowedExtension("pdf")
            .allowedExtension("docx")
            .allowedExtension("xlsx")
            .enableProgressTracking(true)
            .build();

        this.uploader = new MultipartUploadHelper(
            "https://docs.example.com",
            config
        );
    }

    public Mono<String> uploadDocument(File file, String customerId, String category) {
        // Validate file
        try {
            uploader.validateFile(file);
        } catch (Exception e) {
            return Mono.error(e);
        }

        // Prepare metadata
        Map<String, Object> metadata = Map.of(
            "customerId", customerId,
            "category", category,
            "uploadedAt", Instant.now().toString()
        );

        // Upload with progress tracking
        return uploader.uploadFilesWithMetadata(
            "/api/documents/upload",
            Map.of("document", file),
            metadata,
            UploadResponse.class
        ).map(UploadResponse::getFileId);
    }
}
```

---

## See Also

- [REST Client Guide](REST_CLIENT.md) - For standard REST operations
- [GraphQL Client Guide](GRAPHQL_CLIENT.md) - For GraphQL operations
- [OAuth2 Helper Guide](OAUTH2_HELPER.md) - For OAuth2 authentication
- [Configuration Reference](CONFIGURATION.md) - Complete configuration guide

---

**Need Help?** Check the [main documentation](README.md) or open an issue on GitHub.

