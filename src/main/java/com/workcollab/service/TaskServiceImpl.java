package com.workcollab.service;

import com.workcollab.dto.TaskCreateRequest;
import com.workcollab.dto.TaskDto;
import com.workcollab.dto.TaskFilterRequest;
import com.workcollab.dto.TaskMoveRequest;
import com.workcollab.dto.TaskUpdateRequest;
import com.workcollab.entity.Priority;
import com.workcollab.entity.Project;
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
import com.workcollab.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public TaskDto createTask(UUID projectId, TaskCreateRequest request, UUID userId) {
        Project project = projectRepository.findById(Objects.requireNonNull(request.getProjectId()))
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        if (!project.getId().equals(projectId)) {
            throw new IllegalArgumentException("Project ID mismatch");
        }

        TaskList list = null;
        if (request.getListId() != null) {
            list = taskListRepository.findById(request.getListId())
                    .orElseThrow(() -> new ResourceNotFoundException("List not found"));
        }

        User creator = userRepository.getReferenceById(Objects.requireNonNull(userId));
        User assignee = request.getAssignedToId() != null ? userRepository.getReferenceById(Objects.requireNonNull(request.getAssignedToId())) : null;
        Task parentTask = request.getParentTaskId() != null ? taskRepository.getReferenceById(request.getParentTaskId()) : null;
        
        Double position = 65536.0;
        if (list != null) position = generateInitialPosition(list.getId());

        Task task = Task.builder()
                .project(project)
                .list(list)
                .parentTask(parentTask)
                .title(request.getTitle())
                .description(request.getDescription())
                .status(request.getStatus() != null ? request.getStatus() : "TODO")
                .priority(request.getPriority() != null ? request.getPriority() : Priority.MEDIUM)
                .position(position)
                .estimatedHours(request.getEstimatedHours())
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

        publishFieldActivities(task, request, userId);
        if (request.getTitle() != null) task.setTitle(request.getTitle());
        if (request.getDescription() != null) task.setDescription(request.getDescription());
        if (request.getPriority() != null) task.setPriority(request.getPriority());
        if (request.getStatus() != null) task.setStatus(request.getStatus());
        if (request.getDueDate() != null) task.setDueDate(request.getDueDate());
        if (request.getEstimatedHours() != null) task.setEstimatedHours(request.getEstimatedHours());
        if (request.getActualHours() != null) task.setActualHours(request.getActualHours());
        if (request.getProgress() != null) task.setProgress(request.getProgress());
        
        if (request.getAssignedToId() != null) {
            task.setAssignedTo(userRepository.getReferenceById(Objects.requireNonNull(request.getAssignedToId())));
        } else if (request.getAssignedToId() == null && request.getTitle() == null) {
            // Null check bypass trick for DTO
        }

        try {
            Task updatedTask = taskRepository.saveAndFlush(task);
                TaskDto dto = mapToDto(updatedTask);
            publishCollaborationEvent(updatedTask, CollaborationEventType.TASK_UPDATED, dto, userId);
            return dto;
        } catch (OptimisticLockingFailureException e) {
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

        if (task.getList() == null || !task.getList().getId().equals(request.getTargetListId())) {
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

        UUID projectId = task.getProject().getId();
        UUID workspaceId = task.getProject().getWorkspace().getId();
        UUID listId = task.getList() != null ? task.getList().getId() : null;

        taskRepository.delete(task);

        Map<String, Object> deletePayload = Map.of(
                "taskId", taskId,
                "listId", listId != null ? listId : ""
        );
        eventPublisher.publishEvent(Objects.requireNonNull(CollaborationEvent.builder()
                .workspaceId(workspaceId)
                .boardId(projectId)
                .eventType(CollaborationEventType.TASK_DELETED)
                .payload(deletePayload)
                .triggeredBy(userId)
                .build()));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TaskDto> getTasks(UUID projectId, TaskFilterRequest filterRequest) {
        Specification<Task> spec = TaskSpecification.filterTasks(
                projectId,
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

    private void publishFieldActivities(Task task, TaskUpdateRequest request, UUID userId) {
        if (request.getDescription() != null && !Objects.equals(request.getDescription(), task.getDescription())) {
            publishActivity(task.getId(), userId, "DESCRIPTION_CHANGED", details("description", request.getDescription()));
        }
        if (request.getStatus() != null && !Objects.equals(request.getStatus(), task.getStatus())) {
            publishActivity(task.getId(), userId, "STATUS_CHANGED", details("status", request.getStatus()));
        }
        if (request.getPriority() != null && !Objects.equals(request.getPriority(), task.getPriority())) {
            publishActivity(task.getId(), userId, "PRIORITY_CHANGED", details("priority", request.getPriority()));
        }
        boolean genericFieldChanged =
                (request.getTitle() != null && !Objects.equals(request.getTitle(), task.getTitle()))
                || (request.getAssignedToId() != null
                    && !Objects.equals(request.getAssignedToId(),
                    task.getAssignedTo() != null ? task.getAssignedTo().getId() : null))
                || (request.getDueDate() != null && !Objects.equals(request.getDueDate(), task.getDueDate()))
                || (request.getEstimatedHours() != null && !Objects.equals(request.getEstimatedHours(), task.getEstimatedHours()))
                || (request.getActualHours() != null && !Objects.equals(request.getActualHours(), task.getActualHours()))
                || (request.getProgress() != null && !Objects.equals(request.getProgress(), task.getProgress()));
        if (genericFieldChanged) {
            publishActivity(task.getId(), userId, "TASK_UPDATED", "{}");
        }
    }

    private String details(String field, Object value) {
        try {
            return objectMapper.writeValueAsString(Map.of("field", field, "value", value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to record task activity", exception);
        }
    }

    private void publishCollaborationEvent(Task task, CollaborationEventType eventType,
                                           Object payload, UUID userId) {
        UUID projectId = task.getProject().getId();
        UUID workspaceId = task.getProject().getWorkspace().getId();

        eventPublisher.publishEvent(Objects.requireNonNull(CollaborationEvent.builder()
                .workspaceId(workspaceId)
                .boardId(projectId) // boardId in event is now projectId
                .eventType(eventType)
                .payload(payload)
                .triggeredBy(userId)
                .build()));
    }

    private TaskDto mapToDto(Task task) {
        return TaskDto.builder()
                .id(task.getId())
                .projectId(task.getProject().getId())
                .listId(task.getList() != null ? task.getList().getId() : null)
                .sprintId(task.getSprint() != null ? task.getSprint().getId() : null)
                .parentTaskId(task.getParentTask() != null ? task.getParentTask().getId() : null)
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus())
                .priority(task.getPriority())
                .position(task.getPosition())
                .estimatedHours(task.getEstimatedHours())
                .actualHours(task.getActualHours())
                .progress(task.getProgress())
                .dueDate(task.getDueDate())
                .version(task.getVersion())
                .createdById(task.getCreatedBy().getId())
                .assignedToId(task.getAssignedTo() != null ? task.getAssignedTo().getId() : null)
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
