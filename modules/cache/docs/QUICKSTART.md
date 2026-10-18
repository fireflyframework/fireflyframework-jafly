# Cache application status

Cache repeated application-status reads after defining expiry and invalidation. The onboarding tutorial reads its map directly and deliberately disables query caching. A cache is not the authoritative application store.

## Start from the application

[The onboarding tutorial](../../../docs/site/getting-started.md) contains the complete Maven project, controller, command/query handlers, and HTTP tests. Use JDK 25 and the repository Maven wrapper. [Installation and version availability](../../../docs/site/installation.md) explains the current build and published versions.

## Choose the integration

Inject `FireflyCacheManager` for the configured cache. The default is local Caffeine; add a provider adapter and its deployment settings when instances need shared state. A cache hit is returned as an `Optional` inside a `Mono`, so a missing key must be handled explicitly.

### Cache a status value

This API class needs the cache dependency and Spring wiring. Call `evict` when the authoritative status changes, and compose each returned `Mono` into the operation. It does not connect to the tutorial repository by itself.

```java
import lombok.RequiredArgsConstructor;
import java.time.Duration;
import java.util.Optional;
import org.fireflyframework.cache.manager.FireflyCacheManager;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ApplicationStatusCache {
    private final FireflyCacheManager cache;



    public Mono<Void> put(String applicationId, String status) {
        return cache.put(applicationId, status, Duration.ofMinutes(5));
    }

    public Mono<Optional<String>> get(String applicationId) {
        return cache.get(applicationId, String.class);
    }

    public Mono<Boolean> evict(String applicationId) {
        return cache.evict(applicationId);
    }
}
```

## Configuration and API

- [Component guide](../../../docs/site/modules/cache.md) for dependency selection, wiring, configuration, and public contracts.
- [Source and tests](..) for the implementation and integration fixtures.

## Check the application

The complete onboarding example runs its own tests from the repository root:

```sh
./mvnw -B -ntp -f docs/examples/getting-started/pom.xml verify
```

That suite checks the tutorial service. Provider/database behavior added later needs its own integration tests and configured dependencies.

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../../../docs/site/maven.md#constructor-injection).
