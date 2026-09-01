package com.workcollab.controller;

import com.workcollab.collaboration.RedisCollaborationPublisher;
import com.workcollab.dto.PresenceRequest;
import com.workcollab.event.CollaborationEventType;
import com.workcollab.event.CollaborationMessage;
import com.workcollab.event.UserPresencePayload;
import com.workcollab.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;

/**
 * STOMP controller for user presence and heartbeat management.
 *
 * <p>Clients send heartbeat messages to {@code /app/presence.heartbeat} to
 * indicate they are actively viewing or editing a specific task card. These
 * are broadcast to all subscribers of the board topic so that collaborators
 * can display live avatars.</p>
 *
 * <p>Presence events are <strong>non-transactional</strong> — they bypass
 * {@code @TransactionalEventListener} and are published directly to Redis
 * for immediate fan-out.</p>
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class PresenceController {

    private final RedisCollaborationPublisher redisPublisher;

    /**
     * Handles presence heartbeat messages from WebSocket clients.
     *
     * @param request   the presence payload (workspaceId, boardId, taskId, action)
     * @param principal the authenticated STOMP user (set by JwtChannelInterceptor)
     */
    @MessageMapping("/presence.heartbeat")
    public void handlePresenceHeartbeat(@Payload PresenceRequest request, Principal principal) {
        UserDetailsImpl user = extractUser(principal);
        if (user == null) {
            log.warn("Presence heartbeat from unauthenticated session — ignoring");
            return;
        }

        log.debug("Presence heartbeat: user {} is {} task {} on board {}",
                user.getId(), request.getAction(), request.getTaskId(), request.getBoardId());

        UserPresencePayload presencePayload = UserPresencePayload.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .taskId(request.getTaskId())
                .action(request.getAction())
                .timestamp(Instant.now())
                .build();

        CollaborationMessage message = CollaborationMessage.builder()
                .workspaceId(request.getWorkspaceId())
                .boardId(request.getBoardId())
                .eventType(CollaborationEventType.USER_PRESENCE)
                .payload(presencePayload)
                .triggeredBy(user.getId())
                .timestamp(Instant.now())
                .build();

        redisPublisher.publish(message);
    }

    private UserDetailsImpl extractUser(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof UserDetailsImpl userDetails) {
            return userDetails;
        }
        return null;
    }
}
