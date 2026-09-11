package com.minex.backend.service;

import com.minex.backend.domain.Category;
import com.minex.backend.domain.ExtractedField;
import com.minex.backend.repo.AnomalyRepository;
import com.minex.backend.repo.CategoryRepository;
import com.minex.backend.repo.DocumentRepository;
import com.minex.backend.repo.ExtractedFieldRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * §4.5 dashboard/visualization data API backing. Dashboards read APPROVED or
 * PUBLISHED data only — never pending_review (unverified figures).
 */
@Service
public class DashboardService {

    static final List<String> VISIBLE = List.of("approved", "published");

    private final CategoryRepository categories;
    private final ExtractedFieldRepository fields;
    private final DocumentRepository documents;
    private final AnomalyRepository anomalies;

    public DashboardService(CategoryRepository categories, ExtractedFieldRepository fields,
                            DocumentRepository documents, AnomalyRepository anomalies) {
        this.categories = categories;
        this.fields = fields;
        this.documents = documents;
        this.anomalies = anomalies;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> categories() {
        return categories.findAll().stream()
                .map(this::categoryCard)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> timeseries(UUID categoryId, String from, String to) {
        Category category = categories.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        return latestVisible(category).stream()
                .filter(f -> (from == null || (f.getPeriod() != null && f.getPeriod().compareTo(from) >= 0))
                        && (to == null || (f.getPeriod() != null && f.getPeriod().compareTo(to) <= 0)))
                .map(f -> {
                    // LinkedHashMap (not Map.of) so a genuinely missing period/unit
                    // serialises as JSON null — the chart buckets those as "Undated"
                    // instead of plotting a literal "null" category.
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("period", f.getPeriod());
                    point.put("field", f.getFieldName());
                    point.put("value", f.getFieldValue() == null ? 0 : f.getFieldValue());
                    point.put("unit", f.getUnit());
                    point.put("status", f.getStatus());
                    return point;
                })
                .toList();
    }

    /**
     * Approved/published figures collapsed to the newest version of each
     * field+period. Corrections create new rows and keep the old ones, so
     * without this the chart would plot stale values twice (and a category
     * card could report a superseded figure as "latest").
     */
    private List<ExtractedField> latestVisible(Category category) {
        Map<String, ExtractedField> latest = new LinkedHashMap<>();
        for (ExtractedField f : fields.findByCategoryAndStatusIn(category, VISIBLE)) {
            String key = f.getFieldName() + "\u0000" + f.getPeriod();
            ExtractedField prev = latest.get(key);
            if (prev == null || isNewer(f, prev)) {
                latest.put(key, f);
            }
        }
        return new ArrayList<>(latest.values());
    }

    /** Version wins; UUID breaks exact ties so the winner is deterministic. */
    private static boolean isNewer(ExtractedField a, ExtractedField b) {
        if (a.getVersion() != b.getVersion()) {
            return a.getVersion() > b.getVersion();
        }
        return a.getId().compareTo(b.getId()) > 0;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary() {
        return Map.of(
                "documentsTotal", documents.count(),
                "documentsProcessed", documents.countByStatus("PROCESSED"),
                "documentsQueuedForOcr", documents.countByStatus("QUEUED_FOR_OCR"),
                "fieldsPendingReview", pendingReviewCount(),
                "openAnomalies", anomalies.countByStatus("open"),
                "categories", categories());
    }

    /** Distinct figures still awaiting a decision (newest version per document+field+period). */
    private long pendingReviewCount() {
        return fields.countLatestPending();
    }

    private Map<String, Object> categoryCard(Category category) {
        List<ExtractedField> visible = latestVisible(category);
        ExtractedField latest = visible.stream()
                .filter(f -> f.getPeriod() != null)
                .max(Comparator.comparing(ExtractedField::getPeriod)
                        .thenComparing(ExtractedField::getVersion)
                        .thenComparing(ExtractedField::getId))
                .orElse(null);
        Map<String, Object> card = new java.util.LinkedHashMap<>();
        card.put("id", category.getId().toString());
        card.put("name", category.getName());
        card.put("publishedMetrics", (long) visible.size());
        card.put("latestPeriod", latest == null ? null : latest.getPeriod());
        card.put("latestField", latest == null ? null : latest.getFieldName());
        card.put("latestValue", latest == null ? null : latest.getFieldValue());
        card.put("latestUnit", latest == null ? null : latest.getUnit());
        return card;
    }
}
