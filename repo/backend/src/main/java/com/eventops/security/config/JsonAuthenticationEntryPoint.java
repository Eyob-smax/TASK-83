package com.eventops.security.config;

import com.eventops.common.dto.ApiResponse;
import com.eventops.common.dto.ApiResponse.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Custom {@link AuthenticationEntryPoint} that returns a JSON
 * {@link ApiResponse} with HTTP 401 instead of redirecting to a login page.
 *
 * <p>This is required because the application serves a Vue.js SPA that
 * communicates via REST; browser-style redirects are not appropriate.</p>
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiResponse<Void> body = ApiResponse.error(
                "Authentication required",
                List.of(new ApiError(null, "UNAUTHORIZED", authException.getMessage()))
        );

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
