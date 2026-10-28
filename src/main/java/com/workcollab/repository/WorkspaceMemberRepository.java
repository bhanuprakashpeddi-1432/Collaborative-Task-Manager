package com.workcollab.repository;

import com.workcollab.entity.WorkspaceMember;
import com.workcollab.entity.User;
import com.workcollab.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, UUID> {
    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);
    boolean existsByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);
    List<WorkspaceMember> findByUserId(UUID userId);
    List<WorkspaceMember> findByWorkspaceId(UUID workspaceId);

    @Query("""
            select distinct membership.user
            from WorkspaceMember membership
            where membership.role = :teamMemberRole
              and membership.workspace.id in (
                  select managerMembership.workspace.id
                  from WorkspaceMember managerMembership
                  where managerMembership.user.id = :managerId
                    and managerMembership.role = :managerRole
              )
            order by membership.user.fullName
            """)
    List<User> findAssignedTeamMembers(
            @Param("managerId") UUID managerId,
            @Param("teamMemberRole") Role teamMemberRole,
            @Param("managerRole") Role managerRole);

    @Query("""
            select membership
            from WorkspaceMember membership
            where membership.user.id = :memberId
              and membership.workspace.owner.id = :ownerId
            """)
    List<WorkspaceMember> findByUserIdAndWorkspaceOwnerId(
            @Param("memberId") UUID memberId,
            @Param("ownerId") UUID ownerId);
}
