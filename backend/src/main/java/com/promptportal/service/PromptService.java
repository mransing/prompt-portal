package com.promptportal.service;

import com.promptportal.domain.MediaAsset;
import com.promptportal.domain.PromptDocument;
import com.promptportal.domain.PromptVersion;
import com.promptportal.exception.ApiException;
import com.promptportal.logging.AuditLogger;
import com.promptportal.repository.MediaAssetRepository;
import com.promptportal.repository.PromptRepository;
import com.promptportal.repository.PromptVersionRepository;
import com.promptportal.web.dto.PromptDtos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class PromptService {

    private final PromptRepository promptRepo;
    private final PromptVersionRepository versionRepo;
    private final MediaAssetRepository mediaRepo;
    private final MediaService mediaService;
    private final MongoTemplate mongoTemplate;
    private final AuditLogger audit;

    public PromptService(PromptRepository promptRepo,
                         PromptVersionRepository versionRepo,
                         MediaAssetRepository mediaRepo,
                         MediaService mediaService,
                         MongoTemplate mongoTemplate,
                         AuditLogger audit) {
        this.promptRepo = promptRepo;
        this.versionRepo = versionRepo;
        this.mediaRepo = mediaRepo;
        this.mediaService = mediaService;
        this.mongoTemplate = mongoTemplate;
        this.audit = audit;
    }

    public PromptDtos.PromptDetail create(String ownerId, PromptDtos.CreatePromptRequest req) {
        Instant now = Instant.now();
        PromptDocument p = new PromptDocument();
        p.setOwnerId(ownerId);
        p.setTitle(req.title().trim());
        p.setDescription(nullToEmpty(req.description()));
        p.setStatus(normalizeStatus(req.status(), "draft"));
        p.setMediaTypeFocus(normalizeFocus(req.mediaTypeFocus()));
        p.setTags(normalizeTags(req.tags()));
        p.setCurrentVersion(1);
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        p.setLastModifiedAt(now);
        p = promptRepo.save(p);

        PromptVersion v = new PromptVersion();
        v.setPromptId(p.getId());
        v.setVersionNumber(1);
        v.setBody(req.body());
        v.setNegativePrompt(nullToEmpty(req.negativePrompt()));
        v.setParametersJson(nullToEmpty(req.parametersJson()));
        v.setChangeSummary(nullToEmpty(req.changeSummary()));
        v.setCreatedById(ownerId);
        v.setCreatedAt(now);
        versionRepo.save(v);

        audit.log("PROMPT_CREATE", ownerId, "prompt", p.getId(), "ok");
        return detail(ownerId, p.getId());
    }

    public PromptDtos.PromptDetail update(String ownerId, String id, PromptDtos.UpdatePromptRequest req) {
        PromptDocument p = requireActive(ownerId, id);
        Instant now = Instant.now();

        if (req.title() != null && !req.title().isBlank()) {
            p.setTitle(req.title().trim());
        }
        if (req.description() != null) {
            p.setDescription(req.description());
        }
        if (req.status() != null) {
            p.setStatus(normalizeStatus(req.status(), p.getStatus()));
        }
        if (req.mediaTypeFocus() != null) {
            p.setMediaTypeFocus(normalizeFocus(req.mediaTypeFocus()));
        }
        if (req.tags() != null) {
            p.setTags(normalizeTags(req.tags()));
        }

        PromptVersion latest = versionRepo.findFirstByPromptIdOrderByVersionNumberDesc(id)
                .orElseThrow(() -> ApiException.notFound("Version history missing."));

        boolean textChanged = false;
        if (req.body() != null || req.negativePrompt() != null || req.parametersJson() != null) {
            String newBody = req.body() != null ? req.body() : latest.getBody();
            String newNeg = req.negativePrompt() != null ? req.negativePrompt() : nullToEmpty(latest.getNegativePrompt());
            String newParams = req.parametersJson() != null ? req.parametersJson() : nullToEmpty(latest.getParametersJson());
            textChanged = !Objects.equals(newBody, latest.getBody())
                    || !Objects.equals(newNeg, nullToEmpty(latest.getNegativePrompt()))
                    || !Objects.equals(newParams, nullToEmpty(latest.getParametersJson()));
            if (textChanged) {
                int next = p.getCurrentVersion() + 1;
                PromptVersion nv = new PromptVersion();
                nv.setPromptId(id);
                nv.setVersionNumber(next);
                nv.setBody(newBody);
                nv.setNegativePrompt(newNeg);
                nv.setParametersJson(newParams);
                nv.setChangeSummary(nullToEmpty(req.changeSummary()));
                nv.setCreatedById(ownerId);
                nv.setCreatedAt(now);
                versionRepo.save(nv);
                p.setCurrentVersion(next);
            }
        }

        p.setUpdatedAt(now);
        p.setLastModifiedAt(now);
        promptRepo.save(p);
        audit.log("PROMPT_UPDATE", ownerId, "prompt", id, textChanged ? "versioned" : "metadata");
        return detail(ownerId, id);
    }

    public PromptDtos.PromptDetail detail(String ownerId, String id) {
        PromptDocument p = requireActive(ownerId, id);
        PromptVersion latest = versionRepo.findFirstByPromptIdOrderByVersionNumberDesc(id)
                .orElseThrow(() -> ApiException.notFound("Version history missing."));
        List<PromptDtos.MediaSummary> media = mediaService.list(id);
        return new PromptDtos.PromptDetail(
                p.getId(), p.getTitle(), p.getDescription(), p.getStatus(), p.getMediaTypeFocus(),
                p.getTags(), p.getCurrentVersion(), p.getCreatedAt(), p.getUpdatedAt(), p.getLastModifiedAt(),
                toVersionDetail(latest), media
        );
    }

    public Page<PromptDtos.PromptSummary> list(String ownerId,
                                               String q,
                                               String status,
                                               String mediaTypeFocus,
                                               String tag,
                                               Boolean hasMedia,
                                               String sort,
                                               int page,
                                               int size) {
        Sort s = resolveSort(sort);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), s);

        List<Criteria> and = new ArrayList<>();
        and.add(Criteria.where("ownerId").is(ownerId));
        and.add(Criteria.where("deletedAt").is(null));
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            and.add(Criteria.where("status").is(status));
        } else {
            and.add(Criteria.where("status").ne("archived"));
        }
        if (mediaTypeFocus != null && !mediaTypeFocus.isBlank()) {
            and.add(Criteria.where("mediaTypeFocus").is(mediaTypeFocus));
        }
        if (tag != null && !tag.isBlank()) {
            and.add(Criteria.where("tags").is(tag));
        }
        if (q != null && !q.isBlank()) {
            String rx = ".*" + java.util.regex.Pattern.quote(q.trim()) + ".*";
            and.add(new Criteria().orOperator(
                    Criteria.where("title").regex(rx, "i"),
                    Criteria.where("description").regex(rx, "i")
            ));
        }

        Query query = new Query(new Criteria().andOperator(and.toArray(Criteria[]::new)));
        long total = mongoTemplate.count(query, PromptDocument.class);
        query.with(pageable);
        List<PromptDocument> docs = mongoTemplate.find(query, PromptDocument.class);

        List<PromptDtos.PromptSummary> mapped = new ArrayList<>();
        for (PromptDocument p : docs) {
            List<MediaAsset> media = mediaRepo.findByPromptIdOrderByCreatedAtDesc(p.getId());
            if (hasMedia != null) {
                boolean hm = !media.isEmpty();
                if (hasMedia != hm) {
                    continue;
                }
            }
            // body search (optional second pass)
            if (q != null && !q.isBlank()) {
                boolean titleHit = p.getTitle() != null && p.getTitle().toLowerCase(Locale.ROOT)
                        .contains(q.toLowerCase(Locale.ROOT));
                boolean descHit = p.getDescription() != null && p.getDescription().toLowerCase(Locale.ROOT)
                        .contains(q.toLowerCase(Locale.ROOT));
                if (!titleHit && !descHit) {
                    var latest = versionRepo.findFirstByPromptIdOrderByVersionNumberDesc(p.getId());
                    boolean bodyHit = latest.isPresent() && latest.get().getBody() != null
                            && latest.get().getBody().toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT));
                    if (!bodyHit) {
                        continue;
                    }
                }
            }
            mapped.add(toSummary(p, media));
        }

        // when hasMedia filter shrinks list, approximate page
        if (hasMedia != null || (q != null && !q.isBlank())) {
            total = mapped.size();
        }
        return new PageImpl<>(mapped, pageable, total);
    }

    public PromptDtos.PromptDetail archive(String ownerId, String id) {
        PromptDocument p = requireActive(ownerId, id);
        p.setStatus("archived");
        Instant now = Instant.now();
        p.setUpdatedAt(now);
        p.setLastModifiedAt(now);
        promptRepo.save(p);
        audit.log("PROMPT_ARCHIVE", ownerId, "prompt", id, "ok");
        return detail(ownerId, id);
    }

    public void softDelete(String ownerId, String id) {
        PromptDocument p = promptRepo.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> ApiException.notFound("Prompt not found."));
        if (p.getDeletedAt() != null) {
            return;
        }
        Instant now = Instant.now();
        p.setDeletedAt(now);
        p.setUpdatedAt(now);
        p.setLastModifiedAt(now);
        promptRepo.save(p);
        audit.log("PROMPT_DELETE", ownerId, "prompt", id, "soft");
    }

    public List<PromptDtos.VersionSummary> versions(String ownerId, String id) {
        requireActive(ownerId, id);
        return versionRepo.findByPromptIdOrderByVersionNumberDesc(id).stream()
                .map(v -> new PromptDtos.VersionSummary(
                        v.getId(), v.getVersionNumber(), v.getChangeSummary(), v.getCreatedAt(), v.getCreatedById()))
                .toList();
    }

    public PromptDtos.VersionDetail version(String ownerId, String id, int n) {
        requireActive(ownerId, id);
        PromptVersion v = versionRepo.findByPromptIdAndVersionNumber(id, n)
                .orElseThrow(() -> ApiException.notFound("Version not found."));
        return toVersionDetail(v);
    }

    public PromptDtos.PromptDetail restore(String ownerId, String id, int n) {
        PromptDocument p = requireActive(ownerId, id);
        PromptVersion source = versionRepo.findByPromptIdAndVersionNumber(id, n)
                .orElseThrow(() -> ApiException.notFound("Version not found."));
        Instant now = Instant.now();
        int next = p.getCurrentVersion() + 1;
        PromptVersion nv = new PromptVersion();
        nv.setPromptId(id);
        nv.setVersionNumber(next);
        nv.setBody(source.getBody());
        nv.setNegativePrompt(source.getNegativePrompt());
        nv.setParametersJson(source.getParametersJson());
        nv.setChangeSummary("Restored from v" + n);
        nv.setCreatedById(ownerId);
        nv.setCreatedAt(now);
        versionRepo.save(nv);
        p.setCurrentVersion(next);
        p.setUpdatedAt(now);
        p.setLastModifiedAt(now);
        promptRepo.save(p);
        audit.log("VERSION_RESTORE", ownerId, "prompt", id, "from_v" + n);
        return detail(ownerId, id);
    }

    public PromptDtos.CompareResponse compare(String ownerId, String id, int left, int right) {
        requireActive(ownerId, id);
        PromptVersion a = versionRepo.findByPromptIdAndVersionNumber(id, left)
                .orElseThrow(() -> ApiException.notFound("Version not found."));
        PromptVersion b = versionRepo.findByPromptIdAndVersionNumber(id, right)
                .orElseThrow(() -> ApiException.notFound("Version not found."));
        return new PromptDtos.CompareResponse(
                left, right,
                a.getBody(), b.getBody(),
                a.getNegativePrompt(), b.getNegativePrompt(),
                a.getParametersJson(), b.getParametersJson()
        );
    }

    public void hardDeleteCascade(PromptDocument p) {
        mediaService.deleteAllForPrompt(p.getId());
        versionRepo.deleteByPromptId(p.getId());
        promptRepo.delete(p);
    }

    private PromptDocument requireActive(String ownerId, String id) {
        return promptRepo.findByIdAndOwnerIdAndDeletedAtIsNull(id, ownerId)
                .orElseThrow(() -> ApiException.notFound("Prompt not found."));
    }

    private PromptDtos.PromptSummary toSummary(PromptDocument p, List<MediaAsset> media) {
        String thumb = media.isEmpty() ? null : media.get(0).getId();
        return new PromptDtos.PromptSummary(
                p.getId(), p.getTitle(), p.getDescription(), p.getStatus(), p.getMediaTypeFocus(),
                p.getTags(), p.getCurrentVersion(), p.getCreatedAt(), p.getUpdatedAt(), p.getLastModifiedAt(),
                media.size(), thumb
        );
    }

    private PromptDtos.VersionDetail toVersionDetail(PromptVersion v) {
        return new PromptDtos.VersionDetail(
                v.getId(), v.getVersionNumber(), v.getBody(), v.getNegativePrompt(),
                v.getParametersJson(), v.getChangeSummary(), v.getCreatedAt(), v.getCreatedById()
        );
    }

    private static Sort resolveSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "updatedAt");
        }
        return switch (sort) {
            case "created" -> Sort.by(Sort.Direction.DESC, "createdAt");
            case "title" -> Sort.by(Sort.Direction.ASC, "title");
            case "updated" -> Sort.by(Sort.Direction.DESC, "updatedAt");
            default -> Sort.by(Sort.Direction.DESC, "updatedAt");
        };
    }

    private static String normalizeStatus(String status, String fallback) {
        if (status == null || status.isBlank()) {
            return fallback;
        }
        String s = status.toLowerCase(Locale.ROOT);
        if (!List.of("draft", "active", "archived").contains(s)) {
            throw ApiException.validation("Invalid status. Use draft, active, or archived.");
        }
        return s;
    }

    private static String normalizeFocus(String focus) {
        if (focus == null || focus.isBlank()) {
            return "image";
        }
        String s = focus.toLowerCase(Locale.ROOT);
        if (!List.of("image", "video", "mixed", "text-only").contains(s)) {
            throw ApiException.validation("Invalid mediaTypeFocus.");
        }
        return s;
    }

    private static List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return new ArrayList<>();
        }
        return tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .distinct()
                .limit(30)
                .toList();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
