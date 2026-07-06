package org.fireflyframework.ecm.adapter.s3;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Presigning is an offline crypto operation (no network), so these tests build a real
 * {@link S3Presigner} with dummy static credentials and assert the URL structure.
 */
class S3PresignTest {

    private S3DocumentContentAdapter adapter;

    private static S3AdapterProperties baseProps() {
        S3AdapterProperties p = new S3AdapterProperties();
        p.setBucketName("test-bucket");
        p.setRegion("eu-west-1");
        p.setPathPrefix("documents/");
        return p;
    }

    @BeforeEach
    void setUp() {
        S3AdapterProperties props = baseProps();
        S3Presigner presigner = S3Presigner.builder()
                .region(Region.of("eu-west-1"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("AKIDTEST", "SECRETTEST")))
                .build();
        adapter = new S3DocumentContentAdapter(mock(S3Client.class), props, presigner);
    }

    @Test
    void generateDownloadUrlByPath_producesSignedGetUrl() {
        String url = adapter.generateDownloadUrlByPath("documents/2bec02ce/abc.pdf", Duration.ofMinutes(5)).block();
        assertThat(url).isNotNull()
                .contains("test-bucket")
                .contains("documents/2bec02ce/abc.pdf")
                .contains("X-Amz-Algorithm=AWS4-HMAC-SHA256")
                .contains("X-Amz-Signature=")
                .contains("X-Amz-Expires=300");
    }

    @Test
    void generateUploadUrlByPath_producesSignedUrl_differentFromDownload() {
        String get = adapter.generateDownloadUrlByPath("documents/t/x.pdf", Duration.ofMinutes(5)).block();
        String put = adapter.generateUploadUrlByPath("documents/t/x.pdf", Duration.ofMinutes(5)).block();
        assertThat(put).contains("X-Amz-Signature=");
        assertThat(put).isNotEqualTo(get); // PUT and GET presigns differ (signed method/headers)
    }

    @Test
    void generateDownloadUrl_byId_usesPathPrefix() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String url = adapter.generateDownloadUrl(id, Duration.ofMinutes(2)).block();
        assertThat(url).contains("documents/" + id).contains("X-Amz-Expires=120");
    }

    @Test
    void presigner_withEndpointAndPathStyle_producesPathStyleUrl() {
        S3AdapterProperties props = baseProps();
        props.setEndpoint("http://localhost:9000");
        props.setPathStyleAccess(true);
        props.setAccessKey("AKIDTEST");
        props.setSecretKey("SECRETTEST");
        S3Presigner presigner = new S3Beans().s3Presigner(props);
        S3DocumentContentAdapter local = new S3DocumentContentAdapter(mock(S3Client.class), props, presigner);

        String url = local.generateDownloadUrlByPath("documents/t/x.pdf", Duration.ofMinutes(5)).block();
        // path-style => host is the endpoint, bucket lives in the path (not a subdomain)
        assertThat(url).startsWith("http://localhost:9000/test-bucket/documents/t/x.pdf");
    }
}
