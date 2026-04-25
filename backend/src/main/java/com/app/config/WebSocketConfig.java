package com.app.config;

import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.auth.security.CustomUserDetailsService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) return message;

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String token = extractBearer(accessor.getFirstNativeHeader("Authorization"));
                    if (!StringUtils.hasText(token)) {
                        // allow cookie-auth in SockJS transports? (frontend should send Authorization)
                        token = extractBearer(accessor.getFirstNativeHeader("authorization"));
                    }
                    if (!StringUtils.hasText(token)) {
                        throw new IllegalArgumentException("Missing Authorization token");
                    }

                    try {
                        Claims claims = jwtService.validateAndExtract(token);
                        String email = jwtService.extractEmail(claims);
                        CustomUserDetails user = (CustomUserDetails) userDetailsService.loadUserByUsername(email);
                        Authentication auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                        accessor.setUser(auth);
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    } catch (JwtException ex) {
                        throw new IllegalArgumentException("Invalid JWT");
                    }
                }

                return message;
            }
        });
    }

    private static String extractBearer(String headerValue) {
        if (!StringUtils.hasText(headerValue)) return null;
        String v = headerValue.trim();
        if (v.regionMatches(true, 0, "Bearer", 0, "Bearer".length())) {
            int idx = v.indexOf(' ');
            if (idx > 0 && idx < v.length() - 1) return v.substring(idx + 1).trim();
        }
        return v;
    }
}

