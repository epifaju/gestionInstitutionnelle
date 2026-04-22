package com.app.config;

import com.app.modules.auth.security.CustomUserDetails;
import com.app.modules.auth.security.CustomUserDetailsService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private static final String ACCESS_COOKIE = "access_token";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = extractBearerOrCookieToken(request);
        if (!StringUtils.hasText(token)) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            Claims claims = jwtService.validateAndExtract(token);
            String email = jwtService.extractEmail(claims);
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                CustomUserDetails user = (CustomUserDetails) userDetailsService.loadUserByUsername(email);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (JwtException ex) {
            log.warn("JWT invalide: {}", ex.getMessage());
        } catch (org.springframework.security.core.userdetails.UsernameNotFoundException ex) {
            log.warn("Utilisateur JWT introuvable: {}", ex.getMessage());
        }
        filterChain.doFilter(request, response);
    }

    private static String extractBearerOrCookieToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader)) {
            int sep = -1;
            for (int i = 0; i < authHeader.length(); i++) {
                if (Character.isWhitespace(authHeader.charAt(i))) {
                    sep = i;
                    break;
                }
            }
            if (sep > 0 && sep < authHeader.length() - 1) {
                String scheme = authHeader.substring(0, sep);
                if ("Bearer".equalsIgnoreCase(scheme)) {
                    String t = authHeader.substring(sep + 1).trim();
                    if (StringUtils.hasText(t)) {
                        return t;
                    }
                }
            }
        }

        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (ACCESS_COOKIE.equals(c.getName()) && StringUtils.hasText(c.getValue())) {
                return c.getValue();
            }
        }
        return null;
    }
}
