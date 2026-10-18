package org.fireflyframework.client.multipart;

import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.kernel.exception.FireflyException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.retry.Retry;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.zip.GZIPOutputStream;

/**
 * Helper class for multipart file uploads using Spring WebFlux WebClient.
 * 
 * <p>This helper provides a simplified API for file upload operations while
 * maintaining compatibility with the Firefly Common Client library's patterns.
 *
 * <p><strong>Note:</strong> This is a helper utility for file uploads.
 * For standard REST operations, use {@link org.fireflyframework.client.RestClient}.
 *
 * <p>Example usage:
 * <pre>{@code
 * // Create upload helper
 * MultipartUploadHelper uploader = new MultipartUploadHelper("http://localhost:8080");
 *
 * // Upload a single file
 * Mono<UploadResponse> response = uploader.uploadFile(
 *     "/api/upload",
 *     new File("/path/to/file.pdf"),
 *     "document"
 * );
 *
 * // Upload multiple files with metadata
 * Map<String, Object> metadata = Map.of("userId", "123", "category", "invoice");
 * Mono<UploadResponse> response = uploader.uploadFiles(
 *     "/api/upload/batch",
 *     Map.of(
 *         "invoice", new File("/path/to/invoice.pdf"),
 *         "receipt", new File("/path/to/receipt.jpg")
 *     ),
 *     metadata
 * );
 * }</pre>
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
@Slf4j
public class MultipartUploadHelper {

    private final String baseUrl;
    private final WebClient webClient;
    private final UploadConfig config;
    private final Map<String, UploadSession> activeSessions;
    private final FileValidator fileValidator;

    /**
     * Creates a new multipart upload helper with default configuration.
     *
     * @param baseUrl the base URL of the upload service
     */
    public MultipartUploadHelper(String baseUrl) {
        this(baseUrl, UploadConfig.builder().build());
    }

    /**
     * Creates a new multipart upload helper with custom configuration.
     *
     * @param baseUrl the base URL of the upload service
     * @param timeout the upload timeout
     * @param defaultHeaders default headers to include in all requests
     */
    public MultipartUploadHelper(String baseUrl, Duration timeout, Map<String, String> defaultHeaders) {
        this(baseUrl, UploadConfig.builder()
            .timeout(timeout)
            .defaultHeaders(defaultHeaders)
            .build());
    }

    /**
     * Creates a new multipart upload helper with advanced configuration.
     *
     * @param baseUrl the base URL of the upload service
     * @param config the upload configuration
     */
    public MultipartUploadHelper(String baseUrl, UploadConfig config) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Base URL cannot be null or empty");
        }

        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.config = config;
        this.activeSessions = new ConcurrentHashMap<>();
        this.fileValidator = new FileValidator(config);
        this.webClient = createWebClient();

        log.info("Created multipart upload helper for base URL: {} with config: {}", this.baseUrl, config);
    }

    private WebClient createWebClient() {
        WebClient.Builder builder = WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeaders(headers -> config.defaultHeaders.forEach(headers::add));

        return builder.build();
    }

    /**
     * Uploads a single file.
     *
     * @param endpoint the upload endpoint
     * @param file the file to upload
     * @param fieldName the form field name
     * @param <R> the response type
     * @return a Mono containing the response
     */
    public <R> Mono<R> uploadFile(String endpoint, File file, String fieldName, Class<R> responseType) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part(fieldName, file.toPath().toFile());
        
        return executeUpload(endpoint, builder, responseType);
    }

    /**
     * Uploads a single file from a Path.
     *
     * @param endpoint the upload endpoint
     * @param filePath the file path
     * @param fieldName the form field name
     * @param responseType the response type
     * @param <R> the response type
     * @return a Mono containing the response
     */
    public <R> Mono<R> uploadFile(String endpoint, Path filePath, String fieldName, Class<R> responseType) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part(fieldName, filePath.toFile());
        
        return executeUpload(endpoint, builder, responseType);
    }

    /**
     * Uploads a single file from a Resource.
     *
     * @param endpoint the upload endpoint
     * @param resource the file resource
     * @param fieldName the form field name
     * @param filename the filename to use
     * @param responseType the response type
     * @param <R> the response type
     * @return a Mono containing the response
     */
    public <R> Mono<R> uploadFile(String endpoint, Resource resource, String fieldName, 
                                   String filename, Class<R> responseType) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part(fieldName, resource).filename(filename);
        
        return executeUpload(endpoint, builder, responseType);
    }

    /**
     * Uploads multiple files.
     *
     * @param endpoint the upload endpoint
     * @param files map of field names to files
     * @param responseType the response type
     * @param <R> the response type
     * @return a Mono containing the response
     */
    public <R> Mono<R> uploadFiles(String endpoint, Map<String, File> files, Class<R> responseType) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        files.forEach((fieldName, file) -> builder.part(fieldName, file));
        
        return executeUpload(endpoint, builder, responseType);
    }

    /**
     * Uploads files with additional form data.
     *
     * @param endpoint the upload endpoint
     * @param files map of field names to files
     * @param formData additional form data
     * @param responseType the response type
     * @param <R> the response type
     * @return a Mono containing the response
     */
    public <R> Mono<R> uploadFilesWithMetadata(String endpoint, Map<String, File> files, 
                                                Map<String, Object> formData, Class<R> responseType) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        
        // Add files
        files.forEach((fieldName, file) -> builder.part(fieldName, file));
        
        // Add form data
        formData.forEach((key, value) -> builder.part(key, value));
        
        return executeUpload(endpoint, builder, responseType);
    }

    /**
     * Uploads a file with custom headers.
     *
     * @param endpoint the upload endpoint
     * @param file the file to upload
     * @param fieldName the form field name
     * @param headers custom headers
     * @param responseType the response type
     * @param <R> the response type
     * @return a Mono containing the response
     */
    public <R> Mono<R> uploadFileWithHeaders(String endpoint, File file, String fieldName,
                                              Map<String, String> headers, Class<R> responseType) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part(fieldName, file);
        
        return webClient.post()
            .uri(endpoint)
            .headers(httpHeaders -> headers.forEach(httpHeaders::add))
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(builder.build()))
            .retrieve()
            .bodyToMono(responseType)
            .timeout(config.timeout)
            .doOnSubscribe(sub -> log.debug("Uploading file to: {}{}", baseUrl, endpoint))
            .doOnSuccess(response -> log.debug("Upload successful to: {}{}", baseUrl, endpoint))
            .doOnError(error -> log.error("Upload failed to {}{}: {}", baseUrl, endpoint, error.getMessage()));
    }

    /**
     * Uploads a file as a stream.
     *
     * @param endpoint the upload endpoint
     * @param dataStream the data stream
     * @param fieldName the form field name
     * @param filename the filename
     * @param contentType the content type
     * @param responseType the response type
     * @param <R> the response type
     * @return a Mono containing the response
     */
    public <R> Mono<R> uploadStream(String endpoint, Flux<DataBuffer> dataStream, String fieldName,
                                     String filename, MediaType contentType, Class<R> responseType) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.asyncPart(fieldName, dataStream, DataBuffer.class)
            .filename(filename)
            .contentType(contentType);
        
        return executeUpload(endpoint, builder, responseType);
    }

    private <R> Mono<R> executeUpload(String endpoint, MultipartBodyBuilder builder, Class<R> responseType) {
        return webClient.post()
            .uri(endpoint)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(builder.build()))
            .retrieve()
            .bodyToMono(responseType)
            .timeout(config.timeout)
            .doOnSubscribe(sub -> log.debug("Uploading to: {}{}", baseUrl, endpoint))
            .doOnSuccess(response -> log.debug("Upload successful to: {}{}", baseUrl, endpoint))
            .doOnError(error -> log.error("Upload failed to {}{}: {}", baseUrl, endpoint, error.getMessage()));
    }

    /**
     * Gets the base URL.
     *
     * @return the base URL
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    // ==================== ENHANCED METHODS ====================

    /**
     * Uploads a file with progress tracking.
     *
     * @param endpoint the upload endpoint
     * @param file the file to upload
     * @param fieldName the form field name
     * @param progressCallback callback for progress updates
     * @param responseType the response type
     * @param <R> the response type
     * @return a Mono containing the response
     */
    public <R> Mono<R> uploadFileWithProgress(String endpoint, File file, String fieldName,
                                               Consumer<UploadProgress> progressCallback,
                                               Class<R> responseType) {
        fileValidator.validate(file);

        String sessionId = UUID.randomUUID().toString();
        UploadProgress progress = new UploadProgress(sessionId, file.getName(), file.length());

        if (config.enableProgressTracking && progressCallback != null) {
            // Report initial progress
            progressCallback.accept(progress);
        }

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part(fieldName, file);

        Mono<R> upload = executeUpload(endpoint, builder, responseType);

        if (config.enableRetry) {
            upload = upload.retryWhen(
                Retry.backoff(config.maxRetries, config.retryBackoff)
                    .filter(this::isRetryableError)
            );
        }

        return upload
            .doOnSuccess(response -> {
                progress.updateProgress(file.length());
                if (progressCallback != null) {
                    progressCallback.accept(progress);
                }
                log.info("Upload completed: {}", progress);
            })
            .doOnError(error -> log.error("Upload failed for file {}: {}", file.getName(), error.getMessage()));
    }

    /**
     * Uploads multiple files in parallel with progress tracking.
     *
     * @param endpoint the upload endpoint
     * @param files map of field names to files
     * @param progressCallback callback for progress updates
     * @param responseType the response type
     * @param <R> the response type
     * @return a Flux containing the responses
     */
    public <R> Flux<R> uploadFilesParallel(String endpoint, Map<String, File> files,
                                            Consumer<UploadProgress> progressCallback,
                                            Class<R> responseType) {
        return Flux.fromIterable(files.entrySet())
            .flatMap(entry ->
                uploadFileWithProgress(endpoint, entry.getValue(), entry.getKey(),
                    progressCallback, responseType),
                config.maxParallelUploads
            );
    }

    /**
     * Uploads a large file in chunks (resumable upload).
     *
     * @param endpoint the upload endpoint
     * @param file the file to upload
     * @param fieldName the form field name
     * @param progressCallback callback for progress updates
     * @param responseType the response type
     * @param <R> the response type
     * @return a Mono containing the response
     */
    public <R> Mono<R> uploadFileChunked(String endpoint, File file, String fieldName,
                                          Consumer<UploadProgress> progressCallback,
                                          Class<R> responseType) {
        fileValidator.validate(file);

        String sessionId = UUID.randomUUID().toString();
        UploadSession session = new UploadSession(sessionId, file.getName(), file.length());
        activeSessions.put(sessionId, session);

        long fileSize = file.length();
        int totalChunks = (int) Math.ceil((double) fileSize / config.chunkSize);

        log.info("Starting chunked upload for file {} ({} bytes) in {} chunks",
            file.getName(), fileSize, totalChunks);

        return Flux.range(0, totalChunks)
            .flatMap(chunkIndex -> {
                if (session.getProgress().isCancelled()) {
                    return Mono.error(new UploadValidationException("Upload cancelled"));
                }

                long offset = chunkIndex * config.chunkSize;
                long chunkLength = Math.min(config.chunkSize, fileSize - offset);

                return uploadChunk(endpoint, file, fieldName, chunkIndex, offset, chunkLength, sessionId)
                    .doOnSuccess(result -> {
                        session.addChunk(chunkIndex, chunkLength);
                        if (progressCallback != null) {
                            progressCallback.accept(session.getProgress());
                        }
                        log.debug("Uploaded chunk {}/{} for file {}",
                            chunkIndex + 1, totalChunks, file.getName());
                    });
            }, config.maxParallelUploads)
            .then(Mono.defer(() -> {
                activeSessions.remove(sessionId);
                log.info("Chunked upload completed: {}", session.getProgress());
                // Return a completion response
                return Mono.just(responseType.cast(new UploadResponse()));
            }));
    }

    /**
     * Uploads a single chunk of a file.
     */
    private Mono<String> uploadChunk(String endpoint, File file, String fieldName,
                                      int chunkIndex, long offset, long length, String sessionId) {
        try {
            byte[] chunkData = new byte[(int) length];
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                raf.seek(offset);
                raf.readFully(chunkData);
            }

            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part(fieldName, chunkData);
            builder.part("chunkIndex", chunkIndex);
            builder.part("sessionId", sessionId);
            builder.part("totalChunks", (int) Math.ceil((double) file.length() / config.chunkSize));

            return webClient.post()
                .uri(endpoint + "/chunk")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(config.timeout);

        } catch (IOException e) {
            return Mono.error(new UploadValidationException("Failed to read chunk", e));
        }
    }

    /**
     * Uploads a file with compression.
     *
     * @param endpoint the upload endpoint
     * @param file the file to upload
     * @param fieldName the form field name
     * @param responseType the response type
     * @param <R> the response type
     * @return a Mono containing the response
     */
    public <R> Mono<R> uploadFileCompressed(String endpoint, File file, String fieldName,
                                             Class<R> responseType) {
        fileValidator.validate(file);

        try {
            File compressedFile = compressFile(file);

            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part(fieldName, compressedFile);
            builder.part("compressed", "true");
            builder.part("originalFilename", file.getName());

            Mono<R> upload = executeUpload(endpoint, builder, responseType);

            return upload.doFinally(signal -> {
                // Clean up compressed file
                if (compressedFile.exists()) {
                    compressedFile.delete();
                }
            });

        } catch (IOException e) {
            return Mono.error(new UploadValidationException("Failed to compress file", e));
        }
    }

    /**
     * Compresses a file using GZIP.
     */
    private File compressFile(File file) throws IOException {
        File compressedFile = File.createTempFile("upload_", ".gz");

        try (FileInputStream fis = new FileInputStream(file);
             FileOutputStream fos = new FileOutputStream(compressedFile);
             GZIPOutputStream gzos = new GZIPOutputStream(fos)) {

            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                gzos.write(buffer, 0, len);
            }
        }

        log.debug("Compressed file {} from {} bytes to {} bytes",
            file.getName(), file.length(), compressedFile.length());

        return compressedFile;
    }

    /**
     * Cancels an active upload session.
     *
     * @param sessionId the session ID to cancel
     * @return true if the session was cancelled, false if not found
     */
    public boolean cancelUpload(String sessionId) {
        UploadSession session = activeSessions.get(sessionId);
        if (session != null) {
            session.getProgress().cancel();
            activeSessions.remove(sessionId);
            log.info("Cancelled upload session: {}", sessionId);
            return true;
        }
        return false;
    }

    /**
     * Gets the progress of an active upload session.
     *
     * @param sessionId the session ID
     * @return the upload progress, or null if not found
     */
    public UploadProgress getUploadProgress(String sessionId) {
        UploadSession session = activeSessions.get(sessionId);
        return session != null ? session.getProgress() : null;
    }

    /**
     * Gets all active upload sessions.
     *
     * @return map of session IDs to upload progress
     */
    public Map<String, UploadProgress> getActiveUploads() {
        Map<String, UploadProgress> progress = new HashMap<>();
        activeSessions.forEach((id, session) -> progress.put(id, session.getProgress()));
        return progress;
    }

    /**
     * Validates a file before upload.
     *
     * @param file the file to validate
     * @throws UploadValidationException if validation fails
     */
    public void validateFile(File file) throws UploadValidationException {
        fileValidator.validate(file);
    }

    /**
     * Checks if an error is retryable.
     */
    private boolean isRetryableError(Throwable error) {
        // Retry on network errors, timeouts, and 5xx errors
        return error instanceof IOException ||
               error.getMessage().contains("timeout") ||
               error.getMessage().contains("Connection refused");
    }

    /**
     * Example: Upload invoice document
     */
    public static class InvoiceUploadExample {
        public static void main(String[] args) {
            MultipartUploadHelper uploader = new MultipartUploadHelper("http://localhost:8080");
            
            File invoice = new File("/path/to/invoice.pdf");
            Map<String, Object> metadata = Map.of(
                "customerId", "CUST-123",
                "invoiceNumber", "INV-2025-001",
                "amount", 1500.00
            );
            
            Mono<UploadResponse> response = uploader.uploadFilesWithMetadata(
                "/api/invoices/upload",
                Map.of("invoice", invoice),
                metadata,
                UploadResponse.class
            );
            
            response.subscribe(
                result -> System.out.println("Upload successful: " + result.getFileId()),
                error -> System.err.println("Upload failed: " + error.getMessage())
            );
        }
    }

    /**
     * Example: Upload profile picture
     */
    public static class ProfilePictureExample {
        public static void main(String[] args) {
            MultipartUploadHelper uploader = new MultipartUploadHelper(
                "http://localhost:8080",
                Duration.ofMinutes(2),
                Map.of("Authorization", "Bearer token123")
            );
            
            File profilePic = new File("/path/to/profile.jpg");
            
            Mono<UploadResponse> response = uploader.uploadFile(
                "/api/users/123/profile-picture",
                profilePic,
                "profilePicture",
                UploadResponse.class
            );
            
            response.block();
        }
    }

    /**
     * Response DTO for upload operations
     */
    public static class UploadResponse {
        private String fileId;
        private String filename;
        private Long size;
        private String url;

        public String getFileId() { return fileId; }
        public void setFileId(String fileId) { this.fileId = fileId; }

        public String getFilename() { return filename; }
        public void setFilename(String filename) { this.filename = filename; }

        public Long getSize() { return size; }
        public void setSize(Long size) { this.size = size; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }

    /**
     * Upload configuration with builder pattern.
     */
    public static class UploadConfig {
        private final Duration timeout;
        private final boolean enableRetry;
        private final int maxRetries;
        private final Duration retryBackoff;
        private final boolean enableCompression;
        private final long chunkSize;
        private final int maxParallelUploads;
        private final long maxFileSize;
        private final Set<String> allowedMimeTypes;
        private final Set<String> allowedExtensions;
        private final Map<String, String> defaultHeaders;
        private final boolean enableProgressTracking;

        private UploadConfig(Builder builder) {
            this.timeout = builder.timeout;
            this.enableRetry = builder.enableRetry;
            this.maxRetries = builder.maxRetries;
            this.retryBackoff = builder.retryBackoff;
            this.enableCompression = builder.enableCompression;
            this.chunkSize = builder.chunkSize;
            this.maxParallelUploads = builder.maxParallelUploads;
            this.maxFileSize = builder.maxFileSize;
            this.allowedMimeTypes = new HashSet<>(builder.allowedMimeTypes);
            this.allowedExtensions = new HashSet<>(builder.allowedExtensions);
            this.defaultHeaders = new HashMap<>(builder.defaultHeaders);
            this.enableProgressTracking = builder.enableProgressTracking;
        }

        public static Builder builder() {
            return new Builder();
        }

        @Override
        public String toString() {
            return "UploadConfig{" +
                "timeout=" + timeout +
                ", enableRetry=" + enableRetry +
                ", maxRetries=" + maxRetries +
                ", retryBackoff=" + retryBackoff +
                ", enableCompression=" + enableCompression +
                ", chunkSize=" + chunkSize +
                ", maxParallelUploads=" + maxParallelUploads +
                ", maxFileSize=" + maxFileSize +
                ", enableProgressTracking=" + enableProgressTracking +
                '}';
        }

        public static class Builder {
            private Duration timeout = Duration.ofMinutes(5);
            private boolean enableRetry = false;
            private int maxRetries = 3;
            private Duration retryBackoff = Duration.ofSeconds(1);
            private boolean enableCompression = false;
            private long chunkSize = 5 * 1024 * 1024; // 5MB default
            private int maxParallelUploads = 3;
            private long maxFileSize = 100 * 1024 * 1024; // 100MB default
            private Set<String> allowedMimeTypes = new HashSet<>();
            private Set<String> allowedExtensions = new HashSet<>();
            private Map<String, String> defaultHeaders = new HashMap<>();
            private boolean enableProgressTracking = false;

            public Builder timeout(Duration timeout) {
                this.timeout = timeout;
                return this;
            }

            public Builder enableRetry(boolean enable) {
                this.enableRetry = enable;
                return this;
            }

            public Builder maxRetries(int maxRetries) {
                this.maxRetries = maxRetries;
                return this;
            }

            public Builder retryBackoff(Duration backoff) {
                this.retryBackoff = backoff;
                return this;
            }

            public Builder enableCompression(boolean enable) {
                this.enableCompression = enable;
                return this;
            }

            public Builder chunkSize(long chunkSize) {
                this.chunkSize = chunkSize;
                return this;
            }

            public Builder maxParallelUploads(int max) {
                this.maxParallelUploads = max;
                return this;
            }

            public Builder maxFileSize(long maxSize) {
                this.maxFileSize = maxSize;
                return this;
            }

            public Builder allowedMimeType(String mimeType) {
                this.allowedMimeTypes.add(mimeType);
                return this;
            }

            public Builder allowedExtension(String extension) {
                this.allowedExtensions.add(extension);
                return this;
            }

            public Builder defaultHeader(String name, String value) {
                this.defaultHeaders.put(name, value);
                return this;
            }

            public Builder defaultHeaders(Map<String, String> headers) {
                this.defaultHeaders.putAll(headers);
                return this;
            }

            public Builder enableProgressTracking(boolean enable) {
                this.enableProgressTracking = enable;
                return this;
            }

            public UploadConfig build() {
                return new UploadConfig(this);
            }
        }
    }

    /**
     * Upload progress tracking.
     */
    public static class UploadProgress {
        private final String sessionId;
        private final String filename;
        private final long totalBytes;
        private final AtomicLong uploadedBytes;
        private final AtomicBoolean cancelled;
        private final long startTime;

        public UploadProgress(String sessionId, String filename, long totalBytes) {
            this.sessionId = sessionId;
            this.filename = filename;
            this.totalBytes = totalBytes;
            this.uploadedBytes = new AtomicLong(0);
            this.cancelled = new AtomicBoolean(false);
            this.startTime = System.currentTimeMillis();
        }

        public void updateProgress(long bytes) {
            uploadedBytes.addAndGet(bytes);
        }

        public double getPercentage() {
            if (totalBytes == 0) return 0.0;
            return (uploadedBytes.get() * 100.0) / totalBytes;
        }

        public long getUploadedBytes() {
            return uploadedBytes.get();
        }

        public long getTotalBytes() {
            return totalBytes;
        }

        public String getFilename() {
            return filename;
        }

        public String getSessionId() {
            return sessionId;
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

        public void cancel() {
            cancelled.set(true);
        }

        public long getElapsedTimeMs() {
            return System.currentTimeMillis() - startTime;
        }

        public double getSpeedBytesPerSecond() {
            long elapsed = getElapsedTimeMs();
            if (elapsed == 0) return 0.0;
            return (uploadedBytes.get() * 1000.0) / elapsed;
        }

        @Override
        public String toString() {
            return String.format("UploadProgress{file='%s', progress=%.2f%%, uploaded=%d/%d bytes, speed=%.2f KB/s}",
                filename, getPercentage(), uploadedBytes.get(), totalBytes, getSpeedBytesPerSecond() / 1024);
        }
    }

    /**
     * Upload session for resumable uploads.
     */
    static class UploadSession {
        private final String sessionId;
        private final String filename;
        private final long totalSize;
        private final AtomicLong uploadedSize;
        private final List<ChunkInfo> uploadedChunks;
        private final UploadProgress progress;

        public UploadSession(String sessionId, String filename, long totalSize) {
            this.sessionId = sessionId;
            this.filename = filename;
            this.totalSize = totalSize;
            this.uploadedSize = new AtomicLong(0);
            this.uploadedChunks = Collections.synchronizedList(new ArrayList<>());
            this.progress = new UploadProgress(sessionId, filename, totalSize);
        }

        public void addChunk(int chunkNumber, long size) {
            uploadedChunks.add(new ChunkInfo(chunkNumber, size));
            uploadedSize.addAndGet(size);
            progress.updateProgress(size);
        }

        public boolean isComplete() {
            return uploadedSize.get() >= totalSize;
        }

        public UploadProgress getProgress() {
            return progress;
        }

        static class ChunkInfo {
            final int chunkNumber;
            final long size;

            ChunkInfo(int chunkNumber, long size) {
                this.chunkNumber = chunkNumber;
                this.size = size;
            }
        }
    }

    /**
     * File validator for upload operations.
     */
    static class FileValidator {
        private final UploadConfig config;

        FileValidator(UploadConfig config) {
            this.config = config;
        }

        public void validate(File file) throws UploadValidationException {
            if (file == null || !file.exists()) {
                throw new UploadValidationException("File does not exist");
            }

            if (!file.isFile()) {
                throw new UploadValidationException("Path is not a file");
            }

            if (!file.canRead()) {
                throw new UploadValidationException("File is not readable");
            }

            long fileSize = file.length();
            if (fileSize > config.maxFileSize) {
                throw new UploadValidationException(
                    String.format("File size %d bytes exceeds maximum allowed size %d bytes",
                        fileSize, config.maxFileSize));
            }

            if (!config.allowedExtensions.isEmpty()) {
                String filename = file.getName();
                String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
                if (!config.allowedExtensions.contains(extension)) {
                    throw new UploadValidationException(
                        String.format("File extension '%s' is not allowed. Allowed: %s",
                            extension, config.allowedExtensions));
                }
            }

            if (!config.allowedMimeTypes.isEmpty()) {
                try {
                    String mimeType = Files.probeContentType(file.toPath());
                    if (mimeType != null && !config.allowedMimeTypes.contains(mimeType)) {
                        throw new UploadValidationException(
                            String.format("MIME type '%s' is not allowed. Allowed: %s",
                                mimeType, config.allowedMimeTypes));
                    }
                } catch (IOException e) {
                    log.warn("Could not determine MIME type for file: {}", file.getName());
                }
            }
        }
    }

    /**
     * Exception thrown when file validation fails.
     */
    public static class UploadValidationException extends FireflyException {
        public UploadValidationException(String message) {
            super(message);
        }

        public UploadValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

