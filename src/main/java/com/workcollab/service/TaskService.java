package com.workcollab.service;

import com.workcollab.dto.TaskCreateRequest;
import com.workcollab.dto.TaskDto;
import com.workcollab.dto.TaskFilterRequest;
import com.workcollab.dto.TaskMoveRequest;
import com.workcollab.dto.TaskUpdateRequest;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface TaskService {
    TaskDto createTask(UUID boardId, TaskCreateRequest request, UUID userId);
    TaskDto updateTask(UUID taskId, TaskUpdateRequest request, UUID userId);
    TaskDto moveTask(UUID taskId, TaskMoveRequest request, UUID userId);
    void deleteTask(UUID taskId, UUID userId);
    Page<TaskDto> getTasks(UUID boardId, TaskFilterRequest filterRequest);
}
