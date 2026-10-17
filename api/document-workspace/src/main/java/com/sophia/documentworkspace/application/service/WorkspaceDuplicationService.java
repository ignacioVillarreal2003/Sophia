package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.security.WorkspaceAuthorizationService;
import com.sophia.documentworkspace.domain.dto.request.CreateWorkspaceRequest;
import com.sophia.documentworkspace.domain.dto.response.WorkspaceResponse;
import com.sophia.documentworkspace.domain.entity.Artifact;
import com.sophia.documentworkspace.domain.entity.DocumentArtifact;
import com.sophia.documentworkspace.domain.entity.FileArtifact;
import com.sophia.documentworkspace.domain.entity.FileVersion;
import com.sophia.documentworkspace.domain.entity.Folder;
import com.sophia.documentworkspace.domain.entity.Workspace;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceTemplate;
import com.sophia.documentworkspace.domain.event.ArtifactEvents;
import com.sophia.documentworkspace.domain.event.WorkspaceEvents;
import com.sophia.documentworkspace.domain.exception.ConflictException;
import com.sophia.documentworkspace.domain.exception.ResourceNotFoundException;
import com.sophia.documentworkspace.infrastructure.messaging.DomainEventPublisher;
import com.sophia.documentworkspace.infrastructure.repository.ArtifactRepository;
import com.sophia.documentworkspace.infrastructure.repository.FileVersionRepository;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceRepository;
import com.sophia.documentworkspace.infrastructure.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Copies a workspace: its folders, documents, spreadsheets, files and its configuration. People, comments,
 * history, tags, favorites and public links are not part of the copy. Files share the (immutable) stored
 * objects with the original, so a copy costs rows, not bytes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceDuplicationService {

    static final int MAX_ITEMS = 5_000;
    private static final int CHUNK = 200;

    private final WorkspaceService workspaceService;
    private final WorkspaceAuthorizationService authorization;
    private final WorkspaceRepository workspaceRepository;
    private final ArtifactRepository artifactRepository;
    private final FileVersionRepository fileVersionRepository;
    private final DocumentContentService contentService;
    private final StorageService storageService;
    private final DomainEventPublisher eventPublisher;

    @org.springframework.beans.factory.annotation.Value("${sophia.workspaces.default-storage-quota-mb:5120}")
    private long defaultStorageQuotaMb;

    @Transactional
    public WorkspaceResponse duplicate(AuthenticatedUser user, UUID sourceId, String requestedName) {
        authorization.requireActiveMember(sourceId, user);
        Workspace source = workspaceRepository.findByIdAndDeletedAtIsNull(sourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", sourceId));

        List<UUID> ids = artifactRepository.findLiveIdsParentsFirst(sourceId);
        if (ids.size() > MAX_ITEMS) {
            throw new ConflictException("This workspace has more than %d items and cannot be duplicated in one step; "
                    .formatted(MAX_ITEMS) + "export it and restore the ZIP instead");
        }
        long usedBytes = artifactRepository.sumFileSizes(sourceId);
        if (defaultStorageQuotaMb > 0 && usedBytes > defaultStorageQuotaMb * 1024L * 1024L) {
            throw new ConflictException("The files of this workspace are bigger than the storage a new workspace gets");
        }

        String name = requestedName == null || requestedName.isBlank()
                ? source.getName() + " (copy)" : requestedName.strip();
        if (name.length() > 120) {
            name = name.substring(0, 120);
        }
        WorkspaceResponse created = workspaceService.create(user, new CreateWorkspaceRequest(
                name, source.getDescription(), source.getColor(), source.getLanguage(), WorkspaceTemplate.BLANK));
        Workspace copy = workspaceRepository.findById(created.id()).orElseThrow();
        copy.updatePreferences(source.getTrashRetentionDays() == null ? 0 : source.getTrashRetentionDays(),
                false, source.getTimezone(), source.getDateFormat());
        copyLogo(source, copy);

        Map<UUID, Folder> folders = new HashMap<>();
        for (int from = 0; from < ids.size(); from += CHUNK) {
            List<UUID> chunk = ids.subList(from, Math.min(ids.size(), from + CHUNK));
            Map<UUID, Artifact> loaded = artifactRepository.findAllById(chunk).stream()
                    .collect(Collectors.toMap(Artifact::getId, Function.identity()));
            for (UUID id : chunk) {
                Artifact original = loaded.get(id);
                if (original == null) {
                    continue;
                }
                Folder parent = original.getParentId() == null ? null : folders.get(original.getParentId());
                if (original.getParentId() != null && parent == null) {
                    continue; // its folder was not copied, so neither is it
                }
                Artifact duplicate = artifactRepository.save(
                        original.copyTo(copy.getId(), original.getName(), parent, user.id()));
                if (duplicate instanceof Folder folder) {
                    folders.put(original.getId(), folder);
                } else if (duplicate instanceof DocumentArtifact document) {
                    contentService.replaceContent(document, document.contentOrEmpty(), user.id());
                } else if (duplicate instanceof FileArtifact file) {
                    fileVersionRepository.save(FileVersion.of(file.getId(), copy.getId(), 1, file.getStorageKey(),
                            file.getMimeType(), file.getSizeBytes(), file.getChecksumSha256(), user.id()));
                }
                eventPublisher.publish(ArtifactEvents.ArtifactCreated.of(copy.getId(), user.id(), duplicate.getId(),
                        duplicate.getType(), duplicate.getName(), duplicate.getParentId()));
            }
        }

        eventPublisher.publish(WorkspaceEvents.WorkspaceDuplicated.of(copy.getId(), user.id(), sourceId));
        return workspaceService.getById(user, copy.getId());
    }

    private void copyLogo(Workspace source, Workspace copy) {
        String key = source.getLogoUrl();
        if (key == null || key.isBlank()) {
            return;
        }
        try (InputStream in = storageService.getObject(key)) {
            String extension = key.contains(".") ? key.substring(key.lastIndexOf('.') + 1) : "png";
            String newKey = "workspaces/%s/logo/%s.%s".formatted(copy.getId(), UUID.randomUUID(), extension);
            storageService.putObject(newKey, in.readAllBytes(), contentTypeOf(extension));
            copy.changeLogo(newKey);
        } catch (Exception e) {
            log.warn("Could not copy the logo of workspace {}: {}", source.getId(), e.getMessage());
        }
    }

    private static String contentTypeOf(String extension) {
        return switch (extension.toLowerCase()) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            default -> "image/png";
        };
    }
}
