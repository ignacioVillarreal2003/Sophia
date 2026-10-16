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
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
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
            case "pdf" -> List.of(importPdf(user, workspaceId, parent, baseName(filename), content));
            case "zip" -> importZip(user, workspaceId, parent, content);
            case "txt" -> List.of(importMarkdown(user, workspaceId, parent, baseName(filename), new String(content, StandardCharsets.UTF_8)));
            default -> List.of(importAsFileArtifact(user, workspaceId, parent, filename, content).getId());
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
        StringBuilder markdown = new StringBuilder();
        try (XWPFDocument docx = new XWPFDocument(new ByteArrayInputStream(content))) {
            for (XWPFParagraph paragraph : docx.getParagraphs()) {
                String text = paragraph.getText();
                if (text == null || text.isBlank()) {
                    continue;
                }
                String style = paragraph.getStyle();
                if (style != null && style.toLowerCase(Locale.ROOT).startsWith("heading")) {
                    int level = parseHeadingLevel(style);
                    markdown.append("#".repeat(level)).append(' ').append(text).append("\n\n");
                } else {
                    markdown.append(text).append("\n\n");
                }
            }
        } catch (IOException e) {
            throw importFailure("Failed to parse the DOCX file", e);
        }
        return importMarkdown(user, workspaceId, parent, name, markdown.toString());
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
