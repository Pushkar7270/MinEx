package com.minex.backend.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Foundation auth skeleton (PRD §2/§9): no anonymous data access, JWT stateless,
 * RBAC enforced per-endpoint via method security ({@code @PreAuthorize}).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    private final JwtAuthFilter jwtFilter;
    private final GoogleLoginSuccessHandler googleSuccess;

    public SecurityConfig(JwtAuthFilter jwtFilter, GoogleLoginSuccessHandler googleSuccess) {
        this.jwtFilter = jwtFilter;
        this.googleSuccess = googleSuccess;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
            ObjectProvider<org.springframework.security.oauth2.client.registration.ClientRegistrationRepository> oauthRepo)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/auth/**", "/actuator/health", "/error",
                                "/oauth2/**", "/login/oauth2/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        // Google Sign-In only when GOOGLE_CLIENT_ID is configured; otherwise
        // the app boots as password-login-only (teammate auth merges here).
        if (oauthRepo.getIfAvailable() != null) {
            http.oauth2Login(o -> o.successHandler(googleSuccess));
        }
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
