package com.workcollab;

import com.redis.testcontainers.RedisContainer;
import com.workcollab.entity.*;
import com.workcollab.repository.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

/**
 * Shared integration test superclass.
 *
 * <p>Boots the full Spring context against Testcontainers-managed PostgreSQL and Redis
 * instances. Provides seed data and JWT helper methods so subclasses can focus on
 * business-logic assertions.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@SuppressWarnings("resource")
public abstract class IntegrationTestBase {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("workcollab_test")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    static final RedisContainer REDIS =
            new RedisContainer("redis:7-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    // ── Autowired infrastructure ────────────────────────────────────────────

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected WorkspaceRepository workspaceRepository;

    @Autowired
    protected WorkspaceMemberRepository workspaceMemberRepository;

    @Autowired
    protected BoardRepository boardRepository;

    @Autowired
    protected TaskListRepository taskListRepository;

    @Autowired
    protected TaskRepository taskRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private int jwtExpirationMs;

    // ── Seed data (populated fresh before every test) ───────────────────────

    protected User testUser;
    protected Workspace testWorkspace;
    protected Board testBoard;
    protected TaskList todoList;
    protected TaskList inProgressList;
    protected String authToken;

    @BeforeEach
    void setUpSeedData() {
        // Clean slate — order matters because of FK constraints
        taskRepository.deleteAll();
        taskListRepository.deleteAll();
        boardRepository.deleteAll();
        workspaceMemberRepository.deleteAll();
        workspaceRepository.deleteAll();
        userRepository.deleteAll();

        // User
        testUser = userRepository.save(Objects.requireNonNull(User.builder()
                .email("tester@workcollab.dev")
                .passwordHash(passwordEncoder.encode("password123"))
                .fullName("Test User")
                .build()));

        // Workspace + membership
        testWorkspace = workspaceRepository.save(Objects.requireNonNull(Workspace.builder()
                .name("Test Workspace")
                .slug("test-workspace")
                .owner(testUser)
                .build()));

        workspaceMemberRepository.save(Objects.requireNonNull(WorkspaceMember.builder()
                .workspace(testWorkspace)
                .user(testUser)
                .role(Role.ADMIN)
                .build()));

        // Board
        testBoard = boardRepository.save(Objects.requireNonNull(Board.builder()
                .workspace(testWorkspace)
                .name("Sprint Board")
                .position(65536.0)
                .build()));

        // Task lists
        todoList = taskListRepository.save(Objects.requireNonNull(TaskList.builder()
                .board(testBoard)
                .name("To Do")
                .position(65536.0)
                .build()));

        inProgressList = taskListRepository.save(Objects.requireNonNull(TaskList.builder()
                .board(testBoard)
                .name("In Progress")
                .position(131072.0)
                .build()));

        // JWT
        authToken = generateToken(testUser);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Generates a valid JWT token for the given user, matching the format
     * produced by {@link com.workcollab.security.JwtUtils}.
     */
    protected String generateToken(User user) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("id", user.getId().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(key)
                .compact();
    }

    /**
     * Builds HTTP headers with the test user's Bearer token.
     */
    protected HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(Objects.requireNonNull(authToken));
        headers.set("Content-Type", "application/json");
        return headers;
    }

    /**
     * Convenience: task API base URL for the seeded board.
     */
    protected String taskUrl() {
        return "/api/boards/" + testBoard.getId() + "/tasks?workspaceId=" + testWorkspace.getId();
    }

    protected String taskUrl(UUID taskId) {
        return "/api/boards/" + testBoard.getId() + "/tasks/" + taskId + "?workspaceId=" + testWorkspace.getId();
    }

    protected String moveUrl(UUID taskId) {
        return "/api/boards/" + testBoard.getId() + "/tasks/" + taskId + "/move?workspaceId=" + testWorkspace.getId();
    }
}
