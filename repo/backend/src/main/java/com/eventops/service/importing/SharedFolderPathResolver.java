package com.eventops.service.importing;

import com.eventops.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Resolves import source paths and enforces containment inside the configured shared-folder root.
 */
@Component
public class SharedFolderPathResolver {

    private final Path sharedFolderBasePath;

    public SharedFolderPathResolver(@Value("${eventops.shared-folder.base-path}") String sharedFolderBasePath) {
        this.sharedFolderBasePath = Path.of(sharedFolderBasePath).toAbsolutePath().normalize();
    }

    public Path resolveForExecution(String configuredFolderPath) {
        String rawPath = configuredFolderPath == null ? "" : configuredFolderPath.trim();
        if (rawPath.isBlank()) {
            throw new BusinessException("folderPath is required", 422, "INVALID_FOLDER_PATH");
        }

        try {
            Path candidate = Path.of(rawPath);
            Path resolved = candidate.isAbsolute()
                    ? candidate.normalize()
                    : sharedFolderBasePath.resolve(candidate).normalize();

            if (!resolved.startsWith(sharedFolderBasePath)) {
                throw new BusinessException(
                        "folderPath must remain inside the configured shared-folder base path",
                        422,
                        "FOLDER_PATH_OUTSIDE_SHARED_ROOT"
                );
            }

            return resolved;
        } catch (InvalidPathException ex) {
            throw new BusinessException("folderPath is invalid", 422, "INVALID_FOLDER_PATH");
        }
    }

    public String sanitizeForStorage(String configuredFolderPath) {
        resolveForExecution(configuredFolderPath);
        return configuredFolderPath == null ? null : configuredFolderPath.trim();
    }
}
