/*
 * Copyright 2024 Firefly Software Foundation.
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
package org.fireflyframework.ecm.config;

import org.fireflyframework.ecm.adapter.AdapterRegistry;
import org.fireflyframework.ecm.adapter.AdapterSelector;
import org.fireflyframework.ecm.adapter.local.LocalDocumentSearchAdapter;
import org.fireflyframework.ecm.adapter.local.LocalPermissionAdapter;
import org.fireflyframework.ecm.adapter.noop.NoOpAdapterFactory;
import org.fireflyframework.ecm.port.document.*;
import org.fireflyframework.ecm.port.folder.*;
import org.fireflyframework.ecm.port.security.*;
import org.fireflyframework.ecm.port.audit.*;
import org.fireflyframework.ecm.port.esignature.*;
import org.fireflyframework.ecm.port.idp.*;
import org.fireflyframework.ecm.service.EcmPortProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for the Firefly ECM (Enterprise Content Management) system.
 *
 * <p>This configuration class automatically sets up the ECM infrastructure based on
 * application properties and feature flags. It handles:</p>
 * <ul>
 *   <li>Adapter discovery and registration</li>
 *   <li>Port provider configuration</li>
 *   <li>Conditional bean creation based on feature flags</li>
 *   <li>Explicit bean registration for ECM infrastructure components</li>
 * </ul>
 *
 * <p>The auto-configuration is activated when the property {@code firefly.ecm.enabled}
 * is set to {@code true} (which is the default). Individual features can be disabled
 * using the {@code firefly.ecm.features.*} properties.</p>
 *
 * <p>Example configuration to enable only basic document management:</p>
 * <pre>
 * firefly:
 *   ecm:
 *     enabled: true
 *     adapter-type: s3
 *     features:
 *       document-management: true
 *       content-storage: true
 *       versioning: false
 *       esignature: false
 * </pre>
 *
 * @author Firefly Software Foundation.
 * @version 1.0
 * @since 1.0
 * @see EcmProperties
 * @see EcmPortProvider
 * @see AdapterSelector
 * @see org.springframework.boot.autoconfigure.AutoConfiguration
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(EcmProperties.class)
@ConditionalOnProperty(prefix = "firefly.ecm", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EcmAutoConfiguration {

    /**
     * Configures the adapter registry for discovering and managing ECM adapters.
     *
     * <p>The adapter registry automatically discovers all ECM adapters in the Spring
     * application context and provides efficient access to them based on type or
     * interface requirements.</p>
     *
     * @param applicationContext the Spring application context for bean discovery
     * @return a configured AdapterRegistry instance
     * @see AdapterRegistry
     */
    @Bean
    @ConditionalOnMissingBean
    public AdapterRegistry adapterRegistry(ApplicationContext applicationContext) {
        return new AdapterRegistry(applicationContext);
    }

    /**
     * Configures the adapter selector for choosing appropriate ECM adapters.
     *
     * <p>The adapter selector implements the adapter selection logic, providing
     * intelligent fallback mechanisms and validation capabilities.</p>
     *
     * @param adapterRegistry the registry containing available adapters
     * @return a configured AdapterSelector instance
     * @see AdapterSelector
     */
    @Bean
    @ConditionalOnMissingBean
    public AdapterSelector adapterSelector(AdapterRegistry adapterRegistry) {
        return new AdapterSelector(adapterRegistry);
    }

    /**
     * Configures the no-op adapter factory for creating fallback adapter implementations.
     *
     * <p>The no-op adapter factory provides a centralized way to create no-op adapters
     * that serve as fallbacks when no real adapter implementations are available.</p>
     *
     * @return a configured NoOpAdapterFactory instance
     * @see NoOpAdapterFactory
     */
    @Bean
    @ConditionalOnMissingBean
    public NoOpAdapterFactory noOpAdapterFactory() {
        return new NoOpAdapterFactory();
    }

    /**
     * Configures the central ECM port provider that manages adapter selection and port provisioning.
     *
     * <p>The port provider acts as a factory for ECM ports, selecting the appropriate
     * adapter implementation based on the configured adapter type and feature flags.
     * It serves as the main entry point for accessing ECM functionality.</p>
     *
     * @param adapterSelector the adapter selector for choosing appropriate adapters
     * @param ecmProperties the ECM configuration properties
     * @return a configured EcmPortProvider instance
     * @see EcmPortProvider
     * @see AdapterSelector
     */
    @Bean
    @ConditionalOnMissingBean
    public EcmPortProvider ecmPortProvider(AdapterSelector adapterSelector, EcmProperties ecmProperties) {
        log.info("Configuring ECM Port Provider with adapter type: {}", ecmProperties.getAdapterType());
        return new EcmPortProvider(adapterSelector, ecmProperties);
    }

    /**
     * Configures the local in-memory document search adapter.
     *
     * <p>This bean is only created when the {@code firefly.ecm.search.enabled} property
     * is set to {@code true}. It provides an in-memory search implementation for
     * development and testing purposes.</p>
     *
     * @return a configured LocalDocumentSearchAdapter instance
     * @see LocalDocumentSearchAdapter
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "firefly.ecm.search.enabled", havingValue = "true", matchIfMissing = false)
    public LocalDocumentSearchAdapter localDocumentSearchAdapter() {
        return new LocalDocumentSearchAdapter();
    }

    /**
     * Configures the local in-memory permission adapter.
     *
     * <p>This bean is only created when the {@code firefly.ecm.permissions.enabled} property
     * is set to {@code true}. It provides an in-memory permission management implementation
     * for development and testing purposes.</p>
     *
     * @return a configured LocalPermissionAdapter instance
     * @see LocalPermissionAdapter
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "firefly.ecm.permissions.enabled", havingValue = "true", matchIfMissing = false)
    public LocalPermissionAdapter localPermissionAdapter() {
        return new LocalPermissionAdapter();
    }

    /**
     * Configures the document port for basic document CRUD operations.
     *
     * <p>This bean is only created when the {@code document-management} feature is enabled.
     * The document port provides core functionality for creating, reading, updating,
     * and deleting documents in the ECM system.</p>
     *
     * <p>If no suitable adapter is found, a no-op adapter is provided as a fallback
     * to prevent application startup failures while clearly indicating that the
     * functionality is not available.</p>
     *
     * @param portProvider the ECM port provider
     * @param noOpAdapterFactory factory for creating no-op adapters
     * @return a DocumentPort implementation (real adapter or no-op fallback)
     * @see DocumentPort
     */
    @ConditionalOnMissingBean
    @Bean
    @ConditionalOnProperty(prefix = "firefly.ecm.features", name = "document-management", havingValue = "true", matchIfMissing = true)
    public DocumentPort documentPort(EcmPortProvider portProvider, NoOpAdapterFactory noOpAdapterFactory) {
        return portProvider.getDocumentPort()
            .orElseGet(noOpAdapterFactory::createDocumentPort);
    }

    /**
     * Configures the document content port for binary content operations.
     *
     * <p>This bean is only created when the {@code content-storage} feature is enabled.
     * The document content port handles the storage and retrieval of document binary
     * content, including upload, download, and streaming operations.</p>
     *
     * <p>If no suitable adapter is found, a no-op adapter is provided as a fallback
     * to prevent application startup failures while clearly indicating that the
     * functionality is not available.</p>
     *
     * @param portProvider the ECM port provider
     * @param noOpAdapterFactory factory for creating no-op adapters
     * @return a DocumentContentPort implementation (real adapter or no-op fallback)
     * @see DocumentContentPort
     */
    @ConditionalOnMissingBean
    @Bean
    @ConditionalOnProperty(prefix = "firefly.ecm.features", name = "content-storage", havingValue = "true", matchIfMissing = true)
    public DocumentContentPort documentContentPort(EcmPortProvider portProvider, NoOpAdapterFactory noOpAdapterFactory) {
        return portProvider.getDocumentContentPort()
            .orElseGet(noOpAdapterFactory::createDocumentContentPort);
    }
    
    /**
     * Configures the document version port for document versioning capabilities.
     *
     * <p>This bean is only created when the {@code versioning} feature is enabled.
     * The document version port provides functionality for managing document versions,
     * including creating new versions, retrieving version history, and comparing versions.</p>
     *
     * <p>If no suitable adapter is found, a no-op adapter is provided as a fallback
     * to prevent application startup failures while clearly indicating that the
     * functionality is not available.</p>
     *
     * @param portProvider the ECM port provider
     * @param noOpAdapterFactory factory for creating no-op adapters
     * @return a DocumentVersionPort implementation (real adapter or no-op fallback)
     * @see DocumentVersionPort
     */
    @ConditionalOnMissingBean
    @Bean
    @ConditionalOnProperty(prefix = "firefly.ecm.features", name = "versioning", havingValue = "true", matchIfMissing = true)
    public DocumentVersionPort documentVersionPort(EcmPortProvider portProvider, NoOpAdapterFactory noOpAdapterFactory) {
        return portProvider.getDocumentVersionPort()
            .orElseGet(noOpAdapterFactory::createDocumentVersionPort);
    }

    /**
     * Configures the document search port for search and query capabilities.
     *
     * <p>This bean is only created when the {@code search} feature is enabled.
     * The document search port provides functionality for searching documents by
     * metadata, content, and other criteria using various search strategies.</p>
     *
     * <p>If no suitable adapter is found, a no-op adapter is provided as a fallback
     * to prevent application startup failures while clearly indicating that the
     * functionality is not available.</p>
     *
     * @param portProvider the ECM port provider
     * @param noOpAdapterFactory factory for creating no-op adapters
     * @return a DocumentSearchPort implementation (real adapter or no-op fallback)
     * @see DocumentSearchPort
     */
    @ConditionalOnMissingBean
    @Bean
    @ConditionalOnProperty(prefix = "firefly.ecm.features", name = "search", havingValue = "true", matchIfMissing = true)
    public DocumentSearchPort documentSearchPort(EcmPortProvider portProvider, NoOpAdapterFactory noOpAdapterFactory) {
        return portProvider.getDocumentSearchPort()
            .orElseGet(noOpAdapterFactory::createDocumentSearchPort);
    }

    /**
     * Configures the folder port for basic folder management operations.
     *
     * <p>This bean is only created when the {@code folder-management} feature is enabled.
     * The folder port provides functionality for creating, reading, updating, and
     * deleting folders in the ECM system.</p>
     *
     * <p>If no suitable adapter is found, a no-op adapter is provided as a fallback
     * to prevent application startup failures while clearly indicating that the
     * functionality is not available.</p>
     *
     * @param portProvider the ECM port provider
     * @param noOpAdapterFactory factory for creating no-op adapters
     * @return a FolderPort implementation (real adapter or no-op fallback)
     * @see FolderPort
     */
    @ConditionalOnMissingBean
    @Bean
    @ConditionalOnProperty(prefix = "firefly.ecm.features", name = "folder-management", havingValue = "true", matchIfMissing = true)
    public FolderPort folderPort(EcmPortProvider portProvider, NoOpAdapterFactory noOpAdapterFactory) {
        return portProvider.getFolderPort()
            .orElseGet(noOpAdapterFactory::createFolderPort);
    }

    /**
     * Configures the folder hierarchy port for hierarchical folder operations.
     *
     * <p>This bean is only created when the {@code folder-hierarchy} feature is enabled.
     * The folder hierarchy port provides functionality for managing hierarchical
     * folder structures, including tree navigation, path resolution, and parent-child relationships.</p>
     *
     * <p>If no suitable adapter is found, a no-op adapter is provided as a fallback
     * to prevent application startup failures while clearly indicating that the
     * functionality is not available.</p>
     *
     * @param portProvider the ECM port provider
     * @param noOpAdapterFactory factory for creating no-op adapters
     * @return a FolderHierarchyPort implementation (real adapter or no-op fallback)
     * @see FolderHierarchyPort
     */
    @ConditionalOnMissingBean
    @Bean
    @ConditionalOnProperty(prefix = "firefly.ecm.features", name = "folder-hierarchy", havingValue = "true", matchIfMissing = true)
    public FolderHierarchyPort folderHierarchyPort(EcmPortProvider portProvider, NoOpAdapterFactory noOpAdapterFactory) {
        return portProvider.getFolderHierarchyPort()
            .orElseGet(noOpAdapterFactory::createFolderHierarchyPort);
    }

    /**
     * Configures the permission port for access control and permission management.
     *
     * <p>This bean is only created when the {@code permissions} feature is enabled.
     * The permission port provides functionality for managing user and group permissions
     * on documents and folders, including granting, revoking, and checking access rights.</p>
     *
     * <p>If no suitable adapter is found, a no-op adapter is provided as a fallback
     * to prevent application startup failures while clearly indicating that the
     * functionality is not available.</p>
     *
     * @param portProvider the ECM port provider
     * @param noOpAdapterFactory factory for creating no-op adapters
     * @return a PermissionPort implementation (real adapter or no-op fallback)
     * @see PermissionPort
     */
    @ConditionalOnMissingBean
    @Bean
    @ConditionalOnProperty(prefix = "firefly.ecm.features", name = "permissions", havingValue = "true", matchIfMissing = true)
    public PermissionPort permissionPort(EcmPortProvider portProvider, NoOpAdapterFactory noOpAdapterFactory) {
        return portProvider.getPermissionPort()
            .orElseGet(() -> {
                log.warn("=========================================================================");
                log.warn("ECM PermissionPort is using NoOp adapter — ALL permission checks will");
                log.warn("DENY by default. Configure a real adapter for production use.");
                log.warn("=========================================================================");
                return noOpAdapterFactory.createPermissionPort();
            });
    }

    /**
     * Configures the document security port for document-level security operations.
     *
     * <p>This bean is only created when the {@code security} feature is enabled.
     * The document security port provides functionality for applying security policies,
     * encryption, digital rights management, and other security-related operations on documents.</p>
     *
     * <p>If no suitable adapter is found, a no-op adapter is provided as a fallback
     * to prevent application startup failures while clearly indicating that the
     * functionality is not available.</p>
     *
     * @param portProvider the ECM port provider
     * @param noOpAdapterFactory factory for creating no-op adapters
     * @return a DocumentSecurityPort implementation (real adapter or no-op fallback)
     * @see DocumentSecurityPort
     */
    @ConditionalOnMissingBean
    @Bean
    @ConditionalOnProperty(prefix = "firefly.ecm.features", name = "security", havingValue = "true", matchIfMissing = true)
    public DocumentSecurityPort documentSecurityPort(EcmPortProvider portProvider, NoOpAdapterFactory noOpAdapterFactory) {
        return portProvider.getDocumentSecurityPort()
            .orElseGet(() -> {
                log.warn("=========================================================================");
                log.warn("ECM DocumentSecurityPort is using NoOp adapter — ALL security operations");
                log.warn("will be denied/no-op. Configure a real adapter for production use.");
                log.warn("=========================================================================");
                return noOpAdapterFactory.createDocumentSecurityPort();
            });
    }

    /**
     * Configures the audit port for audit trail and compliance logging.
     *
     * <p>This bean is only created when the {@code auditing} feature is enabled.
     * The audit port provides functionality for logging user actions, system events,
     * and maintaining compliance records for regulatory requirements.</p>
     *
     * <p>If no suitable adapter is found, a no-op adapter is provided as a fallback
     * to prevent application startup failures while clearly indicating that the
     * functionality is not available.</p>
     *
     * @param portProvider the ECM port provider
     * @param noOpAdapterFactory factory for creating no-op adapters
     * @return an AuditPort implementation (real adapter or no-op fallback)
     * @see AuditPort
     */
    @ConditionalOnMissingBean
    @Bean
    @ConditionalOnProperty(prefix = "firefly.ecm.features", name = "auditing", havingValue = "true", matchIfMissing = true)
    public AuditPort auditPort(EcmPortProvider portProvider, NoOpAdapterFactory noOpAdapterFactory) {
        return portProvider.getAuditPort()
            .orElseGet(noOpAdapterFactory::createAuditPort);
    }

    /**
     * Configures the signature envelope port for eSignature envelope management.
     *
     * <p>This bean is only created when the {@code esignature} feature is explicitly enabled.
     * The signature envelope port provides functionality for creating, managing, and
     * tracking signature envelopes that contain documents requiring electronic signatures.</p>
     *
     * <p>If no suitable adapter is found, a no-op adapter is provided as a fallback
     * to prevent application startup failures while clearly indicating that the
     * functionality is not available.</p>
     *
     * @param portProvider the ECM port provider
     * @param noOpAdapterFactory factory for creating no-op adapters
     * @return a SignatureEnvelopePort implementation (real adapter or no-op fallback)
     * @see SignatureEnvelopePort
     */
    @ConditionalOnMissingBean
    @Bean
    @ConditionalOnProperty(prefix = "firefly.ecm.features", name = "esignature", havingValue = "true", matchIfMissing = false)
    public SignatureEnvelopePort signatureEnvelopePort(EcmPortProvider portProvider, NoOpAdapterFactory noOpAdapterFactory) {
        return portProvider.getSignatureEnvelopePort()
            .orElseGet(noOpAdapterFactory::createSignatureEnvelopePort);
    }

    /**
     * Configures the signature request port for managing individual signature requests.
     *
     * <p>This bean is only created when the {@code esignature} feature is explicitly enabled.
     * The signature request port provides functionality for creating, sending, and
     * tracking individual signature requests within signature envelopes.</p>
     *
     * <p>If no suitable adapter is found, a no-op adapter is provided as a fallback
     * to prevent application startup failures while clearly indicating that the
     * functionality is not available.</p>
     *
     * @param portProvider the ECM port provider
     * @param noOpAdapterFactory factory for creating no-op adapters
     * @return a SignatureRequestPort implementation (real adapter or no-op fallback)
     * @see SignatureRequestPort
     */
    @ConditionalOnMissingBean
    @Bean
    @ConditionalOnProperty(prefix = "firefly.ecm.features", name = "esignature", havingValue = "true", matchIfMissing = false)
    public SignatureRequestPort signatureRequestPort(EcmPortProvider portProvider, NoOpAdapterFactory noOpAdapterFactory) {
        return portProvider.getSignatureRequestPort()
            .orElseGet(noOpAdapterFactory::createSignatureRequestPort);
    }

    /**
     * Configures the signature validation port for validating electronic signatures.
     *
     * <p>This bean is only created when the {@code esignature} feature is explicitly enabled.
     * The signature validation port provides functionality for validating the authenticity,
     * integrity, and legal compliance of electronic signatures.</p>
     *
     * <p>If no suitable adapter is found, a no-op adapter is provided as a fallback
     * to prevent application startup failures while clearly indicating that the
     * functionality is not available.</p>
     *
     * @param portProvider the ECM port provider
     * @param noOpAdapterFactory factory for creating no-op adapters
     * @return a SignatureValidationPort implementation (real adapter or no-op fallback)
     * @see SignatureValidationPort
     */
    @ConditionalOnMissingBean
    @Bean
    @ConditionalOnProperty(prefix = "firefly.ecm.features", name = "esignature", havingValue = "true", matchIfMissing = false)
    public SignatureValidationPort signatureValidationPort(EcmPortProvider portProvider, NoOpAdapterFactory noOpAdapterFactory) {
        return portProvider.getSignatureValidationPort()
            .orElseGet(noOpAdapterFactory::createSignatureValidationPort);
    }

    /**
     * Configures the signature proof port for generating signature proof and certificates.
     *
     * <p>This bean is only created when the {@code esignature} feature is explicitly enabled.
     * The signature proof port provides functionality for generating tamper-evident
     * proof documents and certificates that demonstrate the validity of electronic signatures.</p>
     *
     * <p>If no suitable adapter is found, a no-op adapter is provided as a fallback
     * to prevent application startup failures while clearly indicating that the
     * functionality is not available.</p>
     *
     * @param portProvider the ECM port provider
     * @param noOpAdapterFactory factory for creating no-op adapters
     * @return a SignatureProofPort implementation (real adapter or no-op fallback)
     * @see SignatureProofPort
     */
    @ConditionalOnMissingBean
    @Bean
    @ConditionalOnProperty(prefix = "firefly.ecm.features", name = "esignature", havingValue = "true", matchIfMissing = false)
    public SignatureProofPort signatureProofPort(EcmPortProvider portProvider, NoOpAdapterFactory noOpAdapterFactory) {
        return portProvider.getSignatureProofPort()
            .orElseGet(noOpAdapterFactory::createSignatureProofPort);
    }

    /**
     * Configures the document extraction port for text extraction and OCR operations.
     *
     * <p>This bean is only created when the {@code idp} feature is explicitly enabled.
     * The document extraction port provides functionality for extracting text and other content
     * from documents using various IDP technologies such as OCR, handwriting recognition,
     * and advanced text analysis.</p>
     *
     * <p>If no suitable adapter is found, a no-op adapter is provided as a fallback
     * to prevent application startup failures while clearly indicating that the
     * functionality is not available.</p>
     *
     * @param portProvider the ECM port provider
     * @param noOpAdapterFactory factory for creating no-op adapters
     * @return a DocumentExtractionPort implementation (real adapter or no-op fallback)
     * @see DocumentExtractionPort
     */
    @ConditionalOnMissingBean
    @Bean
    @ConditionalOnProperty(prefix = "firefly.ecm.features", name = "idp", havingValue = "true", matchIfMissing = false)
    public DocumentExtractionPort documentExtractionPort(EcmPortProvider portProvider, NoOpAdapterFactory noOpAdapterFactory) {
        return portProvider.getDocumentExtractionPort()
            .orElseGet(noOpAdapterFactory::createDocumentExtractionPort);
    }

    /**
     * Configures the document classification port for document classification and categorization.
     *
     * <p>This bean is only created when the {@code idp} feature is explicitly enabled.
     * The document classification port provides functionality for automatically classifying
     * and categorizing documents using various IDP technologies such as machine learning
     * models, rule-based systems, and template matching.</p>
     *
     * <p>If no suitable adapter is found, a no-op adapter is provided as a fallback
     * to prevent application startup failures while clearly indicating that the
     * functionality is not available.</p>
     *
     * @param portProvider the ECM port provider
     * @param noOpAdapterFactory factory for creating no-op adapters
     * @return a DocumentClassificationPort implementation (real adapter or no-op fallback)
     * @see DocumentClassificationPort
     */
    @ConditionalOnMissingBean
    @Bean
    @ConditionalOnProperty(prefix = "firefly.ecm.features", name = "idp", havingValue = "true", matchIfMissing = false)
    public DocumentClassificationPort documentClassificationPort(EcmPortProvider portProvider, NoOpAdapterFactory noOpAdapterFactory) {
        return portProvider.getDocumentClassificationPort()
            .orElseGet(noOpAdapterFactory::createDocumentClassificationPort);
    }

    /**
     * Configures the document validation port for document validation and verification.
     *
     * <p>This bean is only created when the {@code idp} feature is explicitly enabled.
     * The document validation port provides functionality for validating documents and
     * extracted data using various validation techniques including business rule validation,
     * format verification, data consistency checks, and compliance validation.</p>
     *
     * <p>If no suitable adapter is found, a no-op adapter is provided as a fallback
     * to prevent application startup failures while clearly indicating that the
     * functionality is not available.</p>
     *
     * @param portProvider the ECM port provider
     * @param noOpAdapterFactory factory for creating no-op adapters
     * @return a DocumentValidationPort implementation (real adapter or no-op fallback)
     * @see DocumentValidationPort
     */
    @ConditionalOnMissingBean
    @Bean
    @ConditionalOnProperty(prefix = "firefly.ecm.features", name = "idp", havingValue = "true", matchIfMissing = false)
    public DocumentValidationPort documentValidationPort(EcmPortProvider portProvider, NoOpAdapterFactory noOpAdapterFactory) {
        return portProvider.getDocumentValidationPort()
            .orElseGet(noOpAdapterFactory::createDocumentValidationPort);
    }

    /**
     * Configures the data extraction port for structured and semi-structured data extraction.
     *
     * <p>This bean is only created when the {@code idp} feature is explicitly enabled.
     * The data extraction port provides functionality for extracting structured data from
     * documents including forms, tables, key-value pairs, and other organized data elements.</p>
     *
     * <p>If no suitable adapter is found, a no-op adapter is provided as a fallback
     * to prevent application startup failures while clearly indicating that the
     * functionality is not available.</p>
     *
     * @param portProvider the ECM port provider
     * @param noOpAdapterFactory factory for creating no-op adapters
     * @return a DataExtractionPort implementation (real adapter or no-op fallback)
     * @see DataExtractionPort
     */
    @ConditionalOnMissingBean
    @Bean
    @ConditionalOnProperty(prefix = "firefly.ecm.features", name = "idp", havingValue = "true", matchIfMissing = false)
    public DataExtractionPort dataExtractionPort(EcmPortProvider portProvider, NoOpAdapterFactory noOpAdapterFactory) {
        return portProvider.getDataExtractionPort()
            .orElseGet(noOpAdapterFactory::createDataExtractionPort);
    }
}
