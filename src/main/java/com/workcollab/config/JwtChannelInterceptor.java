package com.workcollab.config;

import com.workcollab.security.UserDetailsImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * Intercepts inbound STOMP frames on the client channel. On the {@code CONNECT}
 * frame, retrieves the authenticated {@link UserDetailsImpl} that was placed in
 * the WebSocket session attributes by the {@link JwtHandshakeInterceptor} and
 * sets it as the STOMP {@code user} principal.
 *
 * <p>This ensures that all subsequent SUBSCRIBE / SEND frames carry the
 * authenticated user identity, which can be accessed via
 * {@code StompHeaderAccessor.getUser()} or {@code @Header("simpUser")}.</p>
 */
@Component
@Slf4j
public class JwtChannelInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            Object userAttr = accessor.getSessionAttributes() != null
                    ? accessor.getSessionAttributes().get(JwtHandshakeInterceptor.AUTHENTICATED_USER_ATTR)
                    : null;

            if (userAttr instanceof UserDetailsImpl userDetails) {
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        userDetails, null, Collections.emptyList());
                accessor.setUser(auth);
                log.debug("STOMP CONNECT: principal set for user {}", userDetails.getUsername());
            } else {
                log.warn("STOMP CONNECT: no authenticated user found in session attributes");
            }
        }

        return message;
    }
}
