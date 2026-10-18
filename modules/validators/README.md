# Firefly Framework - Validators

[![CI](https://github.com/fireflyframework/fireflyframework-validators/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-validators/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> A library of financial and domain-specific Jakarta Bean Validation constraints for Spring Boot — drop-in `@Valid*` annotations for IBANs, BICs, credit cards, currencies, tax/national IDs, amounts, phone numbers, passwords and more.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Constraint Reference](#constraint-reference)
- [Configuration](#configuration)
- [How It Works](#how-it-works)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

Firefly Framework Validators provides a curated set of [Jakarta Bean Validation](https://beanvalidation.org/) constraint annotations and their `ConstraintValidator` implementations, purpose-built for financial services and enterprise applications. Instead of hand-rolling regex checks for IBANs, credit cards or tax IDs in every service, you annotate your DTOs, entities and method parameters with intention-revealing constraints such as `@ValidIban`, `@ValidCreditCard` and `@ValidTaxId`, and let the Bean Validation provider enforce them.

Each constraint is a standard `@Constraint`-meta-annotated annotation paired with a dedicated validator, so the library composes naturally with everything Bean Validation already gives you: it works with `@Valid` on Spring MVC / WebFlux request bodies, with method-level validation, with validation groups and `payload`, and alongside the built-in constraints (`@NotNull`, `@Size`, `@Pattern`, …). Many constraints go beyond simple format checks — for example, `@ValidIban` verifies the country-specific length and the ISO 7064 mod-97 check digits, `@ValidCreditCard` applies card-network pattern matching plus the Luhn algorithm, and `@ValidPasswordStrength` enforces configurable composition rules and rejects common weak passwords.

This module is part of the [Firefly Framework](https://github.com/fireflyframework), a reactive, Spring Boot–based microservices platform. It sits in the framework's shared-library layer (alongside the kernel and utility modules): it is a plain, dependency-light JAR with no runtime infrastructure of its own, so any Firefly service — or any standalone Spring Boot application — can depend on it. It builds on `spring-boot-starter-validation` (Hibernate Validator) and reuses utilities from Apache Commons Validator.

## Features

- **Banking & payments constraints**
  - `@ValidIban` — IBAN format, country-specific length, and ISO 7064 mod-97 check-digit verification.
  - `@ValidBic` — BIC / SWIFT code structure validation (8 or 11 characters).
  - `@ValidAccountNumber` — bank account number validation (default country `GB`).
  - `@ValidSortCode` — UK-style bank sort code validation.
  - `@ValidCreditCard` — card-network pattern matching (Visa, MasterCard, Amex, Discover, Diners Club, JCB) plus Luhn checksum, optionally restricted to a set of accepted `CardType`s.
  - `@ValidCVV` — 3/4-digit card verification value, with `ANY`, `STANDARD` (3-digit) or `AMEX` (4-digit) card-type modes.
- **Money & rates constraints**
  - `@ValidAmount` — monetary amount validation with currency-aware decimal places, `min`/`max` bounds and an `allowZero` flag.
  - `@ValidCurrencyCode` — ISO 4217 currency code validation, optionally restricted to European or Euro-only currencies.
  - `@ValidInterestRate` — percentage range (`min`/`max`), decimal-place and `allowZero` validation.
- **Identity & document constraints**
  - `@ValidTaxId` — tax identification numbers (US TIN, Mexico RFC, Argentina CUIT, Spain NIF, and more), optionally pinned to a country.
  - `@ValidNationalId` — national identity documents (Spanish DNI/NIE, Brazilian CPF, US SSN, UK NINO, and more), optionally pinned to a country.
  - `@ValidPhoneNumber` — international phone numbers in E.164 format, optionally country-specific.
- **Security constraints**
  - `@ValidPasswordStrength` — configurable length, character-class requirements, minimum number of character types, and rejection of common/weak passwords.
  - `@ValidPIN` — numeric PIN validation with configurable length range and rejection of common/weak PINs.
- **Date & time constraints**
  - `@ValidDate` — date string validation against a configurable pattern, with `min`/`max` bounds and `allowFuture` / `allowPast` flags.
  - `@ValidDateTime` — same, for date-time strings.
- **Standards-based & composable** — every constraint is a real Jakarta `@Constraint`, so it works with `@Valid`, validation groups, `payload`, method validation, and custom `message` overrides.
- **Zero configuration** — no auto-configuration, no properties; validators are discovered automatically by the Bean Validation provider on the classpath.

## Requirements

- Java 21+ (Java 25 recommended)
- Spring Boot 3.x
- Maven 3.9+
- A Jakarta Bean Validation provider on the classpath (Hibernate Validator, brought in transitively by `spring-boot-starter-validation`)

## Installation

Add the dependency. The version is managed by the Firefly BOM / parent POM, so you normally omit `<version>`:

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-validators</artifactId>
    <!-- version managed by the Firefly BOM / fireflyframework-parent -->
</dependency>
```

If you are not inheriting the Firefly parent / importing the BOM, pin the version explicitly:

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-validators</artifactId>
    <version>26.05.08</version>
</dependency>
```

> This module depends on `spring-boot-starter-validation` (Hibernate Validator) and `commons-validator`. In a Spring Boot application those validation beans are already wired up, so the constraints work out of the box on `@Valid`-annotated request bodies and method parameters.

## Quick Start

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

Programmatic validation (e.g. in a service or test) works the same way:

```java
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolation;
import java.util.Set;

Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

Set<ConstraintViolation<TransferRequest>> violations = validator.validate(request);
violations.forEach(v -> System.out.println(v.getPropertyPath() + " " + v.getMessage()));
```

## Constraint Reference

All annotations live in `org.fireflyframework.annotations`; their validators live in `org.fireflyframework.validators`. Each annotation supports the standard Bean Validation members (`message`, `groups`, `payload`). Module-specific attributes and their defaults:

| Annotation | Module-specific attributes (defaults) | Notes |
|---|---|---|
| `@ValidIban` | — | Country-specific length + mod-97 check digits |
| `@ValidBic` | — | 8 or 11 character BIC / SWIFT structure |
| `@ValidAccountNumber` | `countryCode = "GB"` | Country-aware account number format |
| `@ValidSortCode` | — | UK bank sort code |
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

## Configuration

This module requires **no external configuration** — there are no `firefly.*` properties, no `@ConfigurationProperties` classes, and no Spring auto-configuration. All behavior is controlled per-field through the annotation attributes listed in the [Constraint Reference](#constraint-reference) above.

Validators are discovered automatically by the Jakarta Bean Validation provider (Hibernate Validator) present on the classpath via `spring-boot-starter-validation`. To customize an error message globally, you can either override `message` on the annotation or supply your own `ValidationMessages.properties` following standard Bean Validation conventions.

## How It Works

Every constraint follows the standard Bean Validation contract:

- The annotation is meta-annotated with `@Constraint(validatedBy = …Validator.class)` and retained at runtime.
- The paired class implements `ConstraintValidator<TheAnnotation, T>`, reading the annotation's attributes in `initialize(...)` and performing the check in `isValid(...)`.

Because of this, the constraints integrate with the wider ecosystem with no extra wiring: Spring's `MethodValidationPostProcessor` and `@Valid` on controller arguments pick them up, validation groups and `payload` work as usual, and violations surface as standard `ConstraintViolation`s (turned into HTTP 400 responses by Spring's default error handling). Notable validators implement real domain logic rather than naive regex — e.g. `IbanValidator` performs the ISO 7064 mod-97 computation against the country-specific length table, `CreditCardValidator` combines network BIN patterns with the Luhn checksum, and `PasswordStrengthValidator` (with `PasswordStrengthUtils`) enforces composition rules and screens against a list of common passwords.

## Documentation

- Firefly Framework organization and module catalog: [github.com/fireflyframework](https://github.com/fireflyframework)
- Jakarta Bean Validation specification: [beanvalidation.org](https://beanvalidation.org/)
- Hibernate Validator reference: [hibernate.org/validator](https://hibernate.org/validator/)

API documentation (Javadoc) is published with each release; every public annotation and validator is documented in source.

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
