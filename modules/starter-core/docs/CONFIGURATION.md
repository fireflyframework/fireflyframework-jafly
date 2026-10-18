# Configure the core starter

Choose HTTP-client limits and header propagation for the onboarding service's downstream calls. The namespace is `firefly.webclient`.

## Client settings

```yaml
firefly:
  webclient:
    enabled: true
    connect-timeout-ms: 5000
    read-timeout-ms: 10000
    write-timeout-ms: 10000
    max-in-memory-size: 16777216
    propagation:
      enabled: true
      allowed-headers:
        - X-Request-Id
        - traceparent
        - tracestate
```

This example sets an explicit propagation allow-list for correlation headers. Choose additional identity or locale headers only when the destination is authorized to receive them; an inbound identity header is not an authentication proof.

[WebClientProperties](../src/main/java/org/fireflyframework/core/config/WebClientProperties.java) declares the values shown above and nested TLS, proxy, connection-pool, HTTP/2, and codec settings. [HeaderPropagationProperties](../src/main/java/org/fireflyframework/core/config/HeaderPropagationProperties.java) declares the default header allow-list. Review those defaults before supplying a replacement list.

## Configure the selected integration

Event transport uses [firefly.eda and its provider configuration](../../../docs/site/modules/eda.md#configuration-and-wiring). Workflow, saga, and TCC operations use [the orchestration configuration](../../../docs/site/modules/orchestration.md#configuration-and-wiring). These are separate runtime facilities, not messaging provider declarations under a generic messaging prefix.

## Verify the host

[The core starter guide](../../../docs/site/modules/starter-core.md) links its current dependency, auto-configuration conditions, and tests. Check outbound headers against a local test server and test timeouts/retry behavior before applying the settings to a verification partner.
