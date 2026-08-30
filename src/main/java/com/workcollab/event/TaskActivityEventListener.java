package com.workcollab.event;

import com.workcollab.entity.TaskActivity;
import com.workcollab.repository.TaskActivityRepository;
import com.workcollab.repository.TaskRepository;
import com.workcollab.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class TaskActivityEventListener {

    private final TaskActivityRepository taskActivityRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleTaskActivityEvent(TaskActivityEvent event) {
        log.info("Handling TaskActivityEvent: {}", event);

        try {
            TaskActivity activity = TaskActivity.builder()
                    .task(taskRepository.getReferenceById(event.getTaskId()))
                    .user(event.getUserId() != null ? userRepository.getReferenceById(event.getUserId()) : null)
                    .actionType(event.getActionType())
                    .detailsJson(event.getDetailsJson())
                    .build();

            taskActivityRepository.save(activity);
        } catch (Exception e) {
            log.error("Failed to save TaskActivity for event: {}", event, e);
        }
    }
}
