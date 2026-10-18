package org.fireflyframework.example.service;

import org.fireflyframework.client.multipart.MultipartUploadHelper;
import org.fireflyframework.client.multipart.MultipartUploadHelper.UploadConfig;
import org.fireflyframework.client.multipart.MultipartUploadHelper.UploadProgress;
import org.fireflyframework.example.model.UploadResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Example service demonstrating multipart upload with:
 * - Progress tracking
 * - Chunked uploads for large files
 * - Parallel uploads
 * - File validation
 * - Compression
 */
@Slf4j
@Service
public class FileUploadExampleService {

    private final MultipartUploadHelper uploadClient;

    public FileUploadExampleService() {
        UploadConfig config = UploadConfig.builder()
            .timeout(Duration.ofMinutes(5))
            .enableCompression(true)
            .enableRetry(true)
            .maxRetries(3)
            .chunkSize(5 * 1024 * 1024) // 5MB chunks
            .maxFileSize(100 * 1024 * 1024) // 100MB max
            .allowedMimeType("image/jpeg")
            .allowedMimeType("image/png")
            .allowedMimeType("application/pdf")
            .build();

        this.uploadClient = new MultipartUploadHelper(
            "https://upload.example.com",
            config
        );

        log.info("FileUploadExampleService initialized");
    }

    /**
     * Upload a single file with progress tracking.
     */
    public UploadResponse uploadFile(File file) {
        log.info("Uploading file: {}", file.getName());

        return uploadClient.uploadFileWithProgress(
            "/upload",
            file,
            "file",
            this::logProgress,
            UploadResponse.class
        ).block();
    }

    /**
     * Upload a large file using chunked upload.
     */
    public UploadResponse uploadLargeFile(File file) {
        log.info("Uploading large file in chunks: {}", file.getName());

        return uploadClient.uploadFileChunked(
            "/upload/chunked",
            file,
            "file",
            this::logProgress,
            UploadResponse.class
        ).block();
    }

    /**
     * Upload multiple files in parallel.
     */
    public List<UploadResponse> uploadMultipleFiles(List<File> files) {
        log.info("Uploading {} files in parallel", files.size());

        Map<String, File> namedFiles = new LinkedHashMap<>();
        for (File file : files) {
            namedFiles.put(file.getName(), file);
        }

        return uploadClient.uploadFilesParallel(
            "/upload",
            namedFiles,
            this::logProgress,
            UploadResponse.class
        ).collectList().block();
    }

    /**
     * Upload file with metadata.
     */
    public UploadResponse uploadFileWithMetadata(File file, String description, String category) {
        log.info("Uploading file with metadata: {}", file.getName());

        return uploadClient.uploadFilesWithMetadata(
            "/upload",
            Map.of("file", file),
            Map.of(
                "description", description,
                "category", category
            ),
            UploadResponse.class
        ).block();
    }

    /**
     * Validate file before upload.
     */
    public boolean validateFile(File file) {
        try {
            uploadClient.validateFile(file);
            log.info("File validation passed: {}", file.getName());
            return true;
        } catch (Exception e) {
            log.error("File validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Log upload progress.
     */
    private void logProgress(UploadProgress progress) {
        log.info("Upload progress: {}% ({} / {} bytes) - Speed: {} KB/s",
            String.format("%.1f", progress.getPercentage()),
            progress.getUploadedBytes(),
            progress.getTotalBytes(),
            String.format("%.1f", progress.getSpeedBytesPerSecond() / 1024)
        );
    }
}
