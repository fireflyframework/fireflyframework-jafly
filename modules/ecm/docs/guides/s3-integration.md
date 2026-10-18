# Store applicant documents in Amazon S3

Use the shipped S3 storage adapter through the ECM content port. Provision the bucket, region, and deployment credentials before storing an applicant document.

Start with [the onboarding application](../../../../docs/site/getting-started.md). Its submission/query flow has no document storage or signature integration; add this capability as a separate application operation.

## Configure the provider

The [store applicant documents in amazon s3 guide](../../../../docs/site/modules/ecm-storage-aws.md) contains the current Maven artifact, provider namespace, and API examples. Use JDK 25 and the repository wrapper; [installation](../../../../docs/site/installation.md) explains version availability.

## Keep the application boundary explicit

A content-store operation stores bytes for a document ID. The application owns the applicant-to-document relationship, authorization, retention policy, and any status change. For large documents, choose the port's streaming operation and enforce upload limits at the boundary.

## API and tests

- [ECM ports and models](../api/README.md).
- [Provider source and tests](../../../ecm-storage-aws).
- [ECM component guide](../../../../docs/site/modules/ecm.md).
