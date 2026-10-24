package com.sophia.ai.application.service;

import com.sophia.ai.domain.config.ApplicationProperties;
import com.sophia.ai.domain.exception.ApiException;
import com.sophia.ai.domain.exception.BadRequestException;
import com.sophia.ai.domain.exception.ErrorCode;
import com.sophia.ai.infrastructure.llm.ModelGateway.Attachment;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;

/**
 * Checks an uploaded scan, photo or PDF before it goes anywhere near the model. The file type is decided by what the
 * bytes are (their signature), never by the name or the content type the browser claimed, and the size limits are the
 * provider's own, so a file that would be rejected later is refused now with a clear message.
 */
@Component
@RequiredArgsConstructor
public class AttachmentReader {

    private static final java.util.regex.Pattern PAGE_OBJECT = java.util.regex.Pattern.compile("/Type\\s*/Page(?![A-Za-z])");

    private final ApplicationProperties properties;

    public Attachment read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Choose a file to read");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("The file could not be read");
        }
        return of(bytes);
    }

    Attachment of(byte[] bytes) {
        String type = sniff(bytes);
        if (type == null) {
            throw new ApiException(ErrorCode.UNSUPPORTED_FILE, HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Only PNG, JPEG, GIF, WebP images and PDF files can be read");
        }
        long limit = "application/pdf".equals(type) ? properties.ai().upload().maxPdfBytes()
                : properties.ai().upload().maxImageBytes();
        if (bytes.length > limit) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE,
                    "The file is too large (at most %d MB for %s)".formatted(limit / (1024 * 1024),
                            "application/pdf".equals(type) ? "a PDF" : "an image"));
        }
        if ("application/pdf".equals(type)) {
            int pages = pdfPages(bytes);
            int max = properties.ai().upload().maxPdfPages();
            if (pages > max) {
                throw new ApiException(ErrorCode.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE,
                        "The PDF has about %d pages; at most %d can be read at once".formatted(pages, max));
            }
        }
        return new Attachment(type, Base64.getEncoder().encodeToString(bytes));
    }

    /**
     * A best-effort page count: the number of page objects written out in the clear. PDFs that pack their objects into
     * compressed streams hide them and count as 0, so this only catches the obvious cases; the byte limit and the
     * provider's own page limit remain the hard stops.
     */
    static int pdfPages(byte[] bytes) {
        String text = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        java.util.regex.Matcher m = PAGE_OBJECT.matcher(text);
        int count = 0;
        while (m.find()) {
            count++;
        }
        return count;
    }

    /** The media type the first bytes announce, or null when they are not a supported format. */
    static String sniff(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
            return "image/gif";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') {
            return "application/pdf";
        }
        return null;
    }
}
