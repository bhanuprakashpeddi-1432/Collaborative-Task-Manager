package com.workcollab.repository;

import com.workcollab.entity.User;
import com.workcollab.entity.SystemRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findBySystemRole(SystemRole systemRole);
}
