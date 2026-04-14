package com.eventops.security.config;

import com.eventops.security.ratelimit.RateLimitFilter;
import com.eventops.security.signature.SignatureVerificationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Central Spring Security configuration for the EventOps platform.
 *
 * <p>Key design decisions:</p>
 * <ul>
 *   <li><strong>CSRF disabled</strong> — the Vue.js SPA uses token-based
 *       authentication; the browser never sends implicit credentials.</li>
 *   <li><strong>Session policy IF_REQUIRED</strong> — stateful sessions are
 *       appropriate for the offline local-network deployment model.</li>
 *   <li><strong>CORS wide-open for now</strong> — will be tightened in a
 *       later configuration pass (Prompt 9).</li>
 *   <li><strong>Form login &amp; HTTP Basic disabled</strong> — authentication
 *       is handled by a dedicated REST controller.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final JsonAuthenticationEntryPoint authenticationEntryPoint;
    private final JsonAccessDeniedHandler accessDeniedHandler;
    private final SignatureVerificationFilter signatureVerificationFilter;
    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(UserDetailsService userDetailsService,
                          JsonAuthenticationEntryPoint authenticationEntryPoint,
                          JsonAccessDeniedHandler accessDeniedHandler,
                          SignatureVerificationFilter signatureVerificationFilter,
                          RateLimitFilter rateLimitFilter) {
        this.userDetailsService = userDetailsService;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.signatureVerificationFilter = signatureVerificationFilter;
        this.rateLimitFilter = rateLimitFilter;
    }

    // ── Filter chain ────────────────────────────────────────────────────────

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CSRF — disabled for SPA with token-based auth
            .csrf(csrf -> csrf.disable())

            // CORS — permissive for local-network development; tightened in Prompt 9
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // Session management — stateful, suitable for offline local-network
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )

            // URL-based authorization rules
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/api/auth/register").permitAll()
                .requestMatchers("/api/audit/**").hasRole("SYSTEM_ADMIN")
                .requestMatchers("/api/admin/**").hasRole("SYSTEM_ADMIN")
                .requestMatchers("/api/finance/**").hasAnyRole("FINANCE_MANAGER", "SYSTEM_ADMIN")
                .requestMatchers("/api/checkin/**").hasAnyRole("EVENT_STAFF", "SYSTEM_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/imports/sources", "/api/imports/jobs", "/api/imports/jobs/*")
                    .hasAnyRole("EVENT_STAFF", "SYSTEM_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/imports/sources", "/api/imports/jobs/trigger")
                    .hasRole("SYSTEM_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/imports/sources/*")
                    .hasRole("SYSTEM_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/imports/circuit-breaker")
                    .hasRole("SYSTEM_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/exports/policies")
                    .hasRole("SYSTEM_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/exports/policies")
                    .hasRole("SYSTEM_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/exports/rosters")
                    .hasAnyRole("EVENT_STAFF", "SYSTEM_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/exports/finance-reports")
                    .hasAnyRole("FINANCE_MANAGER", "SYSTEM_ADMIN")
                .requestMatchers("/api/exports/**").hasAnyRole("EVENT_STAFF", "FINANCE_MANAGER", "SYSTEM_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/registrations/session/**")
                    .hasAnyRole("EVENT_STAFF", "SYSTEM_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/registrations")
                    .hasAnyRole("ATTENDEE", "EVENT_STAFF", "SYSTEM_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/registrations/**")
                    .hasAnyRole("ATTENDEE", "EVENT_STAFF", "SYSTEM_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/registrations/**").authenticated()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )

            // Disable form login and HTTP Basic — auth is via REST controller
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())

            // Custom JSON error responses
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )

            // Explicit filter ordering relative to populated SecurityContext
            .addFilterAfter(signatureVerificationFilter, SecurityContextHolderFilter.class)
            .addFilterAfter(rateLimitFilter, SignatureVerificationFilter.class);

        return http.build();
    }

    @Bean
    public FilterRegistrationBean<SignatureVerificationFilter> signatureVerificationFilterRegistration(
            SignatureVerificationFilter filter) {
        FilterRegistrationBean<SignatureVerificationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    // ── CORS ────────────────────────────────────────────────────────────────

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    // ── Password encoder ────────────────────────────────────────────────────

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    // ── Authentication provider & manager ───────────────────────────────────

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}
