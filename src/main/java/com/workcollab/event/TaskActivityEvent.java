package com.workcollab.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskActivityEvent {
    private UUID taskId;
    private UUID userId;
    private String actionType;
    private String detailsJson;
}
