package com.minex.backend.service;

import com.minex.backend.domain.AppUser;
import com.minex.backend.domain.ApprovalRule;
import com.minex.backend.domain.ExtractedField;
import com.minex.backend.repo.ApprovalRuleRepository;
import com.minex.backend.repo.CategoryRepository;
import com.minex.backend.repo.ExtractedFieldRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * §4.5 human-in-the-loop workflow.
 * State machine: draft → pending_review → approved → published (rejected is terminal).
 * Corrections create a NEW version row; the old row is never overwritten.
 * Approvals enforce the configurable approval_rules table + four-eyes principle.
 */
@Service
public class FieldReviewService {

    private final ExtractedFieldRepository fields;
    private final ApprovalRuleRepository rules;
    private final CategoryRepository categories;
    private final DocumentService documents;
    private final AuditService audit;

    public FieldReviewService(ExtractedFieldRepository fields, ApprovalRuleRepository rules,
                              CategoryRepository categories, DocumentService documents, AuditService audit) {
        this.fields = fields;
        this.rules = rules;
        this.categories = categories;
        this.documents = documents;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<ExtractedField> reviewQueue(UUID documentId, AppUser viewer, Pageable pageable) {
        documents.get(documentId, viewer); // access check
        return fields.findByDocumentIdAndNeedsReviewTrue(documentId, pageable);
    }

    /** Data Corrector submits a correction: new version row, status pending_review. */
    @Transactional
    public ExtractedField correct(UUID fieldId, Double newValue, String newText, String categoryName,
                                  AppUser corrector) {
        ExtractedField current = require(fieldId);
        ExtractedField next = new ExtractedField();
        next.setDocument(current.getDocument());
        next.setCategory(resolveCategory(categoryName, current));
        next.setPeriod(current.getPeriod());
        next.setFieldName(current.getFieldName());
        next.setFieldValue(newValue);
        next.setFieldText(newText);
        next.setUnit(current.getUnit());
        next.setConfidenceScore(1.0); // human-verified value
        next.setNeedsReview(false);
        next.setStatus("pending_review");
        next.setVersion(current.getVersion() + 1);
        next.setCreatedBy(corrector);
        next.setCreatedAt(OffsetDateTime.now());
        fields.save(next);

        audit.logAs(corrector, "FIELD_CORRECTED", "extracted_field", next.getId(),
                "{\"from_value\":" + current.getFieldValue() + ",\"from_version\":" + current.getVersion() + "}",
                "{\"value\":" + newValue + ",\"version\":" + next.getVersion() + "}");
        return next;
    }

    @Transactional
    public ExtractedField approve(UUID fieldId, AppUser approver) {
        ExtractedField field = require(fieldId);
        if (!"pending_review".equals(field.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending_review fields can be approved");
        }
        checkMayApprove(approver, field);
        transition(field, approver, "approved", "FIELD_APPROVED");
        return field;
    }

    @Transactional
    public ExtractedField reject(UUID fieldId, AppUser approver) {
        ExtractedField field = require(fieldId);
        if (!"pending_review".equals(field.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending_review fields can be rejected");
        }
        checkMayApprove(approver, field);
        transition(field, approver, "rejected", "FIELD_REJECTED");
        return field;
    }

    /** Publishes approved data so dashboards (§4.5) and later Phase 2/3 can consume it. */
    @Transactional
    public ExtractedField publish(UUID fieldId, AppUser publisher) {
        ExtractedField field = require(fieldId);
        if (!"approved".equals(field.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only approved fields can be published");
        }
        if (!isAdmin(publisher) && publisher.getRole().getRank() < 30) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not permitted for your role");
        }
        checkFourEyes(publisher, field);
        transition(field, publisher, "published", "FIELD_PUBLISHED");
        return field;
    }

    // ---------- internals ----------

    private ExtractedField require(UUID id) {
        return fields.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Field not found"));
    }

    private com.minex.backend.domain.Category resolveCategory(String name, ExtractedField current) {
        if (name == null || name.isBlank()) return current.getCategory();
        return categories.findByName(name.strip())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown category"));
    }

    private void transition(ExtractedField field, AppUser actor, String toStatus, String auditAction) {
        String old = "{\"status\":\"" + field.getStatus() + "\"}";
        field.setStatus(toStatus);
        field.setReviewedBy(actor);
        fields.save(field);
        audit.logAs(actor, auditAction, "extracted_field", field.getId(),
                old, "{\"status\":\"" + toStatus + "\"}");
    }

    private void checkMayApprove(AppUser approver, ExtractedField field) {
        AppUser creator = field.getCreatedBy();
        if (creator == null) {
            // Machine-extracted row: any reviewer role (SUB_SUPERVISOR+) or ADMIN may approve.
            if (!isAdmin(approver) && approver.getRole().getRank() < 20) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not permitted for your role");
            }
            return;
        }
        if (!isAdmin(approver)) {
            List<ApprovalRule> matching =
                    rules.findByRoleAndCanApproveRole(creator.getRole(), approver.getRole());
            boolean scoped = matching.stream().anyMatch(r ->
                    r.getCategory() == null || (field.getCategory() != null
                            && r.getCategory().getId().equals(field.getCategory().getId())));
            if (matching.isEmpty() || !scoped) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not permitted for your role");
            }
        }
        checkFourEyes(approver, field);
    }

    /** Four-eyes: the approver must not be the user who created the draft. */
    private void checkFourEyes(AppUser actor, ExtractedField field) {
        if (field.getCreatedBy() != null && field.getCreatedBy().getId().equals(actor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Four-eyes principle: approver must differ from the creator");
        }
    }

    private boolean isAdmin(AppUser user) {
        return "ADMIN".equals(user.getRole().getName());
    }
}
