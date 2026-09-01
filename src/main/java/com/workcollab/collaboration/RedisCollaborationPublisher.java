package com.workcollab.collaboration;

import com.workcollab.event.CollaborationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;

/**
 * Publishes {@link CollaborationMessage} instances to the Redis Pub/Sub channel.
 *
 * <p>In a multi-node deployment behind a load balancer, each Spring Boot instance
 * publishes task mutation events to Redis. The {@link RedisCollaborationSubscriber}
 * on every node picks up the message and broadcasts it to locally-connected
 * WebSocket clients via STOMP.</p>
 *
 * <p>This is also used directly by the {@link com.workcollab.controller.PresenceController}
 * for non-transactional presence/heartbeat events that don't go through
 * {@code @TransactionalEventListener}.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisCollaborationPublisher {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ChannelTopic collaborationTopic;

    /**
     * Serializes the message to JSON and publishes it to the Redis collaboration channel.
     *
     * @param message the collaboration message to broadcast
     */
    public void publish(CollaborationMessage message) {
        log.debug("Publishing [{}] to Redis channel '{}' for destination {}",
                message.getEventType(), collaborationTopic.getTopic(), message.toDestination());

        redisTemplate.convertAndSend(collaborationTopic.getTopic(), message);
    }
}
