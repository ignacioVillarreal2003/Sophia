package com.sophia.documentworkspace.api.controller;

import com.sophia.documentworkspace.api.security.UserAuthenticationToken;
import com.sophia.documentworkspace.application.security.ArtifactAccessService;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.service.CollaborationService;
import com.sophia.documentworkspace.application.service.DocumentContentService;
import com.sophia.documentworkspace.application.service.DocumentLockService;
import com.sophia.documentworkspace.domain.dto.realtime.AwarenessBroadcast;
import com.sophia.documentworkspace.domain.dto.realtime.AwarenessMessage;
import com.sophia.documentworkspace.domain.dto.realtime.SyncBroadcast;
import com.sophia.documentworkspace.domain.dto.realtime.SyncMessage;
import com.sophia.documentworkspace.domain.entity.DocumentArtifact;
import com.sophia.documentworkspace.domain.entity.enums.AccessMode;
import com.sophia.documentworkspace.domain.event.DocumentEvents;
import com.sophia.documentworkspace.infrastructure.messaging.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.UUID;

/**
 * STOMP handlers of the collaboration protocol. Authentication and per-frame
 * authorization already happened in the channel interceptor; handlers focus on
 * relaying, persisting and confirming.
 */
@Controller
@RequiredArgsConstructor
public class RealtimeController {

    private final SimpMessagingTemplate messagingTemplate;
    private final CollaborationService collaborationService;
    private final DocumentContentService contentService;
    private final DocumentLockService lockService;
    private final ArtifactAccessService artifactAccess;
    private final DomainEventPublisher eventPublisher;

    /** CRDT sync: persist the Yjs update and relay it to every collaborator. */
    @MessageMapping("/workspaces/{workspaceId}/documents/{documentId}/sync")
    public void sync(@DestinationVariable UUID workspaceId,
                     @DestinationVariable UUID documentId,
                     @Payload SyncMessage message,
                     Principal principal) {
        AuthenticatedUser user = userOf(principal);
        lockService.ensureNotLockedByOther(documentId, user.id());
        collaborationService.appendUpdate(user, workspaceId, documentId, message.update());
        messagingTemplate.convertAndSend(
                topic(workspaceId, documentId, "sync"),
                new SyncBroadcast(message.clientId(), message.update(), user.id()));
    }

    /** Ephemeral awareness (cursors, selections): relay only, never persist. */
    @MessageMapping("/workspaces/{workspaceId}/documents/{documentId}/awareness")
    public void awareness(@DestinationVariable UUID workspaceId,
                          @DestinationVariable UUID documentId,
                          @Payload AwarenessMessage message,
                          Principal principal) {
        AuthenticatedUser user = userOf(principal);
        messagingTemplate.convertAndSend(
                topic(workspaceId, documentId, "awareness"),
                new AwarenessBroadcast(message.clientId(), message.payload(), message.cursor(),
                        user.id(), user.email()));
    }

    private AuthenticatedUser userOf(Principal principal) {
        if (principal instanceof UserAuthenticationToken token) {
            return token.getPrincipal();
        }
        throw new IllegalStateException("STOMP session without an authenticated user");
    }

    static String topic(UUID workspaceId, UUID documentId, String channel) {
        return "/topic/workspaces/" + workspaceId + "/documents/" + documentId + "/" + channel;
    }
}
