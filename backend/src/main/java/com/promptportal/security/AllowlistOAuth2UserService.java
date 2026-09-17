package com.promptportal.security;

import com.promptportal.config.AppProperties;
import com.promptportal.domain.UserAccount;
import com.promptportal.logging.AuditLogger;
import com.promptportal.repository.UserAccountRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AllowlistOAuth2UserService extends DefaultOAuth2UserService {

    private final AppProperties props;
    private final UserAccountRepository users;
    private final AuditLogger auditLogger;

    public AllowlistOAuth2UserService(AppProperties props, UserAccountRepository users, AuditLogger auditLogger) {
        this.props = props;
        this.users = users;
        this.auditLogger = auditLogger;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauthUser = super.loadUser(userRequest);
        String provider = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attrs = oauthUser.getAttributes();

        String email = extractEmail(provider, attrs);
        if (email == null || email.isBlank()) {
            auditLogger.log("AUTH_LOGIN_FAILURE", "-", "user", "-", "missing_email");
            throw new OAuth2AuthenticationException(new OAuth2Error("email_required"),
                    "Email is required from the identity provider.");
        }
        email = email.toLowerCase(Locale.ROOT);

        Set<String> allowed = props.getSecurity().allowedEmailSet();
        if (!allowed.isEmpty() && !allowed.contains(email)) {
            auditLogger.log("AUTH_ALLOWLIST_DENY", email, "user", "-", "denied");
            throw new OAuth2AuthenticationException(new OAuth2Error("allowlist"),
                    "This account is not authorized to use Prompt Portal.");
        }

        String providerId = String.valueOf(attrs.getOrDefault("sub", attrs.getOrDefault("id", email)));
        String name = stringAttr(attrs, "name");
        if (name == null) {
            name = stringAttr(attrs, "login");
        }
        String picture = extractPicture(provider, attrs);

        String finalEmail = email;
        UserAccount account = users.findByProviderAndProviderId(provider, providerId)
                .or(() -> users.findAllByEmailIgnoreCase(finalEmail).stream().findFirst())
                .orElseGet(UserAccount::new);

        account.setEmail(email);
        account.setName(name);
        account.setImage(picture);
        account.setProvider(provider);
        account.setProviderId(providerId);
        if (account.getCreatedAt() == null) {
            account.setCreatedAt(Instant.now());
        }
        account = users.save(account);

        auditLogger.log("AUTH_LOGIN_SUCCESS", account.getId(), "user", account.getId(), "ok");
        return new AppUserPrincipal(account.getId(), account.getEmail(), account.getName(), provider, attrs);
    }

    private static String extractEmail(String provider, Map<String, Object> attrs) {
        Object email = attrs.get("email");
        if (email != null) {
            return email.toString();
        }
        return null;
    }

    private static String extractPicture(String provider, Map<String, Object> attrs) {
        Object picture = attrs.get("picture");
        if (picture instanceof String s) {
            return s;
        }
        if (picture instanceof Map<?, ?> m) {
            Object data = m.get("data");
            if (data instanceof Map<?, ?> d && d.get("url") != null) {
                return d.get("url").toString();
            }
        }
        return null;
    }

    private static String stringAttr(Map<String, Object> attrs, String key) {
        Object v = attrs.get(key);
        return v != null ? v.toString() : null;
    }
}
