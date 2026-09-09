package com.workcollab;

import com.workcollab.dto.TaskCreateRequest;
import com.workcollab.dto.TaskDto;
import com.workcollab.dto.TaskMoveRequest;
import com.workcollab.dto.TaskUpdateRequest;
import com.workcollab.entity.Priority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests covering the full task CRUD life-cycle via HTTP.
 *
 * <p>Each test runs against real PostgreSQL and Redis containers and exercises
 * the full Spring MVC → Service → JPA → Database stack.</p>
 */
class TaskLifecycleIntegrationTest extends IntegrationTestBase {

    // ── Helpers ─────────────────────────────────────────────────────────────

    private TaskCreateRequest buildCreateRequest(String title) {
        return TaskCreateRequest.builder()
                .listId(todoList.getId())
                .title(title)
                .description("Test description for " + title)
                .priority(Priority.MEDIUM)
                .build();
    }

    private TaskDto createTask(String title) {
        HttpEntity<TaskCreateRequest> request = new HttpEntity<>(buildCreateRequest(title), authHeaders());
        ResponseEntity<TaskDto> response = restTemplate.postForEntity(taskUrl(), request, TaskDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return Objects.requireNonNull(response.getBody());
    }

    // ── Tests ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /tasks → 201 Created, returns DTO with version=0 and assigned position")
    void createTask_returns201_andPersists() {
        TaskCreateRequest createReq = TaskCreateRequest.builder()
                .listId(todoList.getId())
                .title("Implement login page")
                .description("Build the login page with JWT auth")
                .priority(Priority.HIGH)
                .assignedToId(testUser.getId())
                .build();

        HttpEntity<TaskCreateRequest> request = new HttpEntity<>(createReq, authHeaders());
        ResponseEntity<TaskDto> response = restTemplate.postForEntity(taskUrl(), request, TaskDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        TaskDto body = Objects.requireNonNull(response.getBody());
        assertThat(body).isNotNull();
        assertThat(body.getId()).isNotNull();
        assertThat(body.getTitle()).isEqualTo("Implement login page");
        assertThat(body.getDescription()).isEqualTo("Build the login page with JWT auth");
        assertThat(body.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(body.getVersion()).isEqualTo(0L);
        assertThat(body.getPosition()).isGreaterThan(0.0);
        assertThat(body.getListId()).isEqualTo(todoList.getId());
        assertThat(body.getAssignedToId()).isEqualTo(testUser.getId());

        // Verify persistence
        assertThat(taskRepository.findById(Objects.requireNonNull(body.getId()))).isPresent();
    }

    @Test
    @DisplayName("GET /tasks → returns paginated results after task creation")
    void getTasksAfterCreate_returnsPaginatedResults() {
        createTask("Task A");
        createTask("Task B");

        HttpEntity<Void> request = new HttpEntity<>(authHeaders());
        ResponseEntity<String> response = restTemplate.exchange(
                taskUrl(), HttpMethod.GET, request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        // Verify both tasks appear in the response
        assertThat(response.getBody()).contains("Task A", "Task B");
    }

    @Test
    @DisplayName("PUT /tasks/{id} → 200 OK, updates fields and increments version")
    void updateTask_returns200_incrementsVersion() {
        TaskDto created = createTask("Original Title");
        assertThat(created.getVersion()).isEqualTo(0L);

        TaskUpdateRequest updateReq = TaskUpdateRequest.builder()
                .title("Updated Title")
                .description("Updated description")
                .priority(Priority.URGENT)
                .version(0L)
                .build();

        HttpEntity<TaskUpdateRequest> request = new HttpEntity<>(updateReq, authHeaders());
        ResponseEntity<TaskDto> response = restTemplate.exchange(
                taskUrl(created.getId()), HttpMethod.PUT, request, TaskDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        TaskDto updated = Objects.requireNonNull(response.getBody());
        assertThat(updated).isNotNull();
        assertThat(updated.getTitle()).isEqualTo("Updated Title");
        assertThat(updated.getDescription()).isEqualTo("Updated description");
        assertThat(updated.getPriority()).isEqualTo(Priority.URGENT);
        assertThat(updated.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("PATCH /tasks/{id}/move → moves task to another list and recalculates position")
    void moveTask_changesListAndPosition() {
        TaskDto created = createTask("Moveable Task");
        assertThat(created.getListId()).isEqualTo(todoList.getId());

        TaskMoveRequest moveReq = TaskMoveRequest.builder()
                .targetListId(inProgressList.getId())
                .version(0L)
                .build();

        HttpEntity<TaskMoveRequest> request = new HttpEntity<>(moveReq, authHeaders());
        ResponseEntity<TaskDto> response = restTemplate.exchange(
                moveUrl(created.getId()), HttpMethod.PATCH, request, TaskDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        TaskDto moved = Objects.requireNonNull(response.getBody());
        assertThat(moved).isNotNull();
        assertThat(moved.getListId()).isEqualTo(inProgressList.getId());
        assertThat(moved.getVersion()).isEqualTo(1L);
        assertThat(moved.getPosition()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("DELETE /tasks/{id} → 204 No Content, task removed from database")
    void deleteTask_returns204_removesFromDb() {
        TaskDto created = createTask("Deletable Task");

        HttpEntity<Void> req = new HttpEntity<>(Objects.requireNonNull(authHeaders()));
        ResponseEntity<Void> response = restTemplate.exchange(
                taskUrl(created.getId()), HttpMethod.DELETE, req, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify deletion
        assertThat(taskRepository.findById(Objects.requireNonNull(created.getId()))).isEmpty();
    }

    @Test
    @DisplayName("Full life-cycle: create → update → move → delete")
    void fullLifecycle_createUpdateMoveDelete() {
        // 1. Create
        TaskDto task = createTask("Lifecycle Task");
        assertThat(task.getVersion()).isEqualTo(0L);
        assertThat(task.getListId()).isEqualTo(todoList.getId());

        // 2. Update
        TaskUpdateRequest updateReq = TaskUpdateRequest.builder()
                .title("Lifecycle Task — Updated")
                .description("Progressed through the pipeline")
                .priority(Priority.HIGH)
                .version(task.getVersion())
                .build();

        ResponseEntity<TaskDto> updateResp = restTemplate.exchange(
                taskUrl(task.getId()), HttpMethod.PUT,
                new HttpEntity<>(updateReq, authHeaders()), TaskDto.class);

        assertThat(updateResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        TaskDto updated = Objects.requireNonNull(updateResp.getBody());
        assertThat(updated).isNotNull();
        assertThat(updated.getVersion()).isEqualTo(1L);

        // 3. Move to "In Progress"
        TaskMoveRequest moveReq = TaskMoveRequest.builder()
                .targetListId(inProgressList.getId())
                .version(updated.getVersion())
                .build();

        ResponseEntity<TaskDto> moveResp = restTemplate.exchange(
                moveUrl(task.getId()), HttpMethod.PATCH,
                new HttpEntity<>(moveReq, authHeaders()), TaskDto.class);

        assertThat(moveResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        TaskDto moved = Objects.requireNonNull(moveResp.getBody());
        assertThat(moved).isNotNull();
        assertThat(moved.getListId()).isEqualTo(inProgressList.getId());
        assertThat(moved.getVersion()).isEqualTo(2L);

        // 4. Delete
        ResponseEntity<Void> deleteResp = restTemplate.exchange(
                taskUrl(task.getId()), HttpMethod.DELETE, 
                new HttpEntity<>(Objects.requireNonNull(authHeaders())), Void.class);

        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(taskRepository.findById(Objects.requireNonNull(task.getId()))).isEmpty();
    }
}
