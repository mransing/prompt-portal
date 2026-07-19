package com.promptportal.web;

import com.promptportal.config.AppProperties;
import com.promptportal.security.AppUserPrincipal;
import com.promptportal.security.SecurityUtils;
import com.promptportal.web.dto.PromptDtos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppProperties props;

    @Value("${AUTH_GOOGLE_ID:}")
    private String googleId;

    @Value("${AUTH_FACEBOOK_ID:}")
    private String facebookId;

    public AuthController(AppProperties props) {
        this.props = props;
    }

    @GetMapping("/me")
    public PromptDtos.UserInfo me() {
        AppUserPrincipal user = SecurityUtils.requireUser();
        return new PromptDtos.UserInfo(
                user.getUserId(),
                user.getEmail(),
                user.getName(),
                user.getProvider(),
                null,
                props.getSecurity().isDevMode()
        );
    }

    @GetMapping("/providers")
    public Map<String, Object> providers() {
        Map<String, Object> m = new LinkedHashMap<>();
        boolean google = googleId != null && !googleId.isBlank();
        boolean facebook = facebookId != null && !facebookId.isBlank();
        m.put("google", google);
        m.put("facebook", facebook);
        m.put("devMode", props.getSecurity().isDevMode());
        m.put("googleLoginUrl", google ? "/oauth2/authorization/google" : null);
        m.put("facebookLoginUrl", facebook ? "/oauth2/authorization/facebook" : null);
        m.put("frontendUrl", props.getFrontendUrl());
        return m;
    }
}
