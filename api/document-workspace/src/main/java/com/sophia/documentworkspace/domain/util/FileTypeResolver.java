package com.sophia.documentworkspace.domain.util;

import com.sophia.documentworkspace.domain.entity.enums.ArtifactType;
import org.apache.tika.Tika;

import java.io.IOException;
import java.io.InputStream;

/**
 * Maps uploaded content to a concrete file {@link ArtifactType} using the
 * declared content type, falling back to Tika magic-byte detection.
 */
public final class FileTypeResolver {

    private static final Tika TIKA = new Tika();

    private FileTypeResolver() {
    }

    public static String detectMimeType(InputStream content, String filename, String declaredType) {
        if (declaredType != null && !declaredType.isBlank()
                && !"application/octet-stream".equals(declaredType)) {
            return declaredType;
        }
        try {
            return TIKA.detect(content, filename == null ? "" : filename);
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }

    public static ArtifactType fromMimeType(String mimeType) {
        if (mimeType == null) {
            return ArtifactType.BINARY_FILE;
        }
        String mime = mimeType.toLowerCase();
        if (mime.startsWith("image/")) {
            return ArtifactType.IMAGE;
        }
        if (mime.startsWith("video/")) {
            return ArtifactType.VIDEO;
        }
        if (mime.startsWith("audio/")) {
            return ArtifactType.AUDIO;
        }
        return switch (mime) {
            case "application/pdf" -> ArtifactType.PDF;
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                 "application/msword" -> ArtifactType.DOCX;
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                 "application/vnd.ms-excel" -> ArtifactType.XLSX;
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                 "application/vnd.ms-powerpoint" -> ArtifactType.PPTX;
            case "application/zip", "application/x-zip-compressed" -> ArtifactType.ZIP;
            case "text/plain", "text/markdown", "text/csv" -> ArtifactType.TXT;
            default -> ArtifactType.BINARY_FILE;
        };
    }
}
