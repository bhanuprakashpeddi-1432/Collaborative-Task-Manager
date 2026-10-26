package com.workcollab.controller;

import com.workcollab.entity.Project;
import com.workcollab.entity.TaskList;
import com.workcollab.entity.Workspace;
import com.workcollab.entity.WorkspaceMember;
import com.workcollab.entity.Role;
import com.workcollab.entity.User;
import com.workcollab.entity.SystemRole;
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
        if (userDetails.getSystemRole() == SystemRole.ADMIN) {
            return workspaceRepository.findAll().stream()
                    .map(ws -> Map.<String, Object>of(
                            "id", ws.getId(),
                            "name", ws.getName(),
                            "slug", ws.getSlug(),
                            "role", "ADMIN"
                    ))
                    .toList();
        }

        List<WorkspaceMember> memberships = userDetails.getSystemRole() == SystemRole.ADMIN
                ? workspaceMemberRepository.findAll()
                : workspaceMemberRepository.findByUserId(userDetails.getId());
        return memberships.stream()
                .map(m -> {
                    Workspace ws = m.getWorkspace();
                    return Map.<String, Object>of(
                            "id", ws.getId(),
                            "name", ws.getName(),
                            "slug", ws.getSlug(),
                            "role", userDetails.getSystemRole() == SystemRole.ADMIN
                                    ? "ADMIN" : m.getRole().name()
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
        if (userDetails.getSystemRole() != SystemRole.ADMIN) {
            workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userDetails.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Workspace not found or access denied"));
        }

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
        if (userDetails.getSystemRole() != SystemRole.ADMIN) {
            workspaceMemberRepository.findByWorkspaceIdAndUserId(project.getWorkspace().getId(), userDetails.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Access denied"));
        }

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
        if (userDetails.getSystemRole() != SystemRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("message", "Only administrators can create workspaces"));
        }

        String name = request.get("name");
        String managerId = request.get("managerId");
        if (name == null || name.isBlank() || managerId == null || managerId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Workspace name and manager are required"));
        }

        User manager;
        try {
            manager = userRepository.findById(UUID.fromString(managerId))
                    .orElseThrow(() -> new ResourceNotFoundException("Manager not found"));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", "Manager ID is invalid"));
        }
        if (manager.getSystemRole() != SystemRole.MANAGER) {
            return ResponseEntity.badRequest().body(Map.of("message", "Selected user is not a manager"));
        }

        Workspace workspace = Workspace.builder()
                .name(name.trim())
                .slug(name.trim().toLowerCase().replaceAll("\\s+", "-") + "-" + UUID.randomUUID().toString().substring(0, 5))
                .owner(manager)
                .build();
        workspace = workspaceRepository.save(workspace);

        WorkspaceMember member = WorkspaceMember.builder()
                .workspace(workspace)
                .user(manager)
                .role(Role.MANAGER)
                .build();
        workspaceMemberRepository.save(member);

        return ResponseEntity.ok(Map.of(
                "id", workspace.getId(),
                "name", workspace.getName(),
                "slug", workspace.getSlug(),
                "role", "MANAGER",
                "managerId", manager.getId()
        ));
    }

    @GetMapping("/admin/managers")
    public ResponseEntity<?> getManagers(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        if (userDetails.getSystemRole() != SystemRole.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("message", "Only administrators can view managers"));
        }

        return ResponseEntity.ok(userRepository.findBySystemRole(SystemRole.MANAGER).stream()
                .sorted(java.util.Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(manager -> Map.<String, Object>of(
                        "id", manager.getId(),
                        "fullName", manager.getFullName(),
                        "email", manager.getEmail()
                ))
                .toList());
    }

    @GetMapping("/workspaces/{workspaceId}/members")
    public List<Map<String, Object>> getMembers(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        
        WorkspaceMember currentUserMember = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userDetails.getId())
                .orElse(null);
        if (userDetails.getSystemRole() != SystemRole.ADMIN && currentUserMember == null) {
            throw new ResourceNotFoundException("Access denied");
        }

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
        if (!canManageWorkspace(workspaceId, userDetails)) {
             return ResponseEntity.status(403).body(Map.of("message", "Require ADMIN or OWNER role"));
        }

        User newUser = userRepository.findByEmail(request.get("email"))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
        Role requestedRole;
        try {
            requestedRole = Role.valueOf(request.getOrDefault("role", "TEAM_MEMBER"));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid workspace role"));
        }
        if (userDetails.getSystemRole() != SystemRole.ADMIN && requestedRole != Role.TEAM_MEMBER) {
            return ResponseEntity.status(403).body(Map.of("message", "Managers can only add team members"));
        }

        WorkspaceMember member = WorkspaceMember.builder()
                .workspace(workspace)
                .user(newUser)
                .role(requestedRole)
                .build();
        workspaceMemberRepository.save(member);

        return ResponseEntity.ok(Map.of("message", "Member added"));
    }

    @PutMapping("/workspaces/{workspaceId}")
    public ResponseEntity<?> updateWorkspace(
            @PathVariable UUID workspaceId,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        
        if (!canManageWorkspace(workspaceId, userDetails)) {
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
        
        if (!canManageWorkspace(workspaceId, userDetails)) {
             return ResponseEntity.status(403).body(Map.of("message", "Require ADMIN or OWNER role"));
        }

        WorkspaceMember memberToRemove = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));

        if (memberToRemove.getRole() != Role.TEAM_MEMBER
                && userDetails.getSystemRole() != SystemRole.ADMIN) {
             return ResponseEntity.status(403).body(Map.of("message", "Managers can only remove team members"));
        }

        workspaceMemberRepository.delete(memberToRemove);
        return ResponseEntity.ok(Map.of("message", "Member removed"));
    }

    private boolean canManageWorkspace(UUID workspaceId, UserDetailsImpl userDetails) {
        if (userDetails.getSystemRole() == SystemRole.ADMIN) {
            return true;
        }
        return workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userDetails.getId())
                .map(member -> member.getRole() == Role.MANAGER || member.getRole() == Role.OWNER)
                .orElse(false);
    }
}
