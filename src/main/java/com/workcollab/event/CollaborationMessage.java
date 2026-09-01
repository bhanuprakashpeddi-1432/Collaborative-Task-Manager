package com.workcollab.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire-format DTO that is serialized to JSON and transmitted over both
 * Redis Pub/Sub (for multi-node fan-out) and the STOMP WebSocket topic
 * (for client delivery).
 *
 * <p>Unlike {@link CollaborationEvent}, this is a plain POJO — not a
 * Spring {@code ApplicationEvent} — and is designed to be safely
 * serialized/deserialized by Jackson across network boundaries.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollaborationMessage {

    /** Workspace ID for topic routing. */
    private UUID workspaceId;

    /** Board ID for topic routing. */
    private UUID boardId;

    /** The type of collaboration event. */
    private CollaborationEventType eventType;

    /**
     * JSON-serializable payload. The concrete type depends on {@link #eventType}:
     * <ul>
     *   <li>{@code TASK_CREATED / TASK_UPDATED / TASK_MOVED} → TaskDto</li>
     *   <li>{@code TASK_DELETED} → Map with taskId, listId</li>
     *   <li>{@code USER_PRESENCE} → UserPresencePayload</li>
     * </ul>
     */
    private Object payload;

    /** UUID of the user who triggered this event. */
    private UUID triggeredBy;

    /** When the event occurred. */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * Computes the STOMP topic destination for this message.
     *
     * @return destination in the format {@code /topic/workspace.{wId}.boards.{bId}}
     */
    public String toDestination() {
        return String.format("/topic/workspace.%s.boards.%s", workspaceId, boardId);
    }
}
