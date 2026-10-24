package com.workcollab.controller;

import com.workcollab.dto.TaskCreateRequest;
import com.workcollab.dto.TaskDto;
import com.workcollab.dto.TaskFilterRequest;
import com.workcollab.dto.TaskMoveRequest;
import com.workcollab.dto.TaskUpdateRequest;
import com.workcollab.security.UserDetailsImpl;
import com.workcollab.entity.Project;
import com.workcollab.exception.ResourceNotFoundException;
import com.workcollab.repository.ProjectRepository;
import com.workcollab.repository.TaskRepository;
import com.workcollab.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@workspaceSecurity.hasAccess(T(java.util.UUID).fromString(#workspaceId), 'MEMBER')")
    public TaskDto createTask(
            @PathVariable UUID projectId,
            @RequestParam("workspaceId") String workspaceId, // Required to check RBAC
            @Valid @RequestBody TaskCreateRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        verifyProjectWorkspace(projectId, workspaceId);
        return taskService.createTask(projectId, request, userDetails.getId());
    }

    @PutMapping("/{taskId}")
    @PreAuthorize("@workspaceSecurity.hasAccess(T(java.util.UUID).fromString(#workspaceId), 'MEMBER')")
    public TaskDto updateTask(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @RequestParam("workspaceId") String workspaceId,
            @Valid @RequestBody TaskUpdateRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        verifyProjectWorkspace(projectId, workspaceId);
        verifyTaskProject(taskId, projectId);
        return taskService.updateTask(taskId, request, userDetails.getId());
    }

    @PatchMapping("/{taskId}/move")
    @PreAuthorize("@workspaceSecurity.hasAccess(T(java.util.UUID).fromString(#workspaceId), 'MEMBER')")
    public TaskDto moveTask(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @RequestParam("workspaceId") String workspaceId,
            @Valid @RequestBody TaskMoveRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        verifyProjectWorkspace(projectId, workspaceId);
        verifyTaskProject(taskId, projectId);
        return taskService.moveTask(taskId, request, userDetails.getId());
    }

    @DeleteMapping("/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@workspaceSecurity.hasAccess(T(java.util.UUID).fromString(#workspaceId), 'MEMBER')")
    public void deleteTask(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @RequestParam("workspaceId") String workspaceId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        verifyProjectWorkspace(projectId, workspaceId);
        verifyTaskProject(taskId, projectId);
        taskService.deleteTask(taskId, userDetails.getId());
    }

    @GetMapping
    @PreAuthorize("@workspaceSecurity.hasAccess(T(java.util.UUID).fromString(#workspaceId), 'VIEWER')")
    public Page<TaskDto> getTasks(
            @PathVariable UUID projectId,
            @RequestParam("workspaceId") String workspaceId,
            TaskFilterRequest filterRequest) {
        verifyProjectWorkspace(projectId, workspaceId);
        return taskService.getTasks(projectId, filterRequest);
    }

    private void verifyProjectWorkspace(UUID projectId, String workspaceId) {
        UUID workspaceUuid;
        try {
            workspaceUuid = UUID.fromString(workspaceId);
        } catch (IllegalArgumentException exception) {
            throw new ResourceNotFoundException("Workspace not found or access denied");
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        if (!project.getWorkspace().getId().equals(workspaceUuid)) {
            throw new ResourceNotFoundException("Project not found in this workspace");
        }
    }

    private void verifyTaskProject(UUID taskId, UUID projectId) {
        if (!taskRepository.findById(taskId)
                .map(task -> task.getProject().getId().equals(projectId))
                .orElse(false)) {
            throw new ResourceNotFoundException("Task not found in this project");
        }
    }
}
