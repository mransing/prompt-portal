package com.promptportal;

import com.promptportal.config.AppProperties;
import com.promptportal.service.RetentionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EnableMongoAuditing
@EnableScheduling
public class PromptPortalApplication {

    private static final Logger log = LoggerFactory.getLogger(PromptPortalApplication.class);
    private static final Logger fatalLog = LoggerFactory.getLogger("FATAL");

    public static void main(String[] args) {
        if (Arrays.asList(args).contains("--purge-stale")) {
            System.setProperty("app.retention.cli-only", "true");
        }
        SpringApplication app = new SpringApplication(PromptPortalApplication.class);
        var ctx = app.run(args);
        if (Arrays.asList(args).contains("--purge-stale")) {
            try {
                int n = ctx.getBean(RetentionService.class).purgeStale();
                log.info("CLI purge-stale complete; deletedPrompts={}", n);
            } finally {
                SpringApplication.exit(ctx, () -> 0);
            }
        }
    }

    @Bean
    ApplicationRunner startupChecks(AppProperties props) {
        return args -> {
            Path upload = Path.of(props.getStorage().getUploadDir()).toAbsolutePath().normalize();
            Path logDir = Path.of(props.getLogging().getDir()).toAbsolutePath().normalize();
            try {
                Files.createDirectories(upload);
                Files.createDirectories(logDir);
                if (!Files.isWritable(upload)) {
                    fatalLog.error("FATAL upload root not writable: {}", upload);
                    throw new IllegalStateException("Upload directory not writable: " + upload);
                }
            } catch (Exception e) {
                fatalLog.error("FATAL cannot prepare directories: {}", e.getMessage());
                throw e;
            }
            log.info("Effective log level config={} uploadDir={} logDir={} devMode={} frontendUrl={}",
                    props.getLogging().getLevel(),
                    upload,
                    logDir,
                    props.getSecurity().isDevMode(),
                    props.getFrontendUrl());
        };
    }
}
