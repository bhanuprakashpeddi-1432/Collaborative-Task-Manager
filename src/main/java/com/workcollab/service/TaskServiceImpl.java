package com.workcollab.service;

import com.workcollab.dto.TaskCreateRequest;
import com.workcollab.dto.TaskDto;
import com.workcollab.dto.TaskFilterRequest;
import com.workcollab.dto.TaskMoveRequest;
import com.workcollab.dto.TaskUpdateRequest;
import com.workcollab.entity.Priority;
import com.workcollab.entity.Task;
import com.workcollab.entity.TaskList;
import com.workcollab.entity.User;
import com.workcollab.event.CollaborationEvent;
import com.workcollab.event.CollaborationEventType;
import com.workcollab.event.TaskActivityEvent;
import com.workcollab.exception.ResourceNotFoundException;
import com.workcollab.exception.TaskOptimisticLockingException;
import com.workcollab.repository.TaskListRepository;
import com.workcollab.repository.TaskRepository;
import com.workcollab.repository.TaskSpecification;
import com.workcollab.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final TaskListRepository taskListRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public TaskDto createTask(UUID boardId, TaskCreateRequest request, UUID userId) {
        TaskList list = taskListRepository.findById(Objects.requireNonNull(request.getListId()))
                .orElseThrow(() -> new ResourceNotFoundException("List not found"));
        
        if (!list.getBoard().getId().equals(boardId)) {
            throw new IllegalArgumentException("List does not belong to the specified board");
        }

        User creator = userRepository.getReferenceById(Objects.requireNonNull(userId));
        User assignee = request.getAssignedToId() != null ? userRepository.getReferenceById(Objects.requireNonNull(request.getAssignedToId())) : null;

        Double position = generateInitialPosition(list.getId());

        Task task = Task.builder()
                .list(list)
                .title(request.getTitle())
                .description(request.getDescription())
                .priority(request.getPriority() != null ? request.getPriority() : Priority.MEDIUM)
                .position(position)
                .dueDate(request.getDueDate())
                .version(0L)
                .createdBy(creator)
                .assignedTo(assignee)
                .build();

        Task savedTask = taskRepository.save(Objects.requireNonNull(task));

        publishActivity(savedTask.getId(), userId, "TASK_CREATED", "{}");

        TaskDto dto = mapToDto(savedTask);
        publishCollaborationEvent(savedTask, CollaborationEventType.TASK_CREATED, dto, userId);

        return dto;
    }

    @Override
    @Transactional
    public TaskDto updateTask(UUID taskId, TaskUpdateRequest request, UUID userId) {
        Task task = taskRepository.findById(Objects.requireNonNull(taskId))
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        if (!task.getVersion().equals(request.getVersion())) {
            throw new TaskOptimisticLockingException("Task was modified concurrently", task.getVersion(), request.getVersion());
        }

        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setPriority(request.getPriority());
        task.setDueDate(request.getDueDate());
        
        if (request.getAssignedToId() != null) {
            task.setAssignedTo(userRepository.getReferenceById(Objects.requireNonNull(request.getAssignedToId())));
        } else {
            task.setAssignedTo(null);
        }

        try {
            Task updatedTask = taskRepository.saveAndFlush(task);
            publishActivity(updatedTask.getId(), userId, "TASK_UPDATED", "{}");
            TaskDto dto = mapToDto(updatedTask);
            publishCollaborationEvent(updatedTask, CollaborationEventType.TASK_UPDATED, dto, userId);
            return dto;
        } catch (OptimisticLockingFailureException e) {
            // Fetch current state to report the actual version
            Task currentTask = taskRepository.findById(Objects.requireNonNull(taskId)).orElseThrow();
            throw new TaskOptimisticLockingException("Task was modified concurrently", currentTask.getVersion(), request.getVersion());
        }
    }

    @Override
    @Transactional
    public TaskDto moveTask(UUID taskId, TaskMoveRequest request, UUID userId) {
        Task task = taskRepository.findById(Objects.requireNonNull(taskId))
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        if (!task.getVersion().equals(request.getVersion())) {
            throw new TaskOptimisticLockingException("Task was modified concurrently", task.getVersion(), request.getVersion());
        }

        if (!task.getList().getId().equals(request.getTargetListId())) {
            TaskList targetList = taskListRepository.findById(Objects.requireNonNull(request.getTargetListId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Target list not found"));
            task.setList(targetList);
        }

        Double prev = request.getPreviousPosition();
        Double next = request.getNextPosition();
        Double newPos;

        if (prev == null && next == null) {
            newPos = generateInitialPosition(request.getTargetListId());
        } else if (prev == null) {
            newPos = next / 2.0;
        } else if (next == null) {
            newPos = prev + 65536.0;
        } else {
            newPos = (prev + next) / 2.0;
        }

        task.setPosition(newPos);

        try {
            Task updatedTask = taskRepository.saveAndFlush(task);
            publishActivity(updatedTask.getId(), userId, "TASK_MOVED", 
                    String.format("{\"targetListId\": \"%s\", \"position\": %f}", request.getTargetListId(), newPos));
            TaskDto dto = mapToDto(updatedTask);
            publishCollaborationEvent(updatedTask, CollaborationEventType.TASK_MOVED, dto, userId);
            return dto;
        } catch (OptimisticLockingFailureException e) {
            Task currentTask = taskRepository.findById(Objects.requireNonNull(taskId)).orElseThrow();
            throw new TaskOptimisticLockingException("Task was modified concurrently", currentTask.getVersion(), request.getVersion());
        }
    }

    @Override
    @Transactional
    public void deleteTask(UUID taskId, UUID userId) {
        Task task = taskRepository.findById(Objects.requireNonNull(taskId))
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        // Capture routing metadata before deletion — needed for the WebSocket broadcast
        UUID boardId = task.getList().getBoard().getId();
        UUID workspaceId = task.getList().getBoard().getWorkspace().getId();
        UUID listId = task.getList().getId();

        taskRepository.delete(task);

        // Publish collaboration event with a lightweight delete summary.
        // Task activity logging is skipped because the FK cascades on delete.
        Map<String, Object> deletePayload = Map.of(
                "taskId", taskId,
                "listId", listId
        );
        eventPublisher.publishEvent(Objects.requireNonNull(CollaborationEvent.builder()
                .workspaceId(workspaceId)
                .boardId(boardId)
                .eventType(CollaborationEventType.TASK_DELETED)
                .payload(deletePayload)
                .triggeredBy(userId)
                .build()));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TaskDto> getTasks(UUID boardId, TaskFilterRequest filterRequest) {
        Specification<Task> spec = TaskSpecification.filterTasks(
                boardId,
                filterRequest.getPriority(),
                filterRequest.getAssignedToId(),
                filterRequest.getDueDateStart(),
                filterRequest.getDueDateEnd(),
                filterRequest.getSearchString()
        );

        Pageable pageable = PageRequest.of(filterRequest.getPage(), filterRequest.getSize(), Sort.by("position").ascending());
        
        Page<Task> tasks = taskRepository.findAll(Objects.requireNonNull(spec), pageable);
        return tasks.map(this::mapToDto);
    }

    private Double generateInitialPosition(UUID listId) {
        List<Task> existingTasks = taskRepository.findByListIdOrderByPositionAsc(listId);
        if (existingTasks.isEmpty()) {
            return 65536.0;
        }
        Task lastTask = existingTasks.get(existingTasks.size() - 1);
        return lastTask.getPosition() + 65536.0;
    }

    private void publishActivity(UUID taskId, UUID userId, String action, String jsonDetails) {
        eventPublisher.publishEvent(Objects.requireNonNull(TaskActivityEvent.builder()
                .taskId(taskId)
                .userId(userId)
                .actionType(action)
                .detailsJson(jsonDetails)
                .build()));
    }

    /**
     * Publishes a {@link CollaborationEvent} for real-time WebSocket broadcast.
     * The event is consumed by {@link com.workcollab.event.CollaborationEventListener}
     * only after the enclosing transaction commits.
     */
    private void publishCollaborationEvent(Task task, CollaborationEventType eventType,
                                           Object payload, UUID userId) {
        UUID boardId = task.getList().getBoard().getId();
        UUID workspaceId = task.getList().getBoard().getWorkspace().getId();

        eventPublisher.publishEvent(Objects.requireNonNull(CollaborationEvent.builder()
                .workspaceId(workspaceId)
                .boardId(boardId)
                .eventType(eventType)
                .payload(payload)
                .triggeredBy(userId)
                .build()));
    }

    private TaskDto mapToDto(Task task) {
        return TaskDto.builder()
                .id(task.getId())
                .listId(task.getList().getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .priority(task.getPriority())
                .position(task.getPosition())
                .dueDate(task.getDueDate())
                .version(task.getVersion())
                .createdById(task.getCreatedBy().getId())
                .assignedToId(task.getAssignedTo() != null ? task.getAssignedTo().getId() : null)
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
