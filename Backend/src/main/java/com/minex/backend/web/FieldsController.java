package com.minex.backend.web;

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

    public FieldsController(FieldReviewService review, CurrentUserService currentUser) {
        this.review = review;
        this.currentUser = currentUser;
    }

    public record FieldResponse(UUID id, UUID documentId, String category, String period,
                                String fieldName, Double fieldValue, String fieldText, String unit,
                                double confidenceScore, boolean needsReview, String status, int version) {
        static FieldResponse of(ExtractedField f) {
            return new FieldResponse(f.getId(),
                    f.getDocument() == null ? null : f.getDocument().getId(),
                    f.getCategory() == null ? null : f.getCategory().getName(),
                    f.getPeriod(), f.getFieldName(), f.getFieldValue(), f.getFieldText(),
                    f.getUnit(), f.getConfidenceScore(), f.isNeedsReview(),
                    f.getStatus(), f.getVersion());
        }
    }

    public record CorrectionRequest(Double fieldValue, String fieldText, String category) {}

    @GetMapping("/api/v1/documents/{id}/review-queue")
    @PreAuthorize("isAuthenticated()")
    public Page<FieldResponse> reviewQueue(@PathVariable("id") UUID documentId, Pageable pageable) {
        return review.reviewQueue(documentId, currentUser.requireCurrentUser(), pageable)
                .map(FieldResponse::of);
    }

    @PatchMapping("/api/v1/fields/{id}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('DATA_CORRECTOR','SUB_SUPERVISOR','SUPERVISOR','MANAGER','ADMIN')")
    public FieldResponse correct(@PathVariable UUID id, @RequestBody CorrectionRequest req) {
        return FieldResponse.of(review.correct(id, req.fieldValue(), req.fieldText(), req.category(),
                currentUser.requireCurrentUser()));
    }

    @PostMapping("/api/v1/fields/{id}/approve")
    @PreAuthorize("hasAnyRole('SUB_SUPERVISOR','SUPERVISOR','MANAGER','ADMIN')")
    public FieldResponse approve(@PathVariable UUID id) {
        return FieldResponse.of(review.approve(id, currentUser.requireCurrentUser()));
    }

    @PostMapping("/api/v1/fields/{id}/reject")
    @PreAuthorize("hasAnyRole('SUB_SUPERVISOR','SUPERVISOR','MANAGER','ADMIN')")
    public FieldResponse reject(@PathVariable UUID id) {
        return FieldResponse.of(review.reject(id, currentUser.requireCurrentUser()));
    }

    @PostMapping("/api/v1/fields/{id}/publish")
    @PreAuthorize("hasAnyRole('SUPERVISOR','MANAGER','ADMIN')")
    public FieldResponse publish(@PathVariable UUID id) {
        // Method security is the coarse gate; FieldReviewService re-checks rank + four-eyes.
        return FieldResponse.of(review.publish(id, currentUser.requireCurrentUser()));
    }
}
