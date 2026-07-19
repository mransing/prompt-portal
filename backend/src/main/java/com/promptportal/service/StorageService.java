package com.promptportal.service;

import com.promptportal.config.AppProperties;
import com.promptportal.exception.ApiException;
import com.promptportal.repository.MediaAssetRepository;
import com.promptportal.web.dto.PromptDtos;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Service
public class StorageService {

    private final AppProperties props;
    private final MediaAssetRepository mediaRepo;

    public StorageService(AppProperties props, MediaAssetRepository mediaRepo) {
        this.props = props;
        this.mediaRepo = mediaRepo;
    }

    public Path uploadRoot() {
        return Path.of(props.getStorage().getUploadDir()).toAbsolutePath().normalize();
    }

    public long usedBytes(String ownerId) {
        Long sum = mediaRepo.sumByteSizeByOwnerId(ownerId);
        return sum != null ? sum : 0L;
    }

    public PromptDtos.StorageUsage usage(String ownerId) {
        long used = usedBytes(ownerId);
        long quota = props.getStorage().getQuotaBytes();
        double ratio = quota <= 0 ? 0 : (double) used / (double) quota;
        return new PromptDtos.StorageUsage(used, quota, ratio, ratio >= 0.9);
    }

    public void ensureQuota(String ownerId, long additionalBytes) {
        long used = usedBytes(ownerId);
        long quota = props.getStorage().getQuotaBytes();
        if (used + additionalBytes > quota) {
            throw ApiException.quotaExceeded("Storage quota of 50 MB would be exceeded.");
        }
    }

    public Path resolveSafe(String relativePath) {
        Path root = uploadRoot();
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw ApiException.forbidden("Invalid storage path.");
        }
        return resolved;
    }

    public String writeAsset(String promptId, String assetId, String extension, byte[] bytes) {
        try {
            Path dir = uploadRoot().resolve(promptId);
            Files.createDirectories(dir);
            String relative = promptId + "/" + assetId + "." + extension;
            Path target = resolveSafe(relative);
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return relative.replace('\\', '/');
        } catch (IOException e) {
            throw new ApiException("INTERNAL_ERROR",
                    "Failed to store file.",
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void deleteIfExists(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolveSafe(relativePath));
        } catch (IOException ignored) {
            // best effort
        }
    }

    public byte[] readBytes(String relativePath) {
        try {
            return Files.readAllBytes(resolveSafe(relativePath));
        } catch (IOException e) {
            throw ApiException.notFound("Media file not found.");
        }
    }
}
