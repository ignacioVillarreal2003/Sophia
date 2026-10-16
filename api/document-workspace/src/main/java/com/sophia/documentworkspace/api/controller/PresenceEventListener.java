package com.sophia.documentworkspace.api.controller;

import com.sophia.documentworkspace.api.security.UserAuthenticationToken;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.service.PresenceService;
import com.sophia.documentworkspace.domain.dto.realtime.PresenceEvent;
import com.sophia.documentworkspace.domain.dto.realtime.PresenceUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Presence lifecycle driven by STOMP session events: subscribing to a
 * document's sync topic marks the user active; a disconnect clears every
 * document the session was attached to and notifies collaborators.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceEventListener {

    private static final Pattern SYNC_TOPIC = Pattern.compile(
            "^/topic/workspaces/([0-9a-fA-F-]{36})/documents/([0-9a-fA-F-]{36})/sync$");

    private final PresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        String sessionId = accessor.getSessionId();
        if (destination == null || sessionId == null
                || !(accessor.getUser() instanceof UserAuthenticationToken token)) {
            return;
        }
        Matcher matcher = SYNC_TOPIC.matcher(destination);
        if (!matcher.matches()) {
            return;
        }
        AuthenticatedUser user = token.getPrincipal();
        UUID workspaceId = UUID.fromString(matcher.group(1));
        UUID documentId = UUID.fromString(matcher.group(2));

        PresenceUser presenceUser = presenceService.join(sessionId, workspaceId, documentId,
                user.id(), user.email());
        messagingTemplate.convertAndSend(
                RealtimeController.topic(workspaceId, documentId, "presence"),
                new PresenceEvent(PresenceEvent.Type.JOINED, presenceUser));
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        for (String attachment : presenceService.sessionAttachments(sessionId)) {
            String[] parts = attachment.split("\\|", 4);
            if (parts.length < 3) {
                continue;
            }
            UUID workspaceId = UUID.fromString(parts[0]);
            UUID documentId = UUID.fromString(parts[1]);
            UUID userId = UUID.fromString(parts[2]);
            String email = parts.length > 3 ? parts[3] : null;

            presenceService.leave(documentId, userId);
            messagingTemplate.convertAndSend(
                    RealtimeController.topic(workspaceId, documentId, "presence"),
                    new PresenceEvent(PresenceEvent.Type.LEFT,
                            new PresenceUser(userId, email, Instant.now())));
        }
        presenceService.clearSession(sessionId);
    }
}
