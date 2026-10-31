/*
 * Copyright 2024-2026 Firefly Software Foundation.
 */
package org.fireflyframework.ecm.domain.model.esignature;

import java.net.URI;
import org.fireflyframework.ecm.domain.enums.esignature.SignatureProvider;

/** Nonsecret configured account identity; does not establish caller or provider authorization. */
public record SignatureEnvelopeAccountBinding(SignatureProvider provider, String accountId, String endpoint) {
    public SignatureEnvelopeAccountBinding {
        try {
            if (provider == null || accountId == null || accountId.isBlank() || accountId.length() > 256
                    || !accountId.equals(accountId.strip()) || accountId.codePoints().anyMatch(Character::isISOControl)
                    || endpoint == null || !endpoint.equals(endpoint.strip())) throw invalid();
            URI uri = URI.create(endpoint);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535) throw invalid();
        } catch (RuntimeException error) { throw invalid(); }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid signature envelope account binding");
    }
}
