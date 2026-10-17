package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.domain.entity.Artifact;
import com.sophia.documentworkspace.domain.entity.DocumentArtifact;
import com.sophia.documentworkspace.domain.entity.FileArtifact;
import com.sophia.documentworkspace.domain.entity.Tag;
import com.sophia.documentworkspace.domain.entity.TagArtifact;
import com.sophia.documentworkspace.domain.event.ArtifactEvents;
import com.sophia.documentworkspace.domain.event.CommentEvents;
import com.sophia.documentworkspace.domain.event.DocumentEvents;
import com.sophia.documentworkspace.domain.event.FileEvents;
import com.sophia.documentworkspace.domain.event.PermissionEvents;
import com.sophia.documentworkspace.domain.event.WorkspaceEvents;
import com.sophia.documentworkspace.infrastructure.repository.ArtifactRepository;
import com.sophia.documentworkspace.infrastructure.repository.CommentRepository;
import com.sophia.documentworkspace.infrastructure.repository.TagArtifactRepository;
import com.sophia.documentworkspace.infrastructure.repository.TagRepository;
import com.sophia.documentworkspace.infrastructure.service.SearchIndexService;
import com.sophia.documentworkspace.infrastructure.service.SearchIndexService.IndexedArtifact;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps the OpenSearch index in sync with the database by reacting to domain
 * events after their transaction commits. Indexing is asynchronous and
 * best-effort: a failure is logged by the gateway, never propagated to the
 * user operation that triggered it.
 */
@Component
@RequiredArgsConstructor
public class SearchIndexer {

    private final ArtifactRepository artifactRepository;
    private final CommentRepository commentRepository;
    private final TagArtifactRepository tagArtifactRepository;
    private final TagRepository tagRepository;
    private final SearchIndexService searchIndexService;

    @Async
    @TransactionalEventListener
    public void onArtifactCreated(ArtifactEvents.ArtifactCreated event) {
        reindex(event.artifactId());
    }

    @Async
    @TransactionalEventListener
    public void onArtifactUpdated(ArtifactEvents.ArtifactUpdated event) {
        reindex(event.artifactId());
    }

    @Async
    @TransactionalEventListener
    public void onArtifactMoved(ArtifactEvents.ArtifactMoved event) {
        reindex(event.artifactId());
    }

    @Async
    @TransactionalEventListener
    public void onArtifactDuplicated(ArtifactEvents.ArtifactDuplicated event) {
        reindex(event.newArtifactId());
    }

    @Async
    @TransactionalEventListener
    public void onArtifactRestored(ArtifactEvents.ArtifactRestored event) {
        reindex(event.artifactId());
    }

    @Async
    @TransactionalEventListener
    public void onDocumentContentUpdated(DocumentEvents.DocumentContentUpdated event) {
        reindex(event.documentId());
    }

    @Async
    @TransactionalEventListener
    public void onFileUploaded(FileEvents.FileUploaded event) {
        reindex(event.artifactId());
    }

    @Async
    @TransactionalEventListener
    public void onCommentCreated(CommentEvents.CommentCreated event) {
        reindex(event.artifactId());
    }

    @Async
    @TransactionalEventListener
    public void onCommentUpdated(CommentEvents.CommentUpdated event) {
        reindex(event.artifactId());
    }

    @Async
    @TransactionalEventListener
    public void onCommentDeleted(CommentEvents.CommentDeleted event) {
        reindex(event.artifactId());
    }

    @Async
    @TransactionalEventListener
    public void onArtifactTrashed(ArtifactEvents.ArtifactTrashed event) {
        searchIndexService.delete(event.artifactId());
    }

    @Async
    @TransactionalEventListener
    public void onArtifactPurged(ArtifactEvents.ArtifactPurged event) {
        searchIndexService.delete(event.artifactId());
    }

    @Async
    @TransactionalEventListener
    public void onWorkspaceDeleted(WorkspaceEvents.WorkspaceDeleted event) {
        searchIndexService.deleteByWorkspace(event.workspaceId());
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void reindex(UUID artifactId) {
        Artifact artifact = artifactRepository.findById(artifactId).orElse(null);
        if (artifact == null || artifact.isDeleted()) {
            searchIndexService.delete(artifactId);
            return;
        }
        searchIndexService.index(toIndexedArtifact(artifact));
    }

    private IndexedArtifact toIndexedArtifact(Artifact artifact) {
        List<String> tagNames = tagRepository.findAllById(
                        tagArtifactRepository.findAllByArtifactId(artifact.getId()).stream()
                                .map(TagArtifact::getTagId)
                                .toList()).stream()
                .map(Tag::getName)
                .toList();
        String mimeType = artifact instanceof com.sophia.documentworkspace.domain.entity.FileArtifact file
                ? file.getMimeType()
                : null;
        String comments = commentRepository.findAllByArtifactIdAndDeletedAtIsNull(artifact.getId())
                .stream()
                .map(comment -> comment.getBody() == null ? "" : comment.getBody())
                .collect(java.util.stream.Collectors.joining("\n"));
        return new IndexedArtifact(
                artifact.getId().toString(),
                artifact.getWorkspaceId().toString(),
                artifact.getType().name(),
                artifact.getName(),
                extractContent(artifact),
                comments,
                tagNames,
                artifact.getOwnerId().toString(),
                mimeType,
                artifact.getCreatedAt(),
                artifact.getUpdatedAt());
    }

    /** Searchable body text: the text of documents, preview text for files. */
    private String extractContent(Artifact artifact) {
        if (artifact instanceof DocumentArtifact document) {
            String content = document.contentOrEmpty();
            String text = switch (document.getType()) {
                case SPREADSHEET -> com.sophia.documentworkspace.domain.util.SpreadsheetCodec.searchText(content);
                case PRESENTATION -> com.sophia.documentworkspace.domain.util.PresentationCodec.searchText(content);
                case WHITEBOARD -> com.sophia.documentworkspace.domain.util.WhiteboardCodec.searchText(content);
                default -> com.sophia.documentworkspace.domain.util.MarkdownUtil.plainText(content);
            };
            return text.length() > 100_000 ? text.substring(0, 100_000) : text;
        }
        if (artifact instanceof FileArtifact file && file.getPreviewText() != null) {
            return file.getPreviewText();
        }
        return "";
    }

    /** Collects every string value of a block's content map, recursively. */
    private void appendTextValues(Object node, StringBuilder text) {
        if (node instanceof String s) {
            if (!s.isBlank()) {
                text.append(s).append('\n');
            }
        } else if (node instanceof Map<?, ?> map) {
            map.values().forEach(value -> appendTextValues(value, text));
        } else if (node instanceof List<?> list) {
            list.forEach(value -> appendTextValues(value, text));
        }
    }
}
