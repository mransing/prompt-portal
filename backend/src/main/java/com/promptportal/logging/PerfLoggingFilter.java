package com.promptportal.logging;

import com.promptportal.config.AppProperties;
import com.promptportal.security.AppUserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class PerfLoggingFilter extends OncePerRequestFilter {

    private static final Logger perfLog = LoggerFactory.getLogger("PERF");
    private static final Logger warnLog = LoggerFactory.getLogger(PerfLoggingFilter.class);

    private final AppProperties props;

    public PerfLoggingFilter(AppProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000L;
            String path = request.getRequestURI();
            String method = request.getMethod();
            int status = response.getStatus();
            String cid = MDC.get(CorrelationIdFilter.MDC_KEY);
            String user = resolveUser();
            perfLog.info("PERF method={} path={} status={} durationMs={} correlationId={} userId={}",
                    method, path, status, durationMs, cid, user);
            if (durationMs >= props.getPerf().getSlowMs()) {
                warnLog.warn("Slow request method={} path={} durationMs={} thresholdMs={}",
                        method, path, durationMs, props.getPerf().getSlowMs());
            }
        }
    }

    private String resolveUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal p) {
            return p.getUserId();
        }
        return "anonymous";
    }
}
