package com.eventops.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration for the EventOps backend.
 *
 * <p>Configures CORS policies for local-network Vue.js client access.
 * In the standard Docker Compose deployment, the frontend nginx proxy issues
 * all API requests from the same origin as the browser (same-origin proxying),
 * so these CORS headers are primarily relevant for direct API access during
 * development or automated testing.</p>
 *
 * <p>CORS origins are configurable via the {@code CORS_ALLOWED_ORIGINS}
 * environment variable (comma-separated). The default allows the Vite dev
 * server (port 5173), the nginx frontend port (443), and bare localhost.</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${eventops.cors.allowed-origins:http://localhost:443,http://localhost:5173,http://localhost}")
    private String corsAllowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = corsAllowedOrigins.split(",");
        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
