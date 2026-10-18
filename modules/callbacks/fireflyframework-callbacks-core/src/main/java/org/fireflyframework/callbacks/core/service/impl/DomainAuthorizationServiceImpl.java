/*
 * Copyright 2024-2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fireflyframework.callbacks.core.service.impl;

import org.fireflyframework.callbacks.core.filters.FilterRequest;
import org.fireflyframework.callbacks.core.filters.FilterUtils;
import org.fireflyframework.callbacks.core.filters.PaginationResponse;
import org.fireflyframework.callbacks.core.mapper.AuthorizedDomainMapper;
import org.fireflyframework.callbacks.core.service.DomainAuthorizationService;
import org.fireflyframework.callbacks.interfaces.dto.AuthorizedDomainDTO;
import org.fireflyframework.callbacks.models.entity.AuthorizedDomain;
import org.fireflyframework.callbacks.models.repository.AuthorizedDomainRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/**
 * Implementation of domain authorization service.
 * Validates callback URLs against authorized domains with security checks.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DomainAuthorizationServiceImpl implements DomainAuthorizationService {

    private final AuthorizedDomainRepository domainRepository;
    private final AuthorizedDomainMapper domainMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<PaginationResponse<AuthorizedDomainDTO>> filterDomains(FilterRequest<AuthorizedDomainDTO> filterRequest) {
        return FilterUtils
                .createFilter(AuthorizedDomain.class, domainMapper::toDto)
                .withRepository(domainRepository)
                .filter(filterRequest);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Cacheable(value = "authorizedDomains", key = "#url")
    public Mono<Boolean> isAuthorized(String url) {
        try {
            URI uri = URI.create(url);
            String domain = extractDomain(uri);
            String path = uri.getPath();
            
            log.debug("Checking authorization for URL: {}, domain: {}, path: {}", url, domain, path);

            return domainRepository.findByDomain(domain)
                    .filter(AuthorizedDomain::getActive)
                    .filter(AuthorizedDomain::getVerified)
                    .filter(d -> !isExpired(d))
                    .filter(d -> isPathAllowed(d, path))
                    .filter(d -> isHttpsValid(d, uri))
                    .hasElement()
                    .doOnNext(authorized -> {
                        if (authorized) {
                            log.debug("URL authorized: {}", url);
                        } else {
                            log.warn("URL not authorized: {}", url);
                        }
                    });
        } catch (Exception e) {
            log.error("Error validating URL authorization: {}", url, e);
            return Mono.just(false);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<AuthorizedDomainDTO> create(AuthorizedDomainDTO dto) {
        return Mono.just(dto)
                .map(domainMapper::toEntity)
                .doOnNext(entity -> {
                    // Don't set ID - let R2DBC know this is an INSERT
                    entity.setCreatedAt(Instant.now());
                    entity.setUpdatedAt(Instant.now());
                    entity.setVerified(dto.getVerified() != null ? dto.getVerified() : false);
                    entity.setActive(dto.getActive() != null ? dto.getActive() : true);
                    entity.setTotalCallbacks(0L);
                    entity.setTotalFailed(0L);
                })
                .flatMap(domainRepository::save)
                .map(domainMapper::toDto)
                .doOnSuccess(created -> log.info("Created authorized domain: id={}, domain={}", 
                        created.getId(), created.getDomain()))
                .doOnError(error -> log.error("Error creating domain: {}", dto.getDomain(), error));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @CacheEvict(value = "authorizedDomains", key = "#dto.domain")
    public Mono<AuthorizedDomainDTO> update(UUID id, AuthorizedDomainDTO dto) {
        return domainRepository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException("Domain not found: " + id)))
                .flatMap(existing -> {
                    // Update fields
                    existing.setDomain(dto.getDomain());
                    existing.setOrganization(dto.getOrganization());
                    existing.setContactEmail(dto.getContactEmail());
                    existing.setActive(dto.getActive());
                    existing.setAllowedPaths(dto.getAllowedPaths());
                    existing.setMaxCallbacksPerMinute(dto.getMaxCallbacksPerMinute());
                    existing.setIpWhitelist(dto.getIpWhitelist());
                    existing.setRequireHttps(dto.getRequireHttps());
                    existing.setNotes(dto.getNotes());
                    existing.setExpiresAt(dto.getExpiresAt());
                    existing.setUpdatedAt(Instant.now());
                    
                    return domainRepository.save(existing);
                })
                .map(domainMapper::toDto)
                .doOnSuccess(updated -> log.info("Updated authorized domain: id={}, domain={}", 
                        id, updated.getDomain()))
                .doOnError(error -> log.error("Error updating domain: {}", id, error));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @CacheEvict(value = "authorizedDomains", key = "#domain")
    public Mono<Void> delete(UUID id, String domain) {
        return domainRepository.deleteById(id)
                .doOnSuccess(v -> log.info("Deleted authorized domain: id={}, domain={}", id, domain))
                .doOnError(error -> log.error("Error deleting domain: {}", id, error));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @CacheEvict(value = "authorizedDomains", key = "#domain")
    public Mono<Void> verifyDomain(String domain, String verificationMethod) {
        return domainRepository.findByDomain(domain)
                .switchIfEmpty(Mono.error(new RuntimeException("Domain not found: " + domain)))
                .flatMap(entity -> {
                    entity.setVerified(true);
                    entity.setVerificationMethod(verificationMethod);
                    entity.setVerifiedAt(Instant.now());
                    entity.setUpdatedAt(Instant.now());
                    return domainRepository.save(entity);
                })
                .then()
                .doOnSuccess(v -> log.info("Verified domain: {}, method: {}", domain, verificationMethod))
                .doOnError(error -> log.error("Error verifying domain: {}", domain, error));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<Void> recordCallback(String url, boolean success) {
        try {
            URI uri = URI.create(url);
            String domain = extractDomain(uri);
            
            return domainRepository.findByDomain(domain)
                    .flatMap(entity -> {
                        entity.setLastCallbackAt(Instant.now());
                        entity.setTotalCallbacks(entity.getTotalCallbacks() + 1);
                        if (!success) {
                            entity.setTotalFailed(entity.getTotalFailed() + 1);
                        }
                        entity.setUpdatedAt(Instant.now());
                        return domainRepository.save(entity);
                    })
                    .then()
                    .doOnSuccess(v -> log.debug("Recorded callback for domain: {}, success: {}", domain, success))
                    .doOnError(error -> log.error("Error recording callback for domain: {}", domain, error));
        } catch (Exception e) {
            log.error("Error parsing URL for callback recording: {}", url, e);
            return Mono.empty();
        }
    }

    // ==================== Private Helper Methods ====================

    /**
     * Extracts domain from URI (host + port if non-standard).
     */
    private String extractDomain(URI uri) {
        String host = uri.getHost();
        int port = uri.getPort();
        
        // Include port if non-standard
        if (port != -1 && port != 80 && port != 443) {
            return host + ":" + port;
        }
        
        return host;
    }

    /**
     * Checks if domain has expired.
     */
    private boolean isExpired(AuthorizedDomain domain) {
        if (domain.getExpiresAt() == null) {
            return false;
        }
        return Instant.now().isAfter(domain.getExpiresAt());
    }

    /**
     * Checks if path is allowed for domain.
     */
    private boolean isPathAllowed(AuthorizedDomain domain, String path) {
        String[] allowedPaths = domain.getAllowedPaths();
        
        // If no path restrictions, allow all
        if (allowedPaths == null || allowedPaths.length == 0) {
            return true;
        }
        
        // Check if path matches any allowed pattern
        return Arrays.stream(allowedPaths)
                .anyMatch(allowedPath -> {
                    // Support wildcards
                    if (allowedPath.endsWith("*")) {
                        String prefix = allowedPath.substring(0, allowedPath.length() - 1);
                        return path.startsWith(prefix);
                    }
                    return path.equals(allowedPath);
                });
    }

    /**
     * Checks HTTPS requirement.
     */
    private boolean isHttpsValid(AuthorizedDomain domain, URI uri) {
        if (domain.getRequireHttps() == null || !domain.getRequireHttps()) {
            return true;
        }
        return "https".equalsIgnoreCase(uri.getScheme());
    }
}
