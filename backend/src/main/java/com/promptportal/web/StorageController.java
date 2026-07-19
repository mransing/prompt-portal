package com.promptportal.web;

import com.promptportal.security.SecurityUtils;
import com.promptportal.service.StorageService;
import com.promptportal.web.dto.PromptDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/storage")
public class StorageController {

    private final StorageService storageService;

    public StorageController(StorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping
    public PromptDtos.StorageUsage usage() {
        return storageService.usage(SecurityUtils.requireUser().getUserId());
    }
}
