package com.promptportal.security;

import com.promptportal.config.AppProperties;
import com.promptportal.logging.AuditLogger;
import com.promptportal.repository.UserAccountRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.http.HttpStatus;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AppProperties props;
    private final AllowlistOAuth2UserService oauth2UserService;
    private final UserAccountRepository users;
    private final AuditLogger auditLogger;

    @Value("${AUTH_GOOGLE_ID:}")
    private String googleId;

    @Value("${AUTH_FACEBOOK_ID:}")
    private String facebookId;

    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    public SecurityConfig(AppProperties props,
                          AllowlistOAuth2UserService oauth2UserService,
                          UserAccountRepository users,
                          AuditLogger auditLogger) {
        this.props = props;
        this.oauth2UserService = oauth2UserService;
        this.users = users;
        this.auditLogger = auditLogger;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        boolean oauthConfigured = isOAuthConfigured();

        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api/auth/**", "/oauth2/**", "/login/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((req, res, e) -> {
                            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            res.setContentType("application/json");
                            res.getWriter().write(
                                    "{\"code\":\"FORBIDDEN\",\"message\":\"Access denied.\",\"correlationId\":null,\"details\":null}");
                        })
                );

        if (oauthConfigured) {
            http.oauth2Login(oauth -> oauth
                    .userInfoEndpoint(ui -> ui.userService(oauth2UserService))
                    .defaultSuccessUrl(props.getFrontendUrl() + "/library", true)
                    .failureHandler((req, res, ex) -> {
                        auditLogger.log("AUTH_LOGIN_FAILURE", "-", "user", "-", "oauth_error");
                        res.sendRedirect(props.getFrontendUrl() + "/login?error=auth");
                    })
            );
            http.logout(logout -> logout
                    .logoutUrl("/api/auth/logout")
                    .logoutSuccessHandler((req, res, auth) -> {
                        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal p) {
                            auditLogger.log("AUTH_LOGOUT", p.getUserId(), "user", p.getUserId(), "ok");
                        }
                        res.setStatus(HttpServletResponse.SC_NO_CONTENT);
                    })
            );
        } else {
            http.logout(logout -> logout
                    .logoutUrl("/api/auth/logout")
                    .logoutSuccessHandler((req, res, auth) -> res.setStatus(HttpServletResponse.SC_NO_CONTENT))
            );
        }

        if (props.getSecurity().isDevMode()) {
            http.addFilterBefore(new DevAuthFilter(props, users, auditLogger),
                    UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }

    private boolean isOAuthConfigured() {
        boolean profile = activeProfiles != null && activeProfiles.contains("oauth");
        boolean ids = (googleId != null && !googleId.isBlank())
                || (facebookId != null && !facebookId.isBlank() && !"unused".equals(facebookId));
        return profile && ids;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(props.getFrontendUrl(), "http://localhost:3000", "http://127.0.0.1:3000"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-Correlation-Id", "Content-Disposition"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
