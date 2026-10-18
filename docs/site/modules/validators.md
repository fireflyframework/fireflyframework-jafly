![Jafly. Validators](../assets/modules/validators.svg)

# Validators

Apply Jakarta Bean Validation constraints to financial, identity, contact, and date fields.

Annotate a request or domain field with the relevant Valid constraint and invoke Jakarta validation through the host application. Constraint options and null-handling are defined by each annotation and validator pair; inspect the corresponding tests for accepted and rejected values.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-validators` | `jar` | [modules/validators](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/validators) |

Part of [Team-wide architecture](index.md#team-wide-architecture).

## In the onboarding service

The tutorial validates name and email with Jakarta constraints. Later onboarding steps may collect phone numbers or national identifiers; apply the matching framework constraints only to fields the product actually needs.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

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

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`ValidIban`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/validators/src/main/java/org/fireflyframework/annotations/ValidIban.java) | Defines `message`, `groups`, `payload` operations. |
| [`ValidAmount`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/validators/src/main/java/org/fireflyframework/annotations/ValidAmount.java) | Defines `message`, `groups`, `payload`, `currency` operations. |
| [`ValidPasswordStrength`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/validators/src/main/java/org/fireflyframework/annotations/ValidPasswordStrength.java) | Defines `message`, `groups`, `payload`, `minLength` operations. |
| [`IbanValidator`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/validators/src/main/java/org/fireflyframework/validators/IbanValidator.java) | Validator for International Bank Account Numbers (IBAN). |
| [`ValidAccountNumber`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/validators/src/main/java/org/fireflyframework/annotations/ValidAccountNumber.java) | Defines `message`, `groups`, `payload`, `countryCode` operations. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-validators -am verify
./mvnw -pl :fireflyframework-validators -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [AccountNumberValidatorTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/validators/src/test/java/org/fireflyframework/validators/AccountNumberValidatorTest.java).
- [AmountValidatorTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/validators/src/test/java/org/fireflyframework/validators/AmountValidatorTest.java).
- [BicValidatorTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/validators/src/test/java/org/fireflyframework/validators/BicValidatorTest.java).
- [CVVValidatorTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/validators/src/test/java/org/fireflyframework/validators/CVVValidatorTest.java).
- [CreditCardValidatorTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/validators/src/test/java/org/fireflyframework/validators/CreditCardValidatorTest.java).
- [CurrencyCodeValidatorTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/validators/src/test/java/org/fireflyframework/validators/CurrencyCodeValidatorTest.java).

## Test fixture

The `shouldConvertFormat` test demonstrates one concrete behavior. Excerpt from [DateTimeValidatorTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/validators/src/test/java/org/fireflyframework/validators/DateTimeValidatorTest.java). The test class supplies its fixtures and imports.

```java
@Test
void shouldConvertFormat() {
    assertThat(validator.convertFormat("2023-01-01 12:30:45", "yyyy-MM-dd HH:mm:ss", "dd/MM/yyyy HH:mm"))
        .isEqualTo("01/01/2023 12:30");
    assertThat(validator.convertFormat("01/01/2023 12:30", "dd/MM/yyyy HH:mm", "yyyy-MM-dd HH:mm:ss"))
        .isEqualTo("2023-01-01 12:30:00");
    assertThat(validator.convertFormat("2023-01-01 12:30:45", "yyyy-MM-dd HH:mm:ss", "MMMM d, yyyy h:mm a"))
        .isEqualTo("January 1, 2023 12:30 PM");

    // Invalid inputs
    assertThat(validator.convertFormat(null, "yyyy-MM-dd HH:mm:ss", "dd/MM/yyyy HH:mm")).isNull();
    assertThat(validator.convertFormat("", "yyyy-MM-dd HH:mm:ss", "dd/MM/yyyy HH:mm")).isNull();
    assertThat(validator.convertFormat("2023-01-01 12:30:45", null, "dd/MM/yyyy HH:mm")).isNull();
    assertThat(validator.convertFormat("2023-01-01 12:30:45", "", "dd/MM/yyyy HH:mm")).isNull();
    assertThat(validator.convertFormat("2023-01-01 12:30:45", "yyyy-MM-dd HH:mm:ss", null)).isNull();
    assertThat(validator.convertFormat("2023-01-01 12:30:45", "yyyy-MM-dd HH:mm:ss", "")).isNull();
    assertThat(validator.convertFormat("invalid-date-time", "yyyy-MM-dd HH:mm:ss", "dd/MM/yyyy HH:mm")).isNull();
}
```

## Detailed reference

### Constraint Reference

All annotations live in `org.fireflyframework.annotations`; their validators live in `org.fireflyframework.validators`. Each annotation supports the standard Bean Validation members (`message`, `groups`, `payload`). Module-specific attributes and their defaults:

| Annotation | Module-specific attributes (defaults) | Notes |
|---|---|---|
| `@ValidIban` |. | Country-specific length + mod-97 check digits |
| `@ValidBic` |. | 8 or 11 character BIC / SWIFT structure |
| `@ValidAccountNumber` | `countryCode = "GB"` | Country-aware account number format |
| `@ValidSortCode` |. | UK bank sort code |
| `@ValidCreditCard` | `types = {}` (any) | Network pattern + Luhn; restrict via `CreditCardValidator.CardType` |
| `@ValidCVV` | `cardType = ANY` | `ANY` / `STANDARD` (3-digit) / `AMEX` (4-digit) |
| `@ValidAmount` | `currency = "EUR"`, `min = 0.0`, `max = Double.MAX_VALUE`, `allowZero = true` | Currency-aware decimal places + bounds |
| `@ValidCurrencyCode` | `europeanOnly = false`, `euroOnly = false` | ISO 4217 code |
| `@ValidInterestRate` | `min = 0.0`, `max = 100.0`, `decimalPlaces = 4`, `allowZero = true` | Percentage range |
| `@ValidTaxId` | `country = ""` (auto-detect) | US TIN, MX RFC, AR CUIT, ES NIF, … |
| `@ValidNationalId` | `country = ""` (auto-detect) | ES DNI/NIE, BR CPF, US SSN, UK NINO, … |
| `@ValidPhoneNumber` | `e164Format = true`, `country = ""` | International / E.164 |
| `@ValidPasswordStrength` | `minLength = 8`, `maxLength = 128`, `requireUppercase = true`, `requireLowercase = true`, `requireDigit = true`, `requireSpecialChar = true`, `minCharTypes = 3`, `rejectCommonPasswords = true` | Composition + weak-password rejection |
| `@ValidPIN` | `minLength = 4`, `maxLength = 6`, `numericOnly = true`, `rejectCommonPINs = true` | Rejects sequences like `1234`, `0000` |
| `@ValidDate` | `pattern = "yyyy-MM-dd"`, `min = ""`, `max = ""`, `allowFuture = true`, `allowPast = true` | Date string |
| `@ValidDateTime` | `pattern = "yyyy-MM-dd HH:mm:ss"`, `min = ""`, `max = ""`, `allowFuture = true`, `allowPast = true` | Date-time string |

Examples of customizing constraint behavior:

```java
// Only accept Visa or MasterCard
@ValidCreditCard(types = { CreditCardValidator.CardType.VISA,
                           CreditCardValidator.CardType.MASTERCARD })
private String cardNumber;

// Amex 4-digit CVV only
@ValidCVV(cardType = ValidCVV.CardType.AMEX)
private String securityCode;

// Stronger password policy with a custom message
@ValidPasswordStrength(minLength = 12, minCharTypes = 4,
                       message = "Password must be at least 12 characters and mix all character types")
private String password;

// Spanish tax ID (NIF), bounded amount, past-only date of birth
@ValidTaxId(country = "ES")  private String nif;
@ValidAmount(min = 0.01, max = 10_000.0) private BigDecimal amount;
@ValidDate(allowFuture = false) private String dateOfBirth;
```

### Configuration

This module requires no external configuration. There are no `firefly.*` properties, no `@ConfigurationProperties` classes, and no Spring auto-configuration. All behavior is controlled per-field through the annotation attributes listed in the [Constraint Reference](#constraint-reference) above.

Validators are discovered automatically by the Jakarta Bean Validation provider (Hibernate Validator) present on the classpath via `spring-boot-starter-validation`. To customize an error message globally, you can either override `message` on the annotation or supply your own `ValidationMessages.properties` following standard Bean Validation conventions.

### How It Works

Every constraint follows the standard Bean Validation contract:

- The annotation is meta-annotated with `@Constraint(validatedBy = …Validator.class)` and retained at runtime.
- The paired class implements `ConstraintValidator<TheAnnotation, T>`, reading the annotation's attributes in `initialize(...)` and performing the check in `isValid(...)`.

Because of this, the constraints integrate with the wider ecosystem with no extra wiring: Spring's `MethodValidationPostProcessor` and `@Valid` on controller arguments pick them up, validation groups and `payload` work as usual, and violations surface as standard `ConstraintViolation`s (turned into HTTP 400 responses by Spring's default error handling). Notable validators implement real domain logic rather than naive regex. E.g. `IbanValidator` performs the ISO 7064 mod-97 computation against the country-specific length table, `CreditCardValidator` combines network BIN patterns with the Luhn checksum, and `PasswordStrengthValidator` (with `PasswordStrengthUtils`) enforces composition rules and screens against a list of common passwords.

## Continue the application

Use the [CQRS guide](cqrs.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/validators/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/validators/src/main/java).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
