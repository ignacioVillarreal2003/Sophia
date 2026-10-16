package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.application.security.ArtifactAccessService;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.domain.dto.request.CreateCommentRequest;
import com.sophia.documentworkspace.domain.dto.request.UpdateCommentRequest;
import com.sophia.documentworkspace.domain.dto.response.CommentResponse;
import com.sophia.documentworkspace.domain.dto.response.PageResponse;
import com.sophia.documentworkspace.domain.entity.Artifact;
import com.sophia.documentworkspace.domain.entity.Comment;
import com.sophia.documentworkspace.domain.entity.enums.AccessMode;
import com.sophia.documentworkspace.domain.entity.enums.NotificationType;
import com.sophia.documentworkspace.domain.event.CommentEvents;
import com.sophia.documentworkspace.domain.exception.BadRequestException;
import com.sophia.documentworkspace.domain.exception.ErrorCode;
import com.sophia.documentworkspace.domain.exception.ForbiddenOperationException;
import com.sophia.documentworkspace.domain.exception.ResourceNotFoundException;
import com.sophia.documentworkspace.infrastructure.messaging.DomainEventPublisher;
import com.sophia.documentworkspace.infrastructure.messaging.NotificationCommandPayload;
import com.sophia.documentworkspace.infrastructure.messaging.NotificationCommandPublisher;
import com.sophia.documentworkspace.infrastructure.repository.CommentRepository;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private static final String CATEGORY_MENTION = NotificationCommandPayload.CATEGORY_PREFIX + "MENTION";
    private static final String CATEGORY_COMMENT = NotificationCommandPayload.CATEGORY_PREFIX + "COMMENT";

    private final CommentRepository commentRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final ArtifactAccessService artifactAccess;
    private final NotificationCommandPublisher commandPublisher;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public CommentResponse create(AuthenticatedUser user, UUID workspaceId, UUID artifactId,
                                  CreateCommentRequest request) {
        var result = artifactAccess.require(user, workspaceId, artifactId, AccessMode.COMMENT);
        Artifact artifact = result.artifact();

        UUID parentId = null;
        Comment parent = null;
        if (request.parentCommentId() != null) {
            parent = load(workspaceId, request.parentCommentId());
            if (!parent.getArtifactId().equals(artifactId)) {
                throw new BadRequestException("The parent comment belongs to another artifact");
            }
            if (parent.getParentCommentId() != null) {
                throw new BadRequestException(ErrorCode.INVALID_HIERARCHY,
                        "Replies cannot be nested; reply to the thread's top-level comment");
            }
            parentId = parent.getId();
        }
        List<UUID> mentions = validateMentions(workspaceId, request.mentions());

        Comment comment = commentRepository.save(Comment.create(workspaceId, artifactId,
                request.blockId(), parentId, user.id(), request.body(), mentions));

        eventPublisher.publish(CommentEvents.CommentCreated.of(workspaceId, user.id(),
                comment.getId(), artifactId, request.blockId(), parentId, mentions));
        notifyParticipants(user, workspaceId, artifact, comment, parent, mentions);
        return toResponse(comment, List.of());
    }

    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> listThreads(AuthenticatedUser user, UUID workspaceId,
                                                     UUID artifactId, UUID blockId,
                                                     boolean includeResolved, Pageable pageable) {
        artifactAccess.require(user, workspaceId, artifactId, AccessMode.READ);
        Page<Comment> threads = commentRepository.findThreads(artifactId, blockId,
                includeResolved, pageable);

        Map<UUID, List<Comment>> repliesByParent = commentRepository
                .findAllByParentCommentIdInOrderByCreatedAtAsc(
                        threads.getContent().stream().map(Comment::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(Comment::getParentCommentId));

        return PageResponse.from(threads.map(thread -> toResponse(thread,
                repliesByParent.getOrDefault(thread.getId(), List.of()).stream()
                        .map(reply -> toResponse(reply, List.of()))
                        .toList())));
    }

    @Transactional
    public CommentResponse edit(AuthenticatedUser user, UUID workspaceId, UUID commentId,
                                UpdateCommentRequest request) {
        Comment comment = load(workspaceId, commentId);
        artifactAccess.require(user, workspaceId, comment.getArtifactId(), AccessMode.COMMENT);
        if (!comment.getAuthorId().equals(user.id())) {
            throw new ForbiddenOperationException("Only the author can edit a comment");
        }
        if (comment.isDeleted()) {
            throw new BadRequestException("Deleted comments cannot be edited");
        }

        Set<UUID> previousMentions = new HashSet<>(comment.getMentions());
        String before = comment.getBody();
        List<UUID> mentions = validateMentions(workspaceId, request.mentions());
        comment.edit(request.body(), mentions);

        eventPublisher.publish(CommentEvents.CommentUpdated.of(workspaceId, user.id(), commentId,
                comment.getArtifactId(), before));
        for (UUID mentioned : mentions) {
            if (!previousMentions.contains(mentioned) && !mentioned.equals(user.id())) {
                commandPublisher.publish(NotificationCommandPayload.inAppWithEmail(
                        CATEGORY_MENTION, mentioned, user.id(), NotificationType.MENTION.name(),
                        "You were mentioned in a comment", excerpt(request.body()),
                        NotificationCommandComposer.context(workspaceId, comment.getArtifactId()),
                        NotificationCommandPayload.TARGET_ARTIFACT, comment.getArtifactId()));
            }
        }
        return toResponse(comment, List.of());
    }

    @Transactional
    public CommentResponse setResolved(AuthenticatedUser user, UUID workspaceId, UUID commentId,
                                       boolean resolved) {
        Comment comment = load(workspaceId, commentId);
        artifactAccess.require(user, workspaceId, comment.getArtifactId(), AccessMode.COMMENT);
        if (comment.getParentCommentId() != null) {
            throw new BadRequestException("Only top-level comments can be resolved");
        }
        if (resolved) {
            comment.resolve(user.id());
        } else {
            comment.unresolve();
        }
        eventPublisher.publish(CommentEvents.CommentResolved.of(workspaceId, user.id(), commentId,
                comment.getArtifactId(), resolved));
        return toResponse(comment, List.of());
    }

    @Transactional
    public CommentResponse toggleReaction(AuthenticatedUser user, UUID workspaceId, UUID commentId,
                                          String emoji) {
        Comment comment = load(workspaceId, commentId);
        artifactAccess.require(user, workspaceId, comment.getArtifactId(), AccessMode.COMMENT);
        if (comment.isDeleted()) {
            throw new BadRequestException("Deleted comments cannot receive reactions");
        }
        comment.toggleReaction(emoji, user.id());
        return toResponse(comment, List.of());
    }

    @Transactional
    public void delete(AuthenticatedUser user, UUID workspaceId, UUID commentId) {
        Comment comment = load(workspaceId, commentId);
        if (!comment.getAuthorId().equals(user.id())) {
            artifactAccess.require(user, workspaceId, comment.getArtifactId(), AccessMode.DELETE);
        } else {
            artifactAccess.require(user, workspaceId, comment.getArtifactId(), AccessMode.COMMENT);
        }
        comment.erase(user.id());
        eventPublisher.publish(CommentEvents.CommentDeleted.of(workspaceId, user.id(), commentId,
                comment.getArtifactId()));
    }

    private void notifyParticipants(AuthenticatedUser author, UUID workspaceId, Artifact artifact,
                                    Comment comment, Comment parent, List<UUID> mentions) {
        Set<UUID> notified = new HashSet<>();
        notified.add(author.id());

        for (UUID mentioned : mentions) {
            if (notified.add(mentioned)) {
                commandPublisher.publish(NotificationCommandPayload.inAppWithEmail(
                        CATEGORY_MENTION, mentioned, author.id(), NotificationType.MENTION.name(),
                        "You were mentioned in a comment", excerpt(comment.getBody()),
                        NotificationCommandComposer.context(workspaceId, artifact.getId()),
                        NotificationCommandPayload.TARGET_ARTIFACT, artifact.getId()));
            }
        }
        if (parent != null && notified.add(parent.getAuthorId())) {
            commandPublisher.publish(NotificationCommandPayload.inApp(
                    CATEGORY_COMMENT, parent.getAuthorId(), author.id(), NotificationType.COMMENT.name(),
                    "New reply to your comment", excerpt(comment.getBody()),
                    NotificationCommandComposer.context(workspaceId, artifact.getId()),
                    NotificationCommandPayload.TARGET_ARTIFACT, artifact.getId()));
        }
        if (notified.add(artifact.getOwnerId())) {
            commandPublisher.publish(NotificationCommandPayload.inApp(
                    CATEGORY_COMMENT, artifact.getOwnerId(), author.id(), NotificationType.COMMENT.name(),
                    "New comment on " + artifact.getName(), excerpt(comment.getBody()),
                    NotificationCommandComposer.context(workspaceId, artifact.getId()),
                    NotificationCommandPayload.TARGET_ARTIFACT, artifact.getId()));
        }
    }

    private List<UUID> validateMentions(UUID workspaceId, List<UUID> mentions) {
        if (mentions == null || mentions.isEmpty()) {
            return List.of();
        }
        List<UUID> distinct = mentions.stream().distinct().toList();
        for (UUID mentioned : distinct) {
            if (!memberRepository.existsByWorkspaceIdAndUserId(workspaceId, mentioned)) {
                throw new BadRequestException(ErrorCode.VALIDATION_FAILED,
                        "Mentioned user %s is not a member of this workspace".formatted(mentioned));
            }
        }
        return distinct;
    }

    private Comment load(UUID workspaceId, UUID commentId) {
        return commentRepository.findByIdAndWorkspaceId(commentId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));
    }

    private String excerpt(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 140 ? body : body.substring(0, 137) + "...";
    }

    private CommentResponse toResponse(Comment comment, List<CommentResponse> replies) {
        boolean deleted = comment.isDeleted();
        return new CommentResponse(comment.getId(), comment.getWorkspaceId(),
                comment.getArtifactId(), comment.getBlockId(), comment.getParentCommentId(),
                comment.getAuthorId(), deleted ? null : comment.getBody(),
                comment.getMentions(), comment.getReactions(), comment.isEdited(),
                comment.isResolved(), comment.getResolvedBy(), deleted,
                comment.getCreatedAt(), comment.getUpdatedAt(), replies);
    }
}
