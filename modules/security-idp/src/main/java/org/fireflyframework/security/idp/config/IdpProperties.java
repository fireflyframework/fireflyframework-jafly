/*
 * Copyright (c) 2024 Firefly Software Foundation.
 */
package org.fireflyframework.security.idp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@Data
@Validated
@ConfigurationProperties(prefix = "firefly.security.idp")
public class IdpProperties {

    @NotBlank(message = "IDP provider must be specified (keycloak, cognito, or internal-db)")
    private String provider;
}
