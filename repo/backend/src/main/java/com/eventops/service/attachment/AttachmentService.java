package com.eventops.service.attachment;

import com.eventops.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class AttachmentService {

    private static final String SESSION_FILE = "session.json";

    private final Path storageRoot;
    private final ObjectMapper objectMapper;

    public AttachmentService(@Value("${eventops.attachment.storage-path:/var/eventops/attachments}") String storagePath,
                             ObjectMapper objectMapper) {
        this.storageRoot = Paths.get(storagePath);
        this.objectMapper = objectMapper;
    }

    public UploadStatus createUploadSession(String fileName, long totalSize, int totalChunks, String requestedBy) {
        if (fileName == null || fileName.isBlank()) {
            throw new BusinessException("fileName is required", 422, "VALIDATION_ERROR");
        }
        if (totalChunks <= 0) {
            throw new BusinessException("totalChunks must be greater than 0", 422, "VALIDATION_ERROR");
        }

        String uploadId = UUID.randomUUID().toString();
        UploadSession session = new UploadSession();
        session.uploadId = uploadId;
        session.fileName = fileName;
        session.totalSize = totalSize;
        session.totalChunks = totalChunks;
        session.requestedBy = requestedBy;
        session.createdAt = Instant.now().toString();
        session.completed = false;

        try {
            Path sessionDir = getSessionDir(uploadId);
            Files.createDirectories(sessionDir);
            writeSession(session);
            return toStatus(session, List.of());
        } catch (IOException e) {
            throw new BusinessException("Failed to create upload session", 500, "UPLOAD_SESSION_ERROR");
        }
    }

    public UploadStatus uploadChunk(String uploadId, int chunkIndex, byte[] chunkData) {
        return uploadChunk(uploadId, chunkIndex, chunkData, null, false);
    }

    public UploadStatus uploadChunk(String uploadId, int chunkIndex, byte[] chunkData, String requestedBy, boolean systemAdmin) {
        UploadSession session = readSession(uploadId);
        enforceOwnership(session, requestedBy, systemAdmin);
        if (session.completed) {
            throw new BusinessException("Upload session already completed", 409, "UPLOAD_ALREADY_COMPLETED");
        }
        if (chunkIndex < 0 || chunkIndex >= session.totalChunks) {
            throw new BusinessException("chunkIndex is out of range", 422, "VALIDATION_ERROR");
        }

        try {
            Path sessionDir = getSessionDir(uploadId);
            Files.createDirectories(sessionDir);
            Path chunkPath = getChunkPath(uploadId, chunkIndex);
            Files.write(chunkPath, chunkData, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return getStatus(uploadId, requestedBy, systemAdmin);
        } catch (IOException e) {
            throw new BusinessException("Failed to persist upload chunk", 500, "UPLOAD_CHUNK_ERROR");
        }
    }

    public UploadStatus getStatus(String uploadId) {
        return getStatus(uploadId, null, false);
    }

    public UploadStatus getStatus(String uploadId, String requestedBy, boolean systemAdmin) {
        UploadSession session = readSession(uploadId);
        enforceOwnership(session, requestedBy, systemAdmin);
        List<Integer> uploadedChunks = listUploadedChunks(uploadId);
        return toStatus(session, uploadedChunks);
    }

    public UploadStatus completeUpload(String uploadId) {
        return completeUpload(uploadId, null, false);
    }

    public UploadStatus completeUpload(String uploadId, String requestedBy, boolean systemAdmin) {
        UploadSession session = readSession(uploadId);
        enforceOwnership(session, requestedBy, systemAdmin);
        List<Integer> uploadedChunks = listUploadedChunks(uploadId);

        if (uploadedChunks.size() != session.totalChunks) {
            throw new BusinessException("Upload is incomplete", 422, "UPLOAD_INCOMPLETE");
        }

        Path completedDir = storageRoot.resolve("completed");
        String completedFileName = uploadId + "_" + sanitizeFileName(session.fileName);
        Path completedPath = completedDir.resolve(completedFileName);

        try {
            Files.createDirectories(completedDir);
            try (OutputStream output = Files.newOutputStream(completedPath,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (int i = 0; i < session.totalChunks; i++) {
                    Path chunkPath = getChunkPath(uploadId, i);
                    try (InputStream in = Files.newInputStream(chunkPath)) {
                        in.transferTo(output);
                    }
                }
            }

            session.completed = true;
            session.completedAt = Instant.now().toString();
            session.completedFilePath = completedPath.toString();
            writeSession(session);

            return toStatus(session, uploadedChunks);
        } catch (IOException e) {
            throw new BusinessException("Failed to finalize upload", 500, "UPLOAD_COMPLETE_ERROR");
        }
    }

    private UploadSession readSession(String uploadId) {
        Path sessionFile = getSessionDir(uploadId).resolve(SESSION_FILE);
        if (!Files.exists(sessionFile)) {
            throw new BusinessException("Upload session not found", 404, "NOT_FOUND");
        }
        try {
            return objectMapper.readValue(sessionFile.toFile(), UploadSession.class);
        } catch (IOException e) {
            throw new BusinessException("Upload session is unreadable", 500, "UPLOAD_SESSION_ERROR");
        }
    }

    private void writeSession(UploadSession session) throws IOException {
        Path sessionDir = getSessionDir(session.uploadId);
        Files.createDirectories(sessionDir);
        objectMapper.writeValue(sessionDir.resolve(SESSION_FILE).toFile(), session);
    }

    private List<Integer> listUploadedChunks(String uploadId) {
        Path sessionDir = getSessionDir(uploadId);
        if (!Files.exists(sessionDir)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.list(sessionDir)) {
            return stream
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.startsWith("chunk-") && name.endsWith(".part"))
                    .map(name -> name.substring("chunk-".length(), name.length() - ".part".length()))
                    .map(Integer::parseInt)
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new BusinessException("Unable to list upload chunks", 500, "UPLOAD_STATUS_ERROR");
        }
    }

    private UploadStatus toStatus(UploadSession session, List<Integer> uploadedChunks) {
        UploadStatus status = new UploadStatus();
        status.uploadId = session.uploadId;
        status.fileName = session.fileName;
        status.totalSize = session.totalSize;
        status.totalChunks = session.totalChunks;
        status.uploadedChunks = new ArrayList<>(uploadedChunks);
        status.completed = session.completed;
        status.completedFilePath = session.completedFilePath;
        return status;
    }

    private Path getSessionDir(String uploadId) {
        return storageRoot.resolve("sessions").resolve(uploadId);
    }

    private Path getChunkPath(String uploadId, int chunkIndex) {
        return getSessionDir(uploadId).resolve("chunk-" + chunkIndex + ".part");
    }

    private void enforceOwnership(UploadSession session, String requestedBy, boolean systemAdmin) {
        if (systemAdmin) {
            return;
        }

        if (requestedBy == null || requestedBy.isBlank()) {
            throw new BusinessException("Authentication required", 401, "NOT_AUTHENTICATED");
        }

        if (session.requestedBy == null || !session.requestedBy.equals(requestedBy)) {
            throw new BusinessException("Upload session access denied", 403, "ACCESS_DENIED");
        }
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public static class UploadSession {
        public String uploadId;
        public String fileName;
        public long totalSize;
        public int totalChunks;
        public String requestedBy;
        public String createdAt;
        public boolean completed;
        public String completedAt;
        public String completedFilePath;
    }

    public static class UploadStatus {
        public String uploadId;
        public String fileName;
        public long totalSize;
        public int totalChunks;
        public List<Integer> uploadedChunks;
        public boolean completed;
        public String completedFilePath;
    }
}
