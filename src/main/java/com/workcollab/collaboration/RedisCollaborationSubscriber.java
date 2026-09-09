package com.workcollab.collaboration;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workcollab.event.CollaborationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Subscribes to the Redis Pub/Sub collaboration channel and forwards incoming
 * {@link CollaborationMessage} instances to locally-connected WebSocket clients
 * via the STOMP message broker.
 *
 * <p>Every Spring Boot node in the cluster runs an instance of this subscriber.
 * When Node A publishes a task event to Redis, Nodes A, B, and C all receive
 * the message and broadcast it to their respective WebSocket sessions, achieving
 * full fan-out across a load-balanced deployment.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisCollaborationSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(@NonNull Message message, @Nullable byte[] pattern) {
        try {
            String json = new String(message.getBody());
            CollaborationMessage collaborationMessage = objectMapper.readValue(json, CollaborationMessage.class);

            String destination = collaborationMessage.toDestination();
            log.debug("Redis → STOMP: broadcasting [{}] to {}", collaborationMessage.getEventType(), destination);

            messagingTemplate.convertAndSend(destination, collaborationMessage);
        } catch (Exception e) {
            log.error("Failed to process Redis collaboration message", e);
        }
    }
}
