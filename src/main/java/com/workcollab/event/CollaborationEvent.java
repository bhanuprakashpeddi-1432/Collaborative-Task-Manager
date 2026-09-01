package com.workcollab.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Spring application event fired within a {@code @Transactional} service method
 * after a task mutation (create / update / move / delete).
 *
 * <p>This event is consumed by {@link CollaborationEventListener} which listens
 * on {@code @TransactionalEventListener(phase = AFTER_COMMIT)}, ensuring that
 * the WebSocket broadcast only happens after the database transaction commits.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollaborationEvent {

    /** Workspace ID — used for topic routing. */
    private UUID workspaceId;

    /** Board ID — used for topic routing. */
    private UUID boardId;

    /** The type of collaboration event. */
    private CollaborationEventType eventType;

    /**
     * The event payload. Typically a {@link com.workcollab.dto.TaskDto} for
     * task mutations, or a delete summary map for {@code TASK_DELETED}.
     */
    private Object payload;

    /** The UUID of the user who triggered the event. */
    private UUID triggeredBy;

    /** Timestamp when the event was created. */
    @Builder.Default
    private Instant timestamp = Instant.now();
}
