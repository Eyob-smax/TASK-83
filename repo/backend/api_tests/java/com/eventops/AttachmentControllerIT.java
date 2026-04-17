package com.eventops;

import com.eventops.domain.user.RoleType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AttachmentControllerIT {

    @Autowired private MockMvc mockMvc;

    @Test
    void createSession_asAuthenticatedUser_returnsCreatedSession() throws Exception {
        mockMvc.perform(post("/api/attachments/sessions")
                        .with(csrf())
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileName": "brief.pdf",
                                  "totalSize": 1024,
                                  "totalChunks": 2
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Upload session created"))
                .andExpect(jsonPath("$.data.uploadId").exists())
                .andExpect(jsonPath("$.data.fileName").value("brief.pdf"));
    }

    @Test
    void uploadChunk_andGetStatus_worksEndToEnd() throws Exception {
        // Step 1: Create upload session
        String createResponse = mockMvc.perform(post("/api/attachments/sessions")
                        .with(csrf())
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileName": "brief.pdf",
                                  "totalSize": 1024,
                                  "totalChunks": 2
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String uploadId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.data.uploadId");

        // Step 2: Upload first chunk
        mockMvc.perform(put("/api/attachments/sessions/" + uploadId + "/chunks/0")
                        .with(csrf())
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE))
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(new byte[]{1, 2, 3}))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Chunk uploaded"));

        // Step 3: Get status as owner
        mockMvc.perform(get("/api/attachments/sessions/" + uploadId)
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.uploadId").value(uploadId))
                .andExpect(jsonPath("$.data.uploadedChunks[0]").value(0));
    }

    @Test
    void getStatus_asSystemAdmin_canViewAnySession() throws Exception {
        // Create session as attendee
        String createResponse = mockMvc.perform(post("/api/attachments/sessions")
                        .with(csrf())
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileName": "brief.pdf",
                                  "totalSize": 1024,
                                  "totalChunks": 2
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String uploadId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.data.uploadId");

        // Admin can view the session
        mockMvc.perform(get("/api/attachments/sessions/" + uploadId)
                        .with(TestSecurity.user("admin-1", "admin", RoleType.SYSTEM_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.uploadId").value(uploadId));
    }

    @Test
    void completeUpload_allChunksUploaded_returnsCompletedStatus() throws Exception {
        // Create session with 2 chunks
        String createResponse = mockMvc.perform(post("/api/attachments/sessions")
                        .with(csrf())
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileName": "brief.pdf",
                                  "totalSize": 1024,
                                  "totalChunks": 2
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String uploadId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.data.uploadId");

        // Upload both chunks
        mockMvc.perform(put("/api/attachments/sessions/" + uploadId + "/chunks/0")
                        .with(csrf())
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE))
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(new byte[]{1, 2, 3}))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/attachments/sessions/" + uploadId + "/chunks/1")
                        .with(csrf())
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE))
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(new byte[]{4, 5, 6}))
                .andExpect(status().isOk());

        // Complete the upload
        mockMvc.perform(post("/api/attachments/sessions/" + uploadId + "/complete")
                        .with(csrf())
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Upload completed"))
                .andExpect(jsonPath("$.data.completed").value(true));
    }
}
