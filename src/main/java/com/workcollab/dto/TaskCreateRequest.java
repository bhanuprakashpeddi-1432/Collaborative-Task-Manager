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
public class TaskCreateRequest {

    @NotNull(message = "Project ID is required")
    private UUID projectId;

    private UUID listId; // Optional now that we have status

    private UUID sprintId;

    private UUID parentTaskId;

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    private String status;

    private Priority priority;

    private Double estimatedHours;

    private ZonedDateTime dueDate;

    private UUID assignedToId;
}
