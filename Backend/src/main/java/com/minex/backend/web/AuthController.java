package com.minex.backend.web;

import com.minex.backend.repo.UserRepository;
import com.minex.backend.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Auth skeleton: login with email+password, receive a JWT access token. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthenticationManager authManager;
    private final JwtService jwt;
    private final UserRepository users;

    public AuthController(AuthenticationManager authManager, JwtService jwt, UserRepository users) {
        this.authManager = authManager;
        this.jwt = jwt;
        this.users = users;
    }

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
    public record AuthResponse(String accessToken, String email, String role) {}

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        try {
            authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.email(), req.password()));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        var user = users.findByEmailIgnoreCase(req.email()).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        String token = jwt.generateAccessToken(
                user.getEmail(), Map.of("role", user.getRole().getName()));
        return new AuthResponse(token, user.getEmail(), user.getRole().getName());
    }

    @GetMapping("/me")
    public Map<String, String> me(org.springframework.security.core.Authentication auth) {
        return Map.of("email", auth.getName());
    }
}
