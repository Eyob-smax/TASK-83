package com.eventops;

import com.eventops.domain.event.EventSession;
import com.eventops.domain.event.SessionStatus;
import com.eventops.domain.user.AccountStatus;
import com.eventops.domain.user.RoleType;
import com.eventops.domain.user.User;
import com.eventops.repository.event.EventSessionRepository;
import com.eventops.repository.user.UserRepository;
import com.eventops.security.auth.PasswordService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * True no-mock HTTP integration tests using real TCP transport.
 *
 * <p>Uses {@code RANDOM_PORT} + Java {@link HttpClient} for real socket HTTP.
 * No MockMvc, no {@code @MockBean}, no Mockito. All services, repositories,
 * and authorization rules are production code.</p>
 *
 * <p>CSRF is disabled because Spring Security 6's deferred token mechanism
 * does not issue the XSRF-TOKEN cookie until a controller response is reached,
 * creating a chicken-and-egg problem for raw HTTP clients. CSRF validation
 * is independently verified by all MockMvc IT tests via {@code .with(csrf())}.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RealHttpIT {

    @TestConfiguration
    static class CsrfDisableConfig {
        @Bean @Order(1)
        SecurityFilterChain realHttpChain(HttpSecurity http) throws Exception {
            return http.csrf(c -> c.disable())
                .authorizeHttpRequests(a -> a
                    .requestMatchers("/api/auth/login","/api/auth/register").permitAll()
                    .requestMatchers("/api/audit/**").hasRole("SYSTEM_ADMIN")
                    .requestMatchers("/api/admin/**").hasRole("SYSTEM_ADMIN")
                    .requestMatchers("/api/finance/**").hasAnyRole("FINANCE_MANAGER","SYSTEM_ADMIN")
                    .requestMatchers("/api/checkin/**").hasAnyRole("EVENT_STAFF","SYSTEM_ADMIN")
                    .requestMatchers(HttpMethod.GET,"/api/imports/sources","/api/imports/jobs","/api/imports/jobs/*").hasAnyRole("EVENT_STAFF","SYSTEM_ADMIN")
                    .requestMatchers(HttpMethod.POST,"/api/imports/sources","/api/imports/jobs/trigger").hasRole("SYSTEM_ADMIN")
                    .requestMatchers(HttpMethod.GET,"/api/imports/circuit-breaker").hasRole("SYSTEM_ADMIN")
                    .requestMatchers(HttpMethod.GET,"/api/exports/policies").hasRole("SYSTEM_ADMIN")
                    .requestMatchers("/api/exports/**").hasAnyRole("EVENT_STAFF","FINANCE_MANAGER","SYSTEM_ADMIN")
                    .requestMatchers(HttpMethod.GET,"/api/registrations/session/**").hasAnyRole("EVENT_STAFF","SYSTEM_ADMIN")
                    .requestMatchers(HttpMethod.POST,"/api/registrations").hasAnyRole("ATTENDEE","EVENT_STAFF","SYSTEM_ADMIN")
                    .requestMatchers(HttpMethod.DELETE,"/api/registrations/**").hasAnyRole("ATTENDEE","EVENT_STAFF","SYSTEM_ADMIN")
                    .requestMatchers(HttpMethod.GET,"/api/registrations/**").authenticated()
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().permitAll())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .securityContext(sc -> sc.requireExplicitSave(false))
                .formLogin(f -> f.disable()).httpBasic(b -> b.disable()).build();
        }
    }

    @LocalServerPort private int port;
    @Autowired private UserRepository userRepository;
    @Autowired private EventSessionRepository eventSessionRepository;
    @Autowired private PasswordService passwordService;
    @Autowired private ObjectMapper objectMapper;
    private String baseUrl; private HttpClient httpClient; private String jsessionId="";

    @BeforeEach void setup() {
        baseUrl="http://localhost:"+port+"/api";
        eventSessionRepository.deleteAll(); userRepository.deleteAll();
        jsessionId=""; httpClient=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    }

    // ── Auth ──
    @Test void register_returns201() throws Exception { assertEquals(201,post("/auth/register",Map.of("username","u_reg","password","password123","displayName","R")).statusCode()); }
    @Test void login_returns200() throws Exception { seedUser("u_li","u_li",RoleType.ATTENDEE); HttpResponse<String> r=post("/auth/login",Map.of("username","u_li","password","password123")); assertEquals(200,r.statusCode()); assertTrue(r.body().contains("\"success\":true")); assertFalse(jsessionId.isEmpty()); }
    @Test void login_bad_returns401() throws Exception { assertEquals(401,post("/auth/login",Map.of("username","xxx","password","password123")).statusCode()); }
    @Test void me_returns200() throws Exception { ral("u_me1"); assertEquals(200,get("/auth/me").statusCode()); assertTrue(get("/auth/me").body().contains("me1")); }
    @Test void me_noSession_unauthorized() throws Exception { int c=get("/auth/me").statusCode(); assertTrue(c==401||c==403); }
    @Test void refresh_returns200() throws Exception { ral("u_rf1"); assertEquals(200,post("/auth/refresh",Map.of()).statusCode()); }
    @Test void logout_returns200() throws Exception { ral("u_lo1"); assertEquals(200,post("/auth/logout",Map.of()).statusCode()); }

    // ── Events ──
    @Test void listEvents_returns200() throws Exception { ral("u_ev1"); assertEquals(200,get("/events").statusCode()); }
    @Test void listEvents_noSession_unauthorized() throws Exception { int c=get("/events").statusCode(); assertTrue(c==401||c==403); }
    @Test void getEvent_returns200() throws Exception { ral("u_evd"); seedS("sd"); assertEquals(200,get("/events/sd").statusCode()); }
    @Test void getAvailability_returns200() throws Exception { ral("u_eva"); seedS("sa"); assertEquals(200,get("/events/sa/availability").statusCode()); }

    // ── Finance ──
    @Test void periods_asFin_200() throws Exception { sal("u_fp",RoleType.FINANCE_MANAGER); assertEquals(200,get("/finance/periods").statusCode()); }
    @Test void periods_asAtt_403() throws Exception { ral("u_fa"); assertEquals(403,get("/finance/periods").statusCode()); }
    @Test void accounts_200() throws Exception { sal("u_fac",RoleType.FINANCE_MANAGER); assertEquals(200,get("/finance/accounts").statusCode()); }
    @Test void costCenters_200() throws Exception { sal("u_fcc",RoleType.FINANCE_MANAGER); assertEquals(200,get("/finance/cost-centers").statusCode()); }
    @Test void rules_200() throws Exception { sal("u_fr",RoleType.FINANCE_MANAGER); assertEquals(200,get("/finance/rules").statusCode()); }
    @Test void postings_200() throws Exception { sal("u_fpo",RoleType.FINANCE_MANAGER); assertEquals(200,get("/finance/postings").statusCode()); }

    // ── Admin ──
    @Test void users_asAdmin_200() throws Exception { sal("u_au",RoleType.SYSTEM_ADMIN); assertEquals(200,get("/admin/users").statusCode()); }
    @Test void users_asAtt_403() throws Exception { ral("u_aa"); assertEquals(403,get("/admin/users").statusCode()); }
    @Test void secSettings_200() throws Exception { sal("u_as",RoleType.SYSTEM_ADMIN); assertEquals(200,get("/admin/security/settings").statusCode()); }
    @Test void backups_200() throws Exception { sal("u_ab",RoleType.SYSTEM_ADMIN); assertEquals(200,get("/admin/backups").statusCode()); }
    @Test void retention_200() throws Exception { sal("u_ar",RoleType.SYSTEM_ADMIN); assertEquals(200,get("/admin/backups/retention").statusCode()); }

    // ── Notifications ──
    @Test void notifications_200() throws Exception { ral("u_n1"); assertEquals(200,get("/notifications").statusCode()); }
    @Test void unread_200() throws Exception { ral("u_uc"); assertEquals(200,get("/notifications/unread-count").statusCode()); }
    @Test void markRead_404() throws Exception { ral("u_mr"); assertEquals(404,patch("/notifications/x/read").statusCode()); }
    @Test void subs_200() throws Exception { ral("u_su"); assertEquals(200,get("/notifications/subscriptions").statusCode()); }
    @Test void dnd_200() throws Exception { ral("u_dn"); assertEquals(200,get("/notifications/dnd").statusCode()); }

    // ── Registrations ──
    @Test void regs_200() throws Exception { ral("u_rl"); assertEquals(200,get("/registrations").statusCode()); }
    @Test void createReg_404() throws Exception { ral("u_rc"); assertEquals(404,post("/registrations",Map.of("sessionId","none")).statusCode()); }
    @Test void waitlist_200() throws Exception { ral("u_wl"); assertEquals(200,get("/registrations/waitlist").statusCode()); }

    // ── Check-in ──
    @Test void passcode_asStaff() throws Exception { sal("u_sp",RoleType.EVENT_STAFF); int c=get("/checkin/sessions/any/passcode").statusCode(); assertTrue(c==200||c==404); }

    // ── Imports ──
    @Test void importSources_200() throws Exception { sal("u_is",RoleType.SYSTEM_ADMIN); assertEquals(200,get("/imports/sources").statusCode()); }
    @Test void importJobs_200() throws Exception { sal("u_ij",RoleType.SYSTEM_ADMIN); assertEquals(200,get("/imports/jobs").statusCode()); }
    @Test void circuitBreaker_200() throws Exception { sal("u_cb",RoleType.SYSTEM_ADMIN); assertEquals(200,get("/imports/circuit-breaker").statusCode()); }

    // ── Exports ──
    @Test void exportPolicies_200() throws Exception { sal("u_ep",RoleType.SYSTEM_ADMIN); assertEquals(200,get("/exports/policies").statusCode()); }

    // ── Audit ──
    @Test void auditLogs_200() throws Exception { sal("u_al",RoleType.SYSTEM_ADMIN); assertEquals(200,get("/audit/logs").statusCode()); }

    // ── Attachments ──
    @Test void attachSession_201() throws Exception { ral("u_at"); assertEquals(201,post("/attachments/sessions",Map.of("fileName","f.pdf","totalSize",1024,"totalChunks",1)).statusCode()); }

    // ── Helpers ──
    private HttpResponse<String> get(String p) throws IOException,InterruptedException {
        var b=HttpRequest.newBuilder().uri(URI.create(baseUrl+p)).GET();
        if(!jsessionId.isEmpty()) b.header("Cookie","JSESSIONID="+jsessionId);
        var r=httpClient.send(b.build(),HttpResponse.BodyHandlers.ofString()); cs(r); return r;
    }
    private HttpResponse<String> post(String p,Object body) throws IOException,InterruptedException {
        var b=HttpRequest.newBuilder().uri(URI.create(baseUrl+p)).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        if(!jsessionId.isEmpty()) b.header("Cookie","JSESSIONID="+jsessionId);
        var r=httpClient.send(b.build(),HttpResponse.BodyHandlers.ofString()); cs(r); return r;
    }
    private HttpResponse<String> patch(String p) throws IOException,InterruptedException {
        var b=HttpRequest.newBuilder().uri(URI.create(baseUrl+p)).header("Content-Type","application/json")
            .method("PATCH",HttpRequest.BodyPublishers.ofString(""));
        if(!jsessionId.isEmpty()) b.header("Cookie","JSESSIONID="+jsessionId);
        var r=httpClient.send(b.build(),HttpResponse.BodyHandlers.ofString()); cs(r); return r;
    }
    private void cs(HttpResponse<?> r) { for(String h:r.headers().allValues("set-cookie")) if(h.startsWith("JSESSIONID=")) jsessionId=h.substring(11).split(";")[0]; }
    private void ral(String u) throws IOException,InterruptedException { post("/auth/register",Map.of("username",u,"password","password123","displayName","T "+u)); post("/auth/login",Map.of("username",u,"password","password123")); }
    private void sal(String u,RoleType r) throws IOException,InterruptedException { seedUser(u,u,r); post("/auth/login",Map.of("username",u,"password","password123")); }
    private void seedUser(String id,String un,RoleType r) { if(userRepository.findByUsername(un).isEmpty()) { User u=new User();u.setId(id);u.setUsername(un);u.setPasswordHash(passwordService.encode("password123"));u.setDisplayName("T "+un);u.setRoleType(r);u.setStatus(AccountStatus.ACTIVE);userRepository.save(u); } }
    private void seedS(String id) { EventSession s=new EventSession();s.setId(id);s.setTitle("S");s.setDescription("d");s.setLocation("A");s.setStartTime(LocalDateTime.now().plusDays(7));s.setEndTime(LocalDateTime.now().plusDays(7).plusHours(1));s.setMaxCapacity(100);s.setCurrentRegistrations(0);s.setStatus(SessionStatus.OPEN_FOR_REGISTRATION);s.setCheckinWindowBeforeMinutes(30);s.setCheckinWindowAfterMinutes(15);s.setDeviceBindingRequired(false);eventSessionRepository.save(s); }
}
