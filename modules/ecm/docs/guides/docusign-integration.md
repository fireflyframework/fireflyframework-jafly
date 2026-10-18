# Send an onboarding agreement with DocuSign

Use the shipped DocuSign adapter through the signature envelope port. Configure the account, integration key, authentication, documents, and recipients required by that deployment.

Start with [the onboarding application](../../../../docs/site/getting-started.md). Its submission/query flow has no document storage or signature integration; add this capability as a separate application operation.

## Configure the provider

The [send an onboarding agreement with docusign guide](../../../../docs/site/modules/ecm-esignature-docusign.md) contains the current Maven artifact, provider namespace, and API examples. Use JDK 25 and the repository wrapper; [installation](../../../../docs/site/installation.md) explains version availability.

## Keep the application boundary explicit

Creating or sending an envelope does not complete onboarding. Record the provider envelope ID, decide how status is reconciled, and handle cancellation or failed signing in the application. Verify provider notifications through the configured processing path before changing an application record.

## API and tests

- [ECM ports and models](../api/README.md).
- [Provider source and tests](../../../ecm-esignature-docusign).
- [ECM component guide](../../../../docs/site/modules/ecm.md).
