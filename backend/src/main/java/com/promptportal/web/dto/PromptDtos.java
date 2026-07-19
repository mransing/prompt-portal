package com.promptportal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class PromptDtos {

    private PromptDtos() {
    }

    public record CreatePromptRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 4000) String description,
            String status,
            String mediaTypeFocus,
            List<String> tags,
            @NotBlank String body,
            String negativePrompt,
            String parametersJson,
            String changeSummary
    ) {
    }

    public record UpdatePromptRequest(
            @Size(max = 200) String title,
            @Size(max = 4000) String description,
            String status,
            String mediaTypeFocus,
            List<String> tags,
            String body,
            String negativePrompt,
            String parametersJson,
            String changeSummary
    ) {
    }

    public record MediaSummary(
            String id,
            String fileName,
            String mimeType,
            long byteSize,
            Integer width,
            Integer height,
            String caption,
            Instant createdAt,
            String contentUrl
    ) {
    }

    public record VersionSummary(
            String id,
            int versionNumber,
            String changeSummary,
            Instant createdAt,
            String createdById
    ) {
    }

    public record VersionDetail(
            String id,
            int versionNumber,
            String body,
            String negativePrompt,
            String parametersJson,
            String changeSummary,
            Instant createdAt,
            String createdById
    ) {
    }

    public record PromptSummary(
            String id,
            String title,
            String description,
            String status,
            String mediaTypeFocus,
            List<String> tags,
            int currentVersion,
            Instant createdAt,
            Instant updatedAt,
            Instant lastModifiedAt,
            int mediaCount,
            String thumbnailMediaId
    ) {
    }

    public record PromptDetail(
            String id,
            String title,
            String description,
            String status,
            String mediaTypeFocus,
            List<String> tags,
            int currentVersion,
            Instant createdAt,
            Instant updatedAt,
            Instant lastModifiedAt,
            VersionDetail latestVersion,
            List<MediaSummary> media
    ) {
    }

    public record StorageUsage(
            long usedBytes,
            long quotaBytes,
            double usedRatio,
            boolean warning
    ) {
    }

    public record UserInfo(
            String id,
            String email,
            String name,
            String provider,
            String image,
            boolean devMode
    ) {
    }

    public record CompareResponse(
            int leftVersion,
            int rightVersion,
            String leftBody,
            String rightBody,
            String leftNegative,
            String rightNegative,
            String leftParameters,
            String rightParameters
    ) {
    }
}
