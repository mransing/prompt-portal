package com.promptportal.service;

import com.promptportal.domain.MediaAsset;
import com.promptportal.domain.PromptDocument;
import com.promptportal.exception.ApiException;
import com.promptportal.logging.AuditLogger;
import com.promptportal.media.ImageValidationService;
import com.promptportal.repository.MediaAssetRepository;
import com.promptportal.repository.PromptRepository;
import com.promptportal.web.dto.PromptDtos;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class MediaService {

    private final PromptRepository promptRepo;
    private final MediaAssetRepository mediaRepo;
    private final ImageValidationService validator;
    private final StorageService storage;
    private final AuditLogger audit;

    public MediaService(PromptRepository promptRepo,
                        MediaAssetRepository mediaRepo,
                        ImageValidationService validator,
                        StorageService storage,
                        AuditLogger audit) {
        this.promptRepo = promptRepo;
        this.mediaRepo = mediaRepo;
        this.validator = validator;
        this.storage = storage;
        this.audit = audit;
    }

    public List<PromptDtos.MediaSummary> upload(String ownerId, String promptId, MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw ApiException.validation("No files provided.");
        }
        PromptDocument prompt = requirePrompt(ownerId, promptId);
        List<PromptDtos.MediaSummary> result = new ArrayList<>();
        for (MultipartFile file : files) {
            result.add(storeNew(ownerId, prompt, file));
        }
        touch(prompt);
        return result;
    }

    public PromptDtos.MediaSummary replace(String ownerId, String promptId, String mediaId, MultipartFile file) {
        PromptDocument prompt = requirePrompt(ownerId, promptId);
        MediaAsset existing = mediaRepo.findByIdAndPromptId(mediaId, promptId)
                .orElseThrow(() -> ApiException.notFound("Media not found."));
        if (!ownerId.equals(existing.getOwnerId())) {
            throw ApiException.forbidden("Access denied.");
        }

        ImageValidationService.ValidatedImage v = validator.validate(file);
        long delta = v.bytes().length - existing.getByteSize();
        if (delta > 0) {
            storage.ensureQuota(ownerId, delta);
        }

        String assetId = existing.getId();
        String relative = storage.writeAsset(promptId, assetId + "-new", v.extension(), v.bytes());
        String oldPath = existing.getStoragePath();
        try {
            // move into final name
            storage.deleteIfExists(promptId + "/" + assetId + "." + extensionOf(existing.getStoragePath()));
            // keep new path as storage
            existing.setStoragePath(relative);
            existing.setMimeType(v.mimeType());
            existing.setByteSize(v.bytes().length);
            existing.setWidth(v.width());
            existing.setHeight(v.height());
            existing.setChecksum(v.checksum());
            existing.setFileName(sanitizeName(v.originalFileName(), v.extension()));
            existing.setUpdatedAt(Instant.now());
            existing = mediaRepo.save(existing);
            storage.deleteIfExists(oldPath);
            audit.log("MEDIA_REPLACE", ownerId, "media", existing.getId(), "ok");
            touch(prompt);
            return toSummary(existing);
        } catch (RuntimeException e) {
            storage.deleteIfExists(relative);
            throw e;
        }
    }

    public void delete(String ownerId, String promptId, String mediaId) {
        PromptDocument prompt = requirePrompt(ownerId, promptId);
        MediaAsset existing = mediaRepo.findByIdAndPromptId(mediaId, promptId)
                .orElseThrow(() -> ApiException.notFound("Media not found."));
        if (!ownerId.equals(existing.getOwnerId())) {
            throw ApiException.forbidden("Access denied.");
        }
        storage.deleteIfExists(existing.getStoragePath());
        mediaRepo.delete(existing);
        audit.log("MEDIA_DELETE", ownerId, "media", mediaId, "ok");
        touch(prompt);
    }

    public MediaAsset getOwned(String ownerId, String promptId, String mediaId) {
        requirePrompt(ownerId, promptId);
        MediaAsset asset = mediaRepo.findByIdAndPromptId(mediaId, promptId)
                .orElseThrow(() -> ApiException.notFound("Media not found."));
        if (!ownerId.equals(asset.getOwnerId())) {
            throw ApiException.forbidden("Access denied.");
        }
        return asset;
    }

    public List<PromptDtos.MediaSummary> list(String promptId) {
        return mediaRepo.findByPromptIdOrderByCreatedAtDesc(promptId).stream()
                .map(this::toSummary)
                .toList();
    }

    public void deleteAllForPrompt(String promptId) {
        for (MediaAsset m : mediaRepo.findByPromptIdOrderByCreatedAtDesc(promptId)) {
            storage.deleteIfExists(m.getStoragePath());
        }
        mediaRepo.deleteByPromptId(promptId);
    }

    private PromptDtos.MediaSummary storeNew(String ownerId, PromptDocument prompt, MultipartFile file) {
        ImageValidationService.ValidatedImage v = validator.validate(file);
        storage.ensureQuota(ownerId, v.bytes().length);
        String assetId = UUID.randomUUID().toString().replace("-", "");
        String relative = storage.writeAsset(prompt.getId(), assetId, v.extension(), v.bytes());
        try {
            MediaAsset asset = new MediaAsset();
            asset.setId(assetId);
            asset.setPromptId(prompt.getId());
            asset.setOwnerId(ownerId);
            asset.setRole("output");
            asset.setFileName(sanitizeName(v.originalFileName(), v.extension()));
            asset.setMimeType(v.mimeType());
            asset.setByteSize(v.bytes().length);
            asset.setStoragePath(relative);
            asset.setWidth(v.width());
            asset.setHeight(v.height());
            asset.setChecksum(v.checksum());
            asset.setCreatedAt(Instant.now());
            asset.setUpdatedAt(Instant.now());
            asset = mediaRepo.save(asset);
            audit.log("MEDIA_UPLOAD", ownerId, "media", asset.getId(), "ok");
            if (storage.usage(ownerId).warning()) {
                audit.log("STORAGE_QUOTA_WARNING", ownerId, "storage", ownerId, "near_quota");
            }
            return toSummary(asset);
        } catch (RuntimeException e) {
            storage.deleteIfExists(relative);
            throw e;
        }
    }

    private PromptDocument requirePrompt(String ownerId, String promptId) {
        return promptRepo.findByIdAndOwnerIdAndDeletedAtIsNull(promptId, ownerId)
                .orElseThrow(() -> ApiException.notFound("Prompt not found."));
    }

    private void touch(PromptDocument prompt) {
        Instant now = Instant.now();
        prompt.setUpdatedAt(now);
        prompt.setLastModifiedAt(now);
        promptRepo.save(prompt);
    }

    private PromptDtos.MediaSummary toSummary(MediaAsset m) {
        return new PromptDtos.MediaSummary(
                m.getId(),
                m.getFileName(),
                m.getMimeType(),
                m.getByteSize(),
                m.getWidth(),
                m.getHeight(),
                m.getCaption(),
                m.getCreatedAt(),
                "/api/prompts/" + m.getPromptId() + "/media/" + m.getId()
        );
    }

    private static String sanitizeName(String original, String ext) {
        if (original == null || original.isBlank()) {
            return "image." + ext;
        }
        String base = original.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (base.length() > 120) {
            base = base.substring(0, 120);
        }
        return base;
    }

    private static String extensionOf(String path) {
        if (path == null) {
            return "bin";
        }
        int i = path.lastIndexOf('.');
        return i >= 0 ? path.substring(i + 1) : "bin";
    }
}
