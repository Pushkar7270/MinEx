package com.minex.backend.service;

import com.minex.backend.domain.Anomaly;
import com.minex.backend.domain.AppUser;
import com.minex.backend.domain.Document;
import com.minex.backend.domain.ExtractedField;
import com.minex.backend.repo.AnomalyRepository;
import com.minex.backend.repo.ExtractedFieldRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * §4.4 cross-field validation & fraud-reduction. Flags only — never silently
 * auto-corrects; a human per RBAC rules must approve any correction.
 */
@Service
public class ValidationService {

    /** YoY deviation that triggers a flag (configurable later per category). */
    static final double YOY_THRESHOLD = 0.30;

    private final ExtractedFieldRepository fields;
    private final AnomalyRepository anomalies;
    private final AuditService audit;

    public ValidationService(ExtractedFieldRepository fields, AnomalyRepository anomalies,
                             AuditService audit) {
        this.fields = fields;
        this.anomalies = anomalies;
        this.audit = audit;
    }

    @Transactional
    public void validate(Document doc, List<ExtractedField> fresh, AppUser actor) {
        for (ExtractedField field : fresh) {
            if (field.getFieldValue() == null) continue;
            if (field.getFieldValue() < 0) {
                flag(field, actor, "NEGATIVE_VALUE",
                        "Negative value " + field.getFieldValue() + " for '" + field.getFieldName() + "'");
                continue;
            }
            checkYearOverYear(doc, field, actor);
        }
    }

    private void checkYearOverYear(Document doc, ExtractedField field, AppUser actor) {
        if (field.getPeriod() == null || field.getCategory() == null) return;
        List<ExtractedField> history =
                fields.findByCategoryAndFieldNameIgnoreCaseOrderByPeriodAsc(
                        field.getCategory(), field.getFieldName());
        ExtractedField prior = history.stream()
                .filter(h -> !h.getId().equals(field.getId())
                        && h.getPeriod() != null
                        && h.getPeriod().compareTo(field.getPeriod()) < 0
                        && h.getFieldValue() != null)
                .max(Comparator.comparing(ExtractedField::getPeriod))
                .orElse(null);
        if (prior == null || prior.getFieldValue() == 0) return;
        double deviation = Math.abs(field.getFieldValue() - prior.getFieldValue())
                / Math.abs(prior.getFieldValue());
        if (deviation > YOY_THRESHOLD) {
            int pct = (int) Math.round(deviation * 100);
            flag(field, actor, "YOY_DEVIATION",
                    "Value " + field.getFieldValue() + " deviates " + pct + "% from prior period "
                            + prior.getPeriod() + " value " + prior.getFieldValue());
        }
    }

    private void flag(ExtractedField field, AppUser actor, String rule, String description) {
        Anomaly anomaly = new Anomaly();
        anomaly.setExtractedField(field);
        anomaly.setRuleName(rule);
        anomaly.setDescription(description);
        anomaly.setStatus("open");
        anomaly.setDetectedAt(OffsetDateTime.now());
        anomalies.save(anomaly);

        if (!field.isNeedsReview()) {
            field.setNeedsReview(true);
            fields.save(field);
        }
        if (actor != null) {
            audit.logAs(actor, "VALIDATION_FLAGGED", "extracted_field", field.getId(), null,
                    "{\"rule\":\"" + rule + "\"}");
        }
    }

    /** Pure helper for tests: deviation ratio between two values. */
    static double deviation(double current, double prior) {
        if (prior == 0) return 0;
        return Math.abs(current - prior) / Math.abs(prior);
    }
}
