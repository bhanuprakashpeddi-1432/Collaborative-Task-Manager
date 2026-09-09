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
public class TaskFilterRequest {
    private Priority priority;
    private UUID assignedToId;
    private ZonedDateTime dueDateStart;
    private ZonedDateTime dueDateEnd;
    private String searchString;
    @Builder.Default
    private int page = 0;
    @Builder.Default
    private int size = 20;
}
