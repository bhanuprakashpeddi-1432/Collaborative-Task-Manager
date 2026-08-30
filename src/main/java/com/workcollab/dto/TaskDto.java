package com.workcollab.dto;

import com.workcollab.entity.Priority;
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
public class TaskDto {
    private UUID id;
    private UUID listId;
    private String title;
    private String description;
    private Priority priority;
    private Double position;
    private ZonedDateTime dueDate;
    private Long version;
    private UUID createdById;
    private UUID assignedToId;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
}
