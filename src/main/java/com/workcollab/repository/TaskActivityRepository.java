package com.workcollab.repository;

import com.workcollab.entity.TaskActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaskActivityRepository extends JpaRepository<TaskActivity, UUID> {
    List<TaskActivity> findByTaskIdOrderByCreatedAtDesc(UUID taskId);
}
