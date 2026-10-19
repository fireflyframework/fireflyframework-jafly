![Jafly. Validators](../../docs/site/assets/modules/validators.svg)

# Validators

Apply Jakarta Bean Validation constraints to financial, identity, contact, and date fields.

Annotate a request or domain field with the relevant Valid constraint and invoke Jakarta validation through the host application. Constraint options and null-handling are defined by each annotation and validator pair; inspect the corresponding tests for accepted and rejected values.

[Module guide](../../docs/site/modules/validators.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

The tutorial validates name and email with Jakarta constraints. Later onboarding steps may collect phone numbers or national identifiers; apply the matching framework constraints only to fields the product actually needs.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-validators</artifactId>
    <version>26.10.19</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

Annotate your model with the constraints you need, then trigger validation via `@Valid` (Spring will do this automatically for `@RequestBody` arguments) or programmatically through a `Validator`.

```java
import org.fireflyframework.annotations.*;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record TransferRequest(

    @NotBlank
    @ValidIban
    String recipientIban,

    @ValidBic
    String recipientBic,

    @ValidAmount(currency = "EUR", min = 0.01)
    BigDecimal amount,

    @ValidCurrencyCode(europeanOnly = true)
    String currency,

    @ValidPhoneNumber
    String contactPhone
) {}
```

```java
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/transfers")
class TransferController {

    @PostMapping
    String create(@Valid @RequestBody TransferRequest request) {
        // reaches here only if all constraints pass;
        // otherwise Spring returns 400 with the constraint messages
        return "accepted";
    }
}
```

Programmatic validation (e.g. In a service or test) works the same way:

```java
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolation;
import java.util.Set;

Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

Set<ConstraintViolation<TransferRequest>> violations = validator.validate(request);
violations.forEach(v -> System.out.println(v.getPropertyPath() + " " + v.getMessage()));
```

## Configuration

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`ValidIban`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/validators/src/main/java/org/fireflyframework/annotations/ValidIban.java) | Defines `message`, `groups`, `payload` operations. |
| [`ValidAmount`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/validators/src/main/java/org/fireflyframework/annotations/ValidAmount.java) | Defines `message`, `groups`, `payload`, `currency` operations. |
| [`ValidPasswordStrength`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/validators/src/main/java/org/fireflyframework/annotations/ValidPasswordStrength.java) | Defines `message`, `groups`, `payload`, `minLength` operations. |
| [`IbanValidator`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/validators/src/main/java/org/fireflyframework/validators/IbanValidator.java) | Validator for International Bank Account Numbers (IBAN). |
| [`ValidAccountNumber`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/validators/src/main/java/org/fireflyframework/annotations/ValidAccountNumber.java) | Defines `message`, `groups`, `payload`, `countryCode` operations. |

## Continue the application

Use the [CQRS guide](../../docs/site/modules/cqrs.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-validators -am verify
./mvnw -pl :fireflyframework-validators -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/validators.md).
