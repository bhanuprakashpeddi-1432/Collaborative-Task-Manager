package com.workcollab.config;

import com.workcollab.entity.Role;
import com.workcollab.entity.User;
import com.workcollab.entity.Workspace;
import com.workcollab.entity.WorkspaceMember;
import com.workcollab.repository.UserRepository;
import com.workcollab.repository.WorkspaceMemberRepository;
import com.workcollab.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.count() == 0) {
            log.info("Database is empty. Seeding default user and workspace...");

            User user = User.builder()
                    .email("tester@workcollab.dev")
                    .passwordHash(passwordEncoder.encode("password123"))
                    .fullName("Test User")
                    .build();
            user = userRepository.save(user);

            Workspace workspace = Workspace.builder()
                    .name("Engineering Workspace")
                    .slug("engineering-workspace")
                    .owner(user)
                    .build();
            workspace = workspaceRepository.save(workspace);

            WorkspaceMember member = WorkspaceMember.builder()
                    .workspace(workspace)
                    .user(user)
                    .role(Role.ADMIN)
                    .build();
            workspaceMemberRepository.save(member);

            log.info("Default user created! Email: tester@workcollab.dev | Password: password123");
        }
    }
}   
