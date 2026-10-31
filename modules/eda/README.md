![Jafly. Common EDA Library](../../docs/site/assets/modules/eda.svg)

# Common EDA Library

Publish and consume events through a common envelope and transport-independent APIs.

Start with EventPublisher and the listener annotations; add a transport adapter for broker or database delivery. Choose publisher and consumer names explicitly when several transports coexist.

[Module guide](../../docs/site/modules/eda.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

Once a submitted application must notify another service, define an application.submitted event and select its transport. The first-service tutorial has no event publisher, so adding delivery is a separate change with its own failure behavior.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-eda</artifactId>
    <version>26.10.22</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../../docs/site/maven.md#constructor-injection).

This optional extension publishes an application ID after the onboarding service has accepted a submission. The Java class is an API fragment: register it in a Spring application with EDA enabled, inject it into application code, and subscribe to or compose its returned `Mono`. It does not save the application or make the save and publication atomic.

```java
import lombok.RequiredArgsConstructor;
import java.util.UUID;
import org.fireflyframework.eda.annotation.PublisherType;
import org.fireflyframework.eda.publisher.EventPublisherFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ApplicationEventPublisher {
    private final EventPublisherFactory publishers;



    public Mono<Void> submitted(UUID applicationId) {
        return publishers.getPublisher(PublisherType.APPLICATION_EVENT)
                .publish(applicationId, "application-submitted");
    }
}
```

`APPLICATION_EVENT` selects process-local Spring events explicitly. It does not reach another service or survive process failure. A distributed integration selects and configures a broker provider instead; define the event schema, destination, duplicate handling, and recovery before connecting business effects. The publishing API also offers `@PublishResult` for Spring-managed methods and `@EventListener` for consumers. An `EventEnvelope` is a Java record; access its payload with `payload()`.


## Configuration

The `firefly.eda` namespace is bound by [EdaProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda/src/main/java/org/fireflyframework/eda/properties/EdaProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.eda.enabled` | `boolean` | `true` |
| `firefly.eda.default-publisher-type` | `PublisherType` | `PublisherType.AUTO` |
| `firefly.eda.default-connection-id` | `String` | `"default"` |
| `firefly.eda.default-destination` | `String` | `"events"` |
| `firefly.eda.default-serialization-format` | `String` | `"json"` |

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`EventPublisher`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda/src/main/java/org/fireflyframework/eda/publisher/EventPublisher.java) | Unified interface for publishing events to different messaging systems. |
| [`EventEnvelope`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda/src/main/java/org/fireflyframework/eda/event/EventEnvelope.java) | Unified event envelope for both publishing and consuming events. |
| [`EventPublisherFactory`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda/src/main/java/org/fireflyframework/eda/publisher/EventPublisherFactory.java) | Factory for creating and managing event publishers. |
| [`EdaProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda/src/main/java/org/fireflyframework/eda/properties/EdaProperties.java) | Configuration properties for the Event-Driven Architecture library. |
| [`EventConsumer`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/eda/src/main/java/org/fireflyframework/eda/consumer/EventConsumer.java) | Interface for consuming events from messaging systems. |

## Continue the application

Use the [EDA - Kafka guide](../../docs/site/modules/eda-kafka.md) for the next part of this integration.

## Explicit Avro type contracts

Avro decoding requires the caller's exact `Class<T>` contract. The serializer derives a bounded field and schema graph from that class, including typed collections, arrays, enums, nullable records, and finite field-level `@Union` declarations. Each operation owns its data model, reader, writer, and buffers. It does not change Avro's global validator, trust properties, or package policy. This also supports Avro 1.12.2's class-loading hardening without granting wire-provided class names authority.

Per-operation models register Avro's standard UUID, big-decimal, duration, and date/time logical conversions. No custom conversion registration is offered.

The default serializer refuses nonempty decoding to `Object.class`. An explicitly constructed serializer may bind that request to one concrete root; this is equivalent to supplying that root class directly, not payload type detection:

```java
AvroMessageSerializer serializer = new AvroMessageSerializer(
        new AvroTypeBindings(Set.of(), ApplicationSubmitted.class));
```

Generated `SpecificRecord` types must expose their declared public `SCHEMA$`. A nested schema branch whose Java class cannot be recovered from declared fields (for example, an `Object` union field) requires an exact additional class token in `schemaTypes`. At most 16 additional tokens are accepted. These bindings do not configure Spring automatically and do not replace application authorization or an explicit consumer decoder registry. Generic records retain encoding support for bounded schemas without Java class hints; generic-record decoding still requires a separate schema contract and is unsupported here.

Supported collection declarations are typed `Collection`, `List`, `Set`, `ArrayList`, `HashSet`, `LinkedHashSet`, and string-keyed `Map`, `HashMap`, or `LinkedHashMap`. Values use standard JDK collections; decoding allocates a compatible canonical collection. Raw or unresolved generics, plain `Object` fields without a finite union, non-string map keys, unknown subclasses, custom containers, executable encodings/stringable metadata, explicit schema overrides, and unsupported platform types are refused. Schema Java hints must match their already-approved type at that location. No class is loaded from a schema hint, event header, or payload name.

A contract is limited to 256 distinct declared types, 32 levels of graph depth, 4,096 fields, and 16 union branches. Value traversal is limited to 32 levels and 100,000 visits. Each operation also admits at most 32 levels and 100,000 recursive SDK read calls before descending into a value. SDK optimized scalar and primitive-array I/O retains its inherited checks; the final full-value traversal limit still applies. Terminating nullable recursive records and shared acyclic children are supported; actual object cycles are refused. Avro's inherited binary parsing and collection allocation checks remain in use. These bounds do not establish constant-memory parsing for arbitrary inputs. Null payloads, empty input, and raw byte-array pass-through retain their existing behavior. JSON and Protobuf selection and contracts are unchanged.

To include the optional serializer tests in a normal repository validation, enable their existing conditions:

```bash
./mvnw -pl :fireflyframework-eda -am verify \
  -Dtest.serialization.avro=true -Dtest.serialization.protobuf=true
```

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-eda -am verify
./mvnw -pl :fireflyframework-eda -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/eda.md).
