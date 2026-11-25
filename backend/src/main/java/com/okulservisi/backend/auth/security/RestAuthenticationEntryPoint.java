package com.okulservisi.backend.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.okulservisi.backend.common.web.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** Kimliği doğrulanmamış isteklerde docs/API.md şemasına uygun 401 JSON döner (Spring Security varsayılanı yerine). */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError body = ApiError.of(HttpStatus.UNAUTHORIZED.value(), "UNAUTHORIZED",
                "Bu işlem için giriş yapmanız gerekiyor.", request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
