package com.promptportal.web;

import com.promptportal.domain.MediaAsset;
import com.promptportal.security.SecurityUtils;
import com.promptportal.service.MediaService;
import com.promptportal.service.StorageService;
import com.promptportal.web.dto.PromptDtos;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/prompts/{promptId}/media")
public class MediaController {

    private final MediaService mediaService;
    private final StorageService storageService;

    public MediaController(MediaService mediaService, StorageService storageService) {
        this.mediaService = mediaService;
        this.storageService = storageService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public List<PromptDtos.MediaSummary> upload(@PathVariable String promptId,
                                                @RequestParam("files") MultipartFile[] files) {
        return mediaService.upload(SecurityUtils.requireUser().getUserId(), promptId, files);
    }

    @PutMapping(value = "/{mediaId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PromptDtos.MediaSummary replace(@PathVariable String promptId,
                                           @PathVariable String mediaId,
                                           @RequestParam("file") MultipartFile file) {
        return mediaService.replace(SecurityUtils.requireUser().getUserId(), promptId, mediaId, file);
    }

    @DeleteMapping("/{mediaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String promptId, @PathVariable String mediaId) {
        mediaService.delete(SecurityUtils.requireUser().getUserId(), promptId, mediaId);
    }

    @GetMapping("/{mediaId}")
    public ResponseEntity<byte[]> download(@PathVariable String promptId, @PathVariable String mediaId) {
        String ownerId = SecurityUtils.requireUser().getUserId();
        MediaAsset asset = mediaService.getOwned(ownerId, promptId, mediaId);
        byte[] bytes = storageService.readBytes(asset.getStoragePath());
        String safeName = asset.getFileName() != null ? asset.getFileName().replace("\"", "") : "image";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, asset.getMimeType())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + safeName + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(bytes);
    }
}
