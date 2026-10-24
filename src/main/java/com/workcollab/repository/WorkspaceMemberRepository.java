package com.workcollab.repository;

import com.workcollab.entity.WorkspaceMember;
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
            select membership
            from WorkspaceMember membership
            where membership.user.id = :memberId
              and membership.workspace.owner.id = :ownerId
            """)
    List<WorkspaceMember> findByUserIdAndWorkspaceOwnerId(
            @Param("memberId") UUID memberId,
            @Param("ownerId") UUID ownerId);
}
