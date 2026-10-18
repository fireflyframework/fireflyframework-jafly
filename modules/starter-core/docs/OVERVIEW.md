# Core starter overview

Use the core starter for the shared HTTP-client and operational configuration included by its Maven dependency set. It can support a verification-partner call in the onboarding service; it does not define the application's submission, review, or persistence behavior.

- [Quick start](QUICKSTART.md) explains when to add the starter.
- [API contracts](API.md) covers WebClientTemplate and the configuration classes.
- [Configuration](CONFIGURATION.md) covers client settings and header propagation.
- [Integration choices](INTEGRATIONS.md) connects HTTP calls, events, coordination, notifications, and telemetry to their components.

[The component guide](../../../docs/site/modules/starter-core.md) lists current dependencies, public extension points, source files, and tests. [The domain starter](../../../docs/site/modules/starter-domain.md) is a separate dependency bundle for services needing its included domain facilities.
