package com.workcollab.controller;

import com.workcollab.dto.TaskCreateRequest;
import com.workcollab.dto.TaskDto;
import com.workcollab.dto.TaskFilterRequest;
import com.workcollab.dto.TaskMoveRequest;
import com.workcollab.dto.TaskUpdateRequest;
import com.workcollab.security.UserDetailsImpl;
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
@RequestMapping("/api/boards/{boardId}/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@workspaceSecurity.hasAccess(T(java.util.UUID).fromString(#workspaceId), 'MEMBER')")
    public TaskDto createTask(
            @PathVariable UUID boardId,
            @RequestParam("workspaceId") String workspaceId, // Required to check RBAC
            @Valid @RequestBody TaskCreateRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        
        return taskService.createTask(boardId, request, userDetails.getId());
    }

    @PutMapping("/{taskId}")
    @PreAuthorize("@workspaceSecurity.hasAccess(T(java.util.UUID).fromString(#workspaceId), 'MEMBER')")
    public TaskDto updateTask(
            @PathVariable UUID boardId,
            @PathVariable UUID taskId,
            @RequestParam("workspaceId") String workspaceId,
            @Valid @RequestBody TaskUpdateRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        
        return taskService.updateTask(taskId, request, userDetails.getId());
    }

    @PatchMapping("/{taskId}/move")
    @PreAuthorize("@workspaceSecurity.hasAccess(T(java.util.UUID).fromString(#workspaceId), 'MEMBER')")
    public TaskDto moveTask(
            @PathVariable UUID boardId,
            @PathVariable UUID taskId,
            @RequestParam("workspaceId") String workspaceId,
            @Valid @RequestBody TaskMoveRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        
        return taskService.moveTask(taskId, request, userDetails.getId());
    }

    @DeleteMapping("/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@workspaceSecurity.hasAccess(T(java.util.UUID).fromString(#workspaceId), 'MEMBER')")
    public void deleteTask(
            @PathVariable UUID boardId,
            @PathVariable UUID taskId,
            @RequestParam("workspaceId") String workspaceId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        
        taskService.deleteTask(taskId, userDetails.getId());
    }

    @GetMapping
    @PreAuthorize("@workspaceSecurity.hasAccess(T(java.util.UUID).fromString(#workspaceId), 'VIEWER')")
    public Page<TaskDto> getTasks(
            @PathVariable UUID boardId,
            @RequestParam("workspaceId") String workspaceId,
            TaskFilterRequest filterRequest) {
        
        return taskService.getTasks(boardId, filterRequest);
    }
}
