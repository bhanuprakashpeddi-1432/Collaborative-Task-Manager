package com.workcollab.repository;

import com.workcollab.entity.DirectMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, UUID> {
    Page<DirectMessage> findByRecipientIdOrSenderIdOrderByCreatedAtDesc(UUID recipientId, UUID senderId, Pageable pageable);
}
