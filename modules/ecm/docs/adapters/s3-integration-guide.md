# Amazon S3 Adapter Integration Guide

This guide provides step-by-step instructions for integrating the Amazon S3 adapter with the Firefly ECM Library.

## Overview

The shipped `S3DocumentContentAdapter` implements `DocumentContentPort`: store and retrieve bytes/streams, delete or inspect content, calculate checksums, and generate configured presigned URLs. The application supplies document metadata, folders, and access policy separately.

## Prerequisites

Before integrating the S3 adapter, ensure you have:

1. AWS Account: An active AWS account with S3 access
2. S3 Bucket: A dedicated S3 bucket for document storage
3. IAM Permissions: Appropriate IAM permissions for S3 operations
4. Java 25: The ECM library requires Java 25 or higher
5. Spring Boot 4.1.1: Compatible Spring Boot version

## Step 1: Add Dependencies

Add the S3 adapter dependency to your `pom.xml`:

```xml
<dependencies>
    <!-- Core ECM Library -->
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-ecm</artifactId>
        <version>26.10.20</version>
    </dependency>
    
    <!-- S3 Adapter -->
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-ecm-storage-aws</artifactId>
        <version>26.10.20</version>
    </dependency>
</dependencies>
```

## Step 2: Configure AWS Credentials

### Option 1: Environment Variables (Recommended)

Set the following environment variables:

```bash
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key
export AWS_REGION=us-east-1
export S3_BUCKET_NAME=your-ecm-bucket
```

### Option 2: IAM Roles (Production Recommended)

For production deployments, use IAM roles instead of access keys:

1. Create an IAM role with S3 permissions
2. Attach the role to your EC2 instance or EKS pod
3. The adapter will automatically use the role credentials

### Option 3: YAML Configuration File

Configure credentials in `application.yml`:

```yaml
firefly:
  ecm:
    adapter:
      s3:
        access-key: ${AWS_ACCESS_KEY_ID}
        secret-key: ${AWS_SECRET_ACCESS_KEY}
        region: ${AWS_REGION}
        bucket-name: ${S3_BUCKET_NAME}
```

## Step 3: Configure the S3 Adapter

### Basic Configuration

Add the following to your `application.yml`:

```yaml
firefly:
  ecm:
    adapter-type: s3
    adapter:
      s3:
        bucket-name: ${S3_BUCKET_NAME:firefly-ecm-documents}
        region: ${AWS_REGION:us-east-1}
        path-prefix: documents/
        enable-versioning: true
```

### Advanced Configuration

For production environments, configure additional settings:

```yaml
firefly:
  ecm:
    adapter:
      s3:
        # Required settings
        bucket-name: ${S3_BUCKET_NAME}
        region: ${AWS_REGION}
        
        # Optional authentication (use IAM roles when possible)
        access-key: ${AWS_ACCESS_KEY_ID:}
        secret-key: ${AWS_SECRET_ACCESS_KEY:}
        
        # Storage settings
        path-prefix: documents/
        enable-versioning: true
        storage-class: STANDARD
        
        # Security settings
        enable-encryption: true
        kms-key-id: ${S3_KMS_KEY_ID:}
        
        # Performance settings
        enable-multipart: true
        multipart-threshold: 5242880  # 5MB
        multipart-part-size: 5242880  # 5MB
        
        # Connection settings
        connection-timeout: PT30S
        socket-timeout: PT30S
        max-retries: 3
        
        # S3-compatible services (e.g., MinIO)
        endpoint: ${S3_ENDPOINT:}
        path-style-access: false
```

## Step 4: Set Up S3 Bucket

### Create S3 Bucket

```bash
aws s3 mb s3://your-ecm-bucket --region us-east-1
```

### Configure Bucket Versioning (Optional)

```bash
aws s3api put-bucket-versioning \
    --bucket your-ecm-bucket \
    --versioning-configuration Status=Enabled
```

### Configure Bucket Encryption (Recommended)

```bash
aws s3api put-bucket-encryption \
    --bucket your-ecm-bucket \
    --server-side-encryption-configuration '{
        "Rules": [
            {
                "ApplyServerSideEncryptionByDefault": {
                    "SSEAlgorithm": "AES256"
                }
            }
        ]
    }'
```

## Step 5: Configure IAM Permissions

Create an IAM policy with the following permissions:

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "s3:GetObject",
                "s3:PutObject",
                "s3:DeleteObject",
                "s3:GetObjectVersion",
                "s3:DeleteObjectVersion",
                "s3:ListBucket",
                "s3:ListBucketVersions",
                "s3:GetBucketLocation",
                "s3:GetBucketVersioning"
            ],
            "Resource": [
                "arn:aws:s3:::your-ecm-bucket",
                "arn:aws:s3:::your-ecm-bucket/*"
            ]
        }
    ]
}
```

## Step 6: Use in your application

This API class needs the configured S3 content-port bean. The application assigns the document ID and authorizes the applicant before invoking it; storing bytes does not create an application-owned document record. Lombok setup is described in [Constructor injection](../../../../docs/site/maven.md#constructor-injection).

```java
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.fireflyframework.ecm.port.document.DocumentContentPort;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class ApplicantDocumentContent {
    private final DocumentContentPort content;

    public Mono<String> upload(UUID documentId, byte[] bytes) {
        return content.storeContent(documentId, bytes, "application/pdf");
    }

    public Mono<byte[]> download(UUID documentId) {
        return content.getContent(documentId);
    }
}
```

The port also exposes streaming operations. Use those with an upload-size policy and the provider configuration rather than loading an unbounded document into memory.

## Step 7: Testing

### Unit Tests

```java
@SpringBootTest
@TestPropertySource(properties = {
    "firefly.ecm.adapter-type=s3",
    "firefly.ecm.adapter.s3.bucket-name=test-bucket",
    "firefly.ecm.adapter.s3.region=us-east-1"
})
class S3AdapterIntegrationTest {
    
    @Autowired
    private DocumentPort documentPort;
    
    @Test
    void shouldCreateAndRetrieveDocument() {
        // Test implementation
    }
}
```

### Integration Tests with Testcontainers

```java
@Testcontainers
class S3AdapterTestcontainersTest {
    
    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:latest"))
            .withServices(LocalStackContainer.Service.S3);
    
    @Test
    void shouldWorkWithLocalStack() {
        // Test implementation using LocalStack
    }
}
```

## Troubleshooting

### Common Issues

1. Access Denied: Check IAM permissions and bucket policies
2. Bucket Not Found: Verify bucket name and region configuration
3. Connection Timeout: Adjust timeout settings or check network connectivity
4. Large File Upload Fails: Enable multipart upload and adjust thresholds

### Logging

Enable debug logging for troubleshooting:

```yaml
logging:
  level:
    org.fireflyframework.ecm.adapter.s3: DEBUG
    software.amazon.awssdk.services.s3: DEBUG
```

## Test the configured adapter

Use the [current test sources](../../../ecm-storage-aws/src/test) and the reports from the actual build. Mocked-client tests check their declared request/response cases; they do not establish live-provider acceptance or a permanent success rate. Configure and test credentials, account permissions, and provider failures separately.

## Best Practices

1. Use IAM Roles: Avoid hardcoding access keys in production
2. Enable Versioning: Protect against accidental deletions
3. Configure Encryption: Use server-side encryption for sensitive data
4. Monitor Costs: Set up CloudWatch alarms for S3 usage
5. Lifecycle Policies: Configure automatic archiving for old documents
6. Cross-Region Replication: Consider replication for disaster recovery
7. Resilience Configuration: Properly configure circuit breaker and retry patterns

## Next Steps

- [DocuSign Integration Guide](docusign-integration-guide.md)
- [Configuration Reference](../configuration-reference.md)
- [ECM API contracts](../api/README.md)
