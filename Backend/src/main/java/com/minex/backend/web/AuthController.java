package com.minex.backend.web;

import com.minex.backend.repo.RoleRepository;
import com.minex.backend.repo.UserRepository;
import com.minex.backend.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Auth: local login + Google Sign-In landing + profile.
 * Role model is unchanged (one role per user) — roles now carry a
 * Discord-style display color served via /me for profile badges.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthenticationManager authManager;
    private final JwtService jwt;
    private final UserRepository users;
    private final RoleRepository roles;

    @Value("${GOOGLE_CLIENT_ID:}")
    private String googleClientId;

    public AuthController(AuthenticationManager authManager, JwtService jwt, UserRepository users,
                          RoleRepository roles) {
        this.authManager = authManager;
        this.jwt = jwt;
        this.users = users;
        this.roles = roles;
    }

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
    public record AuthResponse(String accessToken, String email, String role) {}

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        var user = users.findByEmailIgnoreCase(req.email()).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!"local".equals(user.getProvider()) && user.getPasswordHash() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in with Google");
        }
        try {
            authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.email(), req.password()));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        String token = jwt.generateAccessToken(
                user.getEmail(), Map.of("role", user.getRole().getName()));
        return new AuthResponse(token, user.getEmail(), user.getRole().getName());
    }

    /** Profile for the Discord-style header badge (name, role, color). */
    @GetMapping("/me")
    public Map<String, String> me(org.springframework.security.core.Authentication auth) {
        var user = users.findByEmailIgnoreCase(auth.getName()).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        return Map.of(
                "email", user.getEmail(),
                "fullName", user.getFullName() == null ? user.getEmail() : user.getFullName(),
                "role", user.getRole().getName(),
                "roleColor", user.getRole().getColor() == null ? "#8b6cc1" : user.getRole().getColor(),
                "roleRank", String.valueOf(user.getRole().getRank()),
                "provider", user.getProvider() == null ? "local" : user.getProvider());
    }

    /** Tells the login page whether Google Sign-In is configured. */
    @GetMapping("/providers")
    public Map<String, Object> providers() {
        return Map.of(
                "google", !googleClientId.isBlank(),
                "googleUrl", !googleClientId.isBlank() ? "/oauth2/authorization/google" : "");
    }

    @GetMapping("/roles")
    public java.util.List<Map<String, Object>> roles(org.springframework.security.core.Authentication auth) {
        // Hierarchy display (Discord-style): all roles ordered by rank.
        String mine = users.findByEmailIgnoreCase(auth.getName())
                .map(u -> u.getRole().getName()).orElse("");
        return roles.findAll().stream()
                .sorted((a, b) -> Integer.compare(b.getRank(), a.getRank()))
                .map(r -> Map.<String, Object>of(
                        "name", r.getName(),
                        "color", r.getColor() == null ? "#8b6cc1" : r.getColor(),
                        "rank", r.getRank(),
                        "mine", r.getName().equals(mine)))
                .toList();
    }
}
