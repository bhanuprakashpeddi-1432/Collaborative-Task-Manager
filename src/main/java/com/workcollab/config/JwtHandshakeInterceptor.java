package com.workcollab.config;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import com.workcollab.security.CustomUserDetailsService;
import com.workcollab.security.JwtUtils;
import com.workcollab.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Validates the JWT token provided as a query parameter during the WebSocket
 * handshake. Browsers cannot send custom headers on WebSocket upgrade requests,
 * so the token is passed via {@code ?token=<jwt>}.
 *
 * <p>On success, the authenticated {@link UserDetailsImpl} is stored in the
 * session attributes under the key {@code "AUTHENTICATED_USER"} for the
 * {@link JwtChannelInterceptor} to pick up on the STOMP CONNECT frame.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    public static final String AUTHENTICATED_USER_ATTR = "AUTHENTICATED_USER";

    private final JwtUtils jwtUtils;
    private final CustomUserDetailsService userDetailsService;

    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                                   @NonNull WebSocketHandler wsHandler, @NonNull Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String token = servletRequest.getServletRequest().getParameter("token");

            if (token != null && jwtUtils.validateJwtToken(token)) {
                String username = jwtUtils.getUserNameFromJwtToken(token);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                attributes.put(AUTHENTICATED_USER_ATTR, userDetails);
                log.debug("WebSocket handshake authenticated for user: {}", username);
                return true;
            }
        }

        log.warn("WebSocket handshake rejected: missing or invalid JWT token");
        return false;
    }

    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                               @NonNull WebSocketHandler wsHandler, @Nullable Exception exception) {
        // No post-handshake processing needed
    }
}
