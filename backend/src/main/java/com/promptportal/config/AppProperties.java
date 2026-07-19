package com.promptportal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Security security = new Security();
    private final Storage storage = new Storage();
    private final Logging logging = new Logging();
    private final Perf perf = new Perf();
    private final Retention retention = new Retention();
    private String frontendUrl = "http://localhost:3000";

    public Security getSecurity() {
        return security;
    }

    public Storage getStorage() {
        return storage;
    }

    public Logging getLogging() {
        return logging;
    }

    public Perf getPerf() {
        return perf;
    }

    public Retention getRetention() {
        return retention;
    }

    public String getFrontendUrl() {
        return frontendUrl;
    }

    public void setFrontendUrl(String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    public static class Security {
        private boolean devMode = true;
        private String allowedEmails = "";
        private String authSecret = "change-me";

        public boolean isDevMode() {
            return devMode;
        }

        public void setDevMode(boolean devMode) {
            this.devMode = devMode;
        }

        public String getAllowedEmails() {
            return allowedEmails;
        }

        public void setAllowedEmails(String allowedEmails) {
            this.allowedEmails = allowedEmails;
        }

        public String getAuthSecret() {
            return authSecret;
        }

        public void setAuthSecret(String authSecret) {
            this.authSecret = authSecret;
        }

        public Set<String> allowedEmailSet() {
            if (allowedEmails == null || allowedEmails.isBlank()) {
                return Set.of();
            }
            return Arrays.stream(allowedEmails.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
        }
    }

    public static class Storage {
        private String uploadDir = "../data/uploads";
        private long maxUploadBytes = 2_097_152L;
        private long quotaBytes = 52_428_800L;

        public String getUploadDir() {
            return uploadDir;
        }

        public void setUploadDir(String uploadDir) {
            this.uploadDir = uploadDir;
        }

        public long getMaxUploadBytes() {
            return maxUploadBytes;
        }

        public void setMaxUploadBytes(long maxUploadBytes) {
            this.maxUploadBytes = maxUploadBytes;
        }

        public long getQuotaBytes() {
            return quotaBytes;
        }

        public void setQuotaBytes(long quotaBytes) {
            this.quotaBytes = quotaBytes;
        }
    }

    public static class Logging {
        private String level = "INFO";
        private String dir = "../logs";

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }

        public String getDir() {
            return dir;
        }

        public void setDir(String dir) {
            this.dir = dir;
        }
    }

    public static class Perf {
        private long slowMs = 2000L;

        public long getSlowMs() {
            return slowMs;
        }

        public void setSlowMs(long slowMs) {
            this.slowMs = slowMs;
        }
    }

    public static class Retention {
        private boolean enabled = true;
        private int days = 730;
        private String cron = "0 30 3 * * *";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getDays() {
            return days;
        }

        public void setDays(int days) {
            this.days = days;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }
    }
}
