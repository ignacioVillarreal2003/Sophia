package com.sophia.documentworkspace.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.security.WorkspaceAuthorizationService;
import com.sophia.documentworkspace.domain.dto.request.CreateWorkspaceRequest;
import com.sophia.documentworkspace.domain.dto.response.WorkspaceResponse;
import com.sophia.documentworkspace.domain.entity.Artifact;
import com.sophia.documentworkspace.domain.entity.Workspace;
import com.sophia.documentworkspace.domain.entity.enums.WorkspacePermission;
import com.sophia.documentworkspace.domain.exception.BadRequestException;
import com.sophia.documentworkspace.domain.exception.ErrorCode;
import com.sophia.documentworkspace.domain.exception.ResourceNotFoundException;
import com.sophia.documentworkspace.infrastructure.repository.ArtifactRepository;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Takes a whole workspace out as one ZIP (folders, documents as Markdown, spreadsheets as XLSX, files as they are,
 * plus a manifest) and builds a brand new workspace from such a ZIP. Members, permissions and history are not part of
 * an archive: restoring gives the caller a fresh workspace they own.
 */
@Service
@RequiredArgsConstructor
public class WorkspaceArchiveService {

    public static final String MANIFEST = ".sophia-workspace.json";
    private static final int MANIFEST_FORMAT = 1;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final WorkspaceRepository workspaceRepository;
    private final ArtifactRepository artifactRepository;
    private final WorkspaceAuthorizationService authorization;
    private final ExportService exportService;
    private final ImportService importService;
    private final WorkspaceService workspaceService;

    public record Restored(WorkspaceResponse workspace, int artifacts) {
    }

    /** Checks the caller may export and returns the file name to offer; the body is written by {@link #write}. */
    @Transactional(readOnly = true)
    public String exportName(AuthenticatedUser user, UUID workspaceId) {
        authorization.requirePermission(workspaceId, user, WorkspacePermission.EXPORT_CONTENT);
        Workspace workspace = workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));
        return ExportService.sanitizeFilename(workspace.getName()) + ".zip";
    }

    /** Streams the archive; entries are written one by one so memory stays flat for large workspaces. */
    @Transactional(readOnly = true)
    public void write(AuthenticatedUser user, UUID workspaceId, OutputStream out) throws IOException {
        authorization.requirePermission(workspaceId, user, WorkspacePermission.EXPORT_CONTENT);
        Workspace workspace = workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));
        List<Artifact> roots = artifactRepository.findChildren(workspaceId, "", Pageable.unpaged()).getContent();

        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            ObjectNode manifest = JSON.createObjectNode();
            manifest.put("format", MANIFEST_FORMAT);
            manifest.put("name", workspace.getName());
            manifest.put("description", workspace.getDescription());
            manifest.put("color", workspace.getColor());
            manifest.put("language", workspace.getLanguage());
            manifest.put("exportedAt", Instant.now().toString());
            zip.putNextEntry(new ZipEntry(MANIFEST));
            zip.write(JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
            zip.closeEntry();
            exportService.writeRoots(zip, roots);
        }
    }

    /** Creates a new workspace owned by the caller and fills it from an archive produced by {@link #write}. */
    @Transactional
    public Restored restore(AuthenticatedUser user, MultipartFile file, String nameOverride) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException(ErrorCode.IMPORT_FAILED, "Uploaded file is empty");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!original.toLowerCase().endsWith(".zip")) {
            throw new BadRequestException(ErrorCode.IMPORT_FAILED, "A workspace backup must be a .zip file");
        }
        JsonNode manifest = readManifest(file);
        String name = firstNonBlank(nameOverride, text(manifest, "name"),
                original.substring(0, original.length() - 4), "Restored workspace");
        if (name.length() > 120) {
            name = name.substring(0, 120).strip();
        }
        String color = text(manifest, "color");
        String language = text(manifest, "language");
        WorkspaceResponse created = workspaceService.create(user, new CreateWorkspaceRequest(
                name, truncate(text(manifest, "description"), 2000),
                color != null && color.matches("^#[0-9A-Fa-f]{6}$") ? color : null,
                language != null && language.length() >= 2 && language.length() <= 10 ? language : null, null));
        List<UUID> artifacts = importService.importFile(user, created.id(), null, file);
        return new Restored(created, artifacts.size());
    }

    private JsonNode readManifest(MultipartFile file) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(file.getBytes()), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            int seen = 0;
            while ((entry = zip.getNextEntry()) != null && seen++ < 5000) {
                if (MANIFEST.equals(entry.getName())) {
                    return JSON.readTree(zip.readNBytes(64 * 1024));
                }
            }
        } catch (IOException e) {
            throw new BadRequestException(ErrorCode.IMPORT_FAILED, "That file is not a valid ZIP archive");
        }
        return JSON.createObjectNode();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText().strip();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return null;
    }
}
