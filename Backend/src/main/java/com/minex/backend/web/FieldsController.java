package com.minex.backend.web;

import com.minex.backend.config.AppProps;
import com.minex.backend.domain.ExtractedField;
import com.minex.backend.service.CurrentUserService;
import com.minex.backend.service.FieldReviewService;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** §4.5 review queue + correction + approval workflow. */
@RestController
public class FieldsController {
    private final FieldReviewService review;
    private final CurrentUserService currentUser;
    private final AppProps props;

    public FieldsController(FieldReviewService review, CurrentUserService currentUser, AppProps props) {
        this.review = review;
        this.currentUser = currentUser;
        this.props = props;
    }

    public record FieldResponse(UUID id, UUID documentId, String documentName, String category, String period,
                                String fieldName, Double fieldValue, String fieldText, String unit,
                                double confidenceScore, boolean needsReview, String status, int version,
                                String priority, String reviewedBy) {
        static FieldResponse of(ExtractedField f, AppProps props) {
            double low = props.getExtraction().getLowConfidenceThreshold();
            double high = props.getExtraction().getConfidenceThreshold();
            // Review-triage priority: how much human intervention this row needs.
            String priority = (f.getCategory() == null || f.getConfidenceScore() < low) ? "critical"
                    : (f.getConfidenceScore() < high) ? "review"
                    : "ok";
            return new FieldResponse(f.getId(),
                    f.getDocument() == null ? null : f.getDocument().getId(),
                    f.getDocument() == null ? null : f.getDocument().getOriginalFilename(),
                    f.getCategory() == null ? null : f.getCategory().getName(),
                    f.getPeriod(), f.getFieldName(), f.getFieldValue(), f.getFieldText(),
                    f.getUnit(), f.getConfidenceScore(), f.isNeedsReview(),
                    f.getStatus(), f.getVersion(), priority,
                    f.getReviewedBy() == null ? null : f.getReviewedBy().getEmail());
        }
    }

    public record CorrectionRequest(Double fieldValue, String fieldText, String category) {}

    @GetMapping("/api/v1/documents/{id}/review-queue")
    @PreAuthorize("isAuthenticated()")
    public Page<FieldResponse> reviewQueue(@PathVariable("id") UUID documentId,
                                           @RequestParam(required = false, defaultValue = "pending_review") String status,
                                           Pageable pageable) {
        return review.reviewQueue(documentId, currentUser.requireCurrentUser(), status, pageable)
                .map(f -> FieldResponse.of(f, props));
    }

    /** All rejected figures across every document (newest version per field+period). */
    @GetMapping("/api/v1/fields/rejected")
    @PreAuthorize("isAuthenticated()")
    public Page<FieldResponse> rejected(Pageable pageable) {
        return review.rejectedQueue(pageable).map(f -> FieldResponse.of(f, props));
    }

    @PatchMapping("/api/v1/fields/{id}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbac.canCorrect()")
    public FieldResponse correct(@PathVariable UUID id, @RequestBody CorrectionRequest req) {
        return FieldResponse.of(review.correct(id, req.fieldValue(), req.fieldText(), req.category(),
                currentUser.requireCurrentUser()), props);
    }

    @PostMapping("/api/v1/fields/{id}/approve")
    @PreAuthorize("@rbac.canReview()")
    public FieldResponse approve(@PathVariable UUID id) {
        return FieldResponse.of(review.approve(id, currentUser.requireCurrentUser()), props);
    }

    @PostMapping("/api/v1/fields/{id}/reject")
    @PreAuthorize("@rbac.canReview()")
    public FieldResponse reject(@PathVariable UUID id) {
        return FieldResponse.of(review.reject(id, currentUser.requireCurrentUser()), props);
    }

    @PostMapping("/api/v1/fields/{id}/reopen")
    @PreAuthorize("@rbac.canReview()")
    public FieldResponse reopen(@PathVariable UUID id) {
        return FieldResponse.of(review.reopen(id, currentUser.requireCurrentUser()), props);
    }

    @PostMapping("/api/v1/fields/{id}/publish")
    @PreAuthorize("@rbac.canPublish()")
    public FieldResponse publish(@PathVariable UUID id) {
        // Method security is the coarse gate; FieldReviewService re-checks rank + four-eyes.
        return FieldResponse.of(review.publish(id, currentUser.requireCurrentUser()), props);
    }
}
