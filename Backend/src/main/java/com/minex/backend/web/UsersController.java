package com.minex.backend.web;

import com.minex.backend.domain.AppUser;
import com.minex.backend.repo.RoleRepository;
import com.minex.backend.repo.UserRepository;
import com.minex.backend.service.AuditService;
import com.minex.backend.service.CurrentUserService;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin user management. Role changes are sensitive (PRD §2): ADMIN-only,
 * audited, and nobody — not even an admin — can change their own role
 * (no self-demotion lockouts, four-eyes by construction).
 */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("@rbac.canManageUsers()")
public class UsersController {
    private final UserRepository users;
    private final RoleRepository roles;
    private final CurrentUserService currentUser;
    private final AuditService audit;

    public UsersController(UserRepository users, RoleRepository roles,
                           CurrentUserService currentUser, AuditService audit) {
        this.users = users;
        this.roles = roles;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    public record UserResponse(UUID id, String email, String fullName, String role,
                               String roleColor, String provider) {
        static UserResponse of(AppUser u) {
            return new UserResponse(u.getId(), u.getEmail(), u.getFullName(),
                    u.getRole().getName(),
                    u.getRole().getColor() == null ? "#8b6cc1" : u.getRole().getColor(),
                    u.getProvider() == null ? "local" : u.getProvider());
        }
    }

    public record RoleChangeRequest(@NotBlank String role) {}

    @GetMapping
    public Page<UserResponse> list(Pageable pageable) {
        return users.findAll(pageable).map(UserResponse::of);
    }

    @PatchMapping("/{id}/role")
    public UserResponse changeRole(@PathVariable UUID id, @RequestBody RoleChangeRequest req) {
        AppUser actor = currentUser.requireCurrentUser();
        AppUser target = users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (target.getId().equals(actor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot change your own role");
        }
        var role = roles.findByName(req.role().trim().toUpperCase(java.util.Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role"));
        String old = "{\"role\":\"" + target.getRole().getName() + "\"}";
        target.setRole(role);
        users.save(target);
        audit.logAs(actor, "ROLE_CHANGED", "user", target.getId(),
                old, "{\"role\":\"" + role.getName() + "\"}");
        return UserResponse.of(target);
    }
}
