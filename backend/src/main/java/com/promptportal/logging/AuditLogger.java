package com.promptportal.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class AuditLogger {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    public void log(String action, String actor, String resourceType, String resourceId, String outcome) {
        audit.info("AUDIT action={} actor={} resourceType={} resourceId={} outcome={} correlationId={}",
                action,
                nullToDash(actor),
                nullToDash(resourceType),
                nullToDash(resourceId),
                nullToDash(outcome),
                nullToDash(MDC.get(CorrelationIdFilter.MDC_KEY)));
    }

    private static String nullToDash(String v) {
        return v == null || v.isBlank() ? "-" : v;
    }
}
