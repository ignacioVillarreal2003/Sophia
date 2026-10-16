package com.sophia.documentworkspace.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophia.documentworkspace.application.security.ArtifactAccessService;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.security.ResourceAccessEvaluator;
import com.sophia.documentworkspace.application.security.WorkspaceAccessSnapshot;
import com.sophia.documentworkspace.application.security.WorkspaceAuthorizationService;
import com.sophia.documentworkspace.domain.dto.request.CreateTemplateRequest;
import com.sophia.documentworkspace.domain.dto.request.InstantiateTemplateRequest;
import com.sophia.documentworkspace.domain.dto.response.TemplateResponse;
import com.sophia.documentworkspace.domain.entity.Artifact;
import com.sophia.documentworkspace.domain.entity.DocumentArtifact;
import com.sophia.documentworkspace.domain.entity.Folder;
import com.sophia.documentworkspace.domain.entity.TemplateArtifact;
import com.sophia.documentworkspace.domain.entity.enums.AccessMode;
import com.sophia.documentworkspace.domain.entity.enums.ArtifactType;
import com.sophia.documentworkspace.domain.entity.enums.WorkspacePermission;
import com.sophia.documentworkspace.domain.event.ArtifactEvents;
import com.sophia.documentworkspace.domain.event.TemplateEvents;
import com.sophia.documentworkspace.domain.exception.BadRequestException;
import com.sophia.documentworkspace.domain.exception.ErrorCode;
import com.sophia.documentworkspace.infrastructure.messaging.DomainEventPublisher;
import com.sophia.documentworkspace.infrastructure.repository.ArtifactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reusable templates: snapshot an existing artifact's structure, keep it
 * versioned, publish it to the workspace and materialize fresh artifacts from
 * it. Supported targets: Markdown documents, checklists, spreadsheets and
 * folders (empty container with metadata).
 */
@Service
@RequiredArgsConstructor
public class TemplateService {


    private final ArtifactRepository artifactRepository;
    private final ArtifactAccessService artifactAccess;
    private final WorkspaceAuthorizationService workspaceAuthorization;
    private final ResourceAccessEvaluator accessEvaluator;
    private final DocumentContentService contentService;
    private final DomainEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public TemplateResponse createFromArtifact(AuthenticatedUser user, UUID workspaceId,
                                               CreateTemplateRequest request) {
        WorkspaceAccessSnapshot access = workspaceAuthorization.requirePermission(
                workspaceId, user, WorkspacePermission.MANAGE_TEMPLATES);
        var sourceResult = artifactAccess.require(user, workspaceId, request.sourceArtifactId(),
                AccessMode.READ);
        Artifact source = sourceResult.artifact();

        Folder parent = resolveParent(access, workspaceId, request.parentId());
        String content = snapshot(source);

        TemplateArtifact template = TemplateArtifact.create(workspaceId, parent, request.name(),
                user.id(), source.getType(), request.description(),
                content);
        template = (TemplateArtifact) artifactRepository.save(template);

        eventPublisher.publish(TemplateEvents.TemplateCreated.of(workspaceId, user.id(),
                template.getId(), source.getType(), template.getName()));
        return toResponse(template);
    }

    /** Re-snapshots the template from a source artifact, bumping its version. */
    @Transactional
    public TemplateResponse refresh(AuthenticatedUser user, UUID workspaceId, UUID templateId,
                                    UUID sourceArtifactId) {
        workspaceAuthorization.requirePermission(workspaceId, user,
                WorkspacePermission.MANAGE_TEMPLATES);
        TemplateArtifact template = loadTemplate(user, workspaceId, templateId, AccessMode.WRITE);
        var sourceResult = artifactAccess.require(user, workspaceId, sourceArtifactId,
                AccessMode.READ);
        Artifact source = sourceResult.artifact();
        if (source.getType() != template.getTargetType()) {
            throw new BadRequestException(ErrorCode.VALIDATION_FAILED,
                    "The source artifact type (%s) does not match the template target (%s)"
                            .formatted(source.getType(), template.getTargetType()));
        }
        template.resnapshot(snapshot(source));
        return toResponse(template);
    }

    @Transactional
    public TemplateResponse setPublished(AuthenticatedUser user, UUID workspaceId, UUID templateId,
                                         boolean published) {
        workspaceAuthorization.requirePermission(workspaceId, user,
                WorkspacePermission.MANAGE_TEMPLATES);
        TemplateArtifact template = loadTemplate(user, workspaceId, templateId, AccessMode.WRITE);
        if (published) {
            template.publish();
        } else {
            template.unpublish();
        }
        eventPublisher.publish(TemplateEvents.TemplatePublished.of(workspaceId, user.id(),
                templateId, published));
        return toResponse(template);
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> list(AuthenticatedUser user, UUID workspaceId,
                                       boolean publishedOnly) {
        workspaceAuthorization.requireActiveMember(workspaceId, user);
        return artifactRepository.findTemplates(workspaceId).stream()
                .filter(t -> !publishedOnly || t.isPublished())
                .map(this::toResponse)
                .toList();
    }

    /** Materializes a new artifact from the template snapshot. */
    @Transactional
    public UUID instantiate(AuthenticatedUser user, UUID workspaceId, UUID templateId,
                            InstantiateTemplateRequest request) {
        WorkspaceAccessSnapshot access = workspaceAuthorization.requirePermission(
                workspaceId, user, WorkspacePermission.CREATE_CONTENT);
        TemplateArtifact template = loadTemplate(user, workspaceId, templateId, AccessMode.READ);
        Folder parent = resolveParent(access, workspaceId, request.parentId());

        Artifact created = switch (template.getTargetType().kind()) {
            case DOCUMENT -> instantiateDocument(user, workspaceId, parent, template, request.name());
            case FOLDER -> artifactRepository.save(Folder.create(workspaceId, parent,
                    request.name(), user.id(), null, null));
            default -> throw new BadRequestException(ErrorCode.UNSUPPORTED_ARTIFACT_TYPE,
                    "Templates of type %s cannot be instantiated".formatted(template.getTargetType()));
        };

        eventPublisher.publish(ArtifactEvents.ArtifactCreated.of(workspaceId, user.id(),
                created.getId(), created.getType(), created.getName(), created.getParentId()));
        eventPublisher.publish(TemplateEvents.TemplateInstantiated.of(workspaceId, user.id(),
                templateId, created.getId()));
        return created.getId();
    }

    // ------------------------------------------------------------------
    // Snapshot / materialize
    // ------------------------------------------------------------------

    private String snapshot(Artifact source) {
        return switch (source.kind()) {
            case DOCUMENT -> source instanceof DocumentArtifact document ? document.contentOrEmpty() : "";
            // Folder templates capture only the container; children are not cloned.
            case FOLDER -> null;
            default -> throw new BadRequestException(ErrorCode.UNSUPPORTED_ARTIFACT_TYPE,
                    "Artifacts of type %s cannot become templates".formatted(source.getType()));
        };
    }

    private Artifact instantiateDocument(AuthenticatedUser user, UUID workspaceId, Folder parent,
                                         TemplateArtifact template, String name) {
        DocumentArtifact document = DocumentArtifact.create(template.getTargetType(), workspaceId,
                parent, name, user.id(), null, null);
        document = (DocumentArtifact) artifactRepository.save(document);

        if (template.getTemplateContent() != null) {
            contentService.replaceContent(document, template.getTemplateContent(), user.id());
        }
        return document;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private TemplateArtifact loadTemplate(AuthenticatedUser user, UUID workspaceId,
                                          UUID templateId, AccessMode mode) {
        var result = artifactAccess.require(user, workspaceId, templateId, mode);
        if (!(result.artifact() instanceof TemplateArtifact template)) {
            throw new BadRequestException(ErrorCode.UNSUPPORTED_ARTIFACT_TYPE,
                    "Artifact %s is not a template".formatted(templateId));
        }
        return template;
    }

    private Folder resolveParent(WorkspaceAccessSnapshot access, UUID workspaceId, UUID parentId) {
        if (parentId == null) {
            return null;
        }
        Artifact parent = artifactRepository
                .findByIdAndWorkspaceIdAndDeletedAtIsNull(parentId, workspaceId)
                .orElseThrow(() -> new com.sophia.documentworkspace.domain.exception
                        .ResourceNotFoundException("Parent folder", parentId));
        if (!(parent instanceof Folder folder)) {
            throw new BadRequestException(ErrorCode.INVALID_HIERARCHY,
                    "The parent artifact is not a folder");
        }
        accessEvaluator.require(access, folder, AccessMode.WRITE, folder.resourceChain());
        return folder;
    }

    private TemplateResponse toResponse(TemplateArtifact template) {
        return new TemplateResponse(template.getId(), template.getWorkspaceId(),
                template.getParentId(), template.getName(), template.getDescription(),
                template.getTargetType(), template.isPublished(),
                template.getTemplateVersion() == null ? 1 : template.getTemplateVersion(),
                template.getOwnerId(),
                template.getCreatedAt(), template.getUpdatedAt());
    }
}
