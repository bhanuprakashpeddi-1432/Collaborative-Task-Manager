package com.workcollab.repository;

import com.workcollab.entity.Board;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BoardRepository extends JpaRepository<Board, UUID> {
    List<Board> findByWorkspaceIdOrderByPositionAsc(UUID workspaceId);
}
