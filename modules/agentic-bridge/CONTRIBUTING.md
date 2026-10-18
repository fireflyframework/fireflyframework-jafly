# Contributing to fireflyframework-agentic-bridge

Thank you for your interest in contributing! This module follows the
conventions used across the Firefly Framework — please skim
[`fireflyframework-parent`](https://github.com/fireflyframework/fireflyframework-parent)
for the project-wide setup, then read on for module-specific guidelines.

---

## Development setup

```bash
git clone https://github.com/fireflyframework/fireflyframework-agentic-bridge.git
cd fireflyframework-agentic-bridge
mvn -B install
```

The build expects `fireflyframework-parent` and `fireflyframework-kernel`
to be available in the local Maven repository (cloned as siblings is the
recommended layout).

## Code style

- Java 21+ language level (Java 25 default; build with `-Pjava21` for
  compatibility).
- 4-space indentation, no tabs.
- Final fields where possible; Lombok's `@Slf4j` for logger declarations
  is acceptable but not required.
- Public APIs are documented with Javadoc; internal classes prefer
  package-private visibility.

## Tests

- Every public API class has at least one unit test.
- Reactive code is verified with `StepVerifier`.
- HTTP transport tests use WireMock; queue publishers use Testcontainers
  in nightly runs (out of scope for the PR gate).

Run the complete suite with:

```bash
mvn -B test
```

## Pull request checklist

- [ ] `mvn -B verify` passes locally.
- [ ] New configuration properties are documented in `docs/CONFIGURATION.md`.
- [ ] New public classes carry the Apache 2.0 license header.
- [ ] CHANGELOG entry is updated.
- [ ] At least one test exercises the new behaviour.

## Reporting bugs

Please file issues at
<https://github.com/fireflyframework/fireflyframework-agentic-bridge/issues>
including:

- Bridge version and Spring Boot version.
- A minimal reproducer.
- The full stack trace, including the `AgenticErrorContext` if available.

## License

By contributing you agree that your contribution will be released under
the project's [Apache 2.0 license](LICENSE).
