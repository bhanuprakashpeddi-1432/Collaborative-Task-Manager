package com.workcollab.repository;

import com.workcollab.entity.TaskList;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaskListRepository extends JpaRepository<TaskList, UUID> {
    List<TaskList> findByBoardIdOrderByPositionAsc(UUID boardId);
}
