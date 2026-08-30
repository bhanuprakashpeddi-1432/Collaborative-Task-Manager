package com.workcollab.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskMoveRequest {

    @NotNull(message = "Target List ID is required")
    private UUID targetListId;

    private Double previousPosition;
    
    private Double nextPosition;

    @NotNull(message = "Version is required for optimistic locking")
    private Long version;
}
