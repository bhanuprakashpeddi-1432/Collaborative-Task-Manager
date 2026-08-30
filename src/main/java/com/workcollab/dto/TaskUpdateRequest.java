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

    @NotBlank(message = "Title cannot be blank")
    private String title;

    private String description;

    private Priority priority;

    private ZonedDateTime dueDate;

    private UUID assignedToId;

    @NotNull(message = "Version is required for optimistic locking")
    private Long version;
}
