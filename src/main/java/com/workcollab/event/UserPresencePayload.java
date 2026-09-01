package com.workcollab.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload for {@link CollaborationEventType#USER_PRESENCE} events.
 * Indicates that a user is actively viewing or editing a specific task card,
 * enabling live avatar overlays for collaborators on the same board.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPresencePayload {

    /** The user who is present. */
    private UUID userId;

    /** Display name for avatar rendering. */
    private String fullName;

    /** Avatar URL for the user (may be null). */
    private String avatarUrl;

    /** The task card the user is interacting with. */
    private UUID taskId;

    /** What the user is doing with the task. */
    private PresenceAction action;

    /** When this presence event was emitted. */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * Describes the type of user interaction with a task card.
     */
    public enum PresenceAction {
        /** User has the task card open / in view. */
        VIEWING,
        /** User is actively editing the task card. */
        EDITING,
        /** User closed / navigated away from the task card. */
        LEFT
    }
}
