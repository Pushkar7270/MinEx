package com.minex.backend.web;

import com.minex.backend.domain.AuditLog;
import com.minex.backend.service.AuditService;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only audit trail (PRD §2): who uploaded / changed / approved / rejected
 * what, and when. Restricted to ADMIN and MANAGER via {@code app.rbac.audit-rank}.
 */
@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("@rbac.canViewAudit()")
public class AuditController {
    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    public record AuditResponse(UUID id, UUID userId, String userEmail, String userName, String userRole,
                                String action, String entityType, UUID entityId,
                                String oldValue, String newValue, String createdAt) {
        static AuditResponse of(AuditLog a) {
            var user = a.getUser();
            return new AuditResponse(a.getId(),
                    user == null ? null : user.getId(),
                    user == null ? null : user.getEmail(),
                    user == null ? null : user.getFullName(),
                    user == null || user.getRole() == null ? null : user.getRole().getName(),
                    a.getAction(), a.getEntityType(), a.getEntityId(),
                    a.getOldValue(), a.getNewValue(),
                    a.getCreatedAt() == null ? null : a.getCreatedAt().toString());
        }
    }

    @GetMapping
    public Page<AuditResponse> list(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID userId,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return audit.search(action, entityType, userId, pageable).map(AuditResponse::of);
    }

    /** Distinct action codes recorded so far (filter options). */
    @GetMapping("/actions")
    public List<String> actions() {
        return audit.distinctActions();
    }
}
