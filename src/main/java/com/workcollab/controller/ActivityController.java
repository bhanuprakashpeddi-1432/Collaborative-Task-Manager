package com.workcollab.controller;

import com.workcollab.entity.TaskActivity;
import com.workcollab.exception.ResourceNotFoundException;
import com.workcollab.repository.TaskActivityRepository;
import com.workcollab.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks/{taskId}/activity")
@RequiredArgsConstructor
public class ActivityController {

    private final TaskActivityRepository taskActivityRepository;
    private final TaskRepository taskRepository;

    @GetMapping
    @PreAuthorize("@workspaceSecurity.hasTaskAccess(#taskId, 'VIEWER')")
    public List<Map<String, Object>> getTaskActivity(@PathVariable UUID taskId) {
        taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        return taskActivityRepository.findByTaskIdOrderByCreatedAtDesc(taskId).stream()
                .map(this::mapToDto)
                .toList();
    }

    private Map<String, Object> mapToDto(TaskActivity activity) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", activity.getId());
        result.put("userId", activity.getUser() != null ? activity.getUser().getId() : null);
        result.put("userName", activity.getUser() != null ? activity.getUser().getFullName() : "System");
        result.put("actionType", activity.getActionType());
        result.put("details", activity.getDetailsJson() != null ? activity.getDetailsJson() : "{}");
        result.put("createdAt", activity.getCreatedAt());
        return result;
    }
}
