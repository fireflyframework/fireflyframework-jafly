# Primary-only operations with explicit failure propagation

This opt-in API is a source addition for a matching future qualified framework release. It is not part of immutable26.10.23. All framework modules/adapters use the same release train; do not mix this cache API into an older reactor or assume publication from source tests.

Use the policy when a caller must distinguish a successful cache miss/conflict from an unavailable backend or failed operation. Existing factory/manager constructors and global defaults remain LEGACY and retain their current fallback/error recovery. Choosing POSTGRES alone does not select this new policy: the legacy smart setting can still enable an L1.

## Construct one explicit primary

The application supplies a configured CacheManagerFactory and the separately configured PostgreSQL cache provider, service namespace and technical connection factory. This fragment is not a complete deployment configuration:

```java
import java.time.Duration;
import org.fireflyframework.cache.core.CacheOperationPolicy;
import org.fireflyframework.cache.core.CacheType;

var cache = factory.createCacheManager(
        "guard-state", CacheType.POSTGRES, "service:guard-state", Duration.ofMinutes(1),
        "Application-owned guarded cache state", "application",
        CacheOperationPolicy.PRIMARY_ONLY_PROPAGATE_ERRORS);
```

This construction selects exactly one explicit provider. AUTO and NOOP refuse. Smart/L1 and fallback settings are not applied to this instance, even if globally enabled. No operation selects a different provider based on isAvailable/getHealth. Capability comes from the provider's explicit policy-aware construction contract; a cache type or health response cannot prove it. Provider construction failures propagate instead of falling back.

PostgreSQL is the recommended and first supported built-in provider. Caffeine, Redis, Hazelcast and JCache remain explicit unsupported choices for this policy until their provider implementations opt in and are qualified. Their legacy operation behavior is unchanged. Old third-party providers inherit a default refusal for the new policy; they must implement it deliberately. A policy-configured adapter without a policy-configured manager is insufficient: old FireflyCacheManagerImpl constructors still select LEGACY. Prefer the policy-aware factory overload.

## Observe operations honestly

| Operation result | Meaning in this policy |
| --- | --- |
| Optional.empty from get | Successful backend query returned no current value. |
| false from exists/evict/putIfAbsent | Successful backend operation reported no match/removal/insertion, as applicable. |
| Completed put/clear | The provider completed the operation and resource-release publisher; no value is expected. |
| Error | Acquisition, SQL/driver, codec, unsupported result or resource-release failed. It is not converted into a miss, false, empty collection, zero size or completed write. |

Absent publishers, empty required value publishers and invalid result shapes refuse. A Void operation legitimately completes without a value; an emitted value is unsupported. PostgreSQL refuses empty acquisition/update-result publishers and invalid affected counts, while retaining its existing atomic insertion and TTL SQL. Null stored values and unsupported/null decodes refuse in this policy instead of being presented as misses. Use the typed get overload for the representation the actual serializer supports. Legacy null/zero/negative explicit-TTL semantics and all existing storage/namespace/role rules are unchanged.

An operation error can follow an applied mutation, for example when resource release fails after a write. Error propagation does not establish rollback or authorize a new unrelated retry. The consumer owns its failure handling and any required operation identity/reconciliation. The framework adds no Identity-specific logic or domain business state.

getHealth/isAvailable remain diagnostics and must not authorize a later data operation. A DOWN diagnostic is explicitly unhealthy, not a successful authority check. Existing diagnostic error fields are not a new general privacy-safe HTTP contract. The serializer logging correction emits fixed metadata/error type and retains the original exception/cause behavior for application handling; it does not promise that arbitrary caller logging is safe.

## Limits and qualification

No distributed freshness, linearizability, quorum, durable commit, revocation, lock or business-transaction guarantee is implied. Backend topology/protocol and the consuming authority design remain decisive. No new SQL/schema/grant, role bootstrap or automatic external service is introduced.

The module tests cover existing APIs and defaults, explicit provider construction, partial ServiceLoader discovery, R2DBC failure and resource-release publishers, and serializer log channels. PostgreSQL topology, roles, restart behavior, and consumer recovery still require validation in the deployment that uses this API. Source qualification does not establish a published release or consumer adoption.
