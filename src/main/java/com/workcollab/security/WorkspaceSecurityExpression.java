package com.workcollab.security;

import com.workcollab.entity.Role;
import com.workcollab.entity.SystemRole;
import com.workcollab.repository.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("workspaceSecurity")
@RequiredArgsConstructor
public class WorkspaceSecurityExpression {

    private final WorkspaceMemberRepository workspaceMemberRepository;

    public boolean hasAccess(UUID workspaceId, String requiredRole) {
        Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal().equals("anonymousUser")) {
            return false;
        }

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        if (userDetails.getSystemRole() == SystemRole.ADMIN) {
            return true;
        }
        UUID userId = userDetails.getId();

        return workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .map(member -> {
                    Role userRole = member.getRole();
                    return switch (requiredRole.toUpperCase()) {
                        case "ADMIN" -> userRole == Role.ADMIN || userRole == Role.OWNER || userRole == Role.MANAGER;
                        case "MEMBER" -> userRole == Role.ADMIN || userRole == Role.OWNER
                                || userRole == Role.MANAGER || userRole == Role.MEMBER || userRole == Role.TEAM_MEMBER;
                        case "VIEWER" -> true; // Any member can view
                        default -> false;
                    };
                })
                .orElse(false);
    }
}
