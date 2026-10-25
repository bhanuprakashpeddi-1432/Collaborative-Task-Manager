package com.workcollab.config;

import com.workcollab.entity.*;
import com.workcollab.repository.*;
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
    private final ProjectRepository projectRepository;
    private final TaskListRepository taskListRepository;
    private final TaskRepository taskRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.count() == 0) {
            log.info("Database is empty. Seeding default user, workspace, board, lists, and sample tasks...");

            // Create user
            User user = User.builder()
                    .email("tester@workcollab.dev")
                    .passwordHash(passwordEncoder.encode("password123"))
                    .fullName("Test User")
                    .systemRole(SystemRole.ADMIN)
                    .build();
            user = userRepository.save(user);

            // Create workspace
            Workspace workspace = Workspace.builder()
                    .name("Engineering Workspace")
                    .slug("engineering-workspace")
                    .owner(user)
                    .build();
            workspace = workspaceRepository.save(workspace);

            // Add user as admin
            WorkspaceMember member = WorkspaceMember.builder()
                    .workspace(workspace)
                    .user(user)
                    .role(Role.ADMIN)
                    .build();
            workspaceMemberRepository.save(member);

            // Create a default project
            Project project = Project.builder()
                    .workspace(workspace)
                    .name("Engineering Sprint")
                    .status("ACTIVE")
                    .build();
            project = projectRepository.save(project);

            // Create task lists
            TaskList todoList = TaskList.builder()
                    .project(project)
                    .name("To Do")
                    .position(1.0)
                    .build();
            todoList = taskListRepository.save(todoList);

            TaskList inProgressList = TaskList.builder()
                    .project(project)
                    .name("In Progress")
                    .position(2.0)
                    .build();
            inProgressList = taskListRepository.save(inProgressList);

            TaskList doneList = TaskList.builder()
                    .project(project)
                    .name("Done")
                    .position(3.0)
                    .build();
            doneList = taskListRepository.save(doneList);

            // Create sample tasks
            Task task1 = Task.builder()
                    .project(project)
                    .list(todoList)
                    .title("Set up CI/CD pipeline")
                    .description("Configure GitHub Actions for automated builds, testing, and deployment to staging.")
                    .priority(Priority.HIGH)
                    .position(65536.0)
                    .version(0L)
                    .createdBy(user)
                    .build();
            taskRepository.save(task1);

            Task task2 = Task.builder()
                    .project(project)
                    .list(todoList)
                    .title("Design database schema")
                    .description("Create ERD and define all entity relationships for the collaboration module.")
                    .priority(Priority.MEDIUM)
                    .position(131072.0)
                    .version(0L)
                    .createdBy(user)
                    .build();
            taskRepository.save(task2);

            Task task3 = Task.builder()
                    .project(project)
                    .list(todoList)
                    .title("Write API documentation")
                    .description("Document all REST endpoints using OpenAPI/Swagger specification.")
                    .priority(Priority.LOW)
                    .position(196608.0)
                    .version(0L)
                    .createdBy(user)
                    .build();
            taskRepository.save(task3);

            Task task4 = Task.builder()
                    .project(project)
                    .list(inProgressList)
                    .title("Implement WebSocket presence")
                    .description("Build real-time user presence indicators showing who is viewing/editing tasks.")
                    .priority(Priority.HIGH)
                    .position(65536.0)
                    .version(0L)
                    .createdBy(user)
                    .assignedTo(user)
                    .build();
            taskRepository.save(task4);

            Task task5 = Task.builder()
                    .project(project)
                    .list(inProgressList)
                    .title("Build Kanban drag & drop")
                    .description("Implement drag-and-drop task reordering with optimistic UI updates.")
                    .priority(Priority.URGENT)
                    .position(131072.0)
                    .version(0L)
                    .createdBy(user)
                    .assignedTo(user)
                    .build();
            taskRepository.save(task5);

            Task task6 = Task.builder()
                    .project(project)
                    .list(doneList)
                    .title("User authentication")
                    .description("JWT-based authentication with login and registration endpoints.")
                    .priority(Priority.HIGH)
                    .position(65536.0)
                    .version(0L)
                    .createdBy(user)
                    .assignedTo(user)
                    .build();
            taskRepository.save(task6);

            log.info("Seeding complete!");
            log.info("  User:      tester@workcollab.dev / password123");
            log.info("  Workspace: {} (id={})", workspace.getName(), workspace.getId());
            log.info("  Project:   {} (id={})", project.getName(), project.getId());
            log.info("  Lists:     To Do ({}), In Progress ({}), Done ({})", todoList.getId(), inProgressList.getId(), doneList.getId());
            log.info("  Tasks:     6 sample tasks created");
        }

        seedDemoAccounts();
        ensureProjectLists();
    }

    private void seedDemoAccounts() {
        demoteLegacyTestAccount();
        Workspace workspace = workspaceRepository.findAll().stream()
                .findFirst()
                .orElse(null);
        if (workspace == null) {
            log.warn("Skipping demo account seeding because no workspace exists");
            return;
        }

        User admin = ensureUser("admin@demo.com", "Admin User", SystemRole.ADMIN);
        User manager1 = ensureUser("manager1@demo.com", "Manager One", SystemRole.MANAGER);
        User manager2 = ensureUser("manager2@demo.com", "Manager Two", SystemRole.MANAGER);
        User member = ensureUser("mem1@demo.com", "Team Member One", SystemRole.TEAM_MEMBER);

        ensureMembership(workspace, admin, Role.ADMIN);
        ensureMembership(workspace, manager1, Role.MANAGER);
        removeManagerAccessFromManager1Workspaces(manager1, manager2);
        ensureMembership(workspace, member, Role.TEAM_MEMBER);

        log.info("Demo accounts ready: admin@demo.com, manager1@demo.com, manager2@demo.com, mem1@demo.com");
    }

    private void demoteLegacyTestAccount() {
        userRepository.findByEmail("tester@workcollab.dev").ifPresent(user -> {
            user.setSystemRole(SystemRole.TEAM_MEMBER);
            userRepository.save(user);
            workspaceMemberRepository.findByUserId(user.getId()).forEach(membership -> {
                if (membership.getRole() == Role.ADMIN) {
                    membership.setRole(Role.TEAM_MEMBER);
                    workspaceMemberRepository.save(membership);
                }
            });
        });
    }

    private User ensureUser(String email, String fullName, SystemRole systemRole) {
        User user = userRepository.findByEmail(email).orElseGet(User::new);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setPasswordHash(passwordEncoder.encode("admin123"));
        user.setSystemRole(systemRole);
        return userRepository.save(user);
    }

    private void ensureMembership(Workspace workspace, User user, Role role) {
        WorkspaceMember membership = workspaceMemberRepository
                .findByWorkspaceIdAndUserId(workspace.getId(), user.getId())
                .orElseGet(() -> WorkspaceMember.builder()
                        .workspace(workspace)
                        .user(user)
                        .build());
        membership.setRole(role);
        workspaceMemberRepository.save(membership);
    }

    private void removeManagerAccessFromManager1Workspaces(User manager1, User manager2) {
        workspaceMemberRepository
                .findByUserIdAndWorkspaceOwnerId(manager2.getId(), manager1.getId())
                .forEach(workspaceMemberRepository::delete);
    }

    private void ensureProjectLists() {
        projectRepository.findAll().forEach(project -> {
            if (taskListRepository.findByProjectIdOrderByPositionAsc(project.getId()).isEmpty()) {
                taskListRepository.saveAll(java.util.List.of(
                        TaskList.builder().project(project).name("To Do").position(1.0).build(),
                        TaskList.builder().project(project).name("In Progress").position(2.0).build(),
                        TaskList.builder().project(project).name("Done").position(3.0).build()
                ));
            }
        });
    }
}
