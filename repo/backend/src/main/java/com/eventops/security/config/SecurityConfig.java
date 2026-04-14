package com.eventops.security.config;

import com.eventops.security.ratelimit.RateLimitFilter;
import com.eventops.security.signature.SignatureVerificationFilter;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Central Spring Security configuration for the EventOps platform.
 *
 * <p>Key design decisions:</p>
 * <ul>
 *   <li><strong>CSRF disabled (token-based)</strong> — CSRF token enforcement
 *       is not used; instead the session cookie is configured with
 *       {@code SameSite=Strict} (see {@code server.servlet.session.cookie}
 *       in application.yml). Browsers never send a SameSite=Strict cookie on
 *       cross-site requests, so CSRF attacks are structurally prevented without
 *       requiring token round-trips in the SPA.</li>
 *   <li><strong>Session policy IF_REQUIRED</strong> — stateful sessions are
 *       appropriate for the offline local-network deployment model.</li>
 *   <li><strong>CORS origin allowlist</strong> — driven by the
 *       {@code eventops.cors.allowed-origins} property (overridable via the
 *       {@code CORS_ALLOWED_ORIGINS} environment variable). Defaults to
 *       localhost variants for development. Credentials are permitted because
 *       origins are explicitly listed, not wildcarded.</li>
 *   <li><strong>Form login &amp; HTTP Basic disabled</strong> — authentication
 *       is handled by a dedicated REST controller.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${eventops.cors.allowed-origins:http://localhost:443,http://localhost:5173,http://localhost}")
    private List<String> corsAllowedOrigins;

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
            // CSRF — cookie-based token repository; Axios reads XSRF-TOKEN cookie
            // and sends X-XSRF-TOKEN header automatically (matches Spring defaults).
            // CsrfTokenRequestAttributeHandler (non-XOR) is required so the raw
            // cookie value is accepted as the expected token.
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
            )

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
        configuration.setAllowedOrigins(corsAllowedOrigins);
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
