package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.domain.entity.enums.NotificationType;
import com.sophia.documentworkspace.domain.event.MembershipEvents;
import com.sophia.documentworkspace.domain.event.PermissionEvents;
import com.sophia.documentworkspace.infrastructure.messaging.NotificationCommandPayload;
import com.sophia.documentworkspace.infrastructure.messaging.NotificationCommandPublisher;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Turns collaboration domain events into notification commands for the
 * notification microservice. Listeners run synchronously inside the business
 * transaction so the command reaches the outbox atomically with the change;
 * comment and mention notifications are composed by the comment service, which
 * knows the mentioned users.
 */
@Component
@RequiredArgsConstructor
public class NotificationCommandComposer {

    static final String CATEGORY_WORKSPACE = NotificationCommandPayload.CATEGORY_PREFIX + "WORKSPACE";
    static final String CATEGORY_SHARING = NotificationCommandPayload.CATEGORY_PREFIX + "SHARING";

    private final NotificationCommandPublisher commandPublisher;
    private final WorkspaceRepository workspaceRepository;

    @EventListener
    public void onMemberAdded(MembershipEvents.MemberAdded event) {
        if (event.userId().equals(event.actorId())) {
            return; // Self-joins (workspace creation) do not notify.
        }
        String workspaceName = workspaceName(event.workspaceId());
        commandPublisher.publish(NotificationCommandPayload.inApp(
                CATEGORY_WORKSPACE, event.userId(), event.actorId(), NotificationType.MEMBERSHIP.name(),
                "You were added to " + workspaceName,
                "You joined the workspace as " + event.role(),
                context(event.workspaceId(), null),
                NotificationCommandPayload.TARGET_WORKSPACE, event.workspaceId()));
    }

    @EventListener
    public void onMemberRemoved(MembershipEvents.MemberRemoved event) {
        if (event.left()) {
            return; // Leaving voluntarily notifies nobody.
        }
        commandPublisher.publish(NotificationCommandPayload.inApp(
                CATEGORY_WORKSPACE, event.userId(), event.actorId(), NotificationType.MEMBERSHIP.name(),
                "Removed from " + workspaceName(event.workspaceId()),
                "Your membership in this workspace was removed",
                context(event.workspaceId(), null),
                NotificationCommandPayload.TARGET_WORKSPACE, event.workspaceId()));
    }

    @EventListener
    public void onAclGranted(PermissionEvents.AclGranted event) {
        if (event.subjectUserId().equals(event.actorId())) {
            return;
        }
        commandPublisher.publish(NotificationCommandPayload.inApp(
                CATEGORY_SHARING, event.subjectUserId(), event.actorId(), NotificationType.SHARED.name(),
                "Content shared with you",
                "You were granted " + event.modes() + " access",
                context(event.workspaceId(), event.resourceId()),
                event.resourceId() != null
                        ? NotificationCommandPayload.TARGET_ARTIFACT : NotificationCommandPayload.TARGET_WORKSPACE,
                event.resourceId() != null ? event.resourceId() : event.workspaceId()));
    }

    static Map<String, String> context(UUID workspaceId, UUID artifactId) {
        Map<String, String> context = new HashMap<>();
        if (workspaceId != null) {
            context.put("workspaceId", workspaceId.toString());
        }
        if (artifactId != null) {
            context.put("artifactId", artifactId.toString());
        }
        return context;
    }

    private String workspaceName(UUID workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .map(workspace -> workspace.getName())
                .orElse("a workspace");
    }
}
