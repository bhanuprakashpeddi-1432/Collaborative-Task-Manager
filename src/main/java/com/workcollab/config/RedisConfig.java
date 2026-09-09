package com.workcollab.config;

import org.springframework.lang.NonNull;

import com.workcollab.collaboration.RedisCollaborationSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis infrastructure configuration for the collaboration Pub/Sub system.
 *
 * <p>Sets up:</p>
 * <ul>
 *   <li>A {@link RedisTemplate} with JSON serialization for publishing messages</li>
 *   <li>A {@link RedisMessageListenerContainer} that subscribes to the collaboration
 *       channel and delegates to {@link RedisCollaborationSubscriber}</li>
 *   <li>A shared {@link ChannelTopic} bean so publisher and subscriber agree on
 *       the channel name</li>
 * </ul>
 */
@Configuration
public class RedisConfig {

    public static final String COLLABORATION_CHANNEL = "workcollab:collaboration";

    @Bean
    public ChannelTopic collaborationTopic() {
        return new ChannelTopic(COLLABORATION_CHANNEL);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public MessageListenerAdapter collaborationListenerAdapter(@NonNull RedisCollaborationSubscriber subscriber) {
        return new MessageListenerAdapter(subscriber, "onMessage");
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            @NonNull RedisConnectionFactory connectionFactory,
            @NonNull MessageListenerAdapter collaborationListenerAdapter,
            @NonNull ChannelTopic collaborationTopic) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(collaborationListenerAdapter, collaborationTopic);
        return container;
    }
}
