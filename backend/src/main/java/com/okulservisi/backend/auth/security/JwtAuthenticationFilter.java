package com.okulservisi.backend.auth.security;

import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stateless JWT doğrulama filtresi. {@code Authorization: Bearer <token>} başlığını doğrular ve
 * her istekte kullanıcının güncel {@code isActive}/rol durumunu DB'den tazeler (docs/SECURITY.md:
 * "kritik roller/isActive kontrolleri ayrıca her istekte DB'den doğrular").
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            jwtService.parseClaims(token).ifPresent(claims -> authenticate(claims));
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(Claims claims) {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }
        Long userId = jwtService.extractUserId(claims);
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty() || !userOpt.get().isActive()) {
            return;
        }
        AuthenticatedUser principal = new AuthenticatedUser(userOpt.get());
        var authToken = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}
