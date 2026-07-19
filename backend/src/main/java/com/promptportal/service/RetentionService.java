package com.promptportal.service;

import com.promptportal.config.AppProperties;
import com.promptportal.domain.PromptDocument;
import com.promptportal.logging.AuditLogger;
import com.promptportal.repository.PromptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private final AppProperties props;
    private final PromptRepository promptRepo;
    private final PromptService promptService;
    private final AuditLogger audit;

    public RetentionService(AppProperties props,
                            PromptRepository promptRepo,
                            PromptService promptService,
                            AuditLogger audit) {
        this.props = props;
        this.promptRepo = promptRepo;
        this.promptService = promptService;
        this.audit = audit;
    }

    @Scheduled(cron = "${app.retention.cron:0 30 3 * * *}")
    public void scheduledPurge() {
        if (!props.getRetention().isEnabled()) {
            return;
        }
        if ("true".equals(System.getProperty("app.retention.cli-only"))) {
            return;
        }
        purgeStale();
    }

    public int purgeStale() {
        Instant cutoff = Instant.now().minus(props.getRetention().getDays(), ChronoUnit.DAYS);
        List<PromptDocument> stale = promptRepo.findByLastModifiedAtBefore(cutoff);
        int count = 0;
        for (PromptDocument p : stale) {
            promptService.hardDeleteCascade(p);
            count++;
        }
        audit.log("RETENTION_PURGE", "system", "prompt", "-", "deleted=" + count);
        log.info("Retention purge deletedPrompts={} cutoff={}", count, cutoff);
        return count;
    }
}
