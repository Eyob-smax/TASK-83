package com.eventops.service;

import com.eventops.common.exception.BusinessException;
import com.eventops.service.attachment.AttachmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentServiceAuthorizationTest {

    private Path tempDir;
    private AttachmentService attachmentService;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("attachment-authz-test");
        attachmentService = new AttachmentService(tempDir.toString(), new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    @Test
    void getStatus_deniesNonOwnerWhenNotAdmin() {
        AttachmentService.UploadStatus created = attachmentService.createUploadSession(
                "badge.csv", 100, 1, "owner-1"
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attachmentService.getStatus(created.uploadId, "other-user", false));

        assertEquals("ACCESS_DENIED", ex.getErrorCode());
    }

    @Test
    void getStatus_allowsSystemAdminOverride() {
        AttachmentService.UploadStatus created = attachmentService.createUploadSession(
                "badge.csv", 100, 1, "owner-1"
        );

        AttachmentService.UploadStatus status = attachmentService.getStatus(created.uploadId, "admin-1", true);

        assertEquals(created.uploadId, status.uploadId);
    }

    @Test
    void completeUpload_requiresOwnerOrAdmin() {
        AttachmentService.UploadStatus created = attachmentService.createUploadSession(
                "badge.csv", 4, 1, "owner-1"
        );
        attachmentService.uploadChunk(created.uploadId, 0, new byte[]{1, 2, 3, 4}, "owner-1", false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attachmentService.completeUpload(created.uploadId, "other-user", false));

        assertEquals("ACCESS_DENIED", ex.getErrorCode());

        AttachmentService.UploadStatus completed = attachmentService.completeUpload(created.uploadId, "admin-1", true);
        assertTrue(completed.completed);
    }
}
