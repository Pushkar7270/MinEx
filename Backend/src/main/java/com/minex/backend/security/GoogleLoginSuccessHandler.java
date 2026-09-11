package com.minex.backend.security;

import com.minex.backend.domain.AppUser;
import com.minex.backend.repo.RoleRepository;
import com.minex.backend.repo.UserRepository;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Google Sign-In landing: finds the platform account by Google subject (or by
 * email for first link), creates it with the default role when new, then
 * issues our own JWT and hands it to the frontend callback. Role checks after
 * this point are unchanged — teammate RBAC work merges on top untouched.
 */
@Component
public class GoogleLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository users;
    private final RoleRepository roles;
    private final JwtService jwt;

    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    @Value("${app.oauth.default-role}")
    private String defaultRole;

    public GoogleLoginSuccessHandler(UserRepository users, RoleRepository roles, JwtService jwt) {
        this.users = users;
        this.roles = roles;
        this.jwt = jwt;
    }

    @Override
    public void onAuthenticationSuccess(jakarta.servlet.http.HttpServletRequest request,
                                        jakarta.servlet.http.HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OidcUser oidc = (OidcUser) authentication.getPrincipal();
        String subject = oidc.getSubject();
        String email = oidc.getEmail();

        AppUser user = users.findByProviderAndProviderSubject("google", subject)
                .or(() -> users.findByEmailIgnoreCase(email))
                .orElse(null);
        boolean firstTime = (user == null);
        if (firstTime) {
            user = createUser(email, oidc.getFullName());
        }

        // Link Google identity on first Google login for password-era accounts.
        if (user.getProviderSubject() == null) {
            user.setProvider("google");
            user.setProviderSubject(subject);
            users.save(user);
        }

        String token = jwt.generateAccessToken(
                user.getEmail(), Map.of("role", user.getRole().getName()));
        response.sendRedirect(frontendBaseUrl + "/oauth/callback?token=" + token
                + (firstTime ? "&newUser=true" : ""));
    }

    private AppUser createUser(String email, String name) {
        var role = roles.findByName(defaultRole).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Default role missing"));
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(null); // Google-only: no local password
        user.setFullName(name == null ? email : name);
        user.setRole(role);
        user.setActive(true);
        user.setProvider("google");
        user.setCreatedAt(OffsetDateTime.now());
        return users.save(user);
    }
}
