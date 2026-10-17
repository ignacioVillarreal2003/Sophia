package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.domain.entity.enums.NotificationType;
import com.sophia.documentworkspace.infrastructure.messaging.NotificationCommandPayload;
import com.sophia.documentworkspace.infrastructure.messaging.NotificationCommandPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Sends one in-app notification about a workspace; it joins the caller's transaction through the outbox. */
@Component
@RequiredArgsConstructor
public class WorkspaceNotifier {

    private final NotificationCommandPublisher commandPublisher;

    public void notify(UUID recipientId, UUID actorId, String title, String body, UUID workspaceId) {
        commandPublisher.publish(NotificationCommandPayload.inApp(
                NotificationCommandComposer.CATEGORY_WORKSPACE, recipientId, actorId,
                NotificationType.MEMBERSHIP.name(), title, body,
                NotificationCommandComposer.context(workspaceId, null),
                NotificationCommandPayload.TARGET_WORKSPACE, workspaceId));
    }
}
