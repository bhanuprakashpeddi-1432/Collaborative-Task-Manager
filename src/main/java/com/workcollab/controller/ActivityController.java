package com.workcollab.controller;

import com.workcollab.entity.TaskActivity;
import com.workcollab.repository.TaskActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tasks/{taskId}/activity")
@RequiredArgsConstructor
public class ActivityController {

    private final TaskActivityRepository taskActivityRepository;

    @GetMapping
    public List<Map<String, Object>> getTaskActivity(@PathVariable UUID taskId) {
        return taskActivityRepository.findAll().stream()
                .filter(a -> a.getTask().getId().equals(taskId)) // In-memory filter for now due to missing repo method in demo
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private Map<String, Object> mapToDto(TaskActivity activity) {
        return Map.of(
                "id", activity.getId(),
                "userId", activity.getUser() != null ? activity.getUser().getId() : null,
                "actionType", activity.getActionType(),
                "details", activity.getDetailsJson() != null ? activity.getDetailsJson() : "{}",
                "createdAt", activity.getCreatedAt().toString()
        );
    }
}
