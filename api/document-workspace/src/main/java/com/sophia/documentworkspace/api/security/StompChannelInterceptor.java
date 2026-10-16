package com.sophia.documentworkspace.api.security;

import com.sophia.documentworkspace.application.security.ArtifactAccessService;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.domain.entity.enums.AccessMode;
import com.sophia.documentworkspace.domain.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authenticates and authorizes every STOMP frame:
 * <ul>
 *   <li><b>CONNECT</b>: validates the JWT from the {@code Authorization} native
 *       header (same token the REST API uses) and binds the resulting
 *       {@link UserAuthenticationToken} to the WebSocket session.</li>
 *   <li><b>SUBSCRIBE</b>: requires READ on the document of the destination.</li>
 *   <li><b>SEND</b>: requires WRITE for {@code sync}/{@code operations}
 *       destinations and READ for {@code awareness}.</li>
 * </ul>
 * A denied frame raises an exception that Spring turns into a STOMP ERROR.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompChannelInterceptor implements ChannelInterceptor {

    private static final Pattern DOCUMENT_DESTINATION = Pattern.compile(
            "^/(?:topic|app)/workspaces/([0-9a-fA-F-]{36})/documents/([0-9a-fA-F-]{36})/(sync|awareness|operations|presence)$");

    private final JwtDecoder jwtDecoder;
    private final ArtifactAccessService artifactAccess;

    private final JsonWebTokenUserAuthenticationConverter converter = new JsonWebTokenUserAuthenticationConverter();

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> authorize(accessor, accessor.getDestination(), true);
            case SEND -> authorize(accessor, accessor.getDestination(), false);
            default -> {
                // Other frames (ACK, DISCONNECT, heartbeats) need no checks.
            }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing bearer token on STOMP CONNECT");
        }
        try {
            Jwt jwt = jwtDecoder.decode(authorization.substring("Bearer ".length()));
            accessor.setUser(converter.convert(jwt));
        } catch (JwtException e) {
            log.debug("Rejected STOMP CONNECT: {}", e.getMessage());
            throw new UnauthorizedException("Invalid or expired token");
        }
    }

    private void authorize(StompHeaderAccessor accessor, String destination, boolean subscribe) {
        if (destination == null) {
            throw new UnauthorizedException("Frame without destination");
        }
        if (!(accessor.getUser() instanceof UserAuthenticationToken token)) {
            throw new UnauthorizedException("Session is not authenticated");
        }
        AuthenticatedUser user = token.getPrincipal();

        Matcher matcher = DOCUMENT_DESTINATION.matcher(destination);
        if (!matcher.matches()) {
            throw new UnauthorizedException("Unknown destination: " + destination);
        }
        UUID workspaceId = UUID.fromString(matcher.group(1));
        UUID documentId = UUID.fromString(matcher.group(2));
        String channelName = matcher.group(3);

        AccessMode mode = subscribe || "awareness".equals(channelName)
                ? AccessMode.READ
                : AccessMode.WRITE;
        artifactAccess.require(user, workspaceId, documentId, mode);
    }
}
