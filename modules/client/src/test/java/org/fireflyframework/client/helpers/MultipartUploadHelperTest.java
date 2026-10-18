package org.fireflyframework.client.helpers;

import org.fireflyframework.client.multipart.MultipartUploadHelper;
import org.fireflyframework.client.multipart.MultipartUploadHelper.UploadConfig;
import org.fireflyframework.client.multipart.MultipartUploadHelper.UploadProgress;
import org.fireflyframework.client.multipart.MultipartUploadHelper.UploadValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for MultipartUploadHelper.
 */
@DisplayName("Multipart Upload Helper Tests")
class MultipartUploadHelperTest {

    @Test
    @DisplayName("Should create upload helper with valid base URL")
    void shouldCreateUploadHelperWithValidBaseUrl() {
        // When
        MultipartUploadHelper helper = new MultipartUploadHelper("http://localhost:8080");

        // Then
        assertThat(helper).isNotNull();
        assertThat(helper.getBaseUrl()).isEqualTo("http://localhost:8080");
    }

    @Test
    @DisplayName("Should create upload helper with trailing slash removed")
    void shouldCreateUploadHelperWithTrailingSlashRemoved() {
        // When
        MultipartUploadHelper helper = new MultipartUploadHelper("http://localhost:8080/");

        // Then
        assertThat(helper).isNotNull();
        assertThat(helper.getBaseUrl()).isEqualTo("http://localhost:8080");
    }

    @Test
    @DisplayName("Should create upload helper with custom configuration")
    void shouldCreateUploadHelperWithCustomConfiguration() {
        // Given
        Duration timeout = Duration.ofMinutes(10);
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer token123");
        headers.put("X-API-Key", "api-key-456");

        // When
        MultipartUploadHelper helper = new MultipartUploadHelper(
            "https://upload.example.com",
            timeout,
            headers
        );

        // Then
        assertThat(helper).isNotNull();
        assertThat(helper.getBaseUrl()).isEqualTo("https://upload.example.com");
    }

    @Test
    @DisplayName("Should throw exception for null base URL")
    void shouldThrowExceptionForNullBaseUrl() {
        // When/Then
        assertThatThrownBy(() -> new MultipartUploadHelper(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Base URL cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception for empty base URL")
    void shouldThrowExceptionForEmptyBaseUrl() {
        // When/Then
        assertThatThrownBy(() -> new MultipartUploadHelper(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Base URL cannot be null or empty");
    }

    @Test
    @DisplayName("Should demonstrate upload helper API")
    void shouldDemonstrateUploadHelperApi() {
        // Given
        MultipartUploadHelper helper = new MultipartUploadHelper(
            "http://localhost:8080",
            Duration.ofMinutes(5),
            Map.of("X-Client-Version", "1.0.0")
        );

        // Then: Helper should be properly configured
        assertThat(helper).isNotNull();
        assertThat(helper.getBaseUrl()).isEqualTo("http://localhost:8080");

        // Note: Actual file upload tests would require a running HTTP server
        // For integration tests, use WireMock or MockWebServer
    }

    @Test
    @DisplayName("Should create upload helper with advanced config")
    void shouldCreateUploadHelperWithAdvancedConfig() {
        // Given
        UploadConfig config = UploadConfig.builder()
            .timeout(Duration.ofMinutes(10))
            .enableRetry(true)
            .maxRetries(5)
            .retryBackoff(Duration.ofSeconds(2))
            .enableCompression(true)
            .chunkSize(10 * 1024 * 1024) // 10MB
            .maxParallelUploads(5)
            .maxFileSize(500 * 1024 * 1024) // 500MB
            .allowedMimeType("application/pdf")
            .allowedMimeType("image/jpeg")
            .allowedExtension("pdf")
            .allowedExtension("jpg")
            .defaultHeader("X-Upload-Client", "Test")
            .enableProgressTracking(true)
            .build();

        // When
        MultipartUploadHelper helper = new MultipartUploadHelper("http://localhost:8080", config);

        // Then
        assertThat(helper).isNotNull();
        assertThat(helper.getBaseUrl()).isEqualTo("http://localhost:8080");
    }

    @Test
    @DisplayName("Should build upload config with defaults")
    void shouldBuildUploadConfigWithDefaults() {
        // When
        UploadConfig config = UploadConfig.builder().build();

        // Then
        assertThat(config).isNotNull();
        assertThat(config.toString()).contains("timeout=PT5M");
        assertThat(config.toString()).contains("enableRetry=false");
        assertThat(config.toString()).contains("enableCompression=false");
    }

    @Test
    @DisplayName("Should validate file size")
    void shouldValidateFileSize(@TempDir Path tempDir) throws IOException {
        // Given
        UploadConfig config = UploadConfig.builder()
            .maxFileSize(1024) // 1KB max
            .build();

        MultipartUploadHelper helper = new MultipartUploadHelper("http://localhost:8080", config);

        File largeFile = tempDir.resolve("large.txt").toFile();
        try (FileWriter writer = new FileWriter(largeFile)) {
            for (int i = 0; i < 200; i++) {
                writer.write("This is a test line to make the file larger than 1KB.\n");
            }
        }

        // When/Then
        assertThatThrownBy(() -> helper.validateFile(largeFile))
            .isInstanceOf(UploadValidationException.class)
            .hasMessageContaining("exceeds maximum allowed size");
    }

    @Test
    @DisplayName("Should validate file extension")
    void shouldValidateFileExtension(@TempDir Path tempDir) throws IOException {
        // Given
        UploadConfig config = UploadConfig.builder()
            .allowedExtension("pdf")
            .allowedExtension("jpg")
            .build();

        MultipartUploadHelper helper = new MultipartUploadHelper("http://localhost:8080", config);

        File txtFile = tempDir.resolve("document.txt").toFile();
        txtFile.createNewFile();

        // When/Then
        assertThatThrownBy(() -> helper.validateFile(txtFile))
            .isInstanceOf(UploadValidationException.class)
            .hasMessageContaining("extension 'txt' is not allowed");
    }

    @Test
    @DisplayName("Should validate file exists")
    void shouldValidateFileExists() {
        // Given
        MultipartUploadHelper helper = new MultipartUploadHelper("http://localhost:8080");
        File nonExistentFile = new File("/path/to/nonexistent/file.txt");

        // When/Then
        assertThatThrownBy(() -> helper.validateFile(nonExistentFile))
            .isInstanceOf(UploadValidationException.class)
            .hasMessageContaining("does not exist");
    }

    @Test
    @DisplayName("Should track upload progress")
    void shouldTrackUploadProgress() {
        // Given
        UploadProgress progress = new UploadProgress("session-123", "test.pdf", 1000);

        // When
        progress.updateProgress(250);

        // Then
        assertThat(progress.getUploadedBytes()).isEqualTo(250);
        assertThat(progress.getTotalBytes()).isEqualTo(1000);
        assertThat(progress.getPercentage()).isEqualTo(25.0);
        assertThat(progress.getFilename()).isEqualTo("test.pdf");
        assertThat(progress.getSessionId()).isEqualTo("session-123");
        assertThat(progress.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("Should cancel upload progress")
    void shouldCancelUploadProgress() {
        // Given
        UploadProgress progress = new UploadProgress("session-123", "test.pdf", 1000);

        // When
        progress.cancel();

        // Then
        assertThat(progress.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("Should calculate upload speed")
    void shouldCalculateUploadSpeed() throws InterruptedException {
        // Given
        UploadProgress progress = new UploadProgress("session-123", "test.pdf", 10000);

        // When
        Thread.sleep(100); // Wait a bit
        progress.updateProgress(5000);

        // Then
        assertThat(progress.getSpeedBytesPerSecond()).isGreaterThan(0);
        assertThat(progress.getElapsedTimeMs()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should get active uploads")
    void shouldGetActiveUploads() {
        // Given
        MultipartUploadHelper helper = new MultipartUploadHelper("http://localhost:8080");

        // When
        Map<String, UploadProgress> activeUploads = helper.getActiveUploads();

        // Then
        assertThat(activeUploads).isNotNull();
        assertThat(activeUploads).isEmpty();
    }

    @Test
    @DisplayName("Should validate file successfully")
    void shouldValidateFileSuccessfully(@TempDir Path tempDir) throws IOException {
        // Given
        MultipartUploadHelper helper = new MultipartUploadHelper("http://localhost:8080");
        File validFile = tempDir.resolve("valid.txt").toFile();
        validFile.createNewFile();

        // When/Then - should not throw
        helper.validateFile(validFile);
    }
}

