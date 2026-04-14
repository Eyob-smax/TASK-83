package com.eventops.controller.attachment;

import com.eventops.common.dto.ApiResponse;
import com.eventops.common.exception.BusinessException;
import com.eventops.security.auth.EventOpsUserDetails;
import com.eventops.service.attachment.AttachmentService;
import com.eventops.service.attachment.AttachmentService.UploadStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse<UploadStatus>> createSession(@RequestBody CreateUploadSessionRequest request,
                                                                    Authentication authentication) {
        UploadStatus status = attachmentService.createUploadSession(
                request.fileName,
                request.totalSize,
                request.totalChunks,
                resolveUserId(authentication)
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(status, "Upload session created"));
    }

    @PutMapping(path = "/sessions/{uploadId}/chunks/{chunkIndex}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<ApiResponse<UploadStatus>> uploadChunk(@PathVariable String uploadId,
                                                                  @PathVariable int chunkIndex,
                                                                  @RequestBody byte[] chunkData,
                                                                  Authentication authentication) {
        UploadStatus status = attachmentService.uploadChunk(
                uploadId,
                chunkIndex,
                chunkData,
                resolveUserId(authentication),
                isSystemAdmin(authentication)
        );
        return ResponseEntity.ok(ApiResponse.success(status, "Chunk uploaded"));
    }

    @GetMapping("/sessions/{uploadId}")
    public ResponseEntity<ApiResponse<UploadStatus>> getStatus(@PathVariable String uploadId,
                                                               Authentication authentication) {
        UploadStatus status = attachmentService.getStatus(
                uploadId,
                resolveUserId(authentication),
                isSystemAdmin(authentication)
        );
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    @PostMapping("/sessions/{uploadId}/complete")
    public ResponseEntity<ApiResponse<UploadStatus>> complete(@PathVariable String uploadId,
                                                              Authentication authentication) {
        UploadStatus status = attachmentService.completeUpload(
                uploadId,
                resolveUserId(authentication),
                isSystemAdmin(authentication)
        );
        return ResponseEntity.ok(ApiResponse.success(status, "Upload completed"));
    }

    private String resolveUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof EventOpsUserDetails principal)) {
            throw new BusinessException("Authentication required", 401, "NOT_AUTHENTICATED");
        }
        return principal.getUser().getId();
    }

    private boolean isSystemAdmin(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof EventOpsUserDetails principal)) {
            return false;
        }
        return SYSTEM_ADMIN.equals(principal.getUser().getRoleType().name());
    }

    public static class CreateUploadSessionRequest {
        public String fileName;
        public long totalSize;
        public int totalChunks;
    }
}
