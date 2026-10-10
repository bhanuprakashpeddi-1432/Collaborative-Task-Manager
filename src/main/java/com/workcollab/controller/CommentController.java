package com.workcollab.controller;

import com.workcollab.entity.Comment;
import com.workcollab.entity.Notification;
import com.workcollab.entity.Task;
import com.workcollab.entity.User;
import com.workcollab.exception.ResourceNotFoundException;
import com.workcollab.repository.CommentRepository;
import com.workcollab.repository.NotificationRepository;
import com.workcollab.repository.TaskRepository;
import com.workcollab.repository.UserRepository;
import com.workcollab.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tasks/{taskId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentRepository commentRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    @GetMapping
    public List<Map<String, Object>> getComments(@PathVariable UUID taskId) {
        return commentRepository.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> addComment(
            @PathVariable UUID taskId,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        User user = userRepository.getReferenceById(userDetails.getId());

        String content = request.get("content");
        if (content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Content is required"));
        }

        Comment comment = Comment.builder()
                .task(task)
                .user(user)
                .content(content)
                .build();
        
        comment = commentRepository.save(comment);

        parseAndNotifyMentions(content, task, userDetails.getUsername());

        com.workcollab.event.CollaborationEvent event = com.workcollab.event.CollaborationEvent.builder()
                .workspaceId(task.getProject().getWorkspace().getId())
                .boardId(task.getProject().getId())
                .eventType(com.workcollab.event.CollaborationEventType.COMMENT_ADDED)
                .payload(mapToDto(comment))
                .triggeredBy(user.getId())
                .build();
        eventPublisher.publishEvent(event);

        return ResponseEntity.ok(mapToDto(comment));
    }

    @PutMapping("/{commentId}")
    public ResponseEntity<Map<String, Object>> updateComment(
            @PathVariable UUID taskId,
            @PathVariable UUID commentId,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

        if (!comment.getUser().getId().equals(userDetails.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized to edit this comment"));
        }

        comment.setContent(request.get("content"));
        comment = commentRepository.save(comment);

        return ResponseEntity.ok(mapToDto(comment));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<?> deleteComment(
            @PathVariable UUID taskId,
            @PathVariable UUID commentId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

        if (!comment.getUser().getId().equals(userDetails.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized to delete this comment"));
        }

        commentRepository.delete(comment);
        return ResponseEntity.ok(Map.of("message", "Comment deleted"));
    }

    private void parseAndNotifyMentions(String content, Task task, String authorEmail) {
        // Find @email mentions (using email as username equivalent for now)
        Pattern pattern = Pattern.compile("@([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6})");
        Matcher matcher = pattern.matcher(content);
        
        while (matcher.find()) {
            String mentionedEmail = matcher.group(1);
            userRepository.findByEmail(mentionedEmail).ifPresent(mentionedUser -> {
                Notification notification = Notification.builder()
                        .user(mentionedUser)
                        .type("MENTION")
                        .content(authorEmail + " mentioned you in task: " + task.getTitle())
                        .referenceId(task.getId())
                        .isRead(false)
                        .build();
                notificationRepository.save(notification);
            });
        }
    }

    private Map<String, Object> mapToDto(Comment comment) {
        return Map.of(
                "id", comment.getId(),
                "taskId", comment.getTask().getId(),
                "userId", comment.getUser().getId(),
                "userName", comment.getUser().getFullName(),
                "content", comment.getContent(),
                "createdAt", comment.getCreatedAt().toString(),
                "updatedAt", comment.getUpdatedAt().toString()
        );
    }
}
