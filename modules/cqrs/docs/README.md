# CQRS guides

Use commands for application changes and queries for reads. The customer onboarding example shows both paths with a repository owned by the application.

- [Submit and read an application](QUICKSTART.md).
- [Bus and handler architecture](ARCHITECTURE.md).
- [Configuration reference](CONFIGURATION.md).
- [Developer reference](DEVELOPER_GUIDE.md).
- [Event-sourcing integration](EVENT_SOURCING_INTEGRATION.md).

[The CQRS component guide](../../../docs/site/modules/cqrs.md) contains the runnable source and explains validation, authorization, optional cache behavior, and EDA wiring. A protected operation needs an actual authorization policy and trusted identity context; the tutorial permits execution by default.
