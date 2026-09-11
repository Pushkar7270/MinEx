package com.minex.backend.service;

import com.minex.backend.domain.Category;
import com.minex.backend.domain.ExtractedField;
import com.minex.backend.repo.AnomalyRepository;
import com.minex.backend.repo.CategoryRepository;
import com.minex.backend.repo.DocumentRepository;
import com.minex.backend.repo.ExtractedFieldRepository;
import java.util.Comparator;
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
        return fields.findByCategoryAndStatusInOrderByPeriodAsc(category, VISIBLE).stream()
                .filter(f -> (from == null || (f.getPeriod() != null && f.getPeriod().compareTo(from) >= 0))
                        && (to == null || (f.getPeriod() != null && f.getPeriod().compareTo(to) <= 0)))
                .map(f -> Map.<String, Object>of(
                        "period", String.valueOf(f.getPeriod()),
                        "field", f.getFieldName(),
                        "value", f.getFieldValue() == null ? 0 : f.getFieldValue(),
                        "unit", String.valueOf(f.getUnit()),
                        "status", f.getStatus()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary() {
        return Map.of(
                "documentsTotal", documents.count(),
                "documentsProcessed", documents.countByStatus("PROCESSED"),
                "documentsQueuedForOcr", documents.countByStatus("QUEUED_FOR_OCR"),
                "fieldsPendingReview", fields.findByStatus("pending_review",
                        org.springframework.data.domain.Pageable.unpaged()).getTotalElements(),
                "openAnomalies", anomalies.countByStatus("open"),
                "categories", categories());
    }

    private Map<String, Object> categoryCard(Category category) {
        List<ExtractedField> visible = fields.findByCategoryAndStatusIn(category, VISIBLE);
        ExtractedField latest = visible.stream()
                .filter(f -> f.getPeriod() != null)
                .max(Comparator.comparing(ExtractedField::getPeriod))
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
