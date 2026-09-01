package com.workcollab.dto;

import com.workcollab.event.UserPresencePayload;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Inbound DTO for STOMP presence heartbeat messages sent by clients
 * to {@code /app/presence.heartbeat}.
 *
 * <p>Clients send this payload periodically (e.g., every 30 seconds) to
 * indicate they are viewing or editing a specific task card.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PresenceRequest {

    @NotNull
    private UUID workspaceId;

    @NotNull
    private UUID boardId;

    @NotNull
    private UUID taskId;

    @NotNull
    private UserPresencePayload.PresenceAction action;
}
