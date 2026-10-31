/*
 * Copyright 2024-2026 Firefly Software Foundation.
 */
package org.fireflyframework.ecm.domain.model.esignature;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Exact caller-supplied content identity; does not establish ownership or authority over a version. */
public final class SignatureEnvelopeDocument {
    private static final int MAX_CONTENT_BYTES = 10 * 1024 * 1024;
    private final UUID documentId;
    private final UUID documentVersionId;
    private final String fileName;
    private final String mediaType;
    private final long contentLength;
    private final String sha256;
    private final byte[] content;

    public SignatureEnvelopeDocument(UUID documentId, UUID documentVersionId, String fileName, String mediaType,
                                     long contentLength, String sha256, byte[] content) {
        if (documentId == null || documentVersionId == null || !text(fileName, 255) || !text(mediaType, 255)
                || content == null || content.length == 0 || content.length > MAX_CONTENT_BYTES
                || sha256 == null || !sha256.matches("[0-9a-f]{64}")) throw invalid();
        byte[] captured = content.clone();
        if (contentLength != captured.length) throw invalid();
        try {
            if (!sha256.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(captured)))) throw invalid();
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException("SHA-256 is unavailable", error); }
        this.documentId = documentId; this.documentVersionId = documentVersionId; this.fileName = fileName;
        this.mediaType = mediaType; this.contentLength = contentLength; this.sha256 = sha256; this.content = captured;
    }

    private static boolean text(String value, int maximum) {
        return value != null && !value.isBlank() && value.length() <= maximum && value.equals(value.strip())
                && value.codePoints().noneMatch(Character::isISOControl);
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid signature envelope document snapshot"); }
    public UUID documentId() { return documentId; }
    public UUID documentVersionId() { return documentVersionId; }
    public String fileName() { return fileName; }
    public String mediaType() { return mediaType; }
    public long contentLength() { return contentLength; }
    public String sha256() { return sha256; }
    public byte[] content() { return content.clone(); }
}
