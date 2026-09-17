package com.promptportal.security;

import com.promptportal.config.AppProperties;
import com.promptportal.domain.UserAccount;
import com.promptportal.logging.AuditLogger;
import com.promptportal.repository.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Local/dev authentication when OAuth is not configured.
 * Send header: X-Dev-User-Email: you@example.com
 * If ALLOWED_EMAILS is set, email must be on the allowlist.
 */
public class DevAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Dev-User-Email";

    private final AppProperties props;
    private final UserAccountRepository users;
    private final AuditLogger auditLogger;

    public DevAuthFilter(AppProperties props, UserAccountRepository users, AuditLogger auditLogger) {
        this.props = props;
        this.users = users;
        this.auditLogger = auditLogger;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (props.getSecurity().isDevMode()
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String raw = request.getHeader(HEADER);
            if (raw != null && !raw.isBlank()) {
                try {
                    if (!authenticateDevUser(raw, response)) {
                        return;
                    }
                } catch (Exception e) {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    response.setContentType("application/json");
                    response.getWriter().write(
                            "{\"code\":\"INTERNAL_ERROR\",\"message\":\"Login failed. Try again.\",\"correlationId\":null,\"details\":null}");
                    return;
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    /** @return false if the response was already written (deny / error) */
    private boolean authenticateDevUser(String raw, HttpServletResponse response) throws IOException {
        final String email = raw.trim().toLowerCase(Locale.ROOT);
        Set<String> allowed = props.getSecurity().allowedEmailSet();
        if (!allowed.isEmpty() && !allowed.contains(email)) {
            auditLogger.log("AUTH_ALLOWLIST_DENY", email, "user", "-", "denied");
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"code\":\"FORBIDDEN\",\"message\":\"Email is not allowlisted.\",\"correlationId\":null,\"details\":null}");
            return false;
        }

        List<UserAccount> matches = users.findAllByEmailIgnoreCase(email);
        UserAccount account;
        if (matches.isEmpty()) {
            UserAccount u = new UserAccount();
            u.setEmail(email);
            u.setName(email.split("@")[0]);
            u.setProvider("dev");
            u.setProviderId(email);
            u.setCreatedAt(Instant.now());
            account = users.save(u);
        } else {
            matches.sort(Comparator.comparing(
                    UserAccount::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
            account = matches.get(0);
        }

        AppUserPrincipal principal = new AppUserPrincipal(
                account.getId(), account.getEmail(), account.getName(), "dev", Map.of("email", email));
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        return true;
    }
}
