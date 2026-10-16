package com.sophia.documentworkspace.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophia.documentworkspace.application.security.ArtifactAccessService;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.domain.dto.request.SaveContentRequest;
import com.sophia.documentworkspace.domain.dto.response.DocumentContentResponse;
import com.sophia.documentworkspace.domain.dto.response.DocumentVersionResponse;
import com.sophia.documentworkspace.domain.dto.response.PageResponse;
import com.sophia.documentworkspace.domain.entity.Artifact;
import com.sophia.documentworkspace.domain.entity.DocumentArtifact;
import com.sophia.documentworkspace.domain.entity.DocumentVersion;
import com.sophia.documentworkspace.domain.entity.enums.AccessMode;
import com.sophia.documentworkspace.domain.entity.enums.ArtifactType;
import com.sophia.documentworkspace.domain.event.DocumentEvents;
import com.sophia.documentworkspace.domain.exception.BadRequestException;
import com.sophia.documentworkspace.domain.exception.ConflictException;
import com.sophia.documentworkspace.domain.exception.ErrorCode;
import com.sophia.documentworkspace.domain.exception.ResourceNotFoundException;
import com.sophia.documentworkspace.infrastructure.messaging.DomainEventPublisher;
import com.sophia.documentworkspace.infrastructure.repository.DocumentVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads and saves the text content of documents (Markdown notes, checklists, spreadsheets) with
 * optimistic versioning and a coalescing snapshot history.
 */
@Service
@RequiredArgsConstructor
public class DocumentContentService {

    /** Saves by the same author inside this window update one snapshot instead of adding a new one. */
    static final Duration COALESCE_WINDOW = Duration.ofMinutes(10);
    static final int MAX_SNAPSHOTS = 100;

    private final ArtifactAccessService artifactAccess;
    private final DocumentLockService lockService;
    private final DocumentVersionRepository versionRepository;
    private final DomainEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public DocumentContentResponse getContent(AuthenticatedUser user, UUID workspaceId, UUID documentId) {
        var result = artifactAccess.require(user, workspaceId, documentId, AccessMode.READ);
        return toResponse(asDocument(result.artifact()));
    }

    @Transactional
    public DocumentContentResponse save(AuthenticatedUser user, UUID workspaceId, UUID documentId,
                                        SaveContentRequest request) {
        var result = artifactAccess.require(user, workspaceId, documentId, AccessMode.WRITE);
        DocumentArtifact document = asDocument(result.artifact());
        lockService.ensureNotLockedByOther(documentId, user.id());

        if (request.baseVersion() != document.getContentVersion()) {
            throw new ConflictException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                    "Document is at version %d but the edit was based on version %d"
                            .formatted(document.getContentVersion(), request.baseVersion()));
        }
        if (request.content().equals(document.contentOrEmpty())) {
            return toResponse(document);
        }
        validate(document.getType(), request.content());

        long version = document.replaceContent(request.content());
        recordSnapshot(document, user.id());
        eventPublisher.publish(DocumentEvents.DocumentContentUpdated.of(
                workspaceId, user.id(), documentId, version, 1));
        return toResponse(document);
    }

    /** Sets the initial or replacement content outside the interactive flow (import, templates). */
    @Transactional
    public void replaceContent(DocumentArtifact document, String content, UUID actorId) {
        validate(document.getType(), content);
        document.replaceContent(content);
        recordSnapshot(document, actorId);
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentVersionResponse> versions(AuthenticatedUser user, UUID workspaceId,
                                                          UUID documentId, boolean namedOnly,
                                                          Pageable pageable) {
        artifactAccess.require(user, workspaceId, documentId, AccessMode.READ);
        Page<DocumentVersion> page = namedOnly
                ? versionRepository.findAllByArtifactIdAndNameIsNotNullOrderByVersionDesc(documentId, pageable)
                : versionRepository.findAllByArtifactIdOrderByVersionDesc(documentId, pageable);
        return PageResponse.from(page, v -> toVersion(v, false));
    }

    @Transactional(readOnly = true)
    public DocumentVersionResponse version(AuthenticatedUser user, UUID workspaceId, UUID documentId,
                                           UUID versionId) {
        artifactAccess.require(user, workspaceId, documentId, AccessMode.READ);
        return toVersion(loadVersion(documentId, versionId), true);
    }

    @Transactional
    public DocumentContentResponse restore(AuthenticatedUser user, UUID workspaceId, UUID documentId,
                                           UUID versionId) {
        var result = artifactAccess.require(user, workspaceId, documentId, AccessMode.WRITE);
        DocumentArtifact document = asDocument(result.artifact());
        lockService.ensureNotLockedByOther(documentId, user.id());
        DocumentVersion source = loadVersion(documentId, versionId);

        long version = document.replaceContent(source.getContent() == null ? "" : source.getContent());
        // A restore is always its own history entry so it can be undone.
        versionRepository.save(DocumentVersion.of(documentId, workspaceId, version, document.contentOrEmpty(),
                user.id()));
        eventPublisher.publish(DocumentEvents.DocumentContentUpdated.of(
                workspaceId, user.id(), documentId, version, 1));
        return toResponse(document);
    }

    @Transactional
    public DocumentVersionResponse nameVersion(AuthenticatedUser user, UUID workspaceId, UUID documentId,
                                               UUID versionId, String name) {
        artifactAccess.require(user, workspaceId, documentId, AccessMode.WRITE);
        DocumentVersion version = loadVersion(documentId, versionId);
        version.rename(name == null || name.isBlank() ? null : name.strip());
        return toVersion(version, false);
    }

    public DocumentArtifact asDocument(Artifact artifact) {
        if (artifact instanceof DocumentArtifact document) {
            return document;
        }
        throw new BadRequestException(ErrorCode.UNSUPPORTED_ARTIFACT_TYPE,
                "Artifact %s has no text content".formatted(artifact.getId()));
    }

    // ------------------------------------------------------------------

    private void recordSnapshot(DocumentArtifact document, UUID actorId) {
        UUID documentId = document.getId();
        var latest = versionRepository.findFirstByArtifactIdOrderByVersionDesc(documentId);
        if (latest.isPresent()
                && !latest.get().isNamed()
                && latest.get().getCreatedBy().equals(actorId)
                && latest.get().getSavedAt().isAfter(Instant.now().minus(COALESCE_WINDOW))) {
            latest.get().absorb(document.getContentVersion(), document.contentOrEmpty());
            return;
        }
        versionRepository.save(DocumentVersion.of(documentId, document.getWorkspaceId(),
                document.getContentVersion(), document.contentOrEmpty(), actorId));
        trimHistory(documentId);
    }

    private void trimHistory(UUID documentId) {
        long total = versionRepository.countByArtifactId(documentId);
        if (total <= MAX_SNAPSHOTS) {
            return;
        }
        int excess = (int) (total - MAX_SNAPSHOTS);
        List<DocumentVersion> oldest = versionRepository.findUnnamedOldestFirst(documentId,
                PageRequest.of(0, excess));
        versionRepository.deleteAll(oldest);
    }

    private void validate(ArtifactType type, String content) {
        if (type == ArtifactType.SPREADSHEET && !content.isBlank()) {
            try {
                var tree = objectMapper.readTree(content);
                if (!tree.isObject() || !tree.path("sheets").isArray()) {
                    throw new BadRequestException(ErrorCode.VALIDATION_FAILED,
                            "A spreadsheet must be an object with a sheets array");
                }
            } catch (JsonProcessingException e) {
                throw new BadRequestException(ErrorCode.VALIDATION_FAILED,
                        "The spreadsheet content is not valid JSON");
            }
        }
    }

    private DocumentVersion loadVersion(UUID documentId, UUID versionId) {
        return versionRepository.findByIdAndArtifactId(versionId, documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document version", versionId));
    }

    private DocumentContentResponse toResponse(DocumentArtifact document) {
        return new DocumentContentResponse(document.getId(), document.getType(), document.getContentVersion(),
                document.contentOrEmpty(), document.getUpdatedAt());
    }

    private DocumentVersionResponse toVersion(DocumentVersion v, boolean withContent) {
        String content = v.getContent() == null ? "" : v.getContent();
        return new DocumentVersionResponse(v.getId(), v.getArtifactId(), v.getVersion(), v.getName(),
                v.getCreatedBy(), v.getSavedAt(), content.length(), withContent ? content : null);
    }
}
