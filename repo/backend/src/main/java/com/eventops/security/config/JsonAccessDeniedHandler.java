package com.eventops.security.config;

import com.eventops.common.dto.ApiResponse;
import com.eventops.common.dto.ApiResponse.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Custom {@link AccessDeniedHandler} that returns a JSON
 * {@link ApiResponse} with HTTP 403 instead of a default error page.
 *
 * <p>Ensures the Vue.js SPA always receives a consistent JSON envelope
 * when the authenticated user lacks the required role or authority.</p>
 */
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JsonAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiResponse<Void> body = ApiResponse.error(
                "Access denied",
                List.of(new ApiError(null, "FORBIDDEN", accessDeniedException.getMessage()))
        );

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
