package com.workcollab.controller;

import com.workcollab.dto.DirectMessageRequest;
import com.workcollab.entity.DirectMessage;
import com.workcollab.entity.Role;
import com.workcollab.entity.SystemRole;
import com.workcollab.entity.User;
import com.workcollab.exception.ResourceNotFoundException;
import com.workcollab.repository.DirectMessageRepository;
import com.workcollab.repository.UserRepository;
import com.workcollab.repository.WorkspaceMemberRepository;
import com.workcollab.security.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/inbox")
@RequiredArgsConstructor
public class InboxController {

    private final DirectMessageRepository directMessageRepository;
    private final UserRepository userRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    @GetMapping
    public Page<Map<String, Object>> getInbox(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return directMessageRepository
                .findByRecipientIdOrSenderIdOrderByCreatedAtDesc(userDetails.getId(), userDetails.getId(), pageable)
                .map(message -> toMessageDto(message, userDetails.getId()));
    }

    @GetMapping("/contacts")
    public List<Map<String, Object>> getContacts(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        return getPermittedRecipients(userDetails).stream()
                .map(user -> {
                    Map<String, Object> contact = new LinkedHashMap<>();
                    contact.put("id", user.getId());
                    contact.put("fullName", user.getFullName());
                    contact.put("email", user.getEmail());
                    contact.put("systemRole", user.getSystemRole() != null
                            ? user.getSystemRole() : SystemRole.TEAM_MEMBER);
                    return contact;
                })
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> sendMessage(
            @Valid @RequestBody DirectMessageRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        User recipient = userRepository.findById(request.getRecipientId())
                .orElseThrow(() -> new ResourceNotFoundException("Recipient not found"));
        if (recipient.getId().equals(userDetails.getId()) || getPermittedRecipients(userDetails).stream()
                .noneMatch(contact -> contact.getId().equals(recipient.getId()))) {
            throw new AccessDeniedException("You cannot message this recipient");
        }

        User sender = userRepository.getReferenceById(userDetails.getId());
        DirectMessage message = DirectMessage.builder()
                .sender(sender)
                .recipient(recipient)
                .subject(request.getSubject().trim())
                .content(request.getContent().trim())
                .isRead(false)
                .build();
        return toMessageDto(directMessageRepository.save(message), userDetails.getId());
    }

    @PatchMapping("/{messageId}/read")
    public Map<String, Object> markRead(
            @PathVariable UUID messageId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        DirectMessage message = directMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));
        if (!message.getRecipient().getId().equals(userDetails.getId())) {
            throw new AccessDeniedException("Only the recipient can mark a message as read");
        }
        message.setIsRead(true);
        return toMessageDto(directMessageRepository.save(message), userDetails.getId());
    }

    private List<User> getPermittedRecipients(UserDetailsImpl userDetails) {
        if (userDetails.getSystemRole() == SystemRole.ADMIN) {
            return userRepository.findAll().stream()
                    .filter(user -> !user.getId().equals(userDetails.getId()))
                    .sorted(java.util.Comparator.comparing(User::getFullName))
                    .toList();
        }

        if (userDetails.getSystemRole() == SystemRole.TEAM_MEMBER) {
            return List.of();
        }

        List<User> recipients = userRepository.findBySystemRole(SystemRole.ADMIN).stream()
                .filter(user -> !user.getId().equals(userDetails.getId()))
                .collect(java.util.ArrayList::new, java.util.ArrayList::add, java.util.ArrayList::addAll);
        workspaceMemberRepository.findAssignedTeamMembers(
                userDetails.getId(), Role.TEAM_MEMBER, Role.MANAGER).stream()
                .filter(user -> !user.getId().equals(userDetails.getId()))
                .filter(user -> recipients.stream().noneMatch(existing -> existing.getId().equals(user.getId())))
                .forEach(recipients::add);
        return recipients.stream()
                .sorted(java.util.Comparator.comparing(User::getFullName))
                .toList();
    }

    private Map<String, Object> toMessageDto(DirectMessage message, UUID currentUserId) {
        User otherUser = message.getSender().getId().equals(currentUserId)
                ? message.getRecipient() : message.getSender();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", message.getId());
        result.put("senderId", message.getSender().getId());
        result.put("senderName", message.getSender().getFullName());
        result.put("recipientId", message.getRecipient().getId());
        result.put("recipientName", message.getRecipient().getFullName());
        result.put("otherUserId", otherUser.getId());
        result.put("otherUserName", otherUser.getFullName());
        result.put("subject", message.getSubject());
        result.put("content", message.getContent());
        result.put("read", message.getIsRead());
        result.put("createdAt", message.getCreatedAt());
        return result;
    }
}
