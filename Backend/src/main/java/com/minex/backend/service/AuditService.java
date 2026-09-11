package com.minex.backend.service;

import com.minex.backend.domain.AppUser;
import com.minex.backend.domain.AuditLog;
import com.minex.backend.repo.AuditLogRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
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
}
