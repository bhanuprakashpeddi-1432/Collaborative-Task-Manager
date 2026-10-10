package com.workcollab.dto;

import com.workcollab.entity.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskUpdateRequest {

    private String title;

    private String description;

    private UUID listId;

    private UUID sprintId;

    private UUID parentTaskId;

    private String status;

    private Priority priority;

    private Double estimatedHours;

    private Double actualHours;

    private Integer progress;

    private ZonedDateTime dueDate;

    private UUID assignedToId;

    @NotNull(message = "Version is required for optimistic locking")
    private Long version;
}
