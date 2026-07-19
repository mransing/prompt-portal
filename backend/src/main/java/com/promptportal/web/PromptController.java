package com.promptportal.web;

import com.promptportal.security.AppUserPrincipal;
import com.promptportal.security.SecurityUtils;
import com.promptportal.service.PromptService;
import com.promptportal.web.dto.PromptDtos;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/prompts")
public class PromptController {

    private final PromptService promptService;

    public PromptController(PromptService promptService) {
        this.promptService = promptService;
    }

    @GetMapping
    public Page<PromptDtos.PromptSummary> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String mediaTypeFocus,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Boolean hasMedia,
            @RequestParam(required = false, defaultValue = "updated") String sort,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        AppUserPrincipal user = SecurityUtils.requireUser();
        return promptService.list(user.getUserId(), q, status, mediaTypeFocus, tag, hasMedia, sort, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PromptDtos.PromptDetail create(@Valid @RequestBody PromptDtos.CreatePromptRequest req) {
        return promptService.create(SecurityUtils.requireUser().getUserId(), req);
    }

    @GetMapping("/{id}")
    public PromptDtos.PromptDetail get(@PathVariable String id) {
        return promptService.detail(SecurityUtils.requireUser().getUserId(), id);
    }

    @PatchMapping("/{id}")
    public PromptDtos.PromptDetail update(@PathVariable String id,
                                          @Valid @RequestBody PromptDtos.UpdatePromptRequest req) {
        return promptService.update(SecurityUtils.requireUser().getUserId(), id, req);
    }

    @PostMapping("/{id}/archive")
    public PromptDtos.PromptDetail archive(@PathVariable String id) {
        return promptService.archive(SecurityUtils.requireUser().getUserId(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        promptService.softDelete(SecurityUtils.requireUser().getUserId(), id);
    }

    @GetMapping("/{id}/versions")
    public List<PromptDtos.VersionSummary> versions(@PathVariable String id) {
        return promptService.versions(SecurityUtils.requireUser().getUserId(), id);
    }

    @GetMapping("/{id}/versions/{n}")
    public PromptDtos.VersionDetail version(@PathVariable String id, @PathVariable int n) {
        return promptService.version(SecurityUtils.requireUser().getUserId(), id, n);
    }

    @PostMapping("/{id}/versions/{n}/restore")
    public PromptDtos.PromptDetail restore(@PathVariable String id, @PathVariable int n) {
        return promptService.restore(SecurityUtils.requireUser().getUserId(), id, n);
    }

    @GetMapping("/{id}/compare")
    public PromptDtos.CompareResponse compare(@PathVariable String id,
                                              @RequestParam int left,
                                              @RequestParam int right) {
        return promptService.compare(SecurityUtils.requireUser().getUserId(), id, left, right);
    }

    @GetMapping("/{id}/export")
    public Map<String, Object> export(@PathVariable String id,
                                      @RequestParam(defaultValue = "json") String format) {
        var detail = promptService.detail(SecurityUtils.requireUser().getUserId(), id);
        var versions = promptService.versions(SecurityUtils.requireUser().getUserId(), id);
        if ("markdown".equalsIgnoreCase(format) || "md".equalsIgnoreCase(format) || "text".equalsIgnoreCase(format)) {
            String md = "# " + detail.title() + "\n\n"
                    + detail.latestVersion().body() + "\n";
            return Map.of("format", "markdown", "content", md);
        }
        return Map.of(
                "format", "json",
                "prompt", detail,
                "versions", versions
        );
    }
}
