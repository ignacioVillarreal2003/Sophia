package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.application.security.ArtifactAccessService;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.security.ResourceAccessEvaluator;
import com.sophia.documentworkspace.application.security.WorkspaceAccessSnapshot;
import com.sophia.documentworkspace.application.security.WorkspaceAuthorizationService;
import com.sophia.documentworkspace.domain.entity.Artifact;
import com.sophia.documentworkspace.domain.entity.DocumentArtifact;
import com.sophia.documentworkspace.domain.entity.FileArtifact;
import com.sophia.documentworkspace.domain.entity.FileVersion;
import com.sophia.documentworkspace.domain.entity.Folder;
import com.sophia.documentworkspace.domain.entity.Workspace;
import com.sophia.documentworkspace.domain.entity.enums.AccessMode;
import com.sophia.documentworkspace.domain.entity.enums.ArtifactType;
import com.sophia.documentworkspace.domain.entity.enums.WorkspacePermission;
import com.sophia.documentworkspace.domain.event.ArtifactEvents;
import com.sophia.documentworkspace.domain.event.FileEvents;
import com.sophia.documentworkspace.domain.exception.ApiException;
import com.sophia.documentworkspace.domain.exception.BadRequestException;
import com.sophia.documentworkspace.domain.exception.ErrorCode;
import com.sophia.documentworkspace.domain.exception.ResourceNotFoundException;
import com.sophia.documentworkspace.domain.util.MarkdownUtil;
import com.sophia.documentworkspace.domain.util.PresentationCodec;
import com.sophia.documentworkspace.domain.util.SpreadsheetCodec;
import com.sophia.documentworkspace.domain.util.FileTypeResolver;
import com.sophia.documentworkspace.infrastructure.messaging.DomainEventPublisher;
import com.sophia.documentworkspace.infrastructure.repository.ArtifactRepository;
import com.sophia.documentworkspace.infrastructure.repository.FileVersionRepository;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceRepository;
import com.sophia.documentworkspace.infrastructure.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Imports content into a workspace:
 * <ul>
 *   <li>Markdown / HTML / TXT → Markdown documents</li>
 *   <li>XLSX and CSV → spreadsheets</li>
 *   <li>DOCX (POI) and PDF (PDFBox) → text extracted into Markdown documents</li>
 *   <li>ZIP archives — including Obsidian vaults and Notion exports, which are
 *       plain zips of markdown/html plus assets — recreate the folder tree:
 *       md/html entries become documents, everything else becomes file
 *       artifacts stored in object storage.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {

    private static final int MAX_ZIP_ENTRIES = 2000;
    private static final long MAX_ENTRY_BYTES = 200L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024;

    private final ArtifactRepository artifactRepository;
    private final FileVersionRepository fileVersionRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceAuthorizationService workspaceAuthorization;
    private final ArtifactAccessService artifactAccess;
    private final ResourceAccessEvaluator accessEvaluator;
    private final DocumentContentService contentService;
    private final StorageService storageService;
    private final FileService fileService;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public List<UUID> importFile(AuthenticatedUser user, UUID workspaceId, UUID parentId,
                                 MultipartFile file) {
        WorkspaceAccessSnapshot access = workspaceAuthorization.requirePermission(
                workspaceId, user, WorkspacePermission.IMPORT_CONTENT);
        if (file.isEmpty()) {
            throw new BadRequestException(ErrorCode.IMPORT_FAILED, "Uploaded file is empty");
        }
        Folder parent = resolveParent(user, workspaceId, parentId, access);
        byte[] content = readBytes(file);
        fileService.scanOrReject(content, file.getOriginalFilename());

        String filename = file.getOriginalFilename() == null ? "import" : file.getOriginalFilename();
        String extension = extensionOf(filename);

        return switch (extension) {
            case "md", "markdown" -> List.of(importMarkdown(user, workspaceId, parent, baseName(filename), new String(content, StandardCharsets.UTF_8)));
            case "html", "htm" -> List.of(importMarkdown(user, workspaceId, parent, baseName(filename), MarkdownUtil.htmlToMarkdown(
                            new String(content, StandardCharsets.UTF_8))));
            case "xlsx" -> List.of(importSpreadsheet(user, workspaceId, parent, baseName(filename), spreadsheetFromXlsx(content)));
            case "csv", "tsv" -> List.of(importSpreadsheet(user, workspaceId, parent, baseName(filename), SpreadsheetCodec.fromCsv(
                            new String(content, StandardCharsets.UTF_8), "Sheet1")));
            case "docx" -> List.of(importDocx(user, workspaceId, parent, baseName(filename), content));
            case "pptx" -> List.of(importPresentation(user, workspaceId, parent, baseName(filename), content));
            case "pdf" -> List.of(importPdf(user, workspaceId, parent, baseName(filename), content));
            case "zip" -> importZip(user, workspaceId, parent, content);
            case "txt" -> List.of(importMarkdown(user, workspaceId, parent, baseName(filename), new String(content, StandardCharsets.UTF_8)));
            default -> List.of(importAsFileArtifact(user, workspaceId, parent, filename, content).getId());
        };
    }

    /**
     * Turns an uploaded Word/PowerPoint/Excel/PDF/text file into an editable document next to it, leaving the
     * original untouched. Returns the new artifact id.
     */
    @Transactional
    public UUID convertToEditable(AuthenticatedUser user, UUID workspaceId, UUID artifactId) {
        WorkspaceAccessSnapshot access = workspaceAuthorization.requirePermission(
                workspaceId, user, WorkspacePermission.IMPORT_CONTENT);
        var source = artifactAccess.require(user, workspaceId, artifactId, AccessMode.READ).artifact();
        if (!(source instanceof FileArtifact file) || file.getStorageKey() == null) {
            throw new BadRequestException(ErrorCode.IMPORT_FAILED, "Only uploaded files can be converted");
        }
        Folder parent = resolveParent(user, workspaceId, file.getParentId(), access);
        byte[] content;
        try (var in = storageService.getObject(file.getStorageKey())) {
            content = in.readAllBytes();
        } catch (IOException e) {
            throw importFailure("Failed to read the file", e);
        }
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : file.getName();
        String name = baseName(filename);
        return switch (file.getType()) {
            case DOCX -> importDocx(user, workspaceId, parent, name, content);
            case PPTX -> importPresentation(user, workspaceId, parent, name, content);
            case XLSX -> importSpreadsheet(user, workspaceId, parent, name, spreadsheetFromXlsx(content));
            case PDF -> importPdf(user, workspaceId, parent, name, content);
            case TXT -> importMarkdown(user, workspaceId, parent, name, new String(content, StandardCharsets.UTF_8));
            default -> throw new BadRequestException(ErrorCode.IMPORT_FAILED,
                    "This kind of file cannot be converted to an editable document");
        };
    }

    // ------------------------------------------------------------------
    // Format importers
    // ------------------------------------------------------------------

    private UUID importMarkdown(AuthenticatedUser user, UUID workspaceId, Folder parent,
                                String name, String markdown) {
        return createDocument(user, workspaceId, parent, name, ArtifactType.MARKDOWN_DOCUMENT, markdown);
    }

    private UUID importSpreadsheet(AuthenticatedUser user, UUID workspaceId, Folder parent,
                                   String name, String workbookJson) {
        return createDocument(user, workspaceId, parent, name, ArtifactType.SPREADSHEET, workbookJson);
    }

    private String spreadsheetFromXlsx(byte[] content) {
        try {
            return SpreadsheetCodec.fromXlsx(content);
        } catch (IOException | RuntimeException e) {
            throw importFailure("Failed to parse the XLSX file", e);
        }
    }

    private UUID createDocument(AuthenticatedUser user, UUID workspaceId, Folder parent,
                                String name, ArtifactType type, String content) {
        DocumentArtifact document = DocumentArtifact.create(type, workspaceId, parent, name, user.id(),
                null, null);
        document = (DocumentArtifact) artifactRepository.save(document);
        contentService.replaceContent(document, content, user.id());
        eventPublisher.publish(ArtifactEvents.ArtifactCreated.of(workspaceId, user.id(),
                document.getId(), document.getType(), document.getName(), document.getParentId()));
        return document.getId();
    }

    private UUID importDocx(AuthenticatedUser user, UUID workspaceId, Folder parent,
                            String name, byte[] content) {
        return importMarkdown(user, workspaceId, parent, name, docxToMarkdown(content));
    }

    private UUID importPresentation(AuthenticatedUser user, UUID workspaceId, Folder parent,
                                    String name, byte[] content) {
        try {
            return createDocument(user, workspaceId, parent, name, ArtifactType.PRESENTATION,
                    PresentationCodec.fromPptx(content));
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException api) {
                throw api;
            }
            throw importFailure("Failed to parse the PPTX file", e);
        }
    }

    /** Headings, lists, bold/italic and tables survive; layout and styling do not. */
    String docxToMarkdown(byte[] content) {
        StringBuilder markdown = new StringBuilder();
        try (XWPFDocument docx = new XWPFDocument(new ByteArrayInputStream(content))) {
            for (IBodyElement element : docx.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    appendParagraph(markdown, paragraph);
                } else if (element instanceof XWPFTable table) {
                    appendTable(markdown, table);
                }
            }
        } catch (IOException e) {
            throw importFailure("Failed to parse the DOCX file", e);
        }
        return markdown.toString().replaceAll("\n{3,}", "\n\n").strip() + "\n";
    }

    private void appendParagraph(StringBuilder markdown, XWPFParagraph paragraph) {
        String text = inlineMarkdown(paragraph);
        if (text.isBlank()) {
            return;
        }
        String style = paragraph.getStyle();
        String lower = style == null ? "" : style.toLowerCase(Locale.ROOT);
        if (lower.equals("title")) {
            markdown.append("# ").append(text).append("\n\n");
        } else if (lower.startsWith("heading")) {
            markdown.append("#".repeat(Math.min(6, Math.max(1, parseHeadingLevel(style))))).append(' ').append(text).append("\n\n");
        } else if (paragraph.getNumID() != null || lower.startsWith("listparagraph")) {
            int level = paragraph.getNumIlvl() == null ? 0 : paragraph.getNumIlvl().intValue();
            boolean ordered = "decimal".equalsIgnoreCase(paragraph.getNumFmt());
            markdown.append("  ".repeat(Math.max(0, level))).append(ordered ? "1. " : "- ").append(text).append('\n');
        } else {
            markdown.append('\n').append(text).append("\n\n");
        }
    }

    private String inlineMarkdown(XWPFParagraph paragraph) {
        StringBuilder out = new StringBuilder();
        for (XWPFRun run : paragraph.getRuns()) {
            String text = run.text();
            if (text == null || text.isEmpty()) {
                continue;
            }
            String core = text.strip();
            if (core.isEmpty()) {
                out.append(text);
                continue;
            }
            String lead = text.substring(0, text.indexOf(core));
            String trail = text.substring(lead.length() + core.length());
            String mark = (run.isBold() ? "**" : "") + (run.isItalic() ? "*" : "");
            String closing = new StringBuilder(mark).reverse().toString();
            out.append(lead).append(mark).append(core).append(closing).append(trail);
        }
        return out.toString().strip();
    }

    private void appendTable(StringBuilder markdown, XWPFTable table) {
        List<XWPFTableRow> rows = table.getRows();
        if (rows.isEmpty()) {
            return;
        }
        markdown.append('\n');
        for (int r = 0; r < rows.size(); r++) {
            List<XWPFTableCell> cells = rows.get(r).getTableCells();
            markdown.append('|');
            for (XWPFTableCell cell : cells) {
                markdown.append(' ').append(cell.getText().replace('\n', ' ').replace("|", "\\|").strip()).append(" |");
            }
            markdown.append('\n');
            if (r == 0) {
                markdown.append('|').append(" --- |".repeat(Math.max(1, cells.size()))).append('\n');
            }
        }
        markdown.append('\n');
    }

    private UUID importPdf(AuthenticatedUser user, UUID workspaceId, Folder parent,
                           String name, byte[] content) {
        String text;
        try (PDDocument pdf = Loader.loadPDF(content)) {
            text = new PDFTextStripper().getText(pdf);
        } catch (IOException e) {
            throw importFailure("Failed to parse the PDF file", e);
        }
        // Blank-line separated chunks become paragraphs.
        String markdown = String.join("\n\n",
                text.split("\\R\\s*\\R"));
        return importMarkdown(user, workspaceId, parent, name, markdown);
    }

    private List<UUID> importZip(AuthenticatedUser user, UUID workspaceId, Folder rootParent,
                                 byte[] zipBytes) {
        List<UUID> created = new ArrayList<>();
        Map<String, Folder> foldersByPath = new HashMap<>();
        int entries = 0;
        long totalBytes = 0;

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes),
                StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ZIP_ENTRIES) {
                    throw new BadRequestException(ErrorCode.IMPORT_FAILED,
                            "Archive exceeds the maximum of %d entries".formatted(MAX_ZIP_ENTRIES));
                }
                String rawName = entry.getName().replace('\\', '/');
                if (rawName.contains("..")) {
                    throw new BadRequestException(ErrorCode.IMPORT_FAILED,
                            "Archive contains an illegal path: " + rawName);
                }
                if (entry.isDirectory()) {
                    ensureFolderPath(user, workspaceId, rootParent, foldersByPath,
                            stripTrailingSlash(rawName));
                    continue;
                }

                byte[] content = readEntry(zip, entry);
                totalBytes += content.length;
                if (totalBytes > MAX_TOTAL_BYTES) {
                    throw new BadRequestException(ErrorCode.IMPORT_FAILED,
                            "Archive content exceeds the maximum total size");
                }

                String dirPath = rawName.contains("/")
                        ? rawName.substring(0, rawName.lastIndexOf('/'))
                        : "";
                String filename = rawName.substring(rawName.lastIndexOf('/') + 1);
                if (filename.isBlank() || filename.startsWith(".")) {
                    continue; // Hidden files (.obsidian/, .DS_Store) are skipped.
                }
                Folder parent = dirPath.isEmpty()
                        ? rootParent
                        : ensureFolderPath(user, workspaceId, rootParent, foldersByPath,
                                dirPath);

                String extension = extensionOf(filename);
                switch (extension) {
                    case "md", "markdown" -> created.add(importMarkdown(user, workspaceId, parent,
                            baseName(filename),
                            new String(content, StandardCharsets.UTF_8)));
                    case "html", "htm" -> created.add(importMarkdown(user, workspaceId, parent,
                            baseName(filename), MarkdownUtil.htmlToMarkdown(
                                    new String(content, StandardCharsets.UTF_8))));
                    case "xlsx" -> created.add(importSpreadsheet(user, workspaceId, parent,
                            baseName(filename), spreadsheetFromXlsx(content)));
                    case "pptx" -> created.add(importPresentation(user, workspaceId, parent, baseName(filename), content));
                    case "csv" -> created.add(importSpreadsheet(user, workspaceId, parent, baseName(filename), SpreadsheetCodec.fromCsv(
                                    new String(content, StandardCharsets.UTF_8), "Sheet1")));
                    default -> created.add(importAsFileArtifact(user, workspaceId, parent, filename, content).getId());
                }
            }
        } catch (IOException e) {
            throw importFailure("Failed to read the archive", e);
        }
        return created;
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private FileArtifact importAsFileArtifact(AuthenticatedUser user, UUID workspaceId,
                                              Folder parent, String filename,
                                              byte[] content) {
        fileService.ensureWithinMaxSize(content.length);
        fileService.ensureQuota(workspaceId, content.length);

        String mimeType = FileTypeResolver.detectMimeType(
                new ByteArrayInputStream(content), filename, null);
        ArtifactType type = FileTypeResolver.fromMimeType(mimeType);
        String storageKey = FileService.newStorageKey(workspaceId, filename);
        String checksum = FileService.sha256Hex(content);
        storageService.putObject(storageKey, content, mimeType);

        FileArtifact artifact = FileArtifact.create(type, workspaceId, parent, filename,
                user.id(), mimeType, (long) content.length, storageKey, checksum,
                filename);
        artifact = (FileArtifact) artifactRepository.save(artifact);
        fileVersionRepository.save(FileVersion.of(artifact.getId(), workspaceId, 1,
                storageKey, mimeType, (long) content.length, checksum, user.id()));

        eventPublisher.publish(ArtifactEvents.ArtifactCreated.of(workspaceId, user.id(),
                artifact.getId(), type, artifact.getName(), artifact.getParentId()));
        eventPublisher.publish(FileEvents.FileUploaded.of(workspaceId, user.id(),
                artifact.getId(), type, mimeType, (long) content.length, storageKey));
        return artifact;
    }

    /** Creates (and caches) the folder chain for an archive path like {@code a/b/c}. */
    private Folder ensureFolderPath(AuthenticatedUser user, UUID workspaceId, Folder rootParent,
                                    Map<String, Folder> cache, String path) {
        Folder current = rootParent;
        StringBuilder walked = new StringBuilder();
        for (String segment : path.split("/")) {
            if (segment.isBlank()) {
                continue;
            }
            walked.append(walked.isEmpty() ? "" : "/").append(segment);
            String key = walked.toString();
            Folder cached = cache.get(key);
            if (cached != null) {
                current = cached;
                continue;
            }
            Folder folder = Folder.create(workspaceId, current,
                    ExportService.sanitizeFilename(segment), user.id(), null, null);
            folder = (Folder) artifactRepository.save(folder);
            eventPublisher.publish(ArtifactEvents.ArtifactCreated.of(workspaceId, user.id(),
                    folder.getId(), ArtifactType.FOLDER, folder.getName(), folder.getParentId()));
            cache.put(key, folder);
            current = folder;
        }
        return current;
    }

    private Folder resolveParent(AuthenticatedUser user, UUID workspaceId, UUID parentId,
                                 WorkspaceAccessSnapshot access) {
        if (parentId == null) {
            return null;
        }
        Artifact parent = artifactRepository
                .findByIdAndWorkspaceIdAndDeletedAtIsNull(parentId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent folder", parentId));
        if (!(parent instanceof Folder folder)) {
            throw new BadRequestException(ErrorCode.INVALID_HIERARCHY,
                    "The parent artifact is not a folder");
        }
        accessEvaluator.require(access, folder, AccessMode.WRITE, folder.resourceChain());
        return folder;
    }

    private byte[] readEntry(ZipInputStream zip, ZipEntry entry) throws IOException {
        if (entry.getSize() > MAX_ENTRY_BYTES) {
            throw new BadRequestException(ErrorCode.IMPORT_FAILED,
                    "Archive entry %s exceeds the maximum entry size".formatted(entry.getName()));
        }
        byte[] content = zip.readNBytes((int) Math.min(MAX_ENTRY_BYTES + 1, Integer.MAX_VALUE));
        if (content.length > MAX_ENTRY_BYTES) {
            throw new BadRequestException(ErrorCode.IMPORT_FAILED,
                    "Archive entry %s exceeds the maximum entry size".formatted(entry.getName()));
        }
        return content;
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw importFailure("Failed to read the uploaded file", e);
        }
    }

    private int parseHeadingLevel(String style) {
        for (char c : style.toCharArray()) {
            if (Character.isDigit(c)) {
                return Math.min(Math.max(c - '0', 1), 6);
            }
        }
        return 1;
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String baseName(String filename) {
        String name = filename;
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return ExportService.sanitizeFilename(name);
    }

    private String stripTrailingSlash(String path) {
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private ApiException importFailure(String message, Exception cause) {
        return new ApiException(ErrorCode.IMPORT_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                message, Map.of(), cause);
    }
}
