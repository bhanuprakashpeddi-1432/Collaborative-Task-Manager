package com.workcollab.event;

import com.workcollab.collaboration.RedisCollaborationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for {@link CollaborationEvent} instances published by service-layer
 * methods and converts them to {@link CollaborationMessage} for Redis fan-out.
 *
 * <p>The listener is bound to {@code TransactionPhase.AFTER_COMMIT}, which
 * guarantees that WebSocket messages are <strong>never</strong> broadcast for
 * rolled-back transactions. This is critical for data consistency — clients
 * must not see phantom events for operations that ultimately failed.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CollaborationEventListener {

    private final RedisCollaborationPublisher redisPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCollaborationEvent(CollaborationEvent event) {
        log.info("Post-commit collaboration event [{}] for board {} in workspace {}",
                event.getEventType(), event.getBoardId(), event.getWorkspaceId());

        CollaborationMessage message = CollaborationMessage.builder()
                .workspaceId(event.getWorkspaceId())
                .boardId(event.getBoardId())
                .eventType(event.getEventType())
                .payload(event.getPayload())
                .triggeredBy(event.getTriggeredBy())
                .timestamp(event.getTimestamp())
                .build();

        redisPublisher.publish(message);
    }
}
