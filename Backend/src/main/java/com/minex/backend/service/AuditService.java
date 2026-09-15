package com.minex.backend.service;

import com.minex.backend.domain.AppUser;
import com.minex.backend.domain.AuditLog;
import com.minex.backend.repo.AuditLogRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Append-only audit trail. Every state transition in Phase 1 goes through here
 * (PRD §2: who, what, when, old value, new value).
 */
@Service
public class AuditService {
    private final AuditLogRepository audit;
    private final CurrentUserService currentUser;

    public AuditService(AuditLogRepository audit, CurrentUserService currentUser) {
        this.audit = audit;
        this.currentUser = currentUser;
    }

    @Transactional
    public void log(String action, String entityType, UUID entityId, String oldJson, String newJson) {
        AppUser actor = currentUser.requireCurrentUser();
        logAs(actor, action, entityType, entityId, oldJson, newJson);
    }

    @Transactional
    public void logAs(AppUser actor, String action, String entityType, UUID entityId,
                      String oldJson, String newJson) {
        AuditLog entry = new AuditLog();
        entry.setUser(actor);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setOldValue(oldJson);
        entry.setNewValue(newJson);
        entry.setCreatedAt(OffsetDateTime.now());
        audit.save(entry);
    }

    /**
     * Read side of the trail: paginated, newest first, with optional filters.
     * Callers must already hold the {@code canViewAudit} capability.
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> search(String action, String entityType, UUID userId, Pageable pageable) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            // Fetch the actor eagerly (returning the page to the web layer after
            // this read-only transaction closes; open-in-view is off).
            if (!Long.class.equals(query.getResultType())) {
                root.fetch("user", JoinType.LEFT);
            }
            List<Predicate> predicates = new ArrayList<>();
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action.trim()));
            }
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), entityType.trim()));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), userId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return audit.findAll(spec, pageable);
    }

    /** Distinct action codes recorded so far, for filter options. */
    @Transactional(readOnly = true)
    public List<String> distinctActions() {
        return audit.findDistinctActions();
    }
}
