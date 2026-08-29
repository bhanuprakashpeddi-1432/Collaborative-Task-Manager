package com.workcollab.repository;

import com.workcollab.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {
    List<Task> findByListIdOrderByPositionAsc(UUID listId);
}
