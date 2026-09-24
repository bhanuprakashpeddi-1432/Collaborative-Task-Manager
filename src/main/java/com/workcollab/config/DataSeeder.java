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
    private final BoardRepository boardRepository;
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

            // Create a default board
            Board board = Board.builder()
                    .workspace(workspace)
                    .name("Engineering Sprint")
                    .position(1.0)
                    .build();
            board = boardRepository.save(board);

            // Create task lists
            TaskList todoList = TaskList.builder()
                    .board(board)
                    .name("To Do")
                    .position(1.0)
                    .build();
            todoList = taskListRepository.save(todoList);

            TaskList inProgressList = TaskList.builder()
                    .board(board)
                    .name("In Progress")
                    .position(2.0)
                    .build();
            inProgressList = taskListRepository.save(inProgressList);

            TaskList doneList = TaskList.builder()
                    .board(board)
                    .name("Done")
                    .position(3.0)
                    .build();
            doneList = taskListRepository.save(doneList);

            // Create sample tasks
            Task task1 = Task.builder()
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
            log.info("  Board:     {} (id={})", board.getName(), board.getId());
            log.info("  Lists:     To Do ({}), In Progress ({}), Done ({})", todoList.getId(), inProgressList.getId(), doneList.getId());
            log.info("  Tasks:     6 sample tasks created");
        }
    }
}
