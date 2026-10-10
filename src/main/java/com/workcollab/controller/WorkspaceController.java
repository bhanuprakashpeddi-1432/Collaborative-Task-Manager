package com.workcollab.controller;

import com.workcollab.entity.Project;
import com.workcollab.entity.TaskList;
import com.workcollab.entity.Workspace;
import com.workcollab.entity.WorkspaceMember;
import com.workcollab.entity.Role;
import com.workcollab.entity.User;
import com.workcollab.exception.ResourceNotFoundException;
import com.workcollab.repository.ProjectRepository;
import com.workcollab.repository.TaskListRepository;
import com.workcollab.repository.WorkspaceMemberRepository;
import com.workcollab.repository.WorkspaceRepository;
import com.workcollab.repository.UserRepository;
import com.workcollab.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ProjectRepository projectRepository;
    private final TaskListRepository taskListRepository;
    private final UserRepository userRepository;

    /**
     * Returns all workspaces the authenticated user is a member of.
     */
    @GetMapping("/workspaces")
    public List<Map<String, Object>> getMyWorkspaces(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        List<WorkspaceMember> memberships = workspaceMemberRepository.findByUserId(userDetails.getId());
        return memberships.stream()
                .map(m -> {
                    Workspace ws = m.getWorkspace();
                    return Map.<String, Object>of(
                            "id", ws.getId(),
                            "name", ws.getName(),
                            "slug", ws.getSlug(),
                            "role", m.getRole().name()
                    );
                })
                .toList();
    }

    /**
     * Returns all projects within a workspace.
     */
    @GetMapping("/workspaces/{workspaceId}/projects")
    public List<Map<String, Object>> getProjects(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        // Verify membership
        workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found or access denied"));

        List<Project> projects = projectRepository.findByWorkspaceIdOrderByCreatedAtAsc(workspaceId);
        return projects.stream()
                .map(p -> Map.<String, Object>of(
                        "id", p.getId(),
                        "workspaceId", p.getWorkspace().getId(),
                        "name", p.getName(),
                        "status", p.getStatus(),
                        "createdAt", p.getCreatedAt().toString()
                ))
                .toList();
    }

    /**
     * Returns project details along with its lists.
     */
    @GetMapping("/projects/{projectId}")
    public Map<String, Object> getProjectWithLists(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        // Verify membership
        workspaceMemberRepository.findByWorkspaceIdAndUserId(project.getWorkspace().getId(), userDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Access denied"));

        List<TaskList> lists = taskListRepository.findByProjectIdOrderByPositionAsc(projectId);

        List<Map<String, Object>> listDtos = lists.stream()
                .map(l -> Map.<String, Object>of(
                        "id", l.getId(),
                        "projectId", l.getProject().getId(),
                        "name", l.getName(),
                        "position", l.getPosition()
                ))
                .toList();

        Map<String, Object> projectDto = Map.of(
                "id", project.getId(),
                "workspaceId", project.getWorkspace().getId(),
                "name", project.getName(),
                "status", project.getStatus(),
                "createdAt", project.getCreatedAt().toString()
        );

        return Map.of(
                "project", projectDto,
                "lists", listDtos
        );
    }
    
    @PostMapping("/workspaces")
    public ResponseEntity<Map<String, Object>> createWorkspace(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        User user = userRepository.findById(userDetails.getId()).get();
        Workspace workspace = Workspace.builder()
                .name(request.get("name"))
                .slug(request.get("name").toLowerCase().replaceAll("\\s+", "-") + "-" + UUID.randomUUID().toString().substring(0, 5))
                .owner(user)
                .build();
        workspace = workspaceRepository.save(workspace);

        WorkspaceMember member = WorkspaceMember.builder()
                .workspace(workspace)
                .user(user)
                .role(Role.OWNER)
                .build();
        workspaceMemberRepository.save(member);

        return ResponseEntity.ok(Map.of(
                "id", workspace.getId(),
                "name", workspace.getName(),
                "slug", workspace.getSlug(),
                "role", "OWNER"
        ));
    }

    @GetMapping("/workspaces/{workspaceId}/members")
    public List<Map<String, Object>> getMembers(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        
        workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Access denied"));

        return workspaceMemberRepository.findByWorkspaceId(workspaceId).stream()
                .map(m -> Map.<String, Object>of(
                        "id", m.getUser().getId(),
                        "fullName", m.getUser().getFullName(),
                        "email", m.getUser().getEmail(),
                        "role", m.getRole().name()
                ))
                .toList();
    }

    @PostMapping("/workspaces/{workspaceId}/members")
    public ResponseEntity<?> addMember(
            @PathVariable UUID workspaceId,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        // Simple implementation, need proper RBAC check here
        WorkspaceMember currentUserMember = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Access denied"));
        
        if (currentUserMember.getRole() != Role.OWNER && currentUserMember.getRole() != Role.ADMIN) {
             return ResponseEntity.status(403).body(Map.of("message", "Require ADMIN or OWNER role"));
        }

        User newUser = userRepository.findByEmail(request.get("email"))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Workspace workspace = workspaceRepository.findById(workspaceId).get();

        WorkspaceMember member = WorkspaceMember.builder()
                .workspace(workspace)
                .user(newUser)
                .role(Role.valueOf(request.getOrDefault("role", "MEMBER")))
                .build();
        workspaceMemberRepository.save(member);

        return ResponseEntity.ok(Map.of("message", "Member added"));
    }

    @PutMapping("/workspaces/{workspaceId}")
    public ResponseEntity<?> updateWorkspace(
            @PathVariable UUID workspaceId,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        
        WorkspaceMember currentUserMember = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Access denied"));
        
        if (currentUserMember.getRole() != Role.OWNER && currentUserMember.getRole() != Role.ADMIN) {
             return ResponseEntity.status(403).body(Map.of("message", "Require ADMIN or OWNER role"));
        }

        Workspace workspace = workspaceRepository.findById(workspaceId).get();
        if(request.containsKey("name")) workspace.setName(request.get("name"));
        // Additional settings could be updated here
        workspaceRepository.save(workspace);

        return ResponseEntity.ok(Map.of("message", "Workspace updated"));
    }

    @DeleteMapping("/workspaces/{workspaceId}/members/{userId}")
    public ResponseEntity<?> removeMember(
            @PathVariable UUID workspaceId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        
        WorkspaceMember currentUserMember = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Access denied"));
        
        if (currentUserMember.getRole() != Role.OWNER && currentUserMember.getRole() != Role.ADMIN) {
             return ResponseEntity.status(403).body(Map.of("message", "Require ADMIN or OWNER role"));
        }

        WorkspaceMember memberToRemove = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));

        if (memberToRemove.getRole() == Role.OWNER && currentUserMember.getRole() != Role.OWNER) {
             return ResponseEntity.status(403).body(Map.of("message", "Only OWNER can remove another OWNER"));
        }

        workspaceMemberRepository.delete(memberToRemove);
        return ResponseEntity.ok(Map.of("message", "Member removed"));
    }
}
