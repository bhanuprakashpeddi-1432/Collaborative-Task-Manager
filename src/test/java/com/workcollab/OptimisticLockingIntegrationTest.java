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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying the optimistic locking mechanism on tasks.
 *
 * <p>These tests prove that the {@code @Version} field on {@code Task} prevents
 * concurrent modifications from silently overwriting each other, and that the API
 * surfaces a 409 Conflict response with version metadata.</p>
 */
class OptimisticLockingIntegrationTest extends IntegrationTestBase {

    // ── Helpers ─────────────────────────────────────────────────────────────

    private TaskDto createSeedTask() {
        TaskCreateRequest createReq = TaskCreateRequest.builder()
                .listId(todoList.getId())
                .title("Concurrency Test Task")
                .description("Used for optimistic locking tests")
                .priority(Priority.MEDIUM)
                .build();

        HttpEntity<TaskCreateRequest> request = new HttpEntity<>(createReq, authHeaders());
        ResponseEntity<TaskDto> response = restTemplate.postForEntity(taskUrl(), request, TaskDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    // ── Tests ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT with stale version → 409 Conflict with version metadata")
    void updateWithStaleVersion_returns409Conflict() {
        TaskDto task = createSeedTask();

        // First update succeeds (version 0 → 1)
        TaskUpdateRequest firstUpdate = TaskUpdateRequest.builder()
                .title("First Update")
                .priority(Priority.HIGH)
                .version(0L)
                .build();

        ResponseEntity<TaskDto> successResp = restTemplate.exchange(
                taskUrl(task.getId()), HttpMethod.PUT,
                new HttpEntity<>(firstUpdate, authHeaders()), TaskDto.class);
        assertThat(successResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Objects.requireNonNull(successResp.getBody()).getVersion()).isEqualTo(1L);

        // Second update with stale version 0 → should fail
        TaskUpdateRequest staleUpdate = TaskUpdateRequest.builder()
                .title("Stale Update")
                .priority(Priority.LOW)
                .version(0L)
                .build();

        ResponseEntity<String> conflictResp = restTemplate.exchange(
                taskUrl(task.getId()), HttpMethod.PUT,
                new HttpEntity<>(staleUpdate, authHeaders()), String.class);

        assertThat(conflictResp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflictResp.getBody()).contains("staleVersion");
        assertThat(conflictResp.getBody()).contains("currentVersion");
    }

    @Test
    @DisplayName("PATCH move with stale version → 409 Conflict")
    void moveWithStaleVersion_returns409Conflict() {
        TaskDto task = createSeedTask();

        // First move succeeds (version 0 → 1)
        TaskMoveRequest firstMove = TaskMoveRequest.builder()
                .targetListId(inProgressList.getId())
                .version(0L)
                .build();

        ResponseEntity<TaskDto> successResp = restTemplate.exchange(
                moveUrl(task.getId()), HttpMethod.PATCH,
                new HttpEntity<>(firstMove, authHeaders()), TaskDto.class);
        assertThat(successResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Objects.requireNonNull(successResp.getBody()).getVersion()).isEqualTo(1L);

        // Second move with stale version 0 → should fail
        TaskMoveRequest staleMove = TaskMoveRequest.builder()
                .targetListId(todoList.getId())
                .version(0L)
                .build();

        ResponseEntity<String> conflictResp = restTemplate.exchange(
                moveUrl(task.getId()), HttpMethod.PATCH,
                new HttpEntity<>(staleMove, authHeaders()), String.class);

        assertThat(conflictResp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Concurrent updates: exactly one succeeds, all others get 409")
    void concurrentUpdates_exactlyOneSucceeds() throws InterruptedException {
        TaskDto task = createSeedTask();
        int threadCount = 10;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            futures.add(executor.submit(() -> {
                try {
                    barrier.await(5, TimeUnit.SECONDS); // Synchronize all threads

                    TaskUpdateRequest updateReq = TaskUpdateRequest.builder()
                            .title("Concurrent Update #" + idx)
                            .priority(Priority.HIGH)
                            .version(0L) // All threads use the initial version
                            .build();

                    ResponseEntity<String> response = restTemplate.exchange(
                            taskUrl(task.getId()), HttpMethod.PUT,
                            new HttpEntity<>(updateReq, authHeaders()), String.class);

                    if (response.getStatusCode() == HttpStatus.OK) {
                        successCount.incrementAndGet();
                    } else if (response.getStatusCode() == HttpStatus.CONFLICT) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // BrokenBarrier or timeout — test framework will catch assertion failures
                }
            }));
        }

        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // Exactly one thread should win
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threadCount - 1);

        // The winning update should have incremented the version
        restTemplate.exchange(
                taskUrl(Objects.requireNonNull(task.getId())), HttpMethod.GET,
                new HttpEntity<>(Objects.requireNonNull(authHeaders())), TaskDto.class);
        // Note: GET on the task URL returns a Page; so we verify via repo instead
        assertThat(taskRepository.findById(task.getId()))
                .isPresent()
                .hasValueSatisfying(t -> assertThat(t.getVersion()).isEqualTo(1L));
    }

    @Test
    @DisplayName("Concurrent moves: exactly one succeeds, all others get 409")
    void concurrentMoves_exactlyOneSucceeds() throws InterruptedException {
        TaskDto task = createSeedTask();
        int threadCount = 8;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    barrier.await(5, TimeUnit.SECONDS);

                    TaskMoveRequest moveReq = TaskMoveRequest.builder()
                            .targetListId(inProgressList.getId())
                            .version(0L)
                            .build();

                    ResponseEntity<String> response = restTemplate.exchange(
                            moveUrl(task.getId()), HttpMethod.PATCH,
                            new HttpEntity<>(moveReq, authHeaders()), String.class);

                    if (response.getStatusCode() == HttpStatus.OK) {
                        successCount.incrementAndGet();
                    } else if (response.getStatusCode() == HttpStatus.CONFLICT) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // barrier exception — acceptable in test context
                }
            });
        }

        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threadCount - 1);

        assertThat(taskRepository.findById(Objects.requireNonNull(task.getId())))
                .isPresent()
                .hasValueSatisfying(t -> {
                    assertThat(t.getVersion()).isEqualTo(1L);
                    assertThat(t.getList().getId()).isEqualTo(inProgressList.getId());
                });
    }
}
