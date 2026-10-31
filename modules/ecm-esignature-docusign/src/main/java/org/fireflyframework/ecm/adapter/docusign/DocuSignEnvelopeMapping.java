/* Copyright 2024-2026 Firefly Software Foundation. */
package org.fireflyframework.ecm.adapter.docusign;

import com.docusign.esign.model.CarbonCopy;
import com.docusign.esign.model.Envelope;
import com.docusign.esign.model.EnvelopeDefinition;
import com.docusign.esign.model.InitialHere;
import com.docusign.esign.model.Recipients;
import com.docusign.esign.model.RecipientEmailNotification;
import com.docusign.esign.model.Signer;
import com.docusign.esign.model.SignHere;
import com.docusign.esign.model.Tabs;
import com.docusign.esign.model.Text;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.fireflyframework.ecm.domain.enums.esignature.*;
import org.fireflyframework.ecm.domain.model.esignature.SignatureEnvelope;
import org.fireflyframework.ecm.domain.model.esignature.SignatureField;
import org.fireflyframework.ecm.domain.model.esignature.SignatureRequest;

/** Explicit supported subset; fields without an agreed provider contract fail before any provider call. */
final class DocuSignEnvelopeMapping {
    static final int MAX_DOCUMENTS = 10;
    static final int MAX_DOCUMENT_BYTES = 10 * 1024 * 1024;
    static final int MAX_TOTAL_BYTES = 20 * 1024 * 1024;
    private static final int MAX_RECIPIENTS = 100;
    private static final int MAX_TABS = 1000;
    private static final Set<String> LANGUAGES = Set.of("ar", "hy", "id", "ms", "bg", "cs", "zh_CN", "zh_TW",
            "hr", "da", "nl", "en", "en_GB", "et", "fa", "fi", "fr", "fr_CA", "de", "el", "he", "hi", "hu",
            "it", "ja", "ko", "lv", "lt", "no", "pl", "pt", "pt_BR", "ro", "ru", "sr", "sk", "sl", "es",
            "es_MX", "sv", "th", "tr", "uk", "vi");

    private DocuSignEnvelopeMapping() { }

    static DocuSignAdapterException invalid() { return failure(DocuSignAdapterException.Code.INVALID_REQUEST); }
    static DocuSignAdapterException unsupported() { return failure(DocuSignAdapterException.Code.UNSUPPORTED); }
    static DocuSignAdapterException response() { return failure(DocuSignAdapterException.Code.PROVIDER_RESPONSE); }
    private static DocuSignAdapterException failure(DocuSignAdapterException.Code code) {
        return new DocuSignAdapterException(code, DocuSignAdapterException.Operation.ENVELOPE);
    }
    static String text(String value, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum
                || value.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t')) throw invalid();
        return value;
    }
    static String externalId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,128}")) throw invalid();
        return value;
    }
    static void metadataUpdate(SignatureEnvelope envelope) {
        if (envelope == null) throw invalid();
        text(envelope.getTitle(), 100);
        if (envelope.getDescription() != null) text(envelope.getDescription(), 10000);
        if (envelope.getProvider() != null && envelope.getProvider() != SignatureProvider.DOCUSIGN) throw invalid();
        if (envelope.getStatus() != null || envelope.getCreatedBy() != null || envelope.getCreatedAt() != null
                || envelope.getModifiedAt() != null || envelope.getSentBy() != null || envelope.getSentByActorId() != null
                || envelope.getSentAt() != null || envelope.getCompletedAt() != null
                || envelope.getDocumentIds() != null || envelope.getSignatureRequests() != null
                || envelope.getMetadata() != null || envelope.getPriority() != null || envelope.getRequireAllSigners() != null
                || envelope.getSigningOrder() != null || envelope.getReminderSettings() != null
                || envelope.getAuthRequirements() != null || envelope.getCallbackUrl() != null
                || envelope.getExpiresAt() != null || envelope.getVoided() != null || envelope.getVoidReason() != null) throw unsupported();
    }
    static EnvelopeDefinition draft(SignatureEnvelope envelope) {
        if (envelope == null) throw invalid();
        text(envelope.getTitle(), 100);
        if (envelope.getDescription() != null) text(envelope.getDescription(), 10000);
        if (envelope.getStatus() != null && envelope.getStatus() != EnvelopeStatus.DRAFT) throw invalid();
        if (envelope.getProvider() != null && envelope.getProvider() != SignatureProvider.DOCUSIGN) throw invalid();
        if (present(envelope.getMetadata()) || envelope.getPriority() != null || Boolean.FALSE.equals(envelope.getRequireAllSigners())
                || envelope.getReminderSettings() != null || envelope.getAuthRequirements() != null
                || envelope.getCallbackUrl() != null || envelope.getExpiresAt() != null || envelope.getVoided() != null
                || envelope.getVoidReason() != null || envelope.getExternalEnvelopeId() != null
                || envelope.getSentBy() != null || envelope.getSentByActorId() != null || envelope.getSentAt() != null
                || envelope.getCompletedAt() != null || envelope.getCreatedAt() != null || envelope.getModifiedAt() != null) throw unsupported();
        var ids = envelope.getDocumentIds();
        if (ids == null || ids.isEmpty() || ids.size() > MAX_DOCUMENTS || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) throw invalid();
        var requests = envelope.getSignatureRequests();
        if (requests == null || requests.isEmpty() || requests.size() > MAX_RECIPIENTS || requests.stream().anyMatch(Objects::isNull)) throw invalid();
        var definition = new EnvelopeDefinition();
        definition.setEmailSubject(envelope.getTitle()); definition.setEmailBlurb(envelope.getDescription()); definition.setStatus("created");
        var signers = new ArrayList<Signer>(); var copies = new ArrayList<CarbonCopy>();
        var identities = new HashSet<UUID>(); var orders = new HashSet<Integer>(); int tabs = 0;
        for (int i = 0; i < requests.size(); i++) {
            SignatureRequest request = requests.get(i);
            if (request.getId() != null && !identities.add(request.getId())) throw invalid();
            if (request.getEnvelopeId() != null && !request.getEnvelopeId().equals(envelope.getId())) throw invalid();
            String name = text(request.getSignerName(), 100); String email = text(request.getSignerEmail(), 254);
            if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw invalid();
            if (request.getRequestType() == null) throw invalid();
            if (request.getStatus() != null && request.getStatus() != SignatureRequestStatus.CREATED
                    && request.getStatus() != SignatureRequestStatus.PENDING) throw invalid();
            if (request.getCreatedAt() != null || request.getSentAt() != null || request.getViewedAt() != null
                    || request.getSignedAt() != null || request.getCompletedAt() != null || request.getExpiresAt() != null
                    || request.getDeclineReason() != null || request.getSignerIpAddress() != null || request.getSignerUserAgent() != null
                    || request.getExternalSignerId() != null || request.getSigningUrl() != null || present(request.getMetadata())
                    || request.getTimeZone() != null
                    || (request.getAuthMethod() != null && request.getAuthMethod() != AuthenticationMethod.EMAIL
                        && request.getAuthMethod() != AuthenticationMethod.NONE)) throw unsupported();
            if (request.getSignerRole() != null) text(request.getSignerRole(), 100);
            RecipientEmailNotification notification = notification(request, envelope);
            int order = request.getSigningOrder() == null ? 1 : request.getSigningOrder();
            if (order < 1 || order > MAX_RECIPIENTS) throw invalid();
            if (envelope.getSigningOrder() == SigningOrder.SEQUENTIAL && !orders.add(order)) throw invalid();
            if (envelope.getSigningOrder() == SigningOrder.PARALLEL && order != 1) throw invalid();
            if ((envelope.getSigningOrder() == SigningOrder.SEQUENTIAL || envelope.getSigningOrder() == SigningOrder.HYBRID)
                    && request.getSigningOrder() == null) throw invalid();
            String recipient = Integer.toString(i + 1);
            if (request.getRequestType() == SignatureRequestType.CARBON_COPY) {
                if (present(request.getSignatureFields()) || Boolean.TRUE.equals(request.getRequired())) throw unsupported();
                var copy = new CarbonCopy(); copy.setRecipientId(recipient); copy.setName(name); copy.setEmail(email);
                copy.setRoutingOrder(Integer.toString(order)); copy.setRoleName(request.getSignerRole());
                copy.setEmailNotification(notification); copies.add(copy);
            } else {
                if (request.getRequestType() != SignatureRequestType.SIGNATURE && request.getRequestType() != SignatureRequestType.INITIAL
                        && request.getRequestType() != SignatureRequestType.FORM_FILL) throw unsupported();
                if (Boolean.FALSE.equals(request.getRequired())) throw unsupported();
                var fields = request.getSignatureFields();
                if (fields == null || fields.isEmpty() || fields.stream().anyMatch(Objects::isNull)) throw invalid();
                tabs = Math.addExact(tabs, fields.size()); if (tabs > MAX_TABS) throw invalid();
                if (request.getRequestType() == SignatureRequestType.FORM_FILL && fields.stream().anyMatch(f -> f.getFieldType() != FieldType.TEXT)) throw invalid();
                if (request.getRequestType() == SignatureRequestType.SIGNATURE && fields.stream().noneMatch(f -> f.getFieldType() == FieldType.SIGNATURE)) throw invalid();
                if (request.getRequestType() == SignatureRequestType.INITIAL && fields.stream().noneMatch(f -> f.getFieldType() == FieldType.INITIAL)) throw invalid();
                var signer = new Signer(); signer.setRecipientId(recipient); signer.setName(name); signer.setEmail(email);
                signer.setRoutingOrder(Integer.toString(order)); signer.setRoleName(request.getSignerRole());
                signer.setTabs(tabs(fields, ids, recipient)); signer.setEmailNotification(notification); signers.add(signer);
            }
        }
        if (signers.isEmpty()) throw invalid();
        var recipients = new Recipients(); recipients.setSigners(signers); if (!copies.isEmpty()) recipients.setCarbonCopies(copies);
        definition.setRecipients(recipients); return definition;
    }
    private static RecipientEmailNotification notification(SignatureRequest request, SignatureEnvelope envelope) {
        if (request.getCustomMessage() == null && request.getLanguage() == null) return null;
        if (request.getCustomMessage() != null && request.getCustomMessage().length() > 10000) throw invalid();
        if (request.getLanguage() != null && !LANGUAGES.contains(request.getLanguage())) throw unsupported();
        var notification = new RecipientEmailNotification();
        notification.setEmailSubject(envelope.getTitle());
        notification.setEmailBody(request.getCustomMessage() != null ? request.getCustomMessage() : envelope.getDescription());
        notification.setSupportedLanguage(request.getLanguage());
        return notification;
    }
    private static Tabs tabs(List<SignatureField> fields, List<UUID> documents, String recipient) {
        var tabs = new Tabs(); var signatures = new ArrayList<SignHere>(); var initials = new ArrayList<InitialHere>(); var texts = new ArrayList<Text>();
        for (var field : fields) {
            if (field.getFieldType() == null || field.getDocumentId() == null || !documents.contains(field.getDocumentId())) throw invalid();
            if (field.getPageNumber() == null || field.getPageNumber() < 1) throw invalid();
            if (field.getFontSettings() != null || present(field.getMetadata()) || field.getExternalFieldId() != null
                    || field.getFieldGroup() != null || Boolean.TRUE.equals(field.getConditional()) || field.getVisibilityCondition() != null) throw unsupported();
            String document = Integer.toString(documents.indexOf(field.getDocumentId()) + 1);
            String x = point(field.getXPosition(), false), y = point(field.getYPosition(), false);
            String width = field.getWidth() == null ? null : dimension(field.getWidth());
            String height = field.getHeight() == null ? null : dimension(field.getHeight());
            if (field.getTabOrder() != null && field.getTabOrder() < 1) throw invalid();
            switch (field.getFieldType()) {
                case SIGNATURE -> {
                    if (field.getValue() != null || field.getDefaultValue() != null || field.getValidationPattern() != null
                            || Boolean.TRUE.equals(field.getReadOnly())) throw unsupported();
                    var tab = new SignHere(); tab.setDocumentId(document); tab.setRecipientId(recipient);
                    position(field, x, y, width, height, tab::setPageNumber, tab::setXPosition, tab::setYPosition,
                            tab::setWidth, tab::setHeight, tab::setTabLabel, tab::setTooltip, tab::setTabOrder);
                    if (field.getRequired() != null) tab.setOptional(Boolean.toString(!field.getRequired())); signatures.add(tab);
                }
                case INITIAL -> {
                    if (field.getValue() != null || field.getDefaultValue() != null || field.getValidationPattern() != null
                            || Boolean.TRUE.equals(field.getReadOnly())) throw unsupported();
                    var tab = new InitialHere(); tab.setDocumentId(document); tab.setRecipientId(recipient);
                    position(field, x, y, width, height, tab::setPageNumber, tab::setXPosition, tab::setYPosition,
                            tab::setWidth, tab::setHeight, tab::setTabLabel, tab::setTooltip, tab::setTabOrder);
                    if (field.getRequired() != null) tab.setOptional(Boolean.toString(!field.getRequired())); initials.add(tab);
                }
                case TEXT -> {
                    if (field.getValue() != null && field.getDefaultValue() != null && !field.getValue().equals(field.getDefaultValue())) throw invalid();
                    if ((field.getValue() != null && field.getValue().length() > 10000)
                            || (field.getDefaultValue() != null && field.getDefaultValue().length() > 10000)
                            || (field.getValidationPattern() != null && field.getValidationPattern().length() > 1000)) throw invalid();
                    var tab = new Text(); tab.setDocumentId(document); tab.setRecipientId(recipient);
                    position(field, x, y, width, height, tab::setPageNumber, tab::setXPosition, tab::setYPosition,
                            tab::setWidth, tab::setHeight, tab::setTabLabel, tab::setTooltip, tab::setTabOrder);
                    tab.setValue(field.getValue() != null ? field.getValue() : field.getDefaultValue());
                    if (field.getRequired() != null) tab.setRequired(field.getRequired().toString());
                    if (field.getReadOnly() != null) tab.setLocked(field.getReadOnly().toString());
                    tab.setValidationPattern(field.getValidationPattern()); texts.add(tab);
                }
                default -> throw unsupported();
            }
        }
        if (!signatures.isEmpty()) tabs.setSignHereTabs(signatures);
        if (!initials.isEmpty()) tabs.setInitialHereTabs(initials);
        if (!texts.isEmpty()) tabs.setTextTabs(texts);
        return tabs;
    }
    private static String dimension(Double value) {
        if (!Double.isFinite(value) || value <= 0 || value > Integer.MAX_VALUE) throw invalid();
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
    private static String point(Double value, boolean positive) {
        if (value == null || !Double.isFinite(value) || value < (positive ? 1 : 0)
                || value != Math.rint(value) || value > Integer.MAX_VALUE) throw invalid();
        return Integer.toString(value.intValue());
    }
    private static void position(SignatureField field, String x, String y, String width, String height,
                                 Consumer<String> page, Consumer<String> horizontal, Consumer<String> vertical,
                                 Consumer<String> widthSetter, Consumer<String> heightSetter, Consumer<String> label,
                                 Consumer<String> tooltip, Consumer<String> order) {
        page.accept(field.getPageNumber().toString()); horizontal.accept(x); vertical.accept(y);
        if (width != null) widthSetter.accept(width); if (height != null) heightSetter.accept(height);
        if (field.getName() != null) label.accept(text(field.getName(), 100));
        if (field.getTooltip() != null) tooltip.accept(text(field.getTooltip(), 1000));
        if (field.getTabOrder() != null) order.accept(field.getTabOrder().toString());
    }
    static SignatureEnvelope fromProvider(UUID id, String expectedExternalId, Envelope envelope) {
        if (envelope == null || !expectedExternalId.equals(envelope.getEnvelopeId())) throw response();
        return SignatureEnvelope.builder().id(id).externalEnvelopeId(expectedExternalId).provider(SignatureProvider.DOCUSIGN)
                .title(envelope.getEmailSubject()).description(envelope.getEmailBlurb()).status(status(envelope.getStatus()))
                .createdAt(instant(envelope.getCreatedDateTime())).modifiedAt(instant(envelope.getLastModifiedDateTime()))
                .sentAt(instant(envelope.getSentDateTime())).completedAt(instant(envelope.getCompletedDateTime()))
                .expiresAt(instant(envelope.getExpireDateTime())).voided("voided".equals(envelope.getStatus()))
                .voidReason(envelope.getVoidedReason()).build();
    }
    static EnvelopeStatus status(String value) {
        if (value == null) throw response();
        return switch (value) {
            case "created" -> EnvelopeStatus.DRAFT;
            case "sent" -> EnvelopeStatus.SENT;
            case "delivered", "signed" -> EnvelopeStatus.IN_PROGRESS;
            case "completed" -> EnvelopeStatus.COMPLETED;
            case "declined" -> EnvelopeStatus.DECLINED;
            case "voided" -> EnvelopeStatus.VOIDED;
            default -> throw response();
        };
    }
    static String status(EnvelopeStatus value) {
        if (value == null) throw invalid();
        return switch (value) {
            case DRAFT -> "created";
            case SENT -> "sent";
            case COMPLETED -> "completed";
            case DECLINED -> "declined";
            case VOIDED -> "voided";
            default -> throw unsupported();
        };
    }
    static Instant instant(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Instant.parse(value); } catch (RuntimeException error) { throw response(); }
    }
    private static boolean present(Collection<?> value) { return value != null && !value.isEmpty(); }
    private static boolean present(Map<?, ?> value) { return value != null && !value.isEmpty(); }
}
