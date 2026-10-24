package com.workcollab.controller;

import com.workcollab.entity.Project;
import com.workcollab.entity.Workspace;
import com.workcollab.entity.WorkspaceMember;
import com.workcollab.entity.SystemRole;
import com.workcollab.entity.Role;
import com.workcollab.exception.ResourceNotFoundException;
import com.workcollab.repository.ProjectRepository;
import com.workcollab.repository.WorkspaceMemberRepository;
import com.workcollab.repository.WorkspaceRepository;
import com.workcollab.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createProject(
            @PathVariable UUID workspaceId,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        
        if (userDetails.getSystemRole() != SystemRole.ADMIN) {
            WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userDetails.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Workspace not found or access denied"));
            if (!canManage(member.getRole())) {
                return ResponseEntity.status(403).body(Map.of("message", "Manager access required"));
            }
        }

        Workspace workspace = workspaceRepository.findById(workspaceId).get();

        Project project = Project.builder()
                .workspace(workspace)
                .name(request.get("name"))
                .description(request.get("description"))
                .status(request.getOrDefault("status", "PLANNING"))
                .build();
        
        project = projectRepository.save(project);

        return ResponseEntity.ok(Map.of(
                "id", project.getId(),
                "name", project.getName(),
                "status", project.getStatus()
        ));
    }

    @PutMapping("/{projectId}")
    public ResponseEntity<Map<String, Object>> updateProject(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        
        if (userDetails.getSystemRole() != SystemRole.ADMIN) {
            WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userDetails.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Workspace not found or access denied"));
            if (!canManage(member.getRole())) {
                return ResponseEntity.status(403).body(Map.of("message", "Manager access required"));
            }
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        if (!project.getWorkspace().getId().equals(workspaceId)) {
            throw new ResourceNotFoundException("Project not found in this workspace");
        }

        if (request.containsKey("name")) project.setName(request.get("name"));
        if (request.containsKey("description")) project.setDescription(request.get("description"));
        if (request.containsKey("status")) project.setStatus(request.get("status"));

        project = projectRepository.save(project);

        return ResponseEntity.ok(Map.of(
                "id", project.getId(),
                "name", project.getName(),
                "status", project.getStatus()
        ));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Map<String, Object>> archiveProject(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        
        if (userDetails.getSystemRole() != SystemRole.ADMIN) {
            WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userDetails.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Workspace not found or access denied"));
            if (!canManage(member.getRole())) {
                return ResponseEntity.status(403).body(Map.of("message", "Manager access required"));
            }
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        if (!project.getWorkspace().getId().equals(workspaceId)) {
            throw new ResourceNotFoundException("Project not found in this workspace");
        }

        project.setStatus("ARCHIVED");
        projectRepository.save(project);

        return ResponseEntity.ok(Map.of("message", "Project archived successfully"));
    }

    private boolean canManage(Role role) {
        return role == Role.OWNER || role == Role.ADMIN || role == Role.MANAGER;
    }
}
