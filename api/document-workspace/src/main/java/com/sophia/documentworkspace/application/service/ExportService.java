package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.application.security.ArtifactAccessService;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.domain.entity.Artifact;
import com.sophia.documentworkspace.domain.entity.DocumentArtifact;
import com.sophia.documentworkspace.domain.entity.FileArtifact;
import com.sophia.documentworkspace.domain.entity.Folder;
import com.sophia.documentworkspace.domain.entity.enums.AccessMode;
import com.sophia.documentworkspace.domain.entity.enums.ArtifactType;
import com.sophia.documentworkspace.domain.entity.enums.ExportFormat;
import com.sophia.documentworkspace.domain.exception.ApiException;
import com.sophia.documentworkspace.domain.exception.BadRequestException;
import com.sophia.documentworkspace.domain.exception.ErrorCode;
import com.sophia.documentworkspace.domain.util.MarkdownDocxWriter;
import com.sophia.documentworkspace.domain.util.MarkdownUtil;
import com.sophia.documentworkspace.domain.util.PresentationCodec;
import com.sophia.documentworkspace.domain.util.SpreadsheetCodec;
import com.sophia.documentworkspace.domain.util.WhiteboardCodec;
import com.sophia.documentworkspace.infrastructure.repository.ArtifactRepository;
import com.sophia.documentworkspace.infrastructure.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    public record ExportResult(String filename, String contentType, byte[] content) {
    }

    private final ArtifactRepository artifactRepository;
    private final ArtifactAccessService artifactAccess;
    private final StorageService storageService;

    @Transactional(readOnly = true)
    public ExportResult export(AuthenticatedUser user, UUID workspaceId, UUID artifactId,
                               ExportFormat format) {
        var result = artifactAccess.require(user, workspaceId, artifactId, AccessMode.READ);
        Artifact artifact = result.artifact();
        String baseName = sanitizeFilename(artifact.getName());

        if (artifact instanceof Folder) {
            if (format != ExportFormat.ZIP) {
                throw new BadRequestException(ErrorCode.EXPORT_FAILED,
                        "Folders can only be exported as ZIP");
            }
            return new ExportResult(baseName + ".zip", format.contentType(),
                    exportSubtreeZip(artifact));
        }
        if (artifact instanceof FileArtifact file) {
            return exportRawFile(file);
        }

        if (artifact.getType() == ArtifactType.PRESENTATION || artifact.getType() == ArtifactType.WHITEBOARD) {
            return exportBoard(artifact, baseName, format);
        }

        boolean spreadsheet = artifact.getType() == ArtifactType.SPREADSHEET;
        byte[] content;
        try {
            content = switch (format) {
                case XLSX -> requireSpreadsheet(spreadsheet, format)
                        ? SpreadsheetCodec.toXlsx(contentOf(artifact)) : new byte[0];
                case CSV -> requireSpreadsheet(spreadsheet, format)
                        ? SpreadsheetCodec.toCsv(contentOf(artifact), 0).getBytes(StandardCharsets.UTF_8) : new byte[0];
                case MARKDOWN, TXT -> requireDocument(spreadsheet, format)
                        ? contentOf(artifact).getBytes(StandardCharsets.UTF_8) : new byte[0];
                case HTML -> requireDocument(spreadsheet, format)
                        ? MarkdownUtil.markdownToHtml(contentOf(artifact)).getBytes(StandardCharsets.UTF_8) : new byte[0];
                case JSON -> contentOf(artifact).getBytes(StandardCharsets.UTF_8);
                case PDF -> pdfOf(artifact);
                case DOCX -> requireDocument(spreadsheet, format)
                        ? MarkdownDocxWriter.write(artifact.getName(), contentOf(artifact)) : new byte[0];
                case PPTX -> throw new BadRequestException(ErrorCode.EXPORT_FAILED, "PPTX export is only available for presentations");
                case ZIP -> exportSubtreeZip(artifact);
            };
        } catch (IOException e) {
            throw exportFailure("Export to " + format + " failed", e);
        }
        return new ExportResult(baseName + "." + format.extension(), format.contentType(), content);
    }

    /** Presentations (study decks) and whiteboards: their own formats plus the text-based ones. */
    private ExportResult exportBoard(Artifact artifact, String baseName, ExportFormat format) {
        boolean deck = artifact.getType() == ArtifactType.PRESENTATION;
        String json = contentOf(artifact);
        String markdown = markdownFor(artifact);
        try {
            byte[] bytes = switch (format) {
                case JSON -> json.getBytes(StandardCharsets.UTF_8);
                case MARKDOWN, TXT -> markdown.getBytes(StandardCharsets.UTF_8);
                case HTML -> MarkdownUtil.markdownToHtml(markdown).getBytes(StandardCharsets.UTF_8);
                case PDF -> pdfOf(artifact);
                case PPTX -> {
                    if (!deck) {
                        throw new BadRequestException(ErrorCode.EXPORT_FAILED, "PPTX export is only available for presentations");
                    }
                    yield PresentationCodec.toPptx(json);
                }
                case DOCX -> MarkdownDocxWriter.write(artifact.getName(), markdown);
                case ZIP -> exportSubtreeZip(artifact);
                default -> throw new BadRequestException(ErrorCode.EXPORT_FAILED, format + " export is not available for this document");
            };
            return new ExportResult(baseName + "." + format.extension(), format.contentType(), bytes);
        } catch (IOException e) {
            throw exportFailure("Export to " + format + " failed", e);
        }
    }

    private boolean requireSpreadsheet(boolean spreadsheet, ExportFormat format) {
        if (!spreadsheet) {
            throw new BadRequestException(ErrorCode.EXPORT_FAILED, format + " export is only available for spreadsheets");
        }
        return true;
    }

    private boolean requireDocument(boolean spreadsheet, ExportFormat format) {
        if (spreadsheet) {
            throw new BadRequestException(ErrorCode.EXPORT_FAILED,
                    format + " export is not available for spreadsheets; use XLSX, CSV or JSON");
        }
        return true;
    }

    /** Markdown rendition of a document (a table for spreadsheets), used by the anonymous public view. */
    public String markdownFor(Artifact artifact) {
        return switch (artifact.getType()) {
            case SPREADSHEET -> spreadsheetMarkdown(contentOf(artifact));
            case PRESENTATION -> PresentationCodec.toMarkdown(contentOf(artifact));
            case WHITEBOARD -> WhiteboardCodec.toMarkdown(contentOf(artifact));
            default -> contentOf(artifact);
        };
    }

    private String contentOf(Artifact artifact) {
        return artifact instanceof DocumentArtifact document ? document.contentOrEmpty() : "";
    }

    /** First sheet as a GitHub-flavoured table, for read-only public views. */
    private String spreadsheetMarkdown(String workbookJson) {
        try {
            String csv = SpreadsheetCodec.toCsv(workbookJson, 0);
            StringBuilder md = new StringBuilder();
            boolean header = true;
            for (String line : csv.split("\n")) {
                String escaped = "| " + line.replace("|", "\\|").replace(",", " | ") + " |\n";
                md.append(escaped);
                if (header) {
                    md.append("|").append(" --- |".repeat(Math.max(1, line.split(",", -1).length))).append('\n');
                    header = false;
                }
            }
            return md.toString();
        } catch (IOException e) {
            return "";
        }
    }

    private byte[] pdfOf(Artifact artifact) {
        String text = switch (artifact.getType()) {
            case SPREADSHEET -> spreadsheetMarkdown(contentOf(artifact));
            case PRESENTATION, WHITEBOARD -> MarkdownUtil.plainText(markdownFor(artifact));
            default -> MarkdownUtil.plainText(contentOf(artifact));
        };
        try (PDDocument document = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            float fontSize = 10;
            float leading = 14;
            float margin = 50;
            PDRectangle pageSize = PDRectangle.A4;
            float width = pageSize.getWidth() - 2 * margin;
            int maxCharsPerLine = (int) (width / (fontSize * 0.55));

            PDPage page = new PDPage(pageSize);
            document.addPage(page);
            PDPageContentStream stream = new PDPageContentStream(document, page);
            stream.beginText();
            stream.setFont(font, fontSize);
            stream.newLineAtOffset(margin, pageSize.getHeight() - margin);
            float y = pageSize.getHeight() - margin;

            for (String rawLine : (artifact.getName() + "\n\n" + text).split("\n", -1)) {
                for (String line : wrap(rawLine, maxCharsPerLine)) {
                    if (y - leading < margin) {
                        stream.endText();
                        stream.close();
                        page = new PDPage(pageSize);
                        document.addPage(page);
                        stream = new PDPageContentStream(document, page);
                        stream.beginText();
                        stream.setFont(font, fontSize);
                        stream.newLineAtOffset(margin, pageSize.getHeight() - margin);
                        y = pageSize.getHeight() - margin;
                    }
                    stream.showText(sanitizePdfText(line));
                    stream.newLineAtOffset(0, -leading);
                    y -= leading;
                }
            }
            stream.endText();
            stream.close();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw exportFailure("PDF rendering failed", e);
        }
    }

    private ExportResult exportRawFile(FileArtifact file) {
        if (file.getStorageKey() == null) {
            throw new BadRequestException(ErrorCode.EXPORT_FAILED, "The file has no content");
        }
        try (InputStream content = storageService.getObject(file.getStorageKey())) {
            return new ExportResult(
                    file.getOriginalFilename() == null ? file.getName() : file.getOriginalFilename(),
                    file.getMimeType() == null ? "application/octet-stream" : file.getMimeType(),
                    content.readAllBytes());
        } catch (IOException e) {
            throw exportFailure("Failed to read file content", e);
        }
    }

    /** Writes top-level artifacts (and everything under them) straight into an open archive. */
    void writeRoots(ZipOutputStream zip, List<Artifact> roots) throws IOException {
        Set<String> usedNames = new HashSet<>();
        for (Artifact root : roots) {
            writeArtifactToZip(zip, root, "", usedNames);
        }
    }

    private byte[] exportSubtreeZip(Artifact root) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Set<String> usedNames = new HashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            writeArtifactToZip(zip, root, "", usedNames);
        } catch (IOException e) {
            throw exportFailure("ZIP assembly failed", e);
        }
        return buffer.toByteArray();
    }

    private void writeArtifactToZip(ZipOutputStream zip, Artifact artifact,
                                    String path, Set<String> usedNames) throws IOException {
        String name = uniqueName(usedNames, path + sanitizeFilename(artifact.getName()));

        if (artifact instanceof Folder) {
            String folderPath = name + "/";
            zip.putNextEntry(new ZipEntry(folderPath));
            zip.closeEntry();
            var children = artifactRepository
                    .findChildren(artifact.getWorkspaceId(), ((Folder) artifact).childPath(), Pageable.unpaged())
                    .getContent();
            for (Artifact child : children) {
                writeArtifactToZip(zip, child, folderPath, usedNames);
            }
            return;
        }

        byte[] content;
        String entryName;
        switch (artifact.kind()) {
            case DOCUMENT -> {
                if (artifact.getType() == ArtifactType.SPREADSHEET) {
                    entryName = name + ".xlsx";
                    content = SpreadsheetCodec.toXlsx(contentOf(artifact));
                } else if (artifact.getType() == ArtifactType.PRESENTATION) {
                    entryName = name + ".pptx";
                    content = PresentationCodec.toPptx(contentOf(artifact));
                } else if (artifact.getType() == ArtifactType.WHITEBOARD) {
                    entryName = name + ".whiteboard.json";
                    content = contentOf(artifact).getBytes(StandardCharsets.UTF_8);
                } else {
                    entryName = name + ".md";
                    content = contentOf(artifact).getBytes(StandardCharsets.UTF_8);
                }
            }
            case FILE -> {
                FileArtifact file = (FileArtifact) artifact;
                if (file.getStorageKey() == null) {
                    return;
                }
                entryName = name;
                try (InputStream in = storageService.getObject(file.getStorageKey())) {
                    content = in.readAllBytes();
                } catch (RuntimeException e) {
                    log.warn("Skipping unreadable file {} during export: {}",
                            artifact.getId(), e.getMessage());
                    return;
                }
            }
            default -> {
                return; // Templates and other kinds are not part of content exports.
            }
        }
        zip.putNextEntry(new ZipEntry(entryName));
        zip.write(content);
        zip.closeEntry();
    }

    private String uniqueName(Set<String> used, String candidate) {
        String name = candidate;
        int counter = 2;
        while (!used.add(name)) {
            name = candidate + " (" + counter++ + ")";
        }
        return name;
    }

    static String sanitizeFilename(String name) {
        String cleaned = (name == null ? "untitled" : name)
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .strip();
        return cleaned.isEmpty() ? "untitled" : cleaned;
    }

    private List<String> wrap(String line, int maxChars) {
        if (line.length() <= maxChars) {
            return List.of(line);
        }
        List<String> lines = new java.util.ArrayList<>();
        String remaining = line;
        while (remaining.length() > maxChars) {
            int cut = remaining.lastIndexOf(' ', maxChars);
            if (cut <= 0) {
                cut = maxChars;
            }
            lines.add(remaining.substring(0, cut));
            remaining = remaining.substring(cut).stripLeading();
        }
        lines.add(remaining);
        return lines;
    }

    private String sanitizePdfText(String text) {
        StringBuilder safe = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            safe.append(c >= 32 && c <= 255 ? c : '?');
        }
        return safe.toString();
    }

    private ApiException exportFailure(String message, Exception cause) {
        return new ApiException(ErrorCode.EXPORT_FAILED, HttpStatus.INTERNAL_SERVER_ERROR,
                message, Map.of(), cause);
    }
}
